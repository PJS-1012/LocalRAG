package com.localai.workspace.automation;

import com.localai.workspace.agent.*;
import com.localai.workspace.errors.*;
import com.localai.workspace.index.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ProjectStateFingerprintService {
    private final ErrorProjectScope scope;
    private final GitReadOnlyService git;
    private final ErrorHistoryQueryService histories;
    private final ProjectIndexRepository indexes;

    public ProjectStateFingerprintService(ErrorProjectScope scope,GitReadOnlyService git,
            ErrorHistoryQueryService histories,ProjectIndexRepository indexes) {
        this.scope=scope; this.git=git; this.histories=histories; this.indexes=indexes;
    }

    public ProjectStateSnapshot capture(String projectId) {
        long started=System.nanoTime();
        String project=scope.require(projectId);
        List<String> issues=new ArrayList<>();
        String head="NONE";
        String working="UNAVAILABLE";
        String history="UNAVAILABLE";
        String index="UNAVAILABLE";
        long unresolved=0;
        try {
            GitRecentCommitsResult commits=git.getRecentCommits(project,1);
            if(commits.status()==GitToolStatus.SUCCESS && !commits.commits().isEmpty())
                head=commits.commits().get(0).hash();
            else if(commits.status()!=GitToolStatus.SUCCESS) issues.add("Git HEAD: "+commits.status());
            GitStatusResult status=git.getStatus(project);
            GitDiffSummaryResult diff=git.getDiffSummary(project);
            if(status.status()==GitToolStatus.SUCCESS && diff.status()==GitToolStatus.SUCCESS)
                working=AutomationFingerprint.sha256(workingInput(status,diff));
            else issues.add("Git working tree unavailable");
        } catch(RuntimeException exception) { issues.add("Git state collection failed"); }
        try {
            var state=histories.automationState(project);
            unresolved=state.unverified()+state.verified();
            history=AutomationFingerprint.sha256(state.toString());
        } catch(RuntimeException exception) { issues.add("Error History state collection failed"); }
        try {
            ProjectIndexStats stats=indexes.stats(project).orElse(ProjectIndexStats.empty(project));
            index=AutomationFingerprint.sha256(stats.storedChunkCount()+"|"+stats.embeddingModel()+"|"
                    +stats.dimensions()+"|"+stats.latestIndexedAt());
        } catch(RuntimeException exception) { issues.add("Project Index state collection failed"); }
        String fingerprint=AutomationFingerprint.sha256(project+"|"+head+"|"+working+"|"+history+"|"+index);
        return new ProjectStateSnapshot(project,fingerprint,head,working,history,index,unresolved,
                elapsed(started),List.copyOf(issues));
    }

    private String workingInput(GitStatusResult status,GitDiffSummaryResult diff) {
        List<String> paths=new ArrayList<>();
        status.modified().forEach(value->paths.add("M:"+value));
        status.added().forEach(value->paths.add("A:"+value));
        status.deleted().forEach(value->paths.add("D:"+value));
        status.untracked().forEach(value->paths.add("?:"+value));
        diff.files().forEach(value->paths.add("N:"+value.filePath()+":"+value.additions()+":"+value.deletions()
                +":"+value.binary()));
        diff.untrackedFiles().forEach(value->paths.add("U:"+value));
        Collections.sort(paths);
        return status.branch()+"|"+status.clean()+"|"+String.join("|",paths);
    }
    private long elapsed(long started){return (System.nanoTime()-started)/1_000_000;}
}
