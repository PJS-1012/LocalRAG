package com.localai.workspace.rag;

public enum RagChatStatus {
    SUCCESS,
    SUCCESS_WITH_WARNINGS,
    NO_EVIDENCE,
    CONTEXT_FAILED,
    LLM_FAILED
}
