package com.localai.workspace.automation;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workspaces/projects/automation")
public class AutomationController {
    private final ProjectAutomationConfigService configs;
    private final AutomationExecutionService executions;
    private final AutomationHistoryService history;
    private final NotificationCandidateService notifications;
    public AutomationController(ProjectAutomationConfigService configs,AutomationExecutionService executions,
            AutomationHistoryService history,NotificationCandidateService notifications) {
        this.configs=configs;this.executions=executions;this.history=history;this.notifications=notifications;
    }

    @GetMapping
    public ProjectAutomationConfigView get(@RequestParam String projectId){return configs.get(projectId);}
    @PutMapping
    public ProjectAutomationConfigView put(@Valid @RequestBody ProjectAutomationConfigRequest request) {
        return configs.put(request);
    }
    @PostMapping("/run")
    public AutomationExecutionResult run(@Valid @RequestBody AutomationRunRequest request) {
        return executions.runManual(request.projectId());
    }
    @GetMapping("/runs")
    public AutomationPage<AutomationRunView> runs(@RequestParam String projectId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return history.list(projectId,page,size);
    }
    @GetMapping("/notifications")
    public AutomationPage<NotificationCandidateView> notifications(@RequestParam String projectId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return notifications.list(projectId,page,size);
    }
}
