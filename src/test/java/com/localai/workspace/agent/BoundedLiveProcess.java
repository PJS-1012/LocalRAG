package com.localai.workspace.agent;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Process isolation is intentional: interrupting an HTTP/Tool thread alone is not a timeout guarantee. */
public final class BoundedLiveProcess {
    public record Outcome(String status, long durationMillis, Integer exitCode) { }
    public static Outcome run(List<String> command, Map<String,String> env, Path output, Duration timeout) throws Exception {
        Files.createDirectories(output.toAbsolutePath().getParent());
        var builder=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile());
        builder.environment().putAll(env);
        long start=System.nanoTime();
        Process process=builder.start();
        try {
            if (process.waitFor(timeout.toMillis(),TimeUnit.MILLISECONDS))
                return new Outcome(process.exitValue()==0?"COMPLETED":"FAILED",elapsed(start),process.exitValue());
            return new Outcome("TIMEOUT",elapsed(start),null);
        } finally {
            // Only descendants owned by this case are terminated, never the shared Ollama/Docker service.
            var descendants=process.descendants().toList();
            descendants.forEach(ProcessHandle::destroyForcibly);
            if(process.isAlive()) process.destroyForcibly();
            if(!process.waitFor(5,TimeUnit.SECONDS)) throw new IllegalStateException("Case process did not terminate");
            for(var child:descendants) if(child.isAlive()) child.destroyForcibly();
        }
    }
    static String javaExecutable() {
        return Path.of(System.getProperty("java.home"),"bin",
                System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
    }
    static List<String> command(String main,String...args) {
        var command=new ArrayList<>(List.of(javaExecutable(),"-Dfile.encoding=UTF-8","-cp",
                System.getProperty("localrag.eval.classpath",System.getProperty("java.class.path")),main));
        command.addAll(List.of(args)); return command;
    }
    static long elapsed(long start){return (System.nanoTime()-start)/1_000_000;}
}
