package com.localai.workspace.automation;

import com.localai.workspace.agent.*;
import com.localai.workspace.errors.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.regex.*;

@Service
public class ErrorWatchService {
    private static final Pattern ERROR_TYPE=Pattern.compile(
            "(?:[A-Za-z_$][\\w$]*\\.)*[A-Za-z_$][\\w$]*(?:Exception|Error)\\b");
    private final ErrorProjectScope scope;
    private final LogReadOnlyService logs;
    private final ErrorSimilarityService similarities;
    private final DetectedErrorEventRepository events;
    private final LogSecretRedactor redactor;
    private final AutomationProperties properties;

    public ErrorWatchService(ErrorProjectScope scope,LogReadOnlyService logs,
            ErrorSimilarityService similarities,DetectedErrorEventRepository events,
            LogSecretRedactor redactor,AutomationProperties properties) {
        this.scope=scope; this.logs=logs; this.similarities=similarities; this.events=events;
        this.redactor=redactor; this.properties=properties;
    }

    @Transactional
    public ErrorWatchResult watch(String projectId) {
        long started=System.nanoTime();
        String project=scope.require(projectId);
        long logStarted=System.nanoTime();
        LogInspectionResult inspected=logs.getRecentErrors(project,properties.maxLogResults());
        long logMillis=elapsed(logStarted);
        if(inspected.status()!=LogToolStatus.SUCCESS && inspected.status()!=LogToolStatus.NO_LOG_FILES)
            return new ErrorWatchResult(project,"FAILED",AutomationFingerprint.sha256(inspected.status().name()),
                    0,0,0,logMillis,0,0,elapsed(started),List.of(),safe(inspected.reason()));
        List<LogEntry> entries=List.copyOf(inspected.entries());
        String logFingerprint=AutomationFingerprint.sha256(entries.stream().map(this::fingerprint)
                .sorted().reduce((left,right)->left+"|"+right).orElse(inspected.status().name()));
        List<DetectedErrorCandidate> detected=new ArrayList<>();
        int duplicates=0;
        long similarityMillis=0;
        long databaseMillis=0;
        for(LogEntry entry:entries) {
            String fingerprint=fingerprint(entry);
            if(events.existsByProjectIdAndFingerprint(project,fingerprint)) { duplicates++; continue; }
            String message=safe(entry.message());
            List<ErrorSimilarResult> matches=List.of();
            String lookupStatus="NOT_ATTEMPTED";
            long similarityStarted=System.nanoTime();
            try {
                var result=similarities.find(new ErrorSimilarRequest(project,errorType(message),message,message,5));
                matches=result.results(); lookupStatus=result.status();
            } catch(RuntimeException exception) { lookupStatus="FAILED"; }
            similarityMillis+=elapsed(similarityStarted);
            var event=new DetectedErrorEvent();
            event.projectId=project; event.fingerprint=fingerprint; event.detectedAt=Instant.now();
            event.logTimestamp=bounded(safe(entry.timestamp()),100); event.sourceFile=bounded(safe(entry.sourceFile()),1024);
            event.lineNumber=entry.lineNumber(); event.level=bounded(safe(entry.level()),30); event.errorMessage=message;
            event.similarLookupStatus=lookupStatus; event.similarCount=matches.size();
            if(!matches.isEmpty()) {
                event.topSimilarHistoryId=matches.get(0).errorHistoryId();
                event.topSimilarity=matches.get(0).similarity();
            }
            long databaseStarted=System.nanoTime();
            events.saveAndFlush(event);
            databaseMillis+=elapsed(databaseStarted);
            detected.add(new DetectedErrorCandidate(event.id,fingerprint,event.logTimestamp,event.sourceFile,
                    event.lineNumber,event.level,event.errorMessage,lookupStatus,List.copyOf(matches)));
        }
        return new ErrorWatchResult(project,"SUCCESS",logFingerprint,entries.size(),detected.size(),duplicates,
                logMillis,similarityMillis,databaseMillis,elapsed(started),List.copyOf(detected),null);
    }

    private String fingerprint(LogEntry entry) {
        String material=safe(entry.sourceFile())+"|"+safe(entry.timestamp())+"|"+entry.lineNumber()+"|"
                +normalize(safe(entry.message()));
        return AutomationFingerprint.sha256(material);
    }
    private String normalize(String value){return value.toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").strip();}
    private String errorType(String message){Matcher matcher=ERROR_TYPE.matcher(message);return matcher.find()?matcher.group():null;}
    private String safe(String value){return value==null?"":redactor.redact(value);}
    private String bounded(String value,int max){return value.length()<=max?value:value.substring(0,max);}
    private long elapsed(long started){return (System.nanoTime()-started)/1_000_000;}
}
