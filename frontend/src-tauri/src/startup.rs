use serde::Serialize;
use serde_json::Value;
use std::{
    fs::{self, File},
    io::Read,
    path::PathBuf,
    process::{Child, Command, Stdio},
    sync::{Arc, Mutex},
    thread,
    time::{Duration, Instant},
};
use tauri::{Manager, State};

const NAMES: [&str; 5] = ["Docker", "PostgreSQL", "Ollama", "Models", "Backend"];
const PIPE: &str = "npipe:////./pipe/dockerDesktopLinuxEngine";
const CONTAINER: &str = "local-ai-postgres";

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Stage {
    name: String,
    state: String,
    detail: String,
}
#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct StartupStatus {
    pub state: String,
    pub detail: String,
    managed: bool,
    pid: Option<u32>,
    stages: Vec<Stage>,
    running: bool,
}
impl Default for StartupStatus {
    fn default() -> Self {
        Self {
            state: "STARTING".into(),
            detail: "Runtime 준비 중".into(),
            managed: false,
            pid: None,
            running: false,
            stages: NAMES
                .iter()
                .map(|name| Stage {
                    name: name.to_string(),
                    state: "WAITING".into(),
                    detail: String::new(),
                })
                .collect(),
        }
    }
}
pub struct DesktopStartup(pub Arc<Mutex<StartupStatus>>);
#[derive(Clone, Debug, PartialEq)]
enum Probe {
    Ready,
    StartNeeded,
    Waiting,
    Blocked(String, String),
}
trait Runtime {
    fn probe(&mut self, stage: usize) -> Result<Probe, String>;
    fn start(&mut self, stage: usize) -> Result<(), String>;
}
fn stage_update(state: &Arc<Mutex<StartupStatus>>, index: usize, status: &str, detail: &str) {
    let mut s = state.lock().unwrap();
    s.stages[index].state = status.into();
    s.stages[index].detail = detail.into();
    s.detail = format!("{}: {}", NAMES[index], detail);
}
fn orchestrate(
    runtime: &mut impl Runtime,
    state: &Arc<Mutex<StartupStatus>>,
    budgets: [Duration; 5],
    interval: Duration,
) {
    for index in 0..5 {
        let deadline = Instant::now() + budgets[index];
        let mut launched = false;
        stage_update(state, index, "STARTING", "상태 확인 중");
        let failure = loop {
            if Instant::now() >= deadline {
                break (
                    "FAILED".to_string(),
                    "Readiness timeout. Retry Startup으로 재확인하세요.".into(),
                );
            }
            match runtime.probe(index) {
                Ok(Probe::Ready) => {
                    stage_update(
                        state,
                        index,
                        "READY",
                        if launched {
                            "자동 시작 완료"
                        } else {
                            "확인 완료 · 기존 runtime 재사용"
                        },
                    );
                    break (String::new(), String::new());
                }
                Ok(Probe::Blocked(code, detail)) => break (code, detail),
                Err(reason) => break ("FAILED".into(), reason),
                Ok(Probe::StartNeeded) if !launched => {
                    stage_update(state, index, "STARTING", "백그라운드에서 시작 중");
                    if let Err(reason) = runtime.start(index) {
                        break ("FAILED".into(), reason);
                    }
                    launched = true;
                }
                _ => {}
            }
            thread::sleep(interval.min(deadline.saturating_duration_since(Instant::now())));
        };
        if !failure.0.is_empty() {
            stage_update(state, index, &failure.0, &failure.1);
            let mut s = state.lock().unwrap();
            s.state = if failure.0 == "PORT_IN_USE" {
                "PORT_IN_USE"
            } else {
                "UNAVAILABLE"
            }
            .into();
            s.running = false;
            return;
        }
    }
    let mut s = state.lock().unwrap();
    s.state = "READY".into();
    s.detail = "모든 Runtime 준비 완료".into();
    s.running = false;
}
fn hidden(command: &mut Command) -> &mut Command {
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        command.creation_flags(0x08000000);
    }
    command.stdin(Stdio::null())
}
fn capture(mut command: Command, seconds: u64) -> Result<String, String> {
    hidden(&mut command)
        .stdout(Stdio::piped())
        .stderr(Stdio::null());
    let mut child = command
        .spawn()
        .map_err(|e| format!("Runtime command 시작 실패: {e}"))?;
    let output = child.stdout.take().unwrap();
    let (sender, receiver) = std::sync::mpsc::channel();
    thread::spawn(move || {
        let mut data = Vec::new();
        let _ = output.take(262145).read_to_end(&mut data);
        let _ = sender.send(data);
    });
    let deadline = Instant::now() + Duration::from_secs(seconds);
    loop {
        if let Some(exit) = child.try_wait().map_err(|e| e.to_string())? {
            let data = receive_output(receiver, deadline)?;
            if data.len() > 262144 {
                return Err("Runtime output limit exceeded".into());
            }
            return if exit.success() {
                Ok(String::from_utf8_lossy(&data).trim().into())
            } else {
                Err(format!("Runtime command failed ({exit})"))
            };
        }
        if Instant::now() >= deadline {
            // Terminate only this short-lived command, never an existing runtime.
            let _ = child.kill();
            let _ = child.wait();
            return Err("Runtime command timeout".into());
        }
        thread::sleep(Duration::from_millis(50));
    }
}
fn receive_output(
    receiver: std::sync::mpsc::Receiver<Vec<u8>>,
    deadline: Instant,
) -> Result<Vec<u8>, String> {
    receiver
        .recv_timeout(deadline.saturating_duration_since(Instant::now()))
        .map_err(|_| "Runtime output timeout".into())
}
fn known(paths: &[PathBuf], label: &str) -> Result<PathBuf, String> {
    paths
        .iter()
        .find(|p| p.is_absolute() && p.is_file())
        .cloned()
        .ok_or_else(|| format!("{label} 설치 경로를 찾을 수 없습니다."))
}
fn env_path(name: &str) -> PathBuf {
    std::env::var_os(name)
        .map(PathBuf::from)
        .unwrap_or_default()
}
fn docker_path() -> Result<PathBuf, String> {
    known(
        &[env_path("ProgramFiles").join("Docker/Docker/resources/bin/docker.exe")],
        "Docker Desktop",
    )
}
fn docker(args: &[&str]) -> Result<String, String> {
    let mut c = Command::new(docker_path()?);
    c.args(["--host", PIPE]);
    c.args(args);
    capture(c, 10)
}
fn json_get(url: &str) -> Option<Value> {
    let response = reqwest::blocking::Client::builder()
        .no_proxy()
        .redirect(reqwest::redirect::Policy::none())
        .timeout(Duration::from_secs(2))
        .build()
        .ok()?
        .get(url)
        .send()
        .ok()?
        .error_for_status()
        .ok()?;
    let mut bytes = Vec::new();
    response.take(262145).read_to_end(&mut bytes).ok()?;
    if bytes.len() > 262144 {
        return None;
    }
    serde_json::from_slice(&bytes).ok()
}
fn missing_models(value: &Value) -> Vec<&'static str> {
    ["qwen3:8b", "qwen3-embedding:0.6b"]
        .into_iter()
        .filter(|required| {
            !value["models"]
                .as_array()
                .is_some_and(|models| models.iter().any(|m| m["name"].as_str() == Some(required)))
        })
        .collect()
}
fn owned_container(value: &Value) -> bool {
    let labels = &value["Config"]["Labels"];
    labels["com.docker.compose.project"] == "local_ai_work"
        && labels["com.docker.compose.service"] == "postgres"
}
struct NativeRuntime {
    resources: PathBuf,
    logs: PathBuf,
    backend: Option<Child>,
    ollama: Option<Child>,
}
// Java 17's JAR launcher cannot open Tauri's verbatim Windows resource paths.
fn java_resource_path(path: PathBuf) -> PathBuf {
    let text = path.to_string_lossy();
    if let Some(unc) = text.strip_prefix(r"\\?\UNC\") {
        return PathBuf::from(format!(r"\\{unc}"));
    }
    if let Some(local) = text.strip_prefix(r"\\?\") {
        return PathBuf::from(local);
    }
    path
}
impl NativeRuntime {
    fn resource(&self, name: &str) -> Result<PathBuf, String> {
        let path = self.resources.join(name);
        if path.is_file() {
            Ok(path)
        } else {
            Err(format!("Bundled resource missing: {name}"))
        }
    }
    fn spawn_logged(&self, mut command: Command, name: &str) -> Result<Child, String> {
        fs::create_dir_all(&self.logs).map_err(|e| e.to_string())?;
        let out = File::create(self.logs.join(format!("{name}.log"))).map_err(|e| e.to_string())?;
        let err =
            File::create(self.logs.join(format!("{name}-error.log"))).map_err(|e| e.to_string())?;
        hidden(&mut command)
            .current_dir(&self.logs)
            .stdout(out)
            .stderr(err)
            .spawn()
            .map_err(|e| e.to_string())
    }
}
impl Runtime for NativeRuntime {
    fn probe(&mut self, stage: usize) -> Result<Probe, String> {
        match stage {
            0 => Ok(
                if docker(&["info", "--format", "{{.ServerVersion}}"]).is_ok() {
                    Probe::Ready
                } else {
                    Probe::StartNeeded
                },
            ),
            1 => {
                let names = docker(&[
                    "ps",
                    "-a",
                    "--filter",
                    "name=^/local-ai-postgres$",
                    "--format",
                    "{{.Names}}",
                ])?;
                if !names.lines().any(|n| n == CONTAINER) {
                    return Ok(Probe::StartNeeded);
                }
                let raw = docker(&["inspect", CONTAINER])?;
                let list: Value = serde_json::from_str(&raw).map_err(|e| e.to_string())?;
                let c = &list[0];
                if !owned_container(c) {
                    return Ok(Probe::Blocked(
                        "FAILED".into(),
                        "동일 이름 Container의 Compose 소유권이 다릅니다.".into(),
                    ));
                }
                match c["State"]["Status"].as_str() {
                    Some("exited" | "created") => Ok(Probe::StartNeeded),
                    Some("running") if c["State"]["Health"]["Status"] == "healthy" => {
                        Ok(Probe::Ready)
                    }
                    Some("running") if c["State"]["Health"]["Status"] == "unhealthy" => Ok(
                        Probe::Blocked("FAILED".into(), "PostgreSQL UNHEALTHY".into()),
                    ),
                    _ => Ok(Probe::Waiting),
                }
            }
            2 => Ok(if json_get("http://127.0.0.1:11434/api/tags").is_some() {
                Probe::Ready
            } else {
                Probe::StartNeeded
            }),
            3 => {
                let models = json_get("http://127.0.0.1:11434/api/tags")
                    .ok_or("Ollama model list unavailable")?;
                let missing = missing_models(&models);
                Ok(if missing.is_empty() {
                    Probe::Ready
                } else {
                    Probe::Blocked("MODEL_MISSING".into(), missing.join(", "))
                })
            }
            4 => {
                if json_get("http://127.0.0.1:18080/api/health")
                    .is_some_and(|v| v["status"] == "UP" && v["application"] == "localrag")
                {
                    return Ok(Probe::Ready);
                }
                if let Some(child) = self.backend.as_mut() {
                    if let Some(exit) = child.try_wait().map_err(|e| e.to_string())? {
                        self.backend = None;
                        return Err(format!("Backend exited: {exit}. backend-error.log 확인"));
                    }
                    return Ok(Probe::Waiting);
                }
                // Compatibility with the previous packaged LocalRAG health contract.
                if json_get("http://127.0.0.1:18080/api/health")
                    .is_some_and(|v| v["status"] == "UP")
                    && json_get("http://127.0.0.1:18080/api/workspaces/discovery")
                        .is_some_and(|v| v["workspaceRoot"].is_string())
                {
                    return Ok(Probe::Ready);
                }
                let open = "127.0.0.1:18080".parse().ok().is_some_and(|a| {
                    std::net::TcpStream::connect_timeout(&a, Duration::from_millis(200)).is_ok()
                });
                Ok(if open {
                    Probe::Blocked(
                        "PORT_IN_USE".into(),
                        "다른 프로세스가 port 18080을 사용 중입니다.".into(),
                    )
                } else {
                    Probe::StartNeeded
                })
            }
            _ => Err("Unknown startup stage".into()),
        }
    }
    fn start(&mut self, stage: usize) -> Result<(), String> {
        match stage {
            0 => {
                let mut c = Command::new(docker_path()?);
                c.args(["desktop", "start", "--detach"]);
                capture(c, 15).map(|_| ())
            }
            1 => {
                let names = docker(&[
                    "ps",
                    "-a",
                    "--filter",
                    "name=^/local-ai-postgres$",
                    "--format",
                    "{{.Names}}",
                ])?;
                if names.lines().any(|n| n == CONTAINER) {
                    let raw = docker(&["inspect", CONTAINER])?;
                    let value: Value = serde_json::from_str(&raw).map_err(|e| e.to_string())?;
                    if !owned_container(&value[0]) {
                        return Err("Container ownership mismatch".into());
                    }
                    docker(&["start", CONTAINER]).map(|_| ())
                } else {
                    let compose = self.resource("runtime/compose.yml")?;
                    let mut c = Command::new(docker_path()?);
                    c.args([
                        "--host",
                        PIPE,
                        "compose",
                        "--project-name",
                        "local_ai_work",
                        "-f",
                    ])
                    .arg(compose)
                    .args([
                        "up",
                        "-d",
                        "--no-deps",
                        "--pull",
                        "never",
                        "postgres",
                    ]);
                    capture(c, 30).map(|_| ())
                }
            }
            2 => {
                if let Some(child) = self.ollama.as_mut() {
                    if child.try_wait().map_err(|e| e.to_string())?.is_none() {
                        return Ok(());
                    }
                }
                let exe = known(
                    &[
                        env_path("LOCALAPPDATA").join("Programs/Ollama/ollama.exe"),
                        env_path("ProgramFiles").join("Ollama/ollama.exe"),
                    ],
                    "Ollama",
                )?;
                let mut c = Command::new(exe);
                c.arg("serve").env("OLLAMA_HOST", "127.0.0.1:11434");
                self.ollama = Some(self.spawn_logged(c, "ollama")?);
                Ok(())
            }
            4 => {
                let jar = java_resource_path(self.resource("backend/localrag-backend.jar")?);
                let mut candidates = vec![env_path("JAVA_HOME").join("bin/java.exe")];
                if let Some(paths) = std::env::var_os("PATH") {
                    candidates.extend(std::env::split_paths(&paths).map(|p| p.join("java.exe")));
                }
                let java = known(&candidates, "Java 17")?;
                let mut check = Command::new(&java);
                check.args(["--version"]);
                let version = capture(check, 5)?;
                if !version
                    .lines()
                    .next()
                    .is_some_and(|v| v.starts_with("openjdk 17.") || v.starts_with("java 17."))
                {
                    return Err("Java 17이 필요합니다.".into());
                }
                let mut c = Command::new(java);
                c.arg("-jar").arg(jar).args([
                    "--server.address=127.0.0.1",
                    "--server.port=18080",
                    "--spring.output.ansi.enabled=never",
                ]);
                self.backend = Some(self.spawn_logged(c, "backend")?);
                Ok(())
            }
            _ => Err("이 단계는 자동 설치를 지원하지 않습니다.".into()),
        }
    }
}
pub struct NativeState(Mutex<NativeRuntime>);
pub fn initialize(app: &tauri::AppHandle) -> Result<(), Box<dyn std::error::Error>> {
    app.manage(DesktopStartup(Arc::new(Mutex::new(
        StartupStatus::default(),
    ))));
    app.manage(NativeState(Mutex::new(NativeRuntime {
        resources: app.path().resource_dir()?,
        logs: app.path().app_log_dir()?,
        backend: None,
        ollama: None,
    })));
    launch(app.clone());
    Ok(())
}
fn launch(app: tauri::AppHandle) {
    let shared = app.state::<DesktopStartup>().0.clone();
    {
        let mut state = shared.lock().unwrap();
        if state.running {
            return;
        }
        *state = StartupStatus::default();
        state.running = true;
    }
    thread::spawn(move || {
        let native = app.state::<NativeState>();
        let mut runtime = native.0.lock().unwrap();
        orchestrate(
            &mut *runtime,
            &shared,
            [180, 90, 60, 5, 90].map(Duration::from_secs),
            Duration::from_secs(1),
        );
        let mut state = shared.lock().unwrap();
        state.managed = runtime.backend.is_some();
        state.pid = runtime.backend.as_ref().map(Child::id);
    });
}
#[tauri::command]
pub fn desktop_backend_status(state: State<'_, DesktopStartup>) -> StartupStatus {
    state.0.lock().unwrap().clone()
}
#[tauri::command]
pub fn desktop_retry_startup(app: tauri::AppHandle) {
    launch(app);
}

