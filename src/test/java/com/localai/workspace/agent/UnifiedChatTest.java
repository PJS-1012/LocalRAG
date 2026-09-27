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
  verify(chat,times(1)).chatUnifiedWithToolCallbacks(anyString(),anyString(),any(ToolCallback[].class));
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
  verify(briefs,times(1)).collect("P","처음 왔는데 알려줘");
  verify(rag,times(1)).assemble(any());
  verify(rag).assemble(argThat(r->r.query().equals("처음 왔는데 알려줘")));
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
 @Test void uncitedKnowledgeClaimIsWithheldWithoutAnotherModelCall() {
  when(rag.assemble(any())).thenThrow(new IllegalStateException("No index"));
  when(briefs.collect(eq("P"),anyString())).thenReturn(new ProjectBriefService.Brief("P","JAVA",null,
    List.of(),List.of(),null,List.of(new ProjectBriefService.Excerpt("A.java",1,1,"class A {}")),Map.of(),List.of(),1));
  when(chat.chatUnifiedWithToolCallbacks(anyString(),anyString(),any(ToolCallback[].class))).thenAnswer(inv->{
   AgentChatServiceTest.call(AgentChatServiceTest.callbacks(inv.getArguments()),"searchProjectKnowledge","{\"query\":\"구현\"}");
   return "회원가입과 결제가 모두 구현 완료됐습니다.";
  });
  var result=service.unified(new AgentChatRequest("P","구현 상태"),briefs).response();
  assertThat(result.status()).isEqualTo(AgentChatStatus.INSUFFICIENT_EVIDENCE);
  assertThat(result.answer()).doesNotContain("구현 완료");
  verify(chat,times(1)).chatUnifiedWithToolCallbacks(anyString(),anyString(),any(ToolCallback[].class));
 }
 @Test void validCitationCannotJustifyARequestedClassMissingFromActualSources() {
  when(rag.assemble(any())).thenReturn(ProjectKnowledgeAgentToolsTest.context("P"));
  when(briefs.collect(anyString(),anyString())).thenThrow(new RuntimeException("unavailable"));
  when(chat.chatUnifiedWithToolCallbacks(anyString(),anyString(),any(ToolCallback[].class))).thenAnswer(inv->{
   AgentChatServiceTest.call(AgentChatServiceTest.callbacks(inv.getArguments()),"searchProjectKnowledge","{\"query\":\"UnseenService\"}");
   return "UnseenService가 예약을 관리합니다 [K1-S1].";
  });
  var result=service.unified(new AgentChatRequest("P","UnseenService 역할"),briefs).response();
  assertThat(result.status()).isEqualTo(AgentChatStatus.INSUFFICIENT_EVIDENCE);
  assertThat(result.answer()).contains("현재 확보된 근거에서는 확인되지 않습니다").doesNotContain("예약을 관리");
 }
 @Test void identifiersWithKoreanParticlesAndFilesNeedExactEvidence() {
  for(String text:List.of("ReservationLockService는", "ReservationLockService가", "ReservationLockService의 역할",
    "ReservationLockService.java는", "register.ts에서 구현합니다")) {
   assertThat(AgentChatService.unsupportedIdentifiers(text,"class OtherReservationLockService {}", "P")).hasSize(1);
  }
  assertThat(AgentChatService.unsupportedIdentifiers("ReservationLockService는 예약 잠금을 담당합니다.",
    "class ReservationLockService {}","P")).isEmpty();
  assertThat(AgentChatService.unsupportedIdentifiers("register.ts에서", "src/register.ts","P")).isEmpty();
 }
 @Test void inventedAnswerSymbolIsBlockedEvenWhenUserDidNotNameItAndCitationExists() {
  when(rag.assemble(any())).thenReturn(ProjectKnowledgeAgentToolsTest.context("P"));
  when(briefs.collect(anyString(),anyString())).thenThrow(new RuntimeException("unavailable"));
  when(chat.chatUnifiedWithToolCallbacks(anyString(),anyString(),any(ToolCallback[].class))).thenAnswer(inv->{
   AgentChatServiceTest.call(AgentChatServiceTest.callbacks(inv.getArguments()),"searchProjectKnowledge","{\"query\":\"소개\"}");
   return "InventedPaymentService가 결제를 처리합니다 [K1-S1].";
  });
  var result=service.unified(new AgentChatRequest("P","프로젝트 소개"),briefs).response();
  assertThat(result.status()).isEqualTo(AgentChatStatus.INSUFFICIENT_EVIDENCE);
  assertThat(result.answer()).doesNotContain("InventedPaymentService","결제를 처리");
 }
 @Test void incompleteResponseHasFailureStatusAndExplicitMessage() {
  when(chat.chatUnifiedWithToolCallbacks(anyString(),anyString(),any(ToolCallback[].class)))
    .thenThrow(new com.localai.workspace.chat.IncompleteResponseException());
  var result=service.unified(new AgentChatRequest("P","ReservationLockService는?"),briefs).response();
  assertThat(result.status()).isEqualTo(AgentChatStatus.LLM_FAILED);
  assertThat(result.answer()).contains("정상 종료", "다시 시도");
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
