package com.localai.workspace.agent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
class BoundedLiveProcessTest {
    @TempDir Path temp;
    public static class Probe {
        public static void main(String[] args) throws Exception {
            if(args[0].equals("hang")) Thread.sleep(60_000);
            else System.out.println("completed");
        }
    }
    @Test void terminatesHungChildThenContinuesNextCase() throws Exception {
        var timeout=BoundedLiveProcess.run(BoundedLiveProcess.command(Probe.class.getName(),"hang"),
                Map.of(),temp.resolve("hang.log"),Duration.ofSeconds(2));
        assertThat(timeout.status()).isEqualTo("TIMEOUT");
        assertThat(timeout.durationMillis()).isLessThan(10_000);
        var next=BoundedLiveProcess.run(BoundedLiveProcess.command(Probe.class.getName(),"ok"),
                Map.of(),temp.resolve("ok.log"),Duration.ofSeconds(10));
        assertThat(next.status()).isEqualTo("COMPLETED");
    }
}
