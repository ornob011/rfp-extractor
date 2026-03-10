package com.dsi.rfp.adapter.persistence;

import com.dsi.rfp.adapter.security.FileEncryptionService;
import com.dsi.rfp.domain.model.ArtifactMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EncryptedArtifactStorageAdapterTest {

    @Mock
    private LocalArtifactStorageAdapter delegate;

    @Mock
    private FileEncryptionService encryptionService;

    private EncryptedArtifactStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new EncryptedArtifactStorageAdapter(delegate, encryptionService);
    }

    @Test
    void shouldEncryptArtifactBeforeStoring() {
        byte[] content = "artifact content".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = "encrypted".getBytes(StandardCharsets.UTF_8);

        when(encryptionService.encrypt(content)).thenReturn(encrypted);
        when(delegate.storeArtifact(eq(1L), eq("report.xlsx"), eq(encrypted)))
            .thenReturn(ArtifactMetadata.builder().jobId(1L).filename("report.xlsx").build());

        ArtifactMetadata result = adapter.storeArtifact(1L, "report.xlsx", content);

        assertThat(result.getFilename()).isEqualTo("report.xlsx");
        verify(encryptionService).encrypt(content);
    }

    @Test
    void shouldDecryptArtifactOnLoad() {
        byte[] encrypted = "on-disk-bytes".getBytes(StandardCharsets.UTF_8);
        byte[] plaintext = "original content".getBytes(StandardCharsets.UTF_8);

        when(delegate.loadArtifact(1L, "report.xlsx")).thenReturn(encrypted);
        when(encryptionService.decrypt(encrypted)).thenReturn(plaintext);

        byte[] result = adapter.loadArtifact(1L, "report.xlsx");

        assertThat(result).isEqualTo(plaintext);
    }
}
