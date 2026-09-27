package com.localai.workspace.overview;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ProjectEvidencePolicyTest {
    @org.junit.jupiter.api.Test void excerptStartsAtPrimaryTypesOperationInsteadOfSiblingDtoOrLifecycle() {
        String[] lines={"class Data {", "public Data Clone() => new Data();", "}",
                "public class GrowthSystem {", "private void Awake() { Init(); }", "private void OnDestroy() { Cleanup(); }",
                "", "", "public Result ApplyOutcome() {", "  return Calculate();", "}", "}"};
        int from=ProjectBriefService.codeStart("Assets/Scripts/GrowthSystem.cs",lines);
        org.assertj.core.api.Assertions.assertThat(from).isEqualTo(6);
        org.assertj.core.api.Assertions.assertThat(String.join("\n",java.util.Arrays.copyOfRange(lines,from,lines.length)))
                .contains("ApplyOutcome").doesNotContain("Clone","Awake");
    }
    @org.junit.jupiter.api.Test void namedDependencyIsPrioritizedOnlyWhenExplicitlyRequested() {
        org.assertj.core.api.Assertions.assertThat(ProjectEvidencePolicy.priority("Assets/TextMesh Pro/Resources/data.json","TextMesh Pro 사용"))
                .isLessThan(ProjectEvidencePolicy.priority("ProjectSettings/ProjectVersion.txt","TextMesh Pro 사용"));
        org.assertj.core.api.Assertions.assertThat(ProjectEvidencePolicy.allowed("vendor/dep/README.md","README.md 설명"))
                .isFalse();
    }
    @Test void excludesGeneratedAndExternalMaterialWithoutChangingReadPolicy() {
        for(String path:new String[]{"Assets/TextMesh Pro/Fonts/LiberationSans - OFL.txt",
                "Library/PackageCache/x/code.cs","node_modules/pkg/README.md","vendor/framework/help.md",
                "Assets/Plugins/SomePackage/README.md","package-lock.json","AGENTS.md","LICENSE",
                "performance/k6/results/baseline.json","Assets/_Project/Generated/Bindings.cs"})
            assertThat(ProjectEvidencePolicy.allowed(path,"프로젝트 전체 구조 알려줘")).as(path).isFalse();
    }
    @Test void explicitDependencyQuestionCanUseItsSourcesButCannotReadGeneratedPaths() {
        assertThat(ProjectEvidencePolicy.allowed("Assets/TextMesh Pro/Documentation/readme.md","TextMesh Pro는 어떻게 사용해?")).isTrue();
        assertThat(ProjectEvidencePolicy.allowed("Library/TextMesh Pro/example.cs","TextMesh Pro는 어떻게 사용해?")).isFalse();
        assertThat(ProjectEvidencePolicy.allowed("vendor/other-package/README.md","TextMesh Pro는 어떻게 사용해?")).isFalse();
    }
    @Test void prioritizesOwnedUnityRuntimeCodeAndKeepsOriginDistinctFromContentAuthority() {
        String runtime="Assets/_Project/Scripts/UI/MainSceneBootstrap.cs";
        assertThat(ProjectEvidencePolicy.origin(runtime)).isEqualTo(ProjectEvidencePolicy.Origin.PROJECT);
        assertThat(ProjectEvidencePolicy.priority(runtime,"프로젝트 소개"))
                .isLessThan(ProjectEvidencePolicy.priority("Assets/_Project/Editor/SmokeTests.cs","프로젝트 소개"));
        assertThat(ProjectEvidencePolicy.allowed("Assets\\_Project\\Scripts\\Core\\Game.cs","소개")).isTrue();
        assertThat(ProjectEvidencePolicy.origin("README.md")).isEqualTo(ProjectEvidencePolicy.Origin.DOCUMENTATION);
        assertThat(ProjectEvidencePolicy.allowed("src/main/java/AuthService.java","회원가입은 어떻게 해?")).isTrue();
    }
}
