mod startup;
use serde::{Deserialize, Serialize};
use startup::{desktop_backend_status, desktop_retry_startup};
use std::time::Duration;
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

#[tauri::command]
async fn desktop_api_request(request: DesktopApiRequest) -> Result<DesktopApiResponse, String> {
    tauri::async_runtime::spawn_blocking(move || perform_api_request(request))
        .await
        .map_err(|e| e.to_string())?
}
fn perform_api_request(request: DesktopApiRequest) -> Result<DesktopApiResponse, String> {
    let method = request.method.to_ascii_uppercase();
    validate_api_request(&request.path, &method)?;
    let url = format!("http://127.0.0.1:18080{}", request.path);
    let client = reqwest::blocking::Client::builder()
        .no_proxy()
        .redirect(reqwest::redirect::Policy::none())
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
            startup::initialize(app.handle())?;
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            desktop_backend_status,
            desktop_retry_startup,
            desktop_api_request
        ])
        .run(tauri::generate_context!())
        .expect("error while running LocalRAG desktop application");
}
#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn desktop_api_bridge_rejects_external_and_mutating_requests() {
        assert!(validate_api_request("/api/health", "GET").is_ok());
        assert!(validate_api_request("/api/workspaces/projects", "POST").is_ok());
        assert!(validate_api_request("https://example.com/api", "GET").is_err());
        assert!(validate_api_request("/api/../secret", "GET").is_err());
        assert!(validate_api_request("/api/history", "DELETE").is_err());
    }
}
