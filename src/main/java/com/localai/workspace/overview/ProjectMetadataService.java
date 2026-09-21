package com.localai.workspace.overview;

import com.localai.workspace.discovery.*;
import com.localai.workspace.scan.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

/** Bounded, TTL-cached metadata only. Never reads content or invokes a model. */
@Service
public class ProjectMetadataService {
    private final ProjectFileScanner scanner;
    private final Duration ttl;
    private long maxFileSizeBytes=5L*1024*1024;
    @org.springframework.beans.factory.annotation.Autowired
    public void setPolicy(WorkspaceScanPolicy policy) { maxFileSizeBytes=policy.maxFileSizeBytes(); }
    private final Map<String, Snapshot> cache = new LinkedHashMap<>();
    public ProjectMetadataService(ProjectFileScanner scanner,
            @Value("${localrag.overview.metadata-cache-ttl:PT5M}") Duration ttl) {
        this.scanner=scanner; this.ttl=ttl;
        if(ttl.isNegative() || ttl.isZero()) throw new IllegalArgumentException("Cache TTL must be positive");
    }
    public record Language(String name, long files, double percent) {}
    public record Languages(String status, String basis, List<Language> languages,
            long totalSourceFiles, Instant scannedAt, long scanDurationMillis, boolean cacheHit) {}
    public record Snapshot(DetectedProject project, List<FileScanEntry> allowedFiles, Languages statistics) {}
    private static final Map<String,String> LANGUAGES=Map.ofEntries(
        Map.entry("java","Java"),Map.entry("kt","Kotlin"),Map.entry("cs","C#"),
        Map.entry("cpp","C++"),Map.entry("cc","C++"),Map.entry("h","C++"),Map.entry("c","C"),
        Map.entry("js","JavaScript"),Map.entry("jsx","JavaScript"),Map.entry("ts","TypeScript"),
        Map.entry("tsx","TypeScript"),Map.entry("py","Python"),Map.entry("php","PHP"),
        Map.entry("sql","SQL"),Map.entry("html","HTML"),Map.entry("css","CSS"),
        Map.entry("yml","YAML"),Map.entry("yaml","YAML"),Map.entry("rs","Rust"));
    public synchronized Snapshot get(DetectedProject project) {
        String key=project.rootPath().toAbsolutePath().normalize()+"|"+project.projectType();
        Snapshot cached=cache.get(key);
        if(cached!=null && cached.statistics().scannedAt().plus(ttl).isAfter(Instant.now())) {
            var s=cached.statistics();
            return new Snapshot(project,cached.allowedFiles(),new Languages(s.status(),s.basis(),s.languages(),
                    s.totalSourceFiles(),s.scannedAt(),s.scanDurationMillis(),true));
        }
        long started=System.nanoTime();
        var plan=scanner.plan(project);
        var languageCandidates=plan.files().stream().filter(f->f.status()==FileScanStatus.SUPPORTED
                        || f.status()==FileScanStatus.SKIPPED_EXTENSION && "Unsupported file extension".equals(f.reason()))
                .filter(f->f.sizeBytes()>=0 && f.sizeBytes()<=maxFileSizeBytes)
                .filter(f->Arrays.stream(f.relativePath().replace('\\','/').split("/"))
                        .noneMatch(p->Set.of("library","temp","logs","node_modules","build","dist","target",".gradle",".git","bin","obj").contains(p.toLowerCase(Locale.ROOT))))
                .toList();
        var allowed=languageCandidates.stream().filter(f->f.status()==FileScanStatus.SUPPORTED).toList();
        Map<String,Long> counts=new TreeMap<>();
        for(var file:languageCandidates) {String language=LANGUAGES.get(file.extension().replaceFirst("^\\.","").toLowerCase(Locale.ROOT));
            if(language!=null) counts.merge(language,1L,Long::sum);}
        long total=counts.values().stream().mapToLong(Long::longValue).sum();
        var languages=counts.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(e->new Language(e.getKey(),e.getValue(),Math.round(e.getValue()*1000.0/Math.max(1,total))/10.0)).toList();
        var result=new Snapshot(project,allowed,new Languages("SUCCESS","ELIGIBLE_SOURCE_FILE_COUNT",languages,total,
                Instant.now(),(System.nanoTime()-started)/1_000_000,false));
        if(cache.size()>=64)cache.remove(cache.keySet().iterator().next());
        cache.put(key,result); return result;
    }
}
