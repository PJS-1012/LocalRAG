package com.localai.workspace.agent;

import java.util.List;

public record AgentAnalysisRun(AgentChatResponse response, List<ToolEvidence> evidence) { }
