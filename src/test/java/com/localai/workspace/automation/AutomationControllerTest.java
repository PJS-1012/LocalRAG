package com.localai.workspace.automation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AutomationController.class)
class AutomationControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean ProjectAutomationConfigService configs;
    @MockitoBean AutomationExecutionService executions;
    @MockitoBean AutomationHistoryService history;
    @MockitoBean NotificationCandidateService notifications;

    @Test void configurationRoutesDelegateToTheConfigurationService() throws Exception {
        Instant now=Instant.parse("2026-09-14T00:00:00Z");
        var view=new ProjectAutomationConfigView("P",true,true,true,true,true,300,null,now,now,now,0);
        when(configs.get("P")).thenReturn(view);
        when(configs.put(any())).thenReturn(view);

        mvc.perform(get("/api/workspaces/projects/automation").param("projectId","P"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.projectId").value("P"));
        mvc.perform(put("/api/workspaces/projects/automation").contentType("application/json")
                .content(json.writeValueAsString(new ProjectAutomationConfigRequest("P",true,true,true,true,true,300))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.intervalSeconds").value(300));
    }

    @Test void manualRouteUsesTheSharedExecutionService() throws Exception {
        when(executions.runManual("P")).thenReturn(new AutomationExecutionResult(AutomationRunStatus.NO_CHANGE,
                "P",AutomationTriggerType.MANUAL,false,false,null,null,null,null,null,null,null,List.of(),4));

        mvc.perform(post("/api/workspaces/projects/automation/run").contentType("application/json")
                .content("{\"projectId\":\"P\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NO_CHANGE"))
                .andExpect(jsonPath("$.llmInvoked").value(false));
        verify(executions).runManual("P");
    }

    @Test void historyAndNotificationRoutesRemainProjectScoped() throws Exception {
        when(history.list("P",0,20)).thenReturn(new AutomationPage<>(List.of(),0,0,0,20));
        when(notifications.list("P",0,20)).thenReturn(new AutomationPage<>(List.of(),0,0,0,20));

        mvc.perform(get("/api/workspaces/projects/automation/runs").param("projectId","P"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
        mvc.perform(get("/api/workspaces/projects/automation/notifications").param("projectId","P"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
    }
}
