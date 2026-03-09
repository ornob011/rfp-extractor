package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.domain.exception.DocumentCorruptException;
import com.dsi.rfp.domain.exception.DocumentEncryptedException;
import com.dsi.rfp.domain.exception.DocumentXfaException;
import com.dsi.rfp.domain.model.ValidationErrorCode;
import com.dsi.rfp.domain.model.ValidationResult;
import com.dsi.rfp.domain.port.out.MimeTypePort;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

@Slf4j
@Service
public class DocumentValidationService {

    private final MimeTypePort mimeTypePort;
    private final long maxSizeBytes;

    public DocumentValidationService(
        MimeTypePort mimeTypePort,
        @Value("${app.upload.max-size-mb:100}") int maxSizeMb
    ) {
        this.mimeTypePort = mimeTypePort;
        this.maxSizeBytes = (long) maxSizeMb * 1024 * 1024;
    }

    public ValidationResult validate(
        Path filePath,
        long fileSize
    ) {
        if (fileSize > maxSizeBytes) {
            return ValidationResult.fail(
                ValidationErrorCode.FILE_TOO_LARGE,
                String.format(
                    "File size %d bytes exceeds maximum %d bytes",
                    fileSize,
                    maxSizeBytes
                )
            );
        }

        String mimeType = mimeTypePort.detect(filePath);

        if (!MediaType.APPLICATION_PDF_VALUE.equals(mimeType)) {
            return ValidationResult.fail(
                ValidationErrorCode.UNSUPPORTED_TYPE,
                String.format(
                    "Unsupported MIME type: %s",
                    mimeType
                )
            );
        }

        return validatePdfStructure(filePath);
    }

    private ValidationResult validatePdfStructure(Path filePath) {
        try (PDDocument doc = Loader.loadPDF(filePath.toFile())) {
            return validateLoadedPdf(doc, filePath);
        } catch (InvalidPasswordException exception) {
            throw new DocumentEncryptedException(
                String.format("PDF is encrypted: %s", filePath.getFileName()),
                exception
            );
        } catch (IOException exception) {
            throw new DocumentCorruptException(
                String.format("Failed to read PDF: %s", filePath.getFileName()),
                exception
            );
        }
    }

    private ValidationResult validateLoadedPdf(
        PDDocument doc,
        Path filePath
    ) {
        if (doc.isEncrypted()) {
            throw new DocumentEncryptedException(
                String.format("PDF is encrypted: %s", filePath.getFileName())
            );
        }

        PDDocumentCatalog catalog = doc.getDocumentCatalog();
        if (Objects.nonNull(catalog.getAcroForm())
            && catalog.getAcroForm().hasXFA()) {
            throw new DocumentXfaException(
                String.format("PDF contains XFA forms: %s", filePath.getFileName())
            );
        }

        if (doc.getNumberOfPages() < 1) {
            return ValidationResult.fail(
                ValidationErrorCode.EMPTY_PDF,
                "PDF contains zero pages"
            );
        }

        log.info(
            "event=pdf.validated component=DocumentValidationService pages={} file={}",
            doc.getNumberOfPages(),
            filePath.getFileName()
        );

        return ValidationResult.ok();
    }
}
