package com.localai.workspace.automation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localai.workspace.agent.AgentChatService;
import com.localai.workspace.errors.ErrorProjectScope;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties="localrag.automation.scheduler-enabled=false")
@AutoConfigureMockMvc
@Testcontainers
class AutomationPersistenceIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg17").asCompatibleSubstituteFor("postgres"));
    @Autowired ProjectAutomationConfigService configs;
    @Autowired AutomationHistoryService history;
    @Autowired NotificationCandidateService notifications;
    @Autowired ProjectAutomationConfigRepository configRepository;
    @Autowired AutomationRunRepository runRepository;
    @Autowired DetectedErrorEventRepository errorRepository;
    @Autowired NotificationCandidateRepository notificationRepository;
    @Autowired ObjectMapper json;
    @Autowired MockMvc mvc;
    @MockitoBean ErrorProjectScope scope;
    @MockitoBean AgentChatService agent;

    @BeforeEach void clean() {
        notificationRepository.deleteAllInBatch();
        errorRepository.deleteAllInBatch();
        runRepository.deleteAllInBatch();
        configRepository.deleteAllInBatch();
        when(scope.require(anyString())).thenAnswer(invocation->invocation.getArgument(0));
    }

    @Test void configurationRunAndNotificationArePersistentAndProjectScoped() throws Exception {
        var configured=configs.put(new ProjectAutomationConfigRequest("Project-A",true,true,true,true,true,300));
        assertThat(configured.projectId()).isEqualTo("Project-A");
        assertThat(configs.get("Project-A").nextRunAt()).isNotNull();

        var details=new AutomationRunDetails(ChangeStatus.NO_CHANGE,0,0,0,false,false,false,2,1,1,0,3);
        long started=System.nanoTime();
        var run=history.save("Project-A",AutomationTriggerType.MANUAL,Instant.now(),
                AutomationRunStatus.NO_CHANGE,false,"no change",null,details,5);
        var candidate=notifications.create("Project-A",run.id(),NotificationType.PROJECT_CHANGED,
                "f".repeat(64),"Project changed","token=do-not-store");
        System.out.println("AUTOMATION_DB_SAVE_MS="+((System.nanoTime()-started)/1_000_000));

        assertThat(history.list("Project-A",0,20).content()).hasSize(1);
        assertThat(history.list("Project-B",0,20).content()).isEmpty();
        assertThat(candidate).isPresent();
        assertThat(json.writeValueAsString(notifications.list("Project-A",0,20)))
                .doesNotContain("do-not-store");
        assertThat(notifications.create("Project-A",run.id(),NotificationType.PROJECT_CHANGED,
                "f".repeat(64),"duplicate","duplicate")).isEmpty();
    }

    @Test void apiRejectsSubMinuteInterval() throws Exception {
        var request=new ProjectAutomationConfigRequest("Project-A",true,true,true,true,true,59);
        mvc.perform(put("/api/workspaces/projects/automation").contentType("application/json")
                .content(json.writeValueAsString(request))).andExpect(status().isBadRequest());
    }
}
