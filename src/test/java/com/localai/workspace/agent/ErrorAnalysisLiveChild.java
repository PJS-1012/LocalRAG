package com.localai.workspace.agent;

import com.localai.workspace.LocalAiWorkspaceApplication;
import com.localai.workspace.chat.ChatService;
import com.localai.workspace.errors.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import java.nio.file.*;
import java.time.Duration;
import static org.mockito.Mockito.*;

public class ErrorAnalysisLiveChild {
    public static void main(String[] args) throws Exception {
        try(var context=new SpringApplicationBuilder(LocalAiWorkspaceApplication.class).web(WebApplicationType.NONE).run(
                "--spring.flyway.enabled=false","--spring.jpa.hibernate.ddl-auto=none",
                "--spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
                "--spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false",
                "--spring.datasource.hikari.initialization-fail-timeout=-1",
                "--spring.datasource.hikari.connection-timeout=3000")) {
            if(args[0].startsWith("LEGACY_")) {
                var test=new AgentDiagnosisLiveEvaluationTest();
                context.getAutowireCapableBeanFactory().autowireBean(test);
                test.temp=Files.createTempDirectory("localrag-live-");
                test.executeCases();
                Files.copy(Path.of("build/reports/agent-step4-1/evaluation-selected.json"),Path.of(args[1]));
                return;
            }
            var scope=mock(ErrorProjectScope.class);
            when(scope.require("Local_Ai_Work")).thenReturn("Local_Ai_Work");
            var json=new ObjectMapper().findAndRegisterModules();
            var agent=ErrorAnalysisFixtures.agent(context.getBean(ChatService.class),args[0]);
            var service=new ErrorAnalysisService(agent,scope,new LogSecretRedactor(),json,Duration.ofMinutes(30),5);
            var result=service.analyzeError(new ErrorAnalysisRequest("Local_Ai_Work",ErrorAnalysisFixtures.query(args[0])));
            json.writerWithDefaultPrettyPrinter().writeValue(Path.of(args[1]).toFile(),result);
        }
    }
}
