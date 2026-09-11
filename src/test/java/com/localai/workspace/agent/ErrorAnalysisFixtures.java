package com.localai.workspace.agent;

import com.localai.workspace.chat.ChatService;
import com.localai.workspace.rag.*;
import com.localai.workspace.search.ProjectSemanticSearchStatus;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Controlled Tool responses; never written to a real project's logs, Git, or vector index. */
public final class ErrorAnalysisFixtures {
    public static AgentChatService agent(ChatService chat, String scenario) {
        var git=mock(GitReadOnlyService.class);
        var docker=mock(DockerReadOnlyService.class);
        var db=mock(DatabaseReadOnlyService.class);
        var logs=mock(LogReadOnlyService.class);
        var contexts=mock(RagContextAssemblyService.class);
        String message=scenario.equals("DB") ? "ERROR Connection refused"
                : "ERROR java.lang.NullPointerException at UserService.java:42";
        if(scenario.equals("SECRET")) message+=" password=superSecret Authorization: Bearer abc123secret";
        var entries=scenario.equals("EMPTY") ? List.<LogEntry>of()
                : List.of(new LogEntry("2026-09-09T00:00:00Z","ERROR",message,"logs/app.log",42L));
        var result=new LogInspectionResult("Local_Ai_Work",LogToolStatus.SUCCESS,1,1,0,
                entries.size(),false,100,1,1,entries,null);
        when(logs.getRecentErrors(anyString(),anyInt())).thenReturn(result);
        when(logs.searchLogs(anyString(),anyString(),anyInt())).thenReturn(result);
        when(db.getStatus()).thenReturn(new DatabaseStatusResult(LocalEnvironmentStatus.AVAILABLE,true,true,null));
        when(git.getRecentCommits(anyString(),anyInt())).thenReturn(new GitRecentCommitsResult(
                "Local_Ai_Work",GitToolStatus.SUCCESS,5,1,
                List.of(new RecentCommit("abc1234567","Update service","fixture","2026-09-08T00:00:00Z")),null));
        var sources=List.of(new RagContextSource("S1",1,"chunk","Local_Ai_Work",
                "src/UserService.java","UserService.java","java",0,40,44,
                "String name(User user) { return user.getName(); }",.8,"fixture"));
        if(scenario.equals("NO_KNOWLEDGE") || scenario.equals("EMPTY")) sources=List.of();
        when(contexts.assemble(any())).thenReturn(new RagContextAssemblyResult(
                "Local_Ai_Work","query",8000,sources.size(),sources.size(),0,100,
                ProjectSemanticSearchStatus.SUCCESS,RagContextAssemblyStatus.SUCCESS,null,sources,""));
        return new AgentChatService(chat,git,docker,mock(OllamaReadOnlyService.class),db,logs,contexts,
                new LogSecretRedactor(),new RagCitationValidator());
    }
    public static String query(String scenario) {
        return switch(scenario) {
            case "DB" -> "DB Connection refused 오류를 로그와 현재 Database 상태로 분석해줘";
            case "GIT" -> "최근 Git 커밋과 NullPointerException 오류를 함께 분석해줘";
            case "EMPTY" -> "존재하지 않는 MissingWidgetException 오류를 분석해줘";
            default -> "최근 NullPointerException 오류를 로그와 관련 구현으로 분석해줘";
        };
    }
}
