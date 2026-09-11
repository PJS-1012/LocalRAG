package com.localai.workspace.errors;
import com.localai.workspace.discovery.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;
class ErrorProjectScopeTest {
    @TempDir Path temp;
    @Test void resolvesCanonicalIdButRejectsTraversalAndContainer() throws Exception {
        Files.createDirectories(temp.resolve("group/app/src/main/java"));
        Files.writeString(temp.resolve("group/app/pom.xml"),"<project/>");
        var scope=new ErrorProjectScope(new ProjectDiscoveryService(new WorkspaceProperties(temp),new ProjectTypeDetector()));
        assertThat(scope.require("group\\app")).isEqualTo("group/app");
        for(String id: java.util.List.of("../group/app",temp.resolve("group/app").toString(),"group","absent"))
            assertThatThrownBy(()->scope.require(id)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
}
