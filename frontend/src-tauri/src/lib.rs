use serde::{Deserialize, Serialize};
use std::fs::{self, File};
use std::io::{Read, Write};
use std::net::{SocketAddr, TcpStream};
use std::path::PathBuf;
use std::process::{Child, Command, Stdio};
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::Duration;
use tauri::{Manager, State};

const BACKEND_ADDRESS: &str = "127.0.0.1:18080";
const READY_ATTEMPTS: usize = 60;

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct DesktopBackendStatus {
    state: String,
    detail: String,
    managed: bool,
    pid: Option<u32>,
}

struct BackendProcessState {
    status: DesktopBackendStatus,
    child: Option<Child>,
}

struct DesktopBackend(Arc<Mutex<BackendProcessState>>);

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DesktopApiRequest {
    path: String,
    method: String,
    body: Option<String>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct DesktopApiResponse {
    status: u16,
    body: String,
}

#[derive(Debug, PartialEq)]
enum StartupDecision {
    Reuse,
    Start,
    PortInUse,
}

fn startup_decision(port_open: bool, localrag_ready: bool) -> StartupDecision {
    match (port_open, localrag_ready) {
        (_, true) => StartupDecision::Reuse,
        (false, false) => StartupDecision::Start,
        (true, false) => StartupDecision::PortInUse,
    }
}

fn validate_api_request(path: &str, method: &str) -> Result<(), String> {
    if !path.starts_with("/api/")
        || path.contains("..")
        || path.contains("://")
        || path.contains('\\')
    {
        return Err("Desktop API는 LocalRAG /api 경로만 허용합니다.".into());
    }
    if !matches!(method, "GET" | "POST" | "PUT" | "PATCH") {
        return Err("Desktop API method가 허용되지 않습니다.".into());
    }
    Ok(())
}

fn request_contains(path: &str, expected: &str) -> bool {
    let address: SocketAddr = match BACKEND_ADDRESS.parse() {
        Ok(address) => address,
        Err(_) => return false,
    };
    let mut stream = match TcpStream::connect_timeout(&address, Duration::from_millis(350)) {
        Ok(stream) => stream,
        Err(_) => return false,
    };
    let _ = stream.set_read_timeout(Some(Duration::from_millis(750)));
    let _ = stream.set_write_timeout(Some(Duration::from_millis(750)));
    let request = format!("GET {path} HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n");
    if stream.write_all(request.as_bytes()).is_err() {
        return false;
    }
    let mut response = String::new();
    stream.read_to_string(&mut response).is_ok()
        && response.starts_with("HTTP/1.1 200")
        && response.contains(expected)
}

fn localrag_ready() -> bool {
    request_contains("/api/health", "\"status\":\"UP\"")
        && request_contains("/api/workspaces/discovery", "\"workspaceRoot\"")
}

fn port_open() -> bool {
    BACKEND_ADDRESS
        .parse::<SocketAddr>()
        .ok()
        .and_then(|address| TcpStream::connect_timeout(&address, Duration::from_millis(250)).ok())
        .is_some()
}

fn backend_jar(app: &tauri::AppHandle) -> Option<PathBuf> {
    if let Ok(resource_dir) = app.path().resource_dir() {
        let packaged = resource_dir.join("backend/localrag-backend.jar");
        if packaged.is_file() {
            return Some(packaged);
        }
    }
    let development =
        PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../../build/libs/localrag-backend.jar");
    development.is_file().then_some(development)
}

fn spawn_backend(app: &tauri::AppHandle) -> Result<Child, String> {
    let jar =
        backend_jar(app).ok_or_else(|| "Bundled Backend JAR을 찾을 수 없습니다.".to_string())?;
    let log_dir = app
        .path()
        .app_log_dir()
        .map_err(|error| error.to_string())?;
    fs::create_dir_all(&log_dir).map_err(|error| error.to_string())?;
    let stdout = File::create(log_dir.join("backend.log")).map_err(|error| error.to_string())?;
    let stderr =
        File::create(log_dir.join("backend-error.log")).map_err(|error| error.to_string())?;
    let mut command = Command::new("java");
    command
        .arg("-jar")
        .arg(&jar)
        .arg("--server.address=127.0.0.1")
        .arg("--server.port=18080")
        .arg("--spring.output.ansi.enabled=never")
        .current_dir(jar.parent().unwrap_or_else(|| std::path::Path::new(".")))
        .stdin(Stdio::null())
        .stdout(Stdio::from(stdout))
        .stderr(Stdio::from(stderr));
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        command.creation_flags(0x08000000);
    }
    command
        .spawn()
        .map_err(|error| format!("Java 17 Backend 시작 실패: {error}"))
}

