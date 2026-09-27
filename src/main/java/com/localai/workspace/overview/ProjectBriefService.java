package com.localai.workspace.overview;

import com.localai.workspace.agent.LogSecretRedactor;
import com.localai.workspace.discovery.*;
import com.localai.workspace.document.*;
import com.localai.workspace.scan.*;
import org.springframework.stereotype.Service;
import java.util.*;

/** On-demand existing-file evidence. No new Agent Tool, no embedding or model call. */
@Service
public class ProjectBriefService {
    private final ProjectDiscoveryService discovery;
    private final ProjectMetadataService metadata;
    private final ProjectDocumentReader reader;
    private final LogSecretRedactor redactor;
    private final ProjectOverviewService overview;
    public ProjectBriefService(ProjectDiscoveryService discovery,ProjectMetadataService metadata,
            ProjectDocumentReader reader,LogSecretRedactor redactor,ProjectOverviewService overview) {
        this.discovery=discovery;this.metadata=metadata;this.reader=reader;this.redactor=redactor;this.overview=overview;
    }
    public record Excerpt(String path,int startLine,int endLine,String content) {}
    public record Brief(String projectId,String projectType,String framework,List<String> packages,
            List<String> observedPaths,ProjectMetadataService.Languages languages,List<Excerpt> excerpts,
            Map<String,Object> overview,List<String> limitations,long durationMillis) {
        public Brief withoutExcerpts() { return new Brief(projectId,projectType,framework,packages,observedPaths,
                languages,List.of(),overview,limitations,durationMillis); }
    }
    public Brief collect(String projectId,String query) {
        long started=System.nanoTime();
        var project=discovery.findProject(discovery.defaultWorkspaceRoot(),projectId)
                .orElseThrow(()->new IllegalArgumentException("Project not found"));
        var snapshot=metadata.get(project);
        var files=snapshot.allowedFiles().stream()
                .filter(f->ProjectEvidencePolicy.allowed(f.relativePath(),query)).toList();
        String lower=query.toLowerCase(Locale.ROOT);
        var ordered=files.stream().sorted(Comparator.comparingInt((FileScanEntry f)->ProjectEvidencePolicy.priority(f.relativePath(),lower))
                .thenComparingInt(f->f.fileName().matches(".*(System|Manager|Controller)\\.cs")?0:1)
                .thenComparing(FileScanEntry::relativePath)).toList();
        List<Excerpt> excerpts=new ArrayList<>(); List<String> limitations=new ArrayList<>();
        limitations.add("Bounded file samples, not a complete architecture or proof that features/tests are complete. Unread sections remain unknown.");
        int remaining=4800;
        // Keep module diversity: Unity runtime scripts precede Editor/tests and third-party assets.
        var selected=new LinkedHashSet<FileScanEntry>();
        Set<String> modules=new HashSet<>();
        for(var file:ordered) {
            String group=ProjectEvidencePolicy.priority(file.relativePath(),lower)+":"+ProjectEvidencePolicy.module(file.relativePath());
            if(modules.add(group))selected.add(file);
        }
        selected.addAll(ordered);
        for(var file:selected.stream().limit(8).toList()) {
            if(remaining<200||excerpts.size()>=6)break;
            var read=reader.read(project,file.relativePath()); // re-applies policy, boundary and 5 MB check
            if(read.document()==null) {limitations.add(file.relativePath()+": "+read.status());continue;}
            var lines=read.document().content().split("\\R",-1);
            int from=codeStart(file.relativePath(),lines);
            StringBuilder content=new StringBuilder();int end=from;
            int limit=Math.min(1100,remaining);
            for(int i=from;i<lines.length;i++) {String safe=redactor.redact(lines[i]); if(content.length()+safe.length()+1>limit)break;
                content.append(safe).append('\n');end=i+1;}
            if(end>from) {excerpts.add(new Excerpt(file.relativePath().replace('\\','/'),from+1,end,content.toString()));remaining-=content.length();}
        }
        var paths=ordered.stream().limit(12).map(f->f.relativePath().replace('\\','/')).toList();
        var packages=files.stream().map(f->f.relativePath().replace('\\','/'))
                .filter(p->p.contains("/")).map(p->p.substring(0,p.lastIndexOf('/'))).distinct().sorted().limit(12).toList();
        return new Brief(projectId,project.projectType().name(),project.detectedFramework(),packages,paths,
                snapshot.statistics(),List.copyOf(excerpts),overview.facts(projectId),List.copyOf(limitations),(System.nanoTime()-started)/1_000_000);
    }
    static int codeStart(String path,String[] lines) {
        if(!(path.endsWith(".java")||path.endsWith(".cs")))return 0;
        String name=path.replace('\\','/'); name=name.substring(name.lastIndexOf('/')+1).replaceFirst("\\.[^.]+$","");
        int typeStart=0;
        var type=java.util.regex.Pattern.compile("\\b(class|interface|record|enum)\\s+"+java.util.regex.Pattern.quote(name)+"\\b");
        for(int i=0;i<lines.length;i++)if(type.matcher(lines[i]).find()){typeStart=i;break;}
        var method=java.util.regex.Pattern.compile("\\b(public|private|protected|internal)\\s+(?:(?:static|final|virtual|override|async|synchronized)\\s+)*[\\w.<>,?\\[\\]]+\\s+(\\w+)\\s*\\(");
        int first=-1;
        // Prefer a real public operation in the primary type, not a constructor, sibling DTO Clone,
        // or Unity lifecycle boilerplate. Bootstrap lifecycle itself is useful entry-point evidence.
        for(int i=typeStart;i<lines.length;i++) {
            var match=method.matcher(lines[i]);
            if(!match.find() || match.group(2).equals(name))continue;
            if(first<0)first=i;
            if(name.endsWith("Bootstrap"))return Math.max(typeStart,i-2);
            if(match.group(1).equals("public") && !lines[i].contains("=>"))return Math.max(typeStart,i-2);
        }
        return first<0?typeStart:Math.max(typeStart,first-2);
    }
}
