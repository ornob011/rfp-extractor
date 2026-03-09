package com.dsi.rfp.adapter.persistence;

import com.dsi.rfp.adapter.security.FileEncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EncryptedDocumentStorageAdapterTest {

    @TempDir
    Path tempDir;

    @Mock
    private LocalFileStorageAdapter delegate;

    @Mock
    private FileEncryptionService encryptionService;

    private EncryptedDocumentStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new EncryptedDocumentStorageAdapter(delegate, encryptionService);
    }

    @Test
    void shouldEncryptBeforeStoring() {
        byte[] content = "test content".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = "encrypted".getBytes(StandardCharsets.UTF_8);

        when(encryptionService.encrypt(content)).thenReturn(encrypted);
        when(delegate.store(eq(1L), eq(encrypted), eq("test.pdf.enc")))
            .thenReturn(Path.of("/tmp/1/test.pdf.enc"));

        Path result = adapter.store(1L, content, "test.pdf");

        assertThat(result.toString()).endsWith("test.pdf.enc");
        verify(encryptionService).encrypt(content);
    }

    @Test
    void shouldDecryptOnRetrieve() throws IOException {
        byte[] encrypted = "encrypted".getBytes(StandardCharsets.UTF_8);
        byte[] plaintext = "plain".getBytes(StandardCharsets.UTF_8);
        Path encryptedPath = tempDir.resolve("test.pdf.enc");

        Files.write(encryptedPath, encrypted);

        when(delegate.retrieve(1L, "test.pdf.enc")).thenReturn(encryptedPath);
        when(encryptionService.decrypt(encrypted)).thenReturn(plaintext);

        Path result = adapter.retrieve(1L, "test.pdf");

        assertThat(Files.readAllBytes(result)).isEqualTo(plaintext);
        verify(encryptionService).decrypt(encrypted);
    }

    @Test
    void shouldDelegateJobDirectory() {
        when(delegate.jobDirectory(1L)).thenReturn(Path.of("/tmp/1"));

        Path result = adapter.jobDirectory(1L);

        assertThat(result.toString()).isEqualTo("/tmp/1");
    }
}
