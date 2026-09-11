package com.localai.workspace.errors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localai.workspace.agent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ErrorHistoryIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg17").asCompatibleSubstituteFor("postgres"));
    @Autowired ErrorAnalysisService analyses;
    @Autowired ErrorHistoryService histories;
    @Autowired ErrorHistoryRepository repository;
    @Autowired ErrorHistoryVerificationRepository verificationRepository;
    @Autowired ObjectMapper json;
    @Autowired MockMvc mvc;
    @MockitoBean AgentChatService agent;
    @MockitoBean ErrorProjectScope scope;
    @MockitoBean ErrorHistoryEmbeddingService embeddingIndexer;

    @BeforeEach void fixture() throws Exception {
        verificationRepository.deleteAllInBatch();
        repository.deleteAllInBatch();
        when(scope.require(anyString())).thenAnswer(i->i.getArgument(0));
        when(embeddingIndexer.index(any())).thenReturn(new ErrorEmbeddingIndexResult(
                ErrorEmbeddingIndexResult.Status.INDEXED,1024,1,1,null));
        var response=new AgentChatResponse("Local_Ai_Work","query","Claim password=hidden123 [K1-S1]",
                List.of("searchLogs"),2,3,5,AgentChatStatus.SUCCESS,List.of(),List.of(),0,List.of(),List.of(),List.of());
        var data=json.readTree("""
                {"status":"SUCCESS","projectId":"Local_Ai_Work","entries":[
                  {"timestamp":"2026-09-09T00:00:00Z","level":"ERROR",
                   "message":"NullPointerException password=hidden123; api_key=apiHidden; Cookie: session=cookieHidden",
                   "sourceFile":"logs/app.log"}],
                   "token":"tokenHidden", "nested":{"password":"jsonHidden"}}
                """);
        when(agent.analyzeError(any())).thenReturn(new AgentAnalysisRun(response,
                List.of(new ToolEvidence(1,"searchLogs",Instant.now(),data))));
    }

    private ErrorHistoryView save(String project,String type,String message,Instant occurred,
            String file,String commit) throws Exception {
        var response=new AgentChatResponse(project,"query","unverified analysis",List.of("searchLogs"),
                1,1,2,AgentChatStatus.SUCCESS,List.of(),List.of(),0,List.of(),List.of(),List.of());
        var log=json.createObjectNode().put("status","SUCCESS").put("projectId",project);
        var entries=log.putArray("entries");
        entries.addObject().put("timestamp",occurred.toString()).put("level","ERROR")
                .put("message","ERROR "+type+": "+message).put("sourceFile",file);
        var evidence=new ArrayList<ToolEvidence>();
        evidence.add(new ToolEvidence(1,"searchLogs",Instant.now(),log));
        if(commit!=null) {
            var git=json.createObjectNode().put("status","SUCCESS").put("projectId",project);
            git.putArray("commits").addObject().put("hash",commit);
            evidence.add(new ToolEvidence(2,"getRecentCommits",Instant.now(),git));
        }
        when(agent.analyzeError(any())).thenReturn(new AgentAnalysisRun(response,List.copyOf(evidence)));
        var draft=analyses.analyzeError(new ErrorAnalysisRequest(project,message));
        return histories.saveErrorHistory(new ErrorHistorySaveRequest(project,draft.analysisId())).history();
    }

    @Test void analyzeDoesNotSaveAndExplicitSaveRedactsPersistsAndDefaultsUnverified() throws Exception {
        long before=repository.count();
        var result=analyses.analyzeError(new ErrorAnalysisRequest("Local_Ai_Work","symptom"));
        assertThat(repository.count()).isEqualTo(before);
        var saved=histories.saveErrorHistory(new ErrorHistorySaveRequest("Local_Ai_Work",result.analysisId()));
        System.out.println("ERROR_HISTORY_DB_SAVE_MS="+saved.dbSaveDurationMillis());
        assertThat(saved.history().status()).isEqualTo(ErrorStatus.UNVERIFIED);
        assertThat(saved.history().rootCause()).isNull();
        String stored=json.writeValueAsString(histories.list("Local_Ai_Work",0,100));
        assertThat(stored).doesNotContain("hidden123","apiHidden","cookieHidden","tokenHidden","jsonHidden");
        assertThat(saved.history().relatedFiles()).containsExactly("logs/app.log");
        assertThat(histories.saveErrorHistory(new ErrorHistorySaveRequest("Local_Ai_Work",result.analysisId())).alreadySaved()).isTrue();
        assertThat(repository.count()).isEqualTo(before+1);
        assertThat(histories.list("Other",0,20).content()).isEmpty();
        assertThatThrownBy(()->histories.saveErrorHistory(new ErrorHistorySaveRequest("Other",result.analysisId())))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test void onlyExplicitStatusUpdateWithNoteCauseAndVersionCanVerifyOrResolve() throws Exception {
        var draft=analyses.analyzeError(new ErrorAnalysisRequest("Local_Ai_Work","symptom"));
        var h=histories.saveErrorHistory(new ErrorHistorySaveRequest("Local_Ai_Work",draft.analysisId())).history();
        assertThat(repository.findById(h.id()).orElseThrow().version).isEqualTo(h.version());
        mvc.perform(patch("/api/workspaces/projects/errors/history/"+h.id()+"/status").contentType("application/json")
                .content(json.writeValueAsString(new ErrorHistoryStatusRequest("Other",ErrorStatus.VERIFIED,
                        "checked", "confirmed cause",null,h.version())))).andExpect(status().isNotFound());
        mvc.perform(patch("/api/workspaces/projects/errors/history/"+h.id()+"/status").contentType("application/json")
                .content(json.writeValueAsString(new ErrorHistoryStatusRequest("Local_Ai_Work",ErrorStatus.VERIFIED,
                        "", "cause",null,h.version())))).andExpect(status().isBadRequest());
        var verified=histories.changeStatus(h.id(),new ErrorHistoryStatusRequest("Local_Ai_Work",ErrorStatus.VERIFIED,
                "Compared log with code", "user confirmed null input", null,h.version()));
        assertThat(verified.status()).isEqualTo(ErrorStatus.VERIFIED);
        assertThatThrownBy(()->histories.changeStatus(h.id(),new ErrorHistoryStatusRequest("Local_Ai_Work",
                ErrorStatus.RESOLVED,"retested","cause","solution",h.version())))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        var resolved=histories.changeStatus(h.id(),new ErrorHistoryStatusRequest("Local_Ai_Work",ErrorStatus.RESOLVED,
                "Retested", "confirmed cause", "Password=solutionSecret",verified.version()));
        assertThat(resolved.status()).isEqualTo(ErrorStatus.RESOLVED);
        assertThat(resolved.solution()).doesNotContain("solutionSecret");
    }

    @Test void postedVerifiedStatusDoesNotPromoteSavedAnalysis() throws Exception {
        var draft=analyses.analyzeError(new ErrorAnalysisRequest("Local_Ai_Work","symptom"));
        mvc.perform(post("/api/workspaces/projects/errors/history").contentType("application/json")
                .content("{\"projectId\":\"Local_Ai_Work\",\"analysisId\":\""+draft.analysisId()+"\",\"status\":\"VERIFIED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.history.status").value("UNVERIFIED"));
        mvc.perform(get("/api/workspaces/projects/errors/history").param("projectId","Local_Ai_Work").param("size","101"))
                .andExpect(status().isBadRequest());
    }

    @Test void detailIsProjectScopedAndReturnsSnapshotAndAuditOnlyThere() throws Exception {
        var h=save("Project-A","java.lang.NullPointerException","detail failure",
                Instant.parse("2026-09-01T00:00:00Z"),"src/A.java","abc1234");
        long started=System.nanoTime();
        var detail=histories.detail(h.id(),"Project-A");
        System.out.println("ERROR_HISTORY_DETAIL_MS="+((System.nanoTime()-started)/1_000_000));
        assertThat(detail.history().evidenceSummary()).isNotNull();
        assertThat(detail.verifications()).isEmpty();
        assertThat(detail.queryDurationMillis()).isGreaterThanOrEqualTo(0);
        assertThatThrownBy(()->histories.detail(h.id(),"Project-B"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(()->histories.detail(Long.MAX_VALUE,"Project-A"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        mvc.perform(get("/api/workspaces/projects/errors/history/"+h.id()).param("projectId","Project-A"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.history.id").value(h.id()))
                .andExpect(jsonPath("$.verifications.length()").value(0));
    }

    @Test void filtersUseInclusiveDatesAndPageIsNewestFirstWithoutAuditJoin() throws Exception {
        Instant firstTime=Instant.parse("2026-09-01T00:00:00Z");
        Instant secondTime=Instant.parse("2026-09-02T00:00:00Z");
        var first=save("Project-A","java.lang.NullPointerException","null user 100%_failure",
                firstTime,"src/A.java","abc1234");
        var second=save("Project-A","java.lang.IllegalArgumentException","bad argument",
                secondTime,"src/B.java","def5678");
        save("Project-B","java.lang.NullPointerException","other project",
                firstTime,"src/C.java","aaa1111");
        var verified=histories.changeStatusWithAudit(first.id(),new ErrorHistoryStatusRequest("Project-A",
                ErrorStatus.VERIFIED,"checked","confirmed null",null,first.version()));

        assertThat(histories.list(filter("Project-A",ErrorStatus.UNVERIFIED,null,null,null,null,null,null,null,null,0,20))
                .content()).extracting(ErrorHistoryView::id).containsExactly(second.id());
        assertThat(histories.list(filter("Project-A",ErrorStatus.VERIFIED,null,null,null,null,null,null,null,null,0,20))
                .content()).extracting(ErrorHistoryView::id).containsExactly(first.id());
        assertThat(histories.list(filter("Project-A",null," JAVA.LANG.NULLPOINTEREXCEPTION ",null,
                null,null,null,null,null,null,0,20)).content()).extracting(ErrorHistoryView::id).containsExactly(first.id());
        assertThat(histories.list(filter("Project-A",null,null,"ARGUMENT",null,null,null,null,null,null,0,20))
                .content()).extracting(ErrorHistoryView::id).containsExactly(second.id());
        assertThat(histories.list(filter("Project-A",null,null,"%_",null,null,null,null,null,null,0,20))
                .content()).extracting(ErrorHistoryView::id).containsExactly(first.id());
        assertThat(histories.list(filter("Project-A",null,null,null,secondTime,secondTime,
                null,null,null,null,0,20)).content()).extracting(ErrorHistoryView::id).containsExactly(second.id());
        assertThat(histories.list(filter("Project-A",null,null,null,null,null,
                Instant.now().minusSeconds(60),Instant.now().plusSeconds(60),null,null,0,20)).totalElements()).isEqualTo(2);
        assertThat(histories.list(filter("Project-A",null,null,null,null,null,null,null,"src\\A.java",null,0,20))
                .content()).extracting(ErrorHistoryView::id).containsExactly(first.id());
        assertThat(histories.list(filter("Project-A",null,null,null,null,null,null,null,null,"def5678",0,20))
                .content()).extracting(ErrorHistoryView::id).containsExactly(second.id());

        long started=System.nanoTime();
        var page0=histories.list(filter("Project-A",null,null,null,null,null,null,null,null,null,0,1));
        System.out.println("ERROR_HISTORY_LIST_MS="+((System.nanoTime()-started)/1_000_000));
        assertThat(page0.totalElements()).isEqualTo(2); assertThat(page0.totalPages()).isEqualTo(2);
        assertThat(page0.content()).extracting(ErrorHistoryView::id).containsExactly(second.id());
        assertThat(histories.list(filter("Project-A",null,null,null,null,null,null,null,null,null,1,1)).content())
                .extracting(ErrorHistoryView::id).containsExactly(first.id());
        assertThat(verificationRepository.count()).isEqualTo(1);
        assertThat(verified.verification().errorHistoryId()).isEqualTo(first.id());
        mvc.perform(get("/api/workspaces/projects/errors/history").param("projectId","Project-A")
                .param("status","VERIFIED").param("size","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(first.id()));
    }

    @Test void strictTransitionsCreateRedactedAuditAndConflictCreatesNoDuplicate() throws Exception {
        var h=save("Project-A","java.lang.NullPointerException","transition failure",
                Instant.parse("2026-09-01T00:00:00Z"),"src/A.java",null);
        assertThatThrownBy(()->histories.changeStatus(h.id(),new ErrorHistoryStatusRequest("Project-A",
                ErrorStatus.RESOLVED,"skip verified","cause","solution",h.version())))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(verificationRepository.countByErrorHistoryId(h.id())).isZero();

        var verified=histories.changeStatusWithAudit(h.id(),new ErrorHistoryStatusRequest("Project-A",
                ErrorStatus.VERIFIED,"Authorization: Bearer noteSecret","Password=rootSecret",null,h.version()));
        System.out.println("ERROR_HISTORY_STATUS_MS="+verified.stateChangeDurationMillis());
        System.out.println("ERROR_HISTORY_AUDIT_SAVE_MS="+verified.auditSaveDurationMillis());
        assertThat(verified.history().status()).isEqualTo(ErrorStatus.VERIFIED);
        assertThat(json.writeValueAsString(verified)).doesNotContain("noteSecret","rootSecret");
        assertThat(verified.verification().fromStatus()).isEqualTo(ErrorStatus.UNVERIFIED);
        assertThat(verified.verification().toStatus()).isEqualTo(ErrorStatus.VERIFIED);
        assertThat(verified.verification().previousVersion()).isEqualTo(h.version());

        assertThatThrownBy(()->histories.changeStatus(h.id(),new ErrorHistoryStatusRequest("Project-A",
                ErrorStatus.RESOLVED,"stale","cause","solution",h.version())))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(verificationRepository.countByErrorHistoryId(h.id())).isEqualTo(1);
        assertThat(histories.detail(h.id(),"Project-A").history().status()).isEqualTo(ErrorStatus.VERIFIED);

        var resolved=histories.changeStatusWithAudit(h.id(),new ErrorHistoryStatusRequest("Project-A",
                ErrorStatus.RESOLVED,"retested","confirmed cause","apiKey=solutionSecret",
                verified.history().version()));
        var detail=histories.detail(h.id(),"Project-A");
        assertThat(detail.history().status()).isEqualTo(ErrorStatus.RESOLVED);
        assertThat(json.writeValueAsString(detail)).doesNotContain("solutionSecret","noteSecret","rootSecret");
        assertThat(detail.verifications()).extracting(ErrorHistoryVerificationView::fromStatus)
                .containsExactly(ErrorStatus.UNVERIFIED,ErrorStatus.VERIFIED);
        assertThat(detail.verifications()).extracting(ErrorHistoryVerificationView::toStatus)
                .containsExactly(ErrorStatus.VERIFIED,ErrorStatus.RESOLVED);
        assertThat(resolved.history().version()).isGreaterThan(verified.history().version());
        assertThatThrownBy(()->histories.changeStatus(h.id(),new ErrorHistoryStatusRequest("Project-A",
                ErrorStatus.VERIFIED,"go backward","cause",null,resolved.history().version())))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(verificationRepository.countByErrorHistoryId(h.id())).isEqualTo(2);
    }

    private ErrorHistoryFilter filter(String project,ErrorStatus status,String type,String message,
            Instant occurredFrom,Instant occurredTo,Instant recordedFrom,Instant recordedTo,
            String file,String commit,int page,int size) {
        return new ErrorHistoryFilter(project,status,type,message,occurredFrom,occurredTo,
                recordedFrom,recordedTo,file,commit,page,size);
    }
}
