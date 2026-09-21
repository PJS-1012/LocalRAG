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
        var files=snapshot.allowedFiles();
        String lower=query.toLowerCase(Locale.ROOT);
        var ordered=files.stream().sorted(Comparator.comparingInt((FileScanEntry f)->rank(f,lower))
                .thenComparing(FileScanEntry::relativePath)).toList();
        List<Excerpt> excerpts=new ArrayList<>(); List<String> limitations=new ArrayList<>();
        limitations.add("Bounded file samples, not a complete architecture or proof that features/tests are complete. Unread sections remain unknown.");
        int remaining=6000;
        // One representative per category first, then fill; controllers must not crowd out services/build/tests.
        var selected=new LinkedHashSet<FileScanEntry>();
        ordered.stream().collect(java.util.stream.Collectors.groupingBy(f->rank(f,lower),TreeMap::new,
                java.util.stream.Collectors.toList())).values().forEach(group->selected.add(group.get(0)));
        selected.addAll(ordered);
        for(var file:selected.stream().limit(8).toList()) {
            if(remaining<200)break;
            var read=reader.read(project,file.relativePath()); // re-applies policy, boundary and 5 MB check
            if(read.document()==null) {limitations.add(file.relativePath()+": "+read.status());continue;}
            var lines=read.document().content().split("\\R",-1);
            StringBuilder content=new StringBuilder();int end=0;
            int limit=Math.min(1400,remaining);
            for(String line:lines) {String safe=redactor.redact(line); if(content.length()+safe.length()+1>limit)break;
                content.append(safe).append('\n');end++;}
            if(end>0) {excerpts.add(new Excerpt(file.relativePath().replace('\\','/'),1,end,content.toString()));remaining-=content.length();}
        }
        var paths=ordered.stream().limit(30).map(f->f.relativePath().replace('\\','/')).toList();
        var packages=files.stream().map(f->f.relativePath().replace('\\','/'))
                .filter(p->p.contains("/")).map(p->p.substring(0,p.lastIndexOf('/'))).distinct().sorted().limit(25).toList();
        return new Brief(projectId,project.projectType().name(),project.detectedFramework(),packages,paths,
                snapshot.statistics(),List.copyOf(excerpts),overview.facts(projectId),List.copyOf(limitations),(System.nanoTime()-started)/1_000_000);
    }
    private int rank(FileScanEntry f,String query) {
        String name=f.fileName().toLowerCase(Locale.ROOT),stem=name.replaceFirst("\\.[^.]+$","");
        if(stem.length()>3 && query.contains(stem))return 0; // exact identifier lookup, not intent routing
        if(name.startsWith("readme"))return 1;
        if(Set.of("build.gradle","build.gradle.kts","pom.xml","package.json","cargo.toml").contains(name))return 2;
        if(name.endsWith("application.java")||name.equals("program.cs"))return 3;
        if(name.endsWith("controller.java"))return 4;
        if(name.endsWith("service.java"))return 5;
        if(f.relativePath().replace('\\','/').contains("docs/decisions/"))return 6;
        if(name.endsWith("test.java"))return 7;
        return 8;
    }
}
