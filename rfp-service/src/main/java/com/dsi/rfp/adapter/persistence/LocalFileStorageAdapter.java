package com.dsi.rfp.adapter.persistence;

import com.dsi.rfp.domain.exception.SystemIoException;
import com.dsi.rfp.domain.port.out.FileStoragePort;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

@Slf4j
@Component
public class LocalFileStorageAdapter implements FileStoragePort {

    private final Path basePath;

    public LocalFileStorageAdapter(
        @Value("${app.storage.base-path:/tmp/rfp-storage}") String basePath
    ) {
        this.basePath = Path.of(basePath);
    }

    @Override
    public Path store(
        Long jobId,
        byte[] content,
        String filename
    ) {
        Path dir = jobDirectory(jobId);
        createDirectories(dir);

        Path target = dir.resolve(sanitizeFilename(filename));
        writeBytes(
            target,
            content
        );

        log.info(
            "event=file.stored component=LocalFileStorageAdapter jobId={} file={} bytes={}",
            jobId,
            target.getFileName(),
            content.length
        );

        return target;
    }

    @Override
    public Path retrieve(Long jobId, String filename) {
        return jobDirectory(jobId)
            .resolve(sanitizeFilename(filename));
    }

    @Override
    public Path jobDirectory(Long jobId) {
        return basePath.resolve(jobId.toString());
    }

    @Override
    public void deleteJobDirectory(Long jobId) {
        Path directory = jobDirectory(jobId);

        if (!Files.exists(directory)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder())
                .forEach(this::deletePath);
        } catch (IOException exception) {
            throw new SystemIoException(
                String.format("Failed to delete job directory: %s", directory),
                exception
            );
        }
    }

    private String sanitizeFilename(String filename) {
        String cleaned = StringUtils.cleanPath(filename);
        String name = FilenameUtils.getName(cleaned);
        return name.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }

    private void createDirectories(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException exception) {
            throw new SystemIoException(
                String.format("Failed to create directory: %s", dir),
                exception
            );
        }
    }

    private void writeBytes(Path target, byte[] content) {
        try {
            Files.write(target, content);
        } catch (IOException exception) {
            throw new SystemIoException(
                String.format("Failed to write file: %s", target),
                exception
            );
        }
    }

    private void deletePath(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new SystemIoException(
                String.format("Failed to delete path: %s", path),
                exception
            );
        }
    }
}
