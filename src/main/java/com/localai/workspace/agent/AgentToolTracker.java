package com.localai.workspace.agent;

import java.util.List;

interface AgentToolTracker {
    List<AgentToolInvocation> invocations();

    boolean failed();
}
