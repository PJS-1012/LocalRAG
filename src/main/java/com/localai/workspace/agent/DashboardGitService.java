package com.localai.workspace.agent;

import com.localai.workspace.discovery.DetectedProject;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class DashboardGitService {
    private final GitProcessRunner runner;
    public DashboardGitService(GitProcessRunner runner) { this.runner = runner; }
    public record Commit(String hash, String message, String author, String timestamp, String pushStatus) {}
    public record Changes(long modified,long added,long deleted) {}
    public record Snapshot(String status, String branch, Boolean clean, String upstream,
            Long ahead, Long behind, String remoteStatus, List<Commit> commits, String reason,Changes changes) {
        public Snapshot(String status,String branch,Boolean clean,String upstream,Long ahead,Long behind,
                String remoteStatus,List<Commit> commits,String reason) {
            this(status,branch,clean,upstream,ahead,behind,remoteStatus,commits,reason,null);
        }
    }
    public Snapshot read(DetectedProject project) {
        if (!project.gitRepository()) return new Snapshot("NOT_GIT_REPOSITORY",null,null,null,null,null,"NOT_GIT_REPOSITORY",List.of(),null);
        var status = runner.status(project.rootPath());
        if (!status.successful()) return unavailable("Git status unavailable");
        String header = status.output().lines().filter(l -> l.startsWith("## ")).findFirst().orElse("## UNKNOWN").substring(3);
        String branch = header.split("\\.\\.\\.",2)[0].replaceFirst("^No commits yet on ", "");
        if (branch.startsWith("HEAD (no branch)")) branch = "DETACHED_HEAD";
        boolean clean = status.output().lines().noneMatch(l -> !l.startsWith("## ") && !l.isBlank());
        String upstream = null, upstreamSha = null, remote = "NO_UPSTREAM";
        Long ahead = null, behind = null;
        if (header.contains("...")) {
            upstream = header.substring(header.indexOf("...")+3).split(" \\[",2)[0];
            var ref = runner.upstream(project.rootPath());
            remote = "UNAVAILABLE";
            if (ref.successful() && ref.output().strip().matches("[0-9a-fA-F]{40,64}")) {
                upstreamSha = ref.output().strip();
                var counts = runner.divergence(project.rootPath(),upstreamSha);
                if (counts.successful()) {
                    String[] parts=counts.output().strip().split("\\s+");
                    if(parts.length==2) try { ahead=Long.parseLong(parts[0]);behind=Long.parseLong(parts[1]);remote="TRACKING"; } catch(NumberFormatException ignored) { }
                }
            }
        }
        var log = runner.recentCommits(project.rootPath(),5);
        List<Commit> commits = new ArrayList<>();
        if(log.successful()) for(String row:log.output().split("\\u001e")) {
            String[] f=row.strip().split("\\u001f",-1);
            if(f.length!=4 || !f[0].matches("[0-9a-fA-F]{40,64}")) continue;
            String pushStatus="NO_UPSTREAM".equals(remote)?"NO_UPSTREAM":"UNKNOWN";
            if(upstreamSha!=null) {
                var ancestry=runner.isAncestor(project.rootPath(),f[0],upstreamSha);
                if(!ancestry.timedOut()&&!ancestry.truncated()) {
                    if(ancestry.exitCode()==0) pushStatus="PUSHED";
                    else if(ancestry.exitCode()==1) pushStatus="UNPUSHED";
                }
            }
            commits.add(new Commit(f[0],f[1],f[2],f[3],pushStatus));
        }
        return new Snapshot("SUCCESS",branch,clean,upstream,ahead,behind,remote,List.copyOf(commits),
                log.successful()?null:"Recent commits unavailable",changes(status.output()));
    }
    private Changes changes(String output) {
        long modified=0,added=0,deleted=0;
        for(String line:output.split("\\R")) {
            if(line.startsWith("##")||line.length()<3)continue;
            String xy=line.substring(0,2);
            if(xy.contains("D"))deleted++;
            else if(xy.equals("??")||xy.contains("A"))added++;
            else modified++;
        }
        return new Changes(modified,added,deleted);
    }
    private Snapshot unavailable(String reason) { return new Snapshot("UNAVAILABLE",null,null,null,null,null,"UNAVAILABLE",List.of(),reason); }
}
