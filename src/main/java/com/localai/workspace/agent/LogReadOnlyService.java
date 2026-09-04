package com.localai.workspace.agent;

import com.localai.workspace.discovery.DetectedProject;
import com.localai.workspace.discovery.ProjectDiscoveryService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class LogReadOnlyService {

    private static final List<String> LOG_DIRECTORIES = List.of("logs", "log");
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2}[T ][0-9:.+\\-Z]+)"
    );
    private static final Pattern LEVEL_PATTERN = Pattern.compile(
            "\\b(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\\b", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ERROR_PATTERN = Pattern.compile(
            "(?i)\\b(error|exception|caused by|fatal)\\b"
    );
    private static final Pattern STACK_CONTINUATION = Pattern.compile(
            "^\\s*(at\\s+|Caused by:|Suppressed:|\\.\\.\\. \\d+ more)"
    );

    private final ProjectDiscoveryService projectDiscoveryService;
    private final LogInspectionProperties properties;
    private final LogSecretRedactor redactor;

    public LogReadOnlyService(
            ProjectDiscoveryService projectDiscoveryService,
            LogInspectionProperties properties,
            LogSecretRedactor redactor
    ) {
        this.projectDiscoveryService = projectDiscoveryService;
        this.properties = properties;
        this.redactor = redactor;
    }

    public LogInspectionResult getRecentLogs(String projectId, int requestedLimit) {
        return inspect(projectId, requestedLimit, entry -> true, null);
    }

    public LogInspectionResult getRecentErrors(String projectId, int requestedLimit) {
        return inspect(projectId, requestedLimit, this::isError, null);
    }

    public LogInspectionResult searchLogs(String projectId, String query, int requestedLimit) {
        if (query == null || query.isBlank() || query.length() > properties.maxQueryCharacters()) {
            return failure(
                    projectId, LogToolStatus.INVALID_QUERY,
                    "Log search query must be non-blank and within the configured character limit"
            );
        }
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        return inspect(
                projectId, requestedLimit,
                entry -> entry.message().toLowerCase(Locale.ROOT).contains(normalizedQuery),
                normalizedQuery
        );
    }

    private LogInspectionResult inspect(
            String projectId,
            int requestedLimit,
            Predicate<LogEntry> filter,
            String searchQuery
    ) {
        Optional<DetectedProject> resolved = resolve(projectId);
        if (resolved.isEmpty()) {
            return failure(projectId, LogToolStatus.PROJECT_NOT_FOUND,
                    "Project ID was not found in the Workspace");
        }

        long discoveryStartedAt = System.nanoTime();
        DiscoveryOutcome discovery = discoverLogFiles(resolved.get().rootPath());
        long discoveryDuration = elapsedMillis(discoveryStartedAt);
        if (discovery.files().isEmpty()) {
            return new LogInspectionResult(
                    projectId, LogToolStatus.NO_LOG_FILES, 0, 0, 0, 0,
                    discovery.truncated(), 0, discoveryDuration, 0, List.of(),
                    "No log files were found in the allowed Project log locations"
            );
        }

        long readStartedAt = System.nanoTime();
        int limit = properties.appliedLimit(requestedLimit);
        List<LogEntry> matches = new ArrayList<>();
        int scannedFiles = 0;
        int failedFiles = 0;
        int totalCharacters = 0;
        boolean truncated = discovery.truncated();

        for (Path logFile : discovery.files()) {
            TailContent tail;
            try {
                tail = readTail(logFile);
                scannedFiles++;
                if (tail.partial()) {
                    truncated = true;
                }
            } catch (IOException exception) {
                failedFiles++;
                continue;
            }

            List<LogEntry> entries = parseEntries(resolved.get().rootPath(), logFile, tail);
            for (int index = entries.size() - 1; index >= 0; index--) {
                LogEntry entry = entries.get(index);
                if (!filter.test(entry)) {
                    continue;
                }
                int entryCharacters = entry.message().length();
                if (matches.size() >= limit || totalCharacters + entryCharacters > properties.maxTotalCharacters()) {
                    truncated = true;
                    break;
                }
                matches.add(entry);
                totalCharacters += entryCharacters;
            }
            if (matches.size() >= limit || totalCharacters >= properties.maxTotalCharacters()) {
                break;
            }
        }

        LogToolStatus status = scannedFiles == 0 && failedFiles > 0
                ? LogToolStatus.READ_FAILED
                : LogToolStatus.SUCCESS;
        String reason = status == LogToolStatus.READ_FAILED
                ? "Log files were found but could not be read safely"
                : searchQuery != null && matches.isEmpty()
                ? "No sanitized log entry matched the requested literal text"
                : null;
        return new LogInspectionResult(
                projectId, status, discovery.discoveredFiles(), scannedFiles, failedFiles, matches.size(),
                truncated, totalCharacters, discoveryDuration, elapsedMillis(readStartedAt),
                List.copyOf(matches), reason
        );
    }

    private DiscoveryOutcome discoverLogFiles(Path projectRoot) {
        List<Path> candidates = new ArrayList<>();
        boolean truncated = false;
        AtomicInteger visitedPaths = new AtomicInteger();

        try (Stream<Path> rootFiles = Files.list(projectRoot)) {
            List<Path> directLogs = rootFiles
                    .takeWhile(path -> visitedPaths.getAndIncrement() < properties.maxVisitedPaths())
                    .filter(this::isRegularLogFile)
                    .limit(properties.maxFiles() + 1L)
                    .toList();
            candidates.addAll(directLogs);
            truncated = directLogs.size() > properties.maxFiles();
        } catch (IOException ignored) {
            return new DiscoveryOutcome(List.of(), 0, false);
        }

        for (String directoryName : LOG_DIRECTORIES) {
            Path logDirectory = projectRoot.resolve(directoryName);
            if (!Files.isDirectory(logDirectory, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(logDirectory, properties.maxDepth())) {
                List<Path> directoryLogs = paths
                        .takeWhile(path -> visitedPaths.getAndIncrement() < properties.maxVisitedPaths())
                        .filter(this::isRegularLogFile)
                        .limit(properties.maxFiles() + 1L)
                        .toList();
                candidates.addAll(directoryLogs);
                if (directoryLogs.size() > properties.maxFiles()) {
                    truncated = true;
                }
            } catch (IOException ignored) {
                truncated = true;
            }
        }

        if (visitedPaths.get() >= properties.maxVisitedPaths()) {
            truncated = true;
        }

        List<Path> safeFiles = candidates.stream()
                .distinct()
                .filter(path -> insideProject(projectRoot, path))
                .sorted(Comparator.comparingLong(this::lastModified).reversed())
                .toList();
        int discoveredFiles = safeFiles.size();
        if (safeFiles.size() > properties.maxFiles()) {
            truncated = true;
            safeFiles = safeFiles.subList(0, properties.maxFiles());
        }
        return new DiscoveryOutcome(List.copyOf(safeFiles), discoveredFiles, truncated);
    }

    private boolean isRegularLogFile(Path path) {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && hasLogExtension(path);
    }

    private boolean hasLogExtension(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".log");
    }

    private boolean insideProject(Path projectRoot, Path candidate) {
        try {
            Path realProjectRoot = projectRoot.toRealPath();
            Path realCandidate = candidate.toRealPath();
            return realCandidate.startsWith(realProjectRoot)
                    && Files.isRegularFile(realCandidate, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException exception) {
            return false;
        }
    }

    private long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis();
        } catch (IOException exception) {
            return Long.MIN_VALUE;
        }
    }

    private TailContent readTail(Path logFile) throws IOException {
        try (SeekableByteChannel channel = Files.newByteChannel(logFile, StandardOpenOption.READ)) {
            long size = channel.size();
            int bytesToRead = (int) Math.min(size, properties.tailBytesPerFile());
            long start = Math.max(0, size - bytesToRead);
            channel.position(start);
            ByteBuffer buffer = ByteBuffer.allocate(bytesToRead);
            while (buffer.hasRemaining() && channel.read(buffer) != -1) {
                // Continue until the bounded tail buffer is full or EOF is reached.
            }
            buffer.flip();
            String text = StandardCharsets.UTF_8.decode(buffer).toString();
            boolean partial = start > 0;
            if (partial) {
                int firstLineEnd = text.indexOf('\n');
                text = firstLineEnd >= 0 ? text.substring(firstLineEnd + 1) : "";
            }
            return new TailContent(text, partial);
        }
    }

    private List<LogEntry> parseEntries(Path projectRoot, Path logFile, TailContent tail) {
        List<MutableEntry> grouped = new ArrayList<>();
        List<String> lines = tail.text().lines().toList();
        for (int index = 0; index < lines.size(); index++) {
            String sanitized = redactor.redact(lines.get(index));
            if (sanitized.isBlank()) {
                continue;
            }
            if (STACK_CONTINUATION.matcher(sanitized).find() && !grouped.isEmpty()
                    && grouped.get(grouped.size() - 1).stackLines < properties.maxStackTraceLines()) {
                MutableEntry current = grouped.get(grouped.size() - 1);
                current.message.append(System.lineSeparator()).append(sanitized);
                current.stackLines++;
                continue;
            }

            Long lineNumber = tail.partial() ? null : (long) index + 1;
            grouped.add(new MutableEntry(
                    timestamp(sanitized), level(sanitized), new StringBuilder(sanitized),
                    sourcePath(projectRoot, logFile), lineNumber
            ));
        }
        return grouped.stream().map(MutableEntry::toEntry).toList();
    }

    private String timestamp(String line) {
        Matcher matcher = TIMESTAMP_PATTERN.matcher(line);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String level(String line) {
        Matcher matcher = LEVEL_PATTERN.matcher(line);
        return matcher.find() ? matcher.group(1).toUpperCase(Locale.ROOT) : null;
    }

    private boolean isError(LogEntry entry) {
        return "ERROR".equals(entry.level()) || "FATAL".equals(entry.level())
                || ERROR_PATTERN.matcher(entry.message()).find();
    }

    private String sourcePath(Path projectRoot, Path logFile) {
        return projectRoot.relativize(logFile).toString().replace('\\', '/');
    }

    private Optional<DetectedProject> resolve(String projectId) {
        try {
            return projectDiscoveryService.findProject(projectDiscoveryService.defaultWorkspaceRoot(), projectId);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private LogInspectionResult failure(String projectId, LogToolStatus status, String reason) {
        return new LogInspectionResult(
                projectId, status, 0, 0, 0, 0, false, 0, 0, 0, List.of(), reason
        );
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private record DiscoveryOutcome(List<Path> files, int discoveredFiles, boolean truncated) {
    }

    private record TailContent(String text, boolean partial) {
    }

    private static final class MutableEntry {
        private final String timestamp;
        private final String level;
        private final StringBuilder message;
        private final String sourceFile;
        private final Long lineNumber;
        private int stackLines;

        private MutableEntry(
                String timestamp, String level, StringBuilder message, String sourceFile, Long lineNumber
        ) {
            this.timestamp = timestamp;
            this.level = level;
            this.message = message;
            this.sourceFile = sourceFile;
            this.lineNumber = lineNumber;
        }

        private LogEntry toEntry() {
            return new LogEntry(timestamp, level, message.toString(), sourceFile, lineNumber);
        }
    }
}
