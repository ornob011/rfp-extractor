package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.config.BanglaEncodingProperties;
import com.dsi.rfp.domain.exception.DocumentCorruptException;
import com.dsi.rfp.domain.exception.DocumentEncryptedException;
import com.dsi.rfp.domain.model.ValidationErrorCode;
import com.dsi.rfp.domain.model.ValidationResult;
import com.dsi.rfp.domain.port.out.MimeTypePort;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentValidationServiceTest {

    @TempDir
    static Path tempDir;

    static Path validPdf;
    static Path emptyFile;

    @Mock
    private MimeTypePort mimeTypePort;

    private DocumentValidationService service;

    @BeforeAll
    static void createFixtures() throws IOException {
        validPdf = tempDir.resolve("valid.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            doc.save(validPdf.toFile());
        }

        emptyFile = tempDir.resolve("empty.txt");
        Files.writeString(emptyFile, "not a pdf");
    }

    @BeforeEach
    void setUp() {
        BanglaEncodingProperties properties = defaultProperties();

        BanglaEncodingDetector encodingDetector = new BanglaEncodingDetector(
            new BanglaScriptAnalyzer(),
            new LegacyBanglaPatternMatcher(properties),
            new BanglaEncodingDecisionPolicy(properties)
        );

        service = new DocumentValidationService(
            mimeTypePort,
            encodingDetector,
            properties,
            100
        );
    }

    @Test
    void shouldRejectFileThatExceedsSizeLimit() {
        long oversizeBytes = 101L * 1024 * 1024;
        ValidationResult result = service.validate(validPdf, oversizeBytes);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(ValidationErrorCode.FILE_TOO_LARGE);
    }

    @Test
    void shouldRejectUnsupportedMimeType() {
        when(mimeTypePort.detect(any())).thenReturn("text/plain");
        ValidationResult result = service.validate(emptyFile, 100);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(ValidationErrorCode.UNSUPPORTED_TYPE);
    }

    @Test
    void shouldAcceptValidPdf() {
        when(mimeTypePort.detect(any())).thenReturn("application/pdf");
        ValidationResult result = service.validate(validPdf, validPdf.toFile().length());
        assertThat(result.isValid()).isTrue();
    }

    @Test
    void shouldThrowForEncryptedPdf() throws IOException {
        Path encrypted = tempDir.resolve("encrypted.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            doc.protect(new org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy(
                "owner", "user",
                new org.apache.pdfbox.pdmodel.encryption.AccessPermission()
            ));
            doc.save(encrypted.toFile());
        }
        when(mimeTypePort.detect(any())).thenReturn("application/pdf");
        assertThatThrownBy(() -> service.validate(encrypted, 100))
            .isInstanceOf(DocumentEncryptedException.class);
    }

    @Test
    void shouldThrowForCorruptFile() throws IOException {
        Path corrupt = tempDir.resolve("corrupt.pdf");
        Files.writeString(corrupt, "this is not a pdf");
        when(mimeTypePort.detect(any())).thenReturn("application/pdf");
        assertThatThrownBy(() -> service.validate(corrupt, 100))
            .isInstanceOf(DocumentCorruptException.class);
    }

    @Test
    void shouldRejectZeroPagePdf() throws IOException {
        Path zeroPdf = tempDir.resolve("zero.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.save(zeroPdf.toFile());
        }
        when(mimeTypePort.detect(any())).thenReturn("application/pdf");
        ValidationResult result = service.validate(zeroPdf, zeroPdf.toFile().length());
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(ValidationErrorCode.EMPTY_PDF);
    }

    private BanglaEncodingProperties defaultProperties() {
        return new BanglaEncodingProperties(
            3,
            0.8,
            10,
            0.30,
            0.60,
            1,
            3,
            0.60,
            0.85,
            "No legacy encoding detected",
            "Detected %d Bangla Unicode characters with mix ratio %.2f and %d suspicious legacy patterns; possible SutonnyMJ/Bijoy legacy encoding",
            java.util.List.of(
                "cÖ",
                "wK",
                "‡h"
            )
        );
    }
}
