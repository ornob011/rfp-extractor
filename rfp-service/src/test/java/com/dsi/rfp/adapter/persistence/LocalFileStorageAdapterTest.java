package com.dsi.rfp.adapter.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalFileStorageAdapterTest {

    @TempDir
    Path tempDir;

    private LocalFileStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new LocalFileStorageAdapter(tempDir.toString());
    }

    @Test
    void shouldStoreAndRetrieveFile() throws IOException {
        Long jobId = 1L;
        byte[] content = "test content".getBytes();
        Path stored = adapter.store(jobId, content, "test.pdf");

        assertThat(stored).exists();
        assertThat(Files.readAllBytes(stored)).isEqualTo(content);
    }

    @Test
    void shouldRetrieveStoredFilePath() {
        Long jobId = 2L;
        byte[] content = "content".getBytes();
        adapter.store(jobId, content, "doc.pdf");

        Path retrieved = adapter.retrieve(jobId, "doc.pdf");
        assertThat(retrieved).exists();
    }

    @Test
    void shouldReturnJobDirectory() {
        Long jobId = 42L;
        Path dir = adapter.jobDirectory(jobId);
        assertThat(dir.toString()).contains("42");
    }

    @Test
    void shouldSanitizePathTraversal() {
        Long jobId = 3L;
        byte[] content = "safe".getBytes();
        Path stored = adapter.store(
            jobId, content, "../../../etc/passwd"
        );

        assertThat(stored)
            .hasParent(
                tempDir.resolve("3")
            );

        assertThat(stored.getFileName().toString())
            .doesNotContain("..");
    }
}