#[cfg(test)]
mod tests {
    use super::*;
    struct Fake {
        probes: [Probe; 5],
        starts: Vec<usize>,
        never_ready: bool,
    }
    impl Runtime for Fake {
        fn probe(&mut self, i: usize) -> Result<Probe, String> {
            Ok(self.probes[i].clone())
        }
        fn start(&mut self, i: usize) -> Result<(), String> {
            self.starts.push(i);
            if !self.never_ready {
                self.probes[i] = Probe::Ready;
            }
            Ok(())
        }
    }
    fn fake() -> Fake {
        Fake {
            probes: std::array::from_fn(|_| Probe::Ready),
            starts: vec![],
            never_ready: false,
        }
    }
    fn run(f: &mut Fake) -> StartupStatus {
        let s = Arc::new(Mutex::new(StartupStatus::default()));
        orchestrate(
            f,
            &s,
            [Duration::from_millis(8); 5],
            Duration::from_millis(1),
        );
        let v = s.lock().unwrap().clone();
        v
    }
    #[test]
    fn already_running_reuses_all() {
        let mut f = fake();
        assert_eq!(run(&mut f).state, "READY");
        assert!(f.starts.is_empty());
    }
    #[test]
    fn inherited_stdout_cannot_hold_startup_open() {
        let (_sender, receiver) = std::sync::mpsc::channel::<Vec<u8>>();
        let start = Instant::now();
        assert!(receive_output(receiver, start + Duration::from_millis(5)).is_err());
        assert!(start.elapsed() < Duration::from_secs(1));
    }
    #[test]
    fn starts_only_needed_services_in_order() {
        let mut f = fake();
        for i in [0, 1, 2, 4] {
            f.probes[i] = Probe::StartNeeded;
        }
        assert_eq!(run(&mut f).state, "READY");
        assert_eq!(f.starts, vec![0, 1, 2, 4]);
    }
    #[test]
    fn missing_model_blocks_backend() {
        let mut f = fake();
        f.probes[3] = Probe::Blocked("MODEL_MISSING".into(), "qwen3:8b".into());
        let s = run(&mut f);
        assert_eq!(s.stages[3].state, "MODEL_MISSING");
        assert_eq!(s.stages[4].state, "WAITING");
    }
    #[test]
    fn timeout_is_bounded_and_retry_can_recover() {
        let mut f = fake();
        f.probes[0] = Probe::StartNeeded;
        f.never_ready = true;
        assert_eq!(run(&mut f).state, "UNAVAILABLE");
        assert_eq!(f.starts, vec![0]);
        f.never_ready = false;
        assert_eq!(run(&mut f).state, "READY");
    }
    #[test]
    fn port_conflict_does_not_start_backend() {
        let mut f = fake();
        f.probes[4] = Probe::Blocked("PORT_IN_USE".into(), "occupied".into());
        assert_eq!(run(&mut f).state, "PORT_IN_USE");
        assert!(f.starts.is_empty());
    }
    #[test]
    fn checks_exact_model_names() {
        assert_eq!(
            missing_models(&serde_json::json!({"models":[{"name":"qwen3:8b"}]})),
            vec!["qwen3-embedding:0.6b"]
        );
    }
    #[test]
    fn normalizes_packaged_jar_paths_for_java17() {
        assert_eq!(
            java_resource_path(PathBuf::from(r"\\?\C:\LocalRAG\backend.jar")),
            PathBuf::from(r"C:\LocalRAG\backend.jar")
        );
        assert_eq!(
            java_resource_path(PathBuf::from(r"\\?\UNC\server\share\backend.jar")),
            PathBuf::from(r"\\server\share\backend.jar")
        );
    }
    #[test]
    fn refuses_foreign_container() {
        assert!(!owned_container(
            &serde_json::json!({"Config":{"Labels":{"com.docker.compose.project":"other","com.docker.compose.service":"postgres"}}})
        ));
    }
}
