package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.adapter.ocr.OcrExtractionConfig;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;

@Component
@RequiredArgsConstructor
public class PageImageRenderer {

    private final OcrExtractionConfig config;

    public byte[] renderPage(
        String documentPath,
        int pageNum
    ) throws IOException {
        try (PDDocument document = Loader.loadPDF(Path.of(documentPath).toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage image = renderer.renderImageWithDPI(
                pageNum - 1,
                config.renderDpi(),
                ImageType.RGB
            );

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", output);

            return output.toByteArray();
        }
    }
}
