package com.localai.workspace.automation;

import com.localai.workspace.agent.*;
import com.localai.workspace.errors.ErrorProjectScope;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class EnvironmentWatchService {
    private final ErrorProjectScope scope;
    private final DockerReadOnlyService docker;
    private final OllamaReadOnlyService ollama;
    private final DatabaseReadOnlyService database;
    private final LogSecretRedactor redactor;

    public EnvironmentWatchService(ErrorProjectScope scope,DockerReadOnlyService docker,
            OllamaReadOnlyService ollama,DatabaseReadOnlyService database,LogSecretRedactor redactor) {
        this.scope=scope; this.docker=docker; this.ollama=ollama; this.database=database; this.redactor=redactor;
    }

    public EnvironmentWatchResult watch(String projectId) {
        long started=System.nanoTime();
        String project=scope.require(projectId);
        List<EnvironmentObservation> values=new ArrayList<>();
        try {
            var value=docker.getStatus();
            values.add(observation("DOCKER",value.status(),value.reason()));
        } catch(RuntimeException exception) { values.add(failed("DOCKER")); }
        try {
            var value=docker.getProjectContainers(project);
            values.add(observation("PROJECT_CONTAINERS",value.status(),value.reason()));
        } catch(RuntimeException exception) { values.add(failed("PROJECT_CONTAINERS")); }
        try {
            var value=ollama.getStatus();
            values.add(observation("OLLAMA",value.status(),value.reason()));
        } catch(RuntimeException exception) { values.add(failed("OLLAMA")); }
        try {
            var value=database.getStatus();
            values.add(observation("DATABASE",value.status(),value.reason()));
        } catch(RuntimeException exception) { values.add(failed("DATABASE")); }
        int failures=(int)values.stream().filter(this::isFailure).count();
        String input=values.stream().map(value->value.component()+"="+value.status()).sorted()
                .reduce((left,right)->left+"|"+right).orElse("");
        return new EnvironmentWatchResult(project,AutomationFingerprint.sha256(input),failures,
                elapsed(started),List.copyOf(values));
    }

    private EnvironmentObservation observation(String component,LocalEnvironmentStatus status,String reason) {
        return new EnvironmentObservation(component,status.name(),safe(reason));
    }
    private EnvironmentObservation failed(String component) {
        return new EnvironmentObservation(component,LocalEnvironmentStatus.FAILED.name(),"Inspection failed");
    }
    private boolean isFailure(EnvironmentObservation value) {
        return !value.status().equals(LocalEnvironmentStatus.AVAILABLE.name())
                && !value.status().equals(LocalEnvironmentStatus.NOT_CONFIGURED.name());
    }
    private String safe(String value){return value==null?null:redactor.redact(value);}
    private long elapsed(long started){return (System.nanoTime()-started)/1_000_000;}
}
