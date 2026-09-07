package com.localai.workspace.agent;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.*;

class DockerProcessRunnerTest {
    @Test
    void boundsInheritedPipeWaitAndTerminatesOnlyOwnedChildren() throws Exception {
        var releaseReader = new CountDownLatch(1);
        var process = mock(Process.class);
        var child = mock(ProcessHandle.class);
        when(process.descendants()).thenAnswer(inv -> Stream.of(child));
        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(false);
        when(process.getInputStream()).thenReturn(new InputStream() {
            public int read() throws IOException {
                try { releaseReader.await(); return -1; }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
            }
        });
        var runner = new DockerProcessRunner(new LocalEnvironmentProperties(
                Duration.ofMillis(50), Duration.ofSeconds(1), 1024, 5));
        try {
            var result = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> runner.awaitResult(process));
            assertThat(result.timedOut()).isTrue();
            assertThat(result.truncated()).isTrue();
            verify(child, atLeastOnce()).destroyForcibly();
            verify(process, atLeastOnce()).destroyForcibly();
        } finally {
            releaseReader.countDown();
        }
    }
}
