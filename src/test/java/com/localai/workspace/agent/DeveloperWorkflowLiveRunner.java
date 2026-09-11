package com.localai.workspace.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.*;
import java.util.*;

public class DeveloperWorkflowLiveRunner {
    private static final List<String> CASES=List.of("SIMILAR","PROGRESS","ACTIVITY","DATABASE","NO_TOOL");
    public static void main(String[] args) throws Exception {
        long seconds=Long.parseLong(System.getProperty("localrag.eval.timeout-seconds","90"));
        if(seconds<1 || seconds>180) throw new IllegalArgumentException("Live timeout must be 1..180 seconds");
        Path directory=Path.of("build/reports/developer-workflow-live",Instant.now().toString().replace(':','-'));
        Files.createDirectories(directory);
        var json=new ObjectMapper().findAndRegisterModules();
        var rows=new ArrayList<Map<String,Object>>();
        for(String id:CASES) {
            Path artifact=directory.resolve(id+".json").toAbsolutePath();
            BoundedLiveProcess.Outcome outcome=BoundedLiveProcess.run(
                    BoundedLiveProcess.command(DeveloperWorkflowLiveChild.class.getName(),id,artifact.toString()),
                    Map.of(),directory.resolve(id+".log"),Duration.ofSeconds(seconds));
            var row=new LinkedHashMap<String,Object>();
            row.put("case",id); row.put("execution",outcome); row.put("artifact",artifact.toString());
            if(outcome.status().equals("COMPLETED") && Files.exists(artifact)) {
                var result=json.readTree(artifact.toFile());
                row.put("toolsUsed",result.path("toolsUsed"));
                row.put("agentStatus",result.path("status").asText());
                row.put("quality","REQUIRES_REVIEW");
            } else row.put("quality","FAIL");
            rows.add(row);
            json.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("summary.json").toFile(),rows);
            System.out.println(id+" "+outcome.status()+" "+outcome.durationMillis()+" ms");
        }
        System.out.println("Evaluation report: "+directory.toAbsolutePath());
    }
}
