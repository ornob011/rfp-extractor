package com.dsi.rfp.adapter.persistence;

import com.dsi.rfp.domain.port.out.FileStoragePort;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

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

        writeBytes(target, content);

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

    private String sanitizeFilename(String filename) {
        String cleaned = StringUtils.cleanPath(filename);
        String name = FilenameUtils.getName(cleaned);
        return name.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }

    private void createDirectories(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(
                String.format("Failed to create directory: %s", dir),
                e
            );
        }
    }

    private void writeBytes(Path target, byte[] content) {
        try {
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException(
                String.format("Failed to write file: %s", target),
                e
            );
        }
    }
}
