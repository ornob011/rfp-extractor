package com.dsi.rfp.adapter.extraction;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfDocumentLoaderTest {

    @TempDir
    static Path tempDir;

    static Path textPdf;
    static Path emptyPdf;

    private final PdfDocumentLoader loader = new PdfDocumentLoader();

    @BeforeAll
    static void createFixtures() throws IOException {
        textPdf = tempDir.resolve("text.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("Hello PDF World");
                cs.endText();
            }
            PDPage page2 = new PDPage();
            doc.addPage(page2);
            doc.save(textPdf.toFile());
        }

        emptyPdf = tempDir.resolve("empty.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.save(emptyPdf.toFile());
        }
    }

    @Test
    void shouldLoadFullText() throws IOException {
        String text = loader.loadFullText(textPdf);
        assertThat(text).contains("Hello PDF World");
    }

    @Test
    void shouldLoadPageText() throws IOException {
        String text = loader.loadPageText(textPdf, 1);
        assertThat(text).contains("Hello PDF World");
    }

    @Test
    void shouldReturnPageCount() throws IOException {
        int count = loader.getPageCount(textPdf);
        assertThat(count).isEqualTo(2);
    }

    @Test
    void shouldReturnEmptyTextForBlankPage() throws IOException {
        String text = loader.loadPageText(emptyPdf, 1);
        assertThat(text.trim()).isEmpty();
    }

    @Test
    void shouldThrowForMissingFile() {
        Path missing = tempDir.resolve("nonexistent.pdf");
        assertThatThrownBy(() -> loader.loadFullText(missing))
            .isInstanceOf(IOException.class);
    }

    @Test
    void shouldReturnPageDimensions() throws IOException {
        PDRectangle dims = loader.getPageDimensions(textPdf, 1);
        assertThat(dims.getWidth()).isGreaterThan(0);
        assertThat(dims.getHeight()).isGreaterThan(0);
    }
}
