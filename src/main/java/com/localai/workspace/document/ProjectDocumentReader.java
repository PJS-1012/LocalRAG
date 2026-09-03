package com.localai.workspace.document;

import com.localai.workspace.discovery.DetectedProject;
import com.localai.workspace.discovery.ProjectDiscoveryService;
import com.localai.workspace.scan.WorkspaceScanPolicy;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;

@Service
public class ProjectDocumentReader {

    private final ProjectDiscoveryService discoveryService;
    private final WorkspaceScanPolicy scanPolicy;

    public ProjectDocumentReader(ProjectDiscoveryService discoveryService, WorkspaceScanPolicy scanPolicy) {
        this.discoveryService = discoveryService;
        this.scanPolicy = scanPolicy;
    }

    public DocumentReadResult read(String projectName, String relativeFilePath) {
        return read(discoveryService.defaultWorkspaceRoot(), projectName, relativeFilePath);
    }

    public DocumentReadResult read(Path workspaceRoot, String projectName, String relativeFilePath) {
        Optional<DetectedProject> detectedProject = discoveryService.findProject(workspaceRoot, projectName);
        if (detectedProject.isEmpty()) {
            return failed("Project was not found: " + projectName);
        }

        return read(detectedProject.get(), relativeFilePath);
    }

    public DocumentReadResult read(DetectedProject project, String relativeFilePath) {
        Path requestedPath;
        try {
            requestedPath = Path.of(relativeFilePath);
        } catch (InvalidPathException | NullPointerException exception) {
            return failed("The requested file path is invalid");
        }
        if (requestedPath.isAbsolute()) {
            return failed("Only a project-relative file path is allowed");
        }

        Path projectRoot = project.rootPath().toAbsolutePath().normalize();
        Path candidate = projectRoot.resolve(requestedPath).normalize();
        if (!candidate.startsWith(projectRoot)) {
            return failed("The requested file is outside the selected project");
        }

        for (Path directory = candidate.getParent();
             directory != null && !directory.equals(projectRoot);
             directory = directory.getParent()) {
            if (scanPolicy.isExcludedDirectory(directory, projectRoot, project.projectType())) {
                return DocumentReadResult.skipped(
                        DocumentReadStatus.SKIPPED_EXCLUDED_PATH,
                        "The file is inside an excluded directory"
                );
            }
        }
        if (scanPolicy.isSensitiveFile(candidate)) {
            return DocumentReadResult.skipped(DocumentReadStatus.SKIPPED_SENSITIVE, "Sensitive file policy");
        }
        if (scanPolicy.isExcludedFile(candidate) || !scanPolicy.isSupported(candidate)) {
            return DocumentReadResult.skipped(DocumentReadStatus.SKIPPED_EXTENSION, "Unsupported or excluded file type");
        }

        try {
            Path realProjectRoot = projectRoot.toRealPath();
            Path realFile = candidate.toRealPath();
            if (!realFile.startsWith(realProjectRoot)) {
                return failed("The resolved file is outside the selected project");
            }

            BasicFileAttributes attributes = Files.readAttributes(
                    realFile,
                    BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS
            );
            if (!attributes.isRegularFile()) {
                return failed("The requested path is not a regular file");
            }
            if (attributes.size() > scanPolicy.maxFileSizeBytes()) {
                return tooLarge(attributes.size());
            }

            byte[] bytes = readWithinLimit(realFile, scanPolicy.maxFileSizeBytes());
            if (bytes == null) {
                return tooLarge(scanPolicy.maxFileSizeBytes() + 1);
            }
            String content = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();

            return DocumentReadResult.success(new WorkspaceDocument(
                    project.name(),
                    project.projectId(),
                    realFile.getFileName().toString(),
                    realFile.toString(),
                    scanPolicy.extension(realFile),
                    bytes.length,
                    attributes.lastModifiedTime().toInstant(),
                    content
            ));
        } catch (IOException | SecurityException exception) {
            return failed("UTF-8 file read failed: " + exception.getClass().getSimpleName());
        }
    }

    private byte[] readWithinLimit(Path file, long maxBytes) throws IOException {
        try (InputStream input = Files.newInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    return null;
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private DocumentReadResult tooLarge(long observedBytes) {
        return DocumentReadResult.skipped(
                DocumentReadStatus.SKIPPED_TOO_LARGE,
                "File size " + observedBytes + " bytes exceeds current limit "
                        + scanPolicy.maxFileSizeBytes() + " bytes"
        );
    }

    private DocumentReadResult failed(String reason) {
        return DocumentReadResult.skipped(DocumentReadStatus.READ_FAILED, reason);
    }
}
