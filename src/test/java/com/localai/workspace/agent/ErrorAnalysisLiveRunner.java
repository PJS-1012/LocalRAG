package com.localai.workspace.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** One child JVM per case; incremental, timestamped reports retain earlier runs. */
public class ErrorAnalysisLiveRunner {
    public static void main(String[] args) throws Exception {
        runCases(args.length==0?List.of("NPE","DB","EMPTY"):List.of(args));
    }
    public static void runCases(List<String> cases) throws Exception {
        long seconds=Long.parseLong(System.getProperty("localrag.eval.timeout-seconds","90"));
        if(seconds<1 || seconds>180) throw new IllegalArgumentException("Live timeout must be 1..180 seconds");
        Path directory=Path.of("build/reports/error-analysis-live",Instant.now().toString().replace(':','-'));
        Files.createDirectories(directory);
        var json=new ObjectMapper().findAndRegisterModules();
        var rows=new ArrayList<Map<String,Object>>();
        for(String id:cases) {
            if(!id.matches("(NPE|DB|EMPTY|GIT|SECRET|NO_KNOWLEDGE|LEGACY_Q([1-9]|1[0-3]))"))
                throw new IllegalArgumentException("Unsupported evaluation case");
            Path result=directory.resolve(id+".json").toAbsolutePath();
            Map<String,String> env=id.startsWith("LEGACY_")
                    ? Map.of("LOCALRAG_AGENT_EVAL_CASES",id.substring(7)):Map.of();
            BoundedLiveProcess.Outcome outcome;
            long started=System.nanoTime();
            try {
                outcome=BoundedLiveProcess.run(BoundedLiveProcess.command(ErrorAnalysisLiveChild.class.getName(),
                        id,result.toString()),env,directory.resolve(id+".log"),Duration.ofSeconds(seconds));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw interrupted;
            } catch (Exception failure) {
                outcome=new BoundedLiveProcess.Outcome("FAILED",BoundedLiveProcess.elapsed(started),null);
            }
            var row=new LinkedHashMap<String,Object>();
            row.put("case",id); row.put("execution",outcome);
            row.put("quality",outcome.status().equals("COMPLETED")?"REQUIRES_REVIEW":"FAIL");
            row.put("artifact",result.toString()); rows.add(row);
            json.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("summary.json").toFile(),rows);
            System.out.println(id+" "+outcome.status()+" "+outcome.durationMillis()+" ms");
        }
        System.out.println("Evaluation report: "+directory.toAbsolutePath());
    }
}
