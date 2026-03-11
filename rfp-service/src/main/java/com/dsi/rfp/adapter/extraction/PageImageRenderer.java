package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.adapter.vision.VisionExtractionConfig;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;

@Component
@RequiredArgsConstructor
public class PageImageRenderer {

    private final VisionExtractionConfig config;

    public byte[] renderPage(
        String documentPath,
        int pageNum
    ) throws IOException {
        return renderPage(
            documentPath,
            pageNum,
            config.fullPageDpi()
        );
    }

    public byte[] renderPage(
        String documentPath,
        int pageNum,
        int dpi
    ) throws IOException {
        BufferedImage image = renderBufferedImage(
            documentPath,
            pageNum,
            dpi
        );

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", output);

        return output.toByteArray();
    }

    public byte[] renderPageJpeg(
        String documentPath,
        int pageNum,
        int dpi,
        float quality
    ) throws IOException {
        BufferedImage image = renderBufferedImage(
            documentPath,
            pageNum,
            dpi
        );

        return writeJpeg(image, quality);
    }

    private BufferedImage renderBufferedImage(
        String documentPath,
        int pageNum,
        int dpi
    ) throws IOException {
        try (PDDocument document = Loader.loadPDF(
            Path.of(documentPath).toFile()
        )) {
            PDFRenderer renderer = new PDFRenderer(document);

            return renderer.renderImageWithDPI(
                pageNum - 1,
                dpi,
                ImageType.RGB
            );
        }
    }

    private byte[] writeJpeg(
        BufferedImage image,
        float quality
    ) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        ImageWriter writer = writers.next();

        ImageWriteParam params = writer.getDefaultWriteParam();
        params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionQuality(quality);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writer.setOutput(
            ImageIO.createImageOutputStream(output)
        );
        writer.write(
            null,
            new IIOImage(image, null, null),
            params
        );
        writer.dispose();

        return output.toByteArray();
    }
}