fn initial_state(app: &tauri::AppHandle) -> Arc<Mutex<BackendProcessState>> {
    let decision = startup_decision(port_open(), localrag_ready());
    let state = match decision {
        StartupDecision::Reuse => BackendProcessState {
            status: DesktopBackendStatus {
                state: "READY".into(),
                detail: "기존 LocalRAG Backend 재사용".into(),
                managed: false,
                pid: None,
            },
            child: None,
        },
        StartupDecision::PortInUse => BackendProcessState {
            status: DesktopBackendStatus {
                state: "PORT_IN_USE".into(),
                detail: "Port 18080을 LocalRAG가 아닌 프로세스가 사용 중입니다.".into(),
                managed: false,
                pid: None,
            },
            child: None,
        },
        StartupDecision::Start => match spawn_backend(app) {
            Ok(child) => {
                let pid = child.id();
                BackendProcessState {
                    status: DesktopBackendStatus {
                        state: "STARTING".into(),
                        detail: "Bundled Spring Boot Backend 시작 중".into(),
                        managed: true,
                        pid: Some(pid),
                    },
                    child: Some(child),
                }
            }
            Err(detail) => BackendProcessState {
                status: DesktopBackendStatus {
                    state: "UNAVAILABLE".into(),
                    detail,
                    managed: false,
                    pid: None,
                },
                child: None,
            },
        },
    };
    let shared = Arc::new(Mutex::new(state));
    let poll_state = Arc::clone(&shared);
    thread::spawn(move || {
        for _ in 0..READY_ATTEMPTS {
            if localrag_ready() {
                if let Ok(mut state) = poll_state.lock() {
                    state.status.state = "READY".into();
                    state.status.detail = "LocalRAG Backend ready".into();
                }
                return;
            }
            let should_stop = if let Ok(mut state) = poll_state.lock() {
                if let Some(child) = state.child.as_mut() {
                    match child.try_wait() {
                        Ok(Some(exit)) => {
                            state.status.state = "UNAVAILABLE".into();
                            state.status.detail =
                                format!("Backend가 readiness 전에 종료되었습니다: {exit}");
                            true
                        }
                        Ok(None) => false,
                        Err(error) => {
                            state.status.state = "UNAVAILABLE".into();
                            state.status.detail = format!("Backend 상태 확인 실패: {error}");
                            true
                        }
                    }
                } else {
                    state.status.state != "STARTING"
                }
            } else {
                true
            };
            if should_stop {
                return;
            }
            thread::sleep(Duration::from_secs(1));
        }
        if let Ok(mut state) = poll_state.lock() {
            state.status.state = "UNAVAILABLE".into();
            state.status.detail = "Backend readiness 60초 timeout".into();
        }
    });
    shared
}

#[tauri::command]
fn desktop_backend_status(backend: State<'_, DesktopBackend>) -> DesktopBackendStatus {
    if localrag_ready() {
        if let Ok(mut state) = backend.0.lock() {
            state.status.state = "READY".into();
            state.status.detail = "LocalRAG Backend ready".into();
            return state.status.clone();
        }
    }
    backend
        .0
        .lock()
        .map(|mut state| {
            if let Some(child) = state.child.as_mut() {
                if let Ok(Some(exit)) = child.try_wait() {
                    state.status.state = "UNAVAILABLE".into();
                    state.status.detail = format!("Backend process exited: {exit}");
                } else if state.status.state == "READY" {
                    state.status.state = "UNAVAILABLE".into();
                    state.status.detail = "Backend health check failed".into();
                }
            } else if state.status.state == "READY" {
                state.status.state = "UNAVAILABLE".into();
                state.status.detail = "기존 Backend 연결이 끊어졌습니다.".into();
            }
            state.status.clone()
        })
        .unwrap_or(DesktopBackendStatus {
            state: "UNAVAILABLE".into(),
            detail: "Backend lifecycle state unavailable".into(),
            managed: false,
            pid: None,
        })
}

#[tauri::command]
fn desktop_api_request(request: DesktopApiRequest) -> Result<DesktopApiResponse, String> {
    let method = request.method.to_ascii_uppercase();
    validate_api_request(&request.path, &method)?;
    let url = format!("http://127.0.0.1:18080{}", request.path);
    let client = reqwest::blocking::Client::builder()
        .connect_timeout(Duration::from_secs(3))
        .timeout(Duration::from_secs(120))
        .build()
        .map_err(|error| format!("Desktop API client 생성 실패: {error}"))?;
    let method =
        reqwest::Method::from_bytes(method.as_bytes()).map_err(|error| error.to_string())?;
    let mut builder = client
        .request(method, url)
        .header("Accept", "application/json");
    if let Some(body) = request.body {
        builder = builder
            .header("Content-Type", "application/json")
            .body(body);
    }
    let response = builder
        .send()
        .map_err(|error| format!("LocalRAG Backend 요청 실패: {error}"))?;
    let status = response.status().as_u16();
    let bytes = response
        .bytes()
        .map_err(|error| format!("Backend 응답 읽기 실패: {error}"))?;
    if bytes.len() > 10 * 1024 * 1024 {
        return Err("Backend 응답이 Desktop 10 MB 제한을 초과했습니다.".into());
    }
    let body = String::from_utf8(bytes.to_vec())
        .map_err(|_| "Backend 응답이 UTF-8이 아닙니다.".to_string())?;
    Ok(DesktopApiResponse { status, body })
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .setup(|app| {
            app.manage(DesktopBackend(initial_state(app.handle())));
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            desktop_backend_status,
            desktop_api_request
        ])
        .run(tauri::generate_context!())
        .expect("error while running LocalRAG desktop application");
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reuses_only_an_identified_localrag_backend() {
        assert_eq!(startup_decision(true, true), StartupDecision::Reuse);
        assert_eq!(startup_decision(false, false), StartupDecision::Start);
        assert_eq!(startup_decision(true, false), StartupDecision::PortInUse);
    }

    #[test]
    fn desktop_api_bridge_rejects_external_and_mutating_requests() {
        assert!(validate_api_request("/api/health", "GET").is_ok());
        assert!(validate_api_request("/api/workspaces/projects", "POST").is_ok());
        assert!(validate_api_request("https://example.com/api", "GET").is_err());
        assert!(validate_api_request("/api/../secret", "GET").is_err());
        assert!(validate_api_request("/api/history", "DELETE").is_err());
    }
}
