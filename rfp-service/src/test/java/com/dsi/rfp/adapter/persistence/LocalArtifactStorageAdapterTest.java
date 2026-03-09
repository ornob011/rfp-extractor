package com.dsi.rfp.adapter.persistence;

import com.dsi.rfp.domain.exception.ResourceNotFoundException;
import com.dsi.rfp.domain.model.ArtifactFileType;
import com.dsi.rfp.domain.model.ArtifactMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalArtifactStorageAdapterTest {

    @TempDir
    Path tempDir;

    private LocalArtifactStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new LocalArtifactStorageAdapter(
            tempDir.toString(),
            new com.dsi.rfp.adapter.artifact.ArtifactTypeResolver()
        );
    }

    @Test
    void shouldWriteFileToCorrectPathWhenStoreArtifactCalled() {
        byte[] content = "test content".getBytes();

        ArtifactMetadata metadata = adapter.storeArtifact(
            1L,
            "test.docx",
            content
        );

        assertThat(metadata.getFilename()).isEqualTo("test.docx");
        assertThat(metadata.getFileType()).isEqualTo(ArtifactFileType.DOCX);
        assertThat(metadata.getSizeBytes()).isEqualTo(content.length);
        assertThat(metadata.getDownloadUrl())
            .isEqualTo("/api/v1/rfp/artifacts/1/test.docx");

        Path expectedPath = tempDir.resolve("1/artifacts/test.docx");
        assertThat(expectedPath).exists();
    }

    @Test
    void shouldListAllArtifactsWhenDirectoryHasFiles() {
        adapter.storeArtifact(1L, "a.docx", "doc".getBytes());
        adapter.storeArtifact(1L, "b.xlsx", "sheet".getBytes());
        adapter.storeArtifact(1L, "c.html", "html".getBytes());

        List<ArtifactMetadata> artifacts = adapter.listArtifacts(1L);

        assertThat(artifacts).hasSize(3);
    }

    @Test
    void shouldReturnEmptyListWhenNoArtifactsExist() {
        List<ArtifactMetadata> artifacts = adapter.listArtifacts(999L);

        assertThat(artifacts).isEmpty();
    }

    @Test
    void shouldLoadStoredArtifact() {
        byte[] content = "hello world".getBytes();
        adapter.storeArtifact(1L, "test.txt", content);

        byte[] loaded = adapter.loadArtifact(1L, "test.txt");

        assertThat(loaded).isEqualTo(content);
    }

    @Test
    void shouldThrowWhenArtifactNotFound() {
        assertThatThrownBy(() -> adapter.loadArtifact(1L, "missing.docx"))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldRejectPathTraversalFilename() {
        assertThatThrownBy(() -> adapter.storeArtifact(
            1L,
            "../bad.docx",
            "bad".getBytes()
        )).isInstanceOf(ResourceNotFoundException.class);
    }
}
