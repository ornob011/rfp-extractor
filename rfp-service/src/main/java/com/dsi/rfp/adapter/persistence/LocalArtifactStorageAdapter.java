package com.dsi.rfp.adapter.persistence;

import com.dsi.rfp.adapter.artifact.ArtifactTypeResolver;
import com.dsi.rfp.domain.exception.ResourceNotFoundException;
import com.dsi.rfp.domain.exception.SystemIoException;
import com.dsi.rfp.domain.model.ArtifactMetadata;
import com.dsi.rfp.domain.model.StoredArtifact;
import com.dsi.rfp.domain.port.out.ArtifactPort;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Component
public class LocalArtifactStorageAdapter implements ArtifactPort {

    private final String basePath;
    private final ArtifactTypeResolver artifactTypeResolver;

    public LocalArtifactStorageAdapter(
        @Value("${app.storage.base-path}") String basePath,
        ArtifactTypeResolver artifactTypeResolver
    ) {
        this.basePath = basePath;
        this.artifactTypeResolver = artifactTypeResolver;
    }

    @Override
    public ArtifactMetadata storeArtifact(
        Long jobId,
        String filename,
        byte[] content
    ) {
        String sanitizedFilename = sanitizeFilename(filename);
        Path dir = artifactDir(jobId);
        createDirectories(dir);

        writeArtifact(
            dir,
            sanitizedFilename,
            content
        );

        log.info(
            "Artifact stored: jobId={}, filename={}, size={}",
            jobId,
            sanitizedFilename,
            content.length
        );

        return ArtifactMetadata.builder()
                               .jobId(jobId)
                               .filename(sanitizedFilename)
                               .fileType(artifactTypeResolver.fileType(sanitizedFilename))
                               .sizeBytes(content.length)
                               .generatedAt(Instant.now())
                               .downloadUrl(String.format(
                                   "/api/v1/rfp/artifacts/%d/%s",
                                   jobId,
                                   sanitizedFilename
                               ))
                               .build();
    }

    @Override
    public List<ArtifactMetadata> listArtifacts(Long jobId) {
        Path dir = artifactDir(jobId);

        if (!Files.exists(dir)) {
            return List.of();
        }

        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(Files::isRegularFile)
                        .map(path -> toMetadata(jobId, path))
                        .toList();
        } catch (IOException ex) {
            throw new SystemIoException(
                String.format("Failed to list artifacts for job: %d", jobId),
                ex
            );
        }
    }

    @Override
    public byte[] loadArtifact(Long jobId, String filename) {
        String sanitizedFilename = sanitizeFilename(filename);
        Path filePath = artifactDir(jobId).resolve(sanitizedFilename);

        if (!Files.exists(filePath)) {
            throw new ResourceNotFoundException(
                String.format(
                    "Artifact not found: jobId=%d, filename=%s",
                    jobId,
                    sanitizedFilename
                )
            );
        }

        try {
            return Files.readAllBytes(filePath);
        } catch (IOException ex) {
            throw new SystemIoException(
                String.format(
                    "Failed to load artifact: jobId=%d, filename=%s",
                    jobId,
                    sanitizedFilename
                ),
                ex
            );
        }
    }

    @Override
    public void deleteArtifacts(Long jobId) {
        Path dir = artifactDir(jobId);

        if (!Files.exists(dir)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(java.util.Comparator.reverseOrder())
                 .forEach(this::deletePath);
        } catch (IOException ex) {
            throw new SystemIoException(
                String.format("Failed to delete artifacts for job: %d", jobId),
                ex
            );
        }
    }

    @Override
    public List<ArtifactMetadata> replaceArtifacts(
        Long jobId,
        List<StoredArtifact> artifacts
    ) {
        Path dir = artifactDir(jobId);
        Path tempDir = tempArtifactDir(jobId);

        deleteDirectoryIfExists(tempDir);
        createDirectories(tempDir);

        List<ArtifactMetadata> metadata = artifacts.stream()
                                                   .map(artifact -> writeArtifact(
                                                       tempDir,
                                                       sanitizeFilename(artifact.filename()),
                                                       artifact.content()
                                                   ))
                                                   .map(path -> toMetadata(jobId, path))
                                                   .toList();

        deleteDirectoryIfExists(dir);
        moveDirectory(
            tempDir,
            dir
        );

        return metadata;
    }

    private Path artifactDir(Long jobId) {
        return Path.of(basePath, jobId.toString(), "artifacts");
    }

    private Path tempArtifactDir(Long jobId) {
        return Path.of(
            basePath,
            jobId.toString(),
            "artifacts.tmp"
        );
    }

    private void createDirectories(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException ex) {
            throw new SystemIoException(
                String.format("Failed to create artifact directory: %s", dir),
                ex
            );
        }
    }

    private void writeBytes(Path filePath, byte[] content) {
        try {
            Files.write(filePath, content);
        } catch (IOException ex) {
            throw new SystemIoException(
                String.format("Failed to write artifact: %s", filePath),
                ex
            );
        }
    }

    private Path writeArtifact(
        Path dir,
        String filename,
        byte[] content
    ) {
        Path filePath = dir.resolve(filename);
        writeBytes(
            filePath,
            content
        );
        return filePath;
    }

    private ArtifactMetadata toMetadata(Long jobId, Path path) {
        try {
            return ArtifactMetadata.builder()
                                   .jobId(jobId)
                                   .filename(path.getFileName().toString())
                                   .fileType(artifactTypeResolver.fileType(
                                       path.getFileName().toString()
                                   ))
                                   .sizeBytes(Files.size(path))
                                   .generatedAt(
                                       Files.getLastModifiedTime(path).toInstant()
                                   )
                                   .downloadUrl(String.format(
                                       "/api/v1/rfp/artifacts/%d/%s",
                                       jobId,
                                       path.getFileName()
                                   ))
                                   .build();
        } catch (IOException ex) {
            throw new SystemIoException(
                String.format("Failed to read artifact metadata: %s", path),
                ex
            );
        }
    }

    private String sanitizeFilename(String filename) {
        String normalized = Path.of(filename).getFileName().toString();

        if (StringUtils.isBlank(normalized)) {
            throw new ResourceNotFoundException("Artifact filename is blank");
        }

        if (!normalized.equals(filename)) {
            throw new ResourceNotFoundException(
                String.format(
                    "Artifact filename is invalid: %s",
                    filename
                )
            );
        }

        return normalized;
    }

    private void deletePath(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            throw new SystemIoException(
                String.format("Failed to delete artifact path: %s", path),
                ex
            );
        }
    }

    private void deleteDirectoryIfExists(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(java.util.Comparator.reverseOrder())
                 .forEach(this::deletePath);
        } catch (IOException ex) {
            throw new SystemIoException(
                String.format("Failed to delete artifact directory: %s", dir),
                ex
            );
        }
    }

    private void moveDirectory(
        Path source,
        Path target
    ) {
        try {
            Files.move(
                source,
                target
            );
        } catch (IOException ex) {
            throw new SystemIoException(
                String.format(
                    "Failed to move artifact directory from %s to %s",
                    source,
                    target
                ),
                ex
            );
        }
    }
}
