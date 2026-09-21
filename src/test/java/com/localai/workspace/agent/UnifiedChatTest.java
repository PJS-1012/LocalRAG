package com.localai.workspace.agent;
import com.localai.workspace.chat.ChatService;
import com.localai.workspace.overview.ProjectBriefService;
import com.localai.workspace.rag.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class UnifiedChatTest {
 final ChatService chat=mock(ChatService.class);
 final GitReadOnlyService git=mock(GitReadOnlyService.class);
 final RagContextAssemblyService rag=mock(RagContextAssemblyService.class);
 final ProjectBriefService briefs=mock(ProjectBriefService.class);
 final AgentChatService service=new AgentChatService(chat,git,mock(DockerReadOnlyService.class),
   mock(OllamaReadOnlyService.class),mock(DatabaseReadOnlyService.class),mock(LogReadOnlyService.class),
   rag,new LogSecretRedactor(),new RagCitationValidator());
 @Test void generalQuestionDoesNotInspectProjectOrCallAnotherModel() {
  when(chat.chatUnifiedWithToolCallbacks(anyString(),anyString(),any(ToolCallback[].class))).thenReturn("[GENERAL] HashMap은 키와 값을 저장합니다.");
  var run=service.unified(new AgentChatRequest("P","Java HashMap 설명해줘"),briefs);
  assertThat(run.response().toolsUsed()).isEmpty();assertThat(run.evidence()).isEmpty();
  verifyNoInteractions(git,rag,briefs);
  verify(chat,times(1)).chatUnifiedWithToolCallbacks(anyString(),anyString(),any(ToolCallback[].class));
 }
 @Test void unverifiedNoToolDraftIsNeverPresentedAsProjectFactsAndRetryIsBounded() {
  when(chat.chatUnifiedWithToolCallbacks(anyString(),anyString(),any(ToolCallback[].class))).thenReturn("Python main.py 프로젝트입니다.");
  var run=service.unified(new AgentChatRequest("P","인수인계해줘"),briefs);
  assertThat(run.response().status()).isEqualTo(AgentChatStatus.INSUFFICIENT_EVIDENCE);
  assertThat(run.response().answer()).doesNotContain("Python","main.py");
  verify(chat,times(2)).chatUnifiedWithToolCallbacks(anyString(),anyString(),any(ToolCallback[].class));
 }
 @Test void failedRagStillReturnsLiveSourcesAndDeduplicatesRepeatedToolEvidence() {
  when(rag.assemble(any())).thenThrow(new IllegalStateException("No stored index"));
  when(briefs.collect(eq("P"),anyString())).thenReturn(new ProjectBriefService.Brief("P","JAVA",null,
    List.of("src"),List.of("README.md"),null,List.of(new ProjectBriefService.Excerpt("README.md",1,1,"booking project")),
    Map.of(),List.of("bounded snapshot"),1));
  when(chat.chatUnifiedWithToolCallbacks(anyString(),anyString(),any(ToolCallback[].class))).thenAnswer(inv->{
   var callbacks=AgentChatServiceTest.callbacks(inv.getArguments());
   String a=AgentChatServiceTest.call(callbacks,"searchProjectKnowledge","{\"query\":\"소개\"}");
   assertThat(a).contains("booking project","SUCCESS").doesNotContain("No stored index");
   AgentChatServiceTest.call(callbacks,"searchProjectKnowledge","{\"query\":\"소개\"}");
   return "예약 프로젝트입니다 [K1-S1].";
  });
  var run=service.unified(new AgentChatRequest("P","처음 왔는데 알려줘"),briefs);
  assertThat(run.response().knowledgeSourceCount()).isEqualTo(1);
  assertThat(run.response().usedSourceIds()).containsExactly("K1-S1");
  assertThat(run.evidence()).hasSize(2);
  verify(briefs,times(1)).collect("P","소개");
  verify(rag,times(1)).assemble(any());
 }
 @Test void withheldProjectAnswerIsNotReportedAsGeneralKnowledgeRoute() {
  when(chat.chatUnifiedWithToolCallbacks(anyString(),anyString(),any(ToolCallback[].class))).thenReturn("Unverified project claim");
  var scope=mock(com.localai.workspace.errors.ErrorProjectScope.class);
  when(scope.require("P")).thenReturn("P");
  var controller=new UnifiedChatController(service,scope,briefs,mock(com.localai.workspace.errors.ErrorAnalysisService.class));
  var result=controller.chat(new UnifiedChatController.Request("P","인수인계해줘"));
  assertThat(result.routes()).containsExactly("UNRESOLVED");
  assertThat(result.result().status()).isEqualTo(AgentChatStatus.INSUFFICIENT_EVIDENCE);
 }
 @Test void noRagSourcesDoesNotBlockGitAnswer() {
  when(git.getStatus("P")).thenReturn(new GitStatusResult("P",GitToolStatus.SUCCESS,"main",true,List.of(),List.of(),List.of(),List.of(),null));
  when(chat.chatUnifiedWithToolCallbacks(anyString(),anyString(),any(ToolCallback[].class))).thenAnswer(inv->{
   AgentChatServiceTest.call(AgentChatServiceTest.callbacks(inv.getArguments()),"getGitStatus","{\"projectId\":\"P\"}");
   return "getGitStatus: main 브랜치, 변경 없음.";
  });
  var run=service.unified(new AgentChatRequest("P","현재 브랜치 상태"),briefs);
  assertThat(run.response().status()).isEqualTo(AgentChatStatus.SUCCESS);
  assertThat(run.response().knowledgeSources()).isEmpty();assertThat(run.evidence()).hasSize(1);
  verifyNoInteractions(rag,briefs);
 }
 @Test void failingAllEvidenceRetainsInsufficientStatusAndToolBudgetIsBounded() {
  when(git.getStatus(anyString())).thenThrow(new RuntimeException("private"));
  when(chat.chatUnifiedWithToolCallbacks(anyString(),anyString(),any(ToolCallback[].class))).thenAnswer(inv->{
   AgentChatServiceTest.call(AgentChatServiceTest.callbacks(inv.getArguments()),"getGitStatus","{\"projectId\":\"P\"}");
   return "확인할 근거가 부족합니다.";
  });
  assertThat(service.unified(new AgentChatRequest("P","Git"),briefs).response().status()).isEqualTo(AgentChatStatus.INSUFFICIENT_EVIDENCE);
 }
}
