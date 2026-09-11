package com.localai.workspace.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localai.workspace.LocalAiWorkspaceApplication;
import com.localai.workspace.chat.ChatService;
import com.localai.workspace.errors.*;
import com.localai.workspace.rag.*;
import com.localai.workspace.workflow.*;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class DeveloperWorkflowLiveChild {
    public static void main(String[] args) throws Exception {
        try(var context=new SpringApplicationBuilder(LocalAiWorkspaceApplication.class).web(WebApplicationType.NONE).run(
                "--spring.flyway.enabled=false","--spring.jpa.hibernate.ddl-auto=none",
                "--spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
                "--spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false",
                "--spring.datasource.hikari.initialization-fail-timeout=-1",
                "--spring.datasource.hikari.connection-timeout=3000")) {
            String id=args[0];
            var similar=mock(ErrorSimilarityService.class);
            var progress=mock(ProjectProgressService.class);
            var activity=mock(DevelopmentActivityService.class);
            var db=mock(DatabaseReadOnlyService.class);
            when(similar.find(any())).thenReturn(new ErrorSimilarityResponse("SUCCESS","Local_Ai_Work",5,.65,0,
                    10,2,12,"A similar case is not proof of the current cause.",List.of()));
            when(progress.analyze(any())).thenReturn(progress());
            when(activity.summarize(any())).thenReturn(activity());
            when(db.getStatus()).thenReturn(new DatabaseStatusResult(LocalEnvironmentStatus.AVAILABLE,true,true,null));
            var agent=new AgentChatService(context.getBean(ChatService.class),mock(GitReadOnlyService.class),
                    mock(DockerReadOnlyService.class),mock(OllamaReadOnlyService.class),db,
                    mock(LogReadOnlyService.class),mock(RagContextAssemblyService.class),new LogSecretRedactor(),
                    new RagCitationValidator(),similar,progress,activity);
            AgentChatResponse response=agent.chat(new AgentChatRequest("Local_Ai_Work",query(id)));
            new ObjectMapper().findAndRegisterModules().writerWithDefaultPrettyPrinter()
                    .writeValue(Path.of(args[1]).toFile(),response);
        }
    }

    private static String query(String id) {
        return switch(id) {
            case "SIMILAR" -> "이 오류 예전에 비슷한 거 있었어? 오류 메시지는 PostgreSQL connection refused야.";
            case "PROGRESS" -> "LocalRAG 지금 어디까지 했어?";
            case "ACTIVITY" -> "최근 작업 요약해줘";
            case "DATABASE" -> "DB 실행 중이야?";
            case "NO_TOOL" -> "Java HashMap 설명해줘";
            default -> throw new IllegalArgumentException("Unsupported case");
        };
    }

    private static ProjectProgressResponse progress() {
        return new ProjectProgressResponse("SUCCESS","Local_Ai_Work","Phase 8 evidence snapshot",
                List.of("Phase 7 recorded in Git"),List.of("Phase 8 working-tree changes"),List.of(),List.of(),
                List.of("README may be outdated"),List.of("Current tests were not executed"),0,List.of(),
                List.of("getGitStatus","getRecentCommits","searchProjectKnowledge"),Instant.now(),2,3,4,9);
    }
    private static DevelopmentActivityResponse activity() {
        return new DevelopmentActivityResponse("SUCCESS","Local_Ai_Work",null,5,
                List.of(new RecentCommit("abcdef1234","Add error history","developer","2026-09-10T00:00:00Z")),
                List.of("error handling/history"),"Recent Error History work was recorded.",
                new GitStatusResult("Local_Ai_Work",GitToolStatus.SUCCESS,"main",false,
                        List.of("src/main/java/Error.java"),List.of(),List.of(),List.of(),null),
                List.of(),Instant.now(),2,1,3,6);
    }
}
