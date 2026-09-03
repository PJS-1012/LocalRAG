package com.localai.workspace.scan;

import com.localai.workspace.discovery.ProjectType;
import org.springframework.stereotype.Component;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class WorkspaceScanPolicy {

    private final WorkspaceScanProperties properties;
    private final Set<String> includeExtensions;
    private final Set<String> includeFileNames;
    private final Set<String> commonExcludeDirectories;
    private final Set<String> commonExcludePaths;
    private final Set<String> unityExcludeDirectories;
    private final List<PathMatcher> excludeFileMatchers;
    private final List<PathMatcher> sensitiveFileMatchers;

    public WorkspaceScanPolicy(WorkspaceScanProperties properties) {
        this.properties = properties;
        this.includeExtensions = normalizeExtensions(properties.includeExtensions());
        this.includeFileNames = normalize(properties.includeFileNames());
        this.commonExcludeDirectories = normalize(properties.commonExcludeDirectories());
        this.commonExcludePaths = normalizePaths(properties.commonExcludePaths());
        this.unityExcludeDirectories = normalize(properties.unityExcludeDirectories());
        this.excludeFileMatchers = matchers(properties.excludeFilePatterns());
        this.sensitiveFileMatchers = matchers(properties.sensitiveFilePatterns());
    }

    public long maxFileSizeBytes() {
        return properties.maxFileSize().toBytes();
    }

    public boolean isExcludedDirectory(Path directory, Path projectRoot, ProjectType projectType) {
        String name = normalize(directory.getFileName().toString());
        String relativePath = normalizePath(projectRoot.relativize(directory).toString());
        return commonExcludeDirectories.contains(name)
                || commonExcludePaths.contains(relativePath)
                || (projectType == ProjectType.UNITY && unityExcludeDirectories.contains(name));
    }

    public boolean isSensitiveFile(Path file) {
        return matches(sensitiveFileMatchers, file.getFileName().toString());
    }

    public boolean isExcludedFile(Path file) {
        return matches(excludeFileMatchers, file.getFileName().toString());
    }

    public boolean isSupported(Path file) {
        String fileName = normalize(file.getFileName().toString());
        return includeFileNames.contains(fileName)
                || includeExtensions.contains(extension(file));
    }

    public String extension(Path file) {
        String name = file.getFileName().toString();
        int lastDot = name.lastIndexOf('.');
        return lastDot >= 0 && lastDot < name.length() - 1
                ? name.substring(lastDot + 1).toLowerCase(Locale.ROOT)
                : "";
    }

    private boolean matches(List<PathMatcher> matchers, String fileName) {
        Path normalizedName = Path.of(normalize(fileName));
        return matchers.stream().anyMatch(matcher -> matcher.matches(normalizedName));
    }

    private List<PathMatcher> matchers(List<String> patterns) {
        if (patterns == null) {
            return List.of();
        }
        return patterns.stream()
                .map(this::normalize)
                .map(pattern -> FileSystems.getDefault().getPathMatcher("glob:" + pattern))
                .toList();
    }

    private Set<String> normalize(Set<String> values) {
        if (values == null) {
            return Set.of();
        }
        Set<String> normalized = new HashSet<>();
        values.forEach(value -> normalized.add(normalize(value)));
        return Set.copyOf(normalized);
    }

    private Set<String> normalizeExtensions(Set<String> values) {
        if (values == null) {
            return Set.of();
        }
        Set<String> normalized = new HashSet<>();
        values.forEach(value -> normalized.add(normalize(value).replaceFirst("^\\.", "")));
        return Set.copyOf(normalized);
    }

    private Set<String> normalizePaths(Set<String> values) {
        if (values == null) {
            return Set.of();
        }
        Set<String> normalized = new HashSet<>();
        values.forEach(value -> normalized.add(normalizePath(value)));
        return Set.copyOf(normalized);
    }

    private String normalizePath(String value) {
        return normalize(value).replace('\\', '/').replaceAll("^/+|/+$", "");
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
