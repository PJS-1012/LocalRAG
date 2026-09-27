package com.localai.workspace.agent;

import com.localai.workspace.errors.ErrorProjectScope;
import com.localai.workspace.overview.ProjectBriefService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/workspaces/projects/chat/unified")
public class UnifiedChatController {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(UnifiedChatController.class);
    private final AgentChatService agent;
    private final ErrorProjectScope scope;
    private final ProjectBriefService briefs;
    private final com.localai.workspace.errors.ErrorAnalysisService errors;
    public UnifiedChatController(AgentChatService agent,ErrorProjectScope scope,ProjectBriefService briefs,
            com.localai.workspace.errors.ErrorAnalysisService errors) {
        this.agent=agent;this.scope=scope;this.briefs=briefs;this.errors=errors;
    }
    public record Request(@NotBlank String projectId,@NotBlank @Size(max=4000) String query) {}
    public record Diagnosis(String status,List<String> limitations) {}
    public record Response(AgentChatResponse result,String routing,List<String> routes,List<ToolEvidence> evidence,Diagnosis diagnosis,
            com.localai.workspace.chat.UnifiedRequestTrace.Snapshot diagnostics) {}
    @PostMapping
    public Response chat(@Valid @RequestBody Request request) {
        try (var trace=com.localai.workspace.chat.UnifiedRequestTrace.begin()) {
        String project=scope.require(request.projectId());
        trace.projectId(project);
        var run=agent.unified(new AgentChatRequest(project,request.query()),briefs);
        var result=run.response();
        boolean answered=result.status()==AgentChatStatus.SUCCESS || result.status()==AgentChatStatus.SUCCESS_WITH_WARNINGS;
        var routes=result.toolsUsed().isEmpty()?List.of(answered?"GENERAL":"UNRESOLVED"):result.toolsUsed();
        Diagnosis diagnosis=null;
        if(routes.stream().anyMatch(Set.of("getRecentErrors","getRecentLogs","searchLogs","getDatabaseStatus")::contains)) {
            var review=errors.reviewCollected(new com.localai.workspace.errors.ErrorAnalysisRequest(project,request.query()),run);
            diagnosis=new Diagnosis(review.evidenceStatus(),review.unknown());
        }
        var diagnostics=trace.snapshot();
        LOG.info("Unified request metrics: {}",diagnostics);
        return new Response(run.response(),"EXISTING_AGENT_TOOL_SELECTION",routes,run.evidence(),diagnosis,diagnostics);
        }
    }
}
