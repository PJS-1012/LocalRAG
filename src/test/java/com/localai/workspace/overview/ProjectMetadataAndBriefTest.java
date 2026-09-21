package com.localai.workspace.overview;
import com.localai.workspace.agent.LogSecretRedactor;
import com.localai.workspace.discovery.*;
import com.localai.workspace.document.*;
import com.localai.workspace.scan.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectMetadataAndBriefTest {
 @TempDir Path root;
 @Test void cachesMetadataAndRechecksSensitiveExcludedLargeAndChangedFiles() throws Exception {
  var projectRoot=Files.createDirectory(root.resolve("P"));
  Files.writeString(projectRoot.resolve("build.gradle"),"plugins {}");
  Files.writeString(projectRoot.resolve("README.md"),"# Booking\npassword=hidden-value\n");
  Files.writeString(projectRoot.resolve("EntryApplication.java"),"class EntryApplication {}");
  Files.writeString(projectRoot.resolve("BookingService.java"),"class BookingService {}");
  Files.writeString(projectRoot.resolve("settings.yaml"),"mode: local");
  Files.writeString(projectRoot.resolve("secret.java"),"private data");
  Files.writeString(projectRoot.resolve("Large.java"),"x".repeat(101));
  Files.createDirectories(projectRoot.resolve("build"));
  Files.writeString(projectRoot.resolve("build/Generated.java"),"generated");
  var discovery=new ProjectDiscoveryService(new WorkspaceProperties(root),new ProjectTypeDetector());
  var policy=new WorkspaceScanPolicy(new WorkspaceScanProperties(DataSize.ofBytes(100),Set.of("java","md","gradle","yaml"),Set.of(),
    Set.of("build"),Set.of(),Set.of("Library"),List.of(),List.of("*secret*")));
  var scanner=spy(new ProjectFileScanner(discovery,policy));
  var metadata=new ProjectMetadataService(scanner,Duration.ofMinutes(5));
  var project=discovery.findProject(root,"P").orElseThrow();
  var first=metadata.get(project);
  assertThat(first.statistics().totalSourceFiles()).isEqualTo(3);
  assertThat(first.statistics().languages()).extracting(ProjectMetadataService.Language::name).containsExactly("Java","YAML");
  assertThat(first.statistics().languages().get(0).percent()).isEqualTo(66.7);
  assertThat(metadata.get(project).statistics().cacheHit()).isTrue();
  verify(scanner,times(1)).plan(project);
  var overview=mock(ProjectOverviewService.class);when(overview.facts("P")).thenReturn(Map.of("index","not indexed"));
  var brief=new ProjectBriefService(discovery,metadata,new ProjectDocumentReader(discovery,policy),new LogSecretRedactor(),overview);
  var result=brief.collect("P","프로젝트 소개");
  assertThat(result.excerpts()).extracting(ProjectBriefService.Excerpt::path).contains("README.md","BookingService.java")
    .doesNotContain("secret.java","Large.java","build/Generated.java");
  assertThat(result.toString()).doesNotContain("hidden-value","private data");
  Files.writeString(projectRoot.resolve("BookingService.java"),"x".repeat(101)); // stale cache must not bypass reader policy
  assertThat(brief.collect("P","BookingService").excerpts()).extracting(ProjectBriefService.Excerpt::path).doesNotContain("BookingService.java");
  assertThatThrownBy(()->brief.collect("../P","query")).isInstanceOf(RuntimeException.class);
 }
 @Test void cacheSeparatesProjectRootsAndRejectsInvalidTtl() {
  var scanner=mock(ProjectFileScanner.class);
  var a=new DetectedProject("backend","A/backend",root.resolve("A/backend"),ProjectType.JAVA,null,false,true,List.of());
  var b=new DetectedProject("backend","B/backend",root.resolve("B/backend"),ProjectType.JAVA,null,false,true,List.of());
  when(scanner.plan(a)).thenReturn(new ProjectScanPlan(a,null,List.of()));
  when(scanner.plan(b)).thenReturn(new ProjectScanPlan(b,null,List.of()));
  var metadata=new ProjectMetadataService(scanner,Duration.ofMinutes(1));
  metadata.get(a);metadata.get(b);metadata.get(a);
  verify(scanner,times(1)).plan(a);verify(scanner,times(1)).plan(b);
  assertThatThrownBy(()->new ProjectMetadataService(scanner,Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
 }
}
