package com.localai.workspace.chat;

/** Fixed public message; never expose a partial model answer or provider payload. */
public final class IncompleteResponseException extends RuntimeException {
    public IncompleteResponseException() {
        super("응답이 중간에 종료되었거나 정상 종료를 확인할 수 없습니다. 다시 시도해주세요.");
    }
}
