package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.domain.model.EmbeddedImageInfo;
import com.dsi.rfp.domain.model.TextBlock;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class PdfDocumentLoader {

    public String loadFullText(Path filePath) throws IOException {
        try (PDDocument doc = Loader.loadPDF(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    public String loadPageText(
        Path filePath,
        int pageNumber
    ) throws IOException {
        try (PDDocument doc = Loader.loadPDF(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(pageNumber);
            stripper.setEndPage(pageNumber);
            return stripper.getText(doc);
        }
    }

    public int getPageCount(Path filePath) throws IOException {
        try (PDDocument doc = Loader.loadPDF(filePath.toFile())) {
            return doc.getNumberOfPages();
        }
    }

    public PDRectangle getPageDimensions(
        Path filePath,
        int pageNumber
    ) throws IOException {
        try (PDDocument doc = Loader.loadPDF(filePath.toFile())) {
            PDPage page = doc.getPage(pageNumber - 1);
            return page.getMediaBox();
        }
    }

    public List<TextBlock> loadPageBoundingBoxes(
        Path filePath,
        int pageNumber
    ) throws IOException {
        try (PDDocument doc = Loader.loadPDF(filePath.toFile())) {
            BoundingBoxTextStripper stripper =
                new BoundingBoxTextStripper(pageNumber);
            stripper.setStartPage(pageNumber);
            stripper.setEndPage(pageNumber);
            stripper.getText(doc);
            return stripper.getTextBlocks();
        }
    }

    public List<EmbeddedImageInfo> loadPageImages(
        Path filePath,
        int pageNumber
    ) throws IOException {
        try (PDDocument doc = Loader.loadPDF(filePath.toFile())) {
            PDPage page = doc.getPage(pageNumber - 1);
            return extractImages(page, pageNumber);
        }
    }

    private List<EmbeddedImageInfo> extractImages(
        PDPage page,
        int pageNumber
    ) throws IOException {
        List<EmbeddedImageInfo> images = new ArrayList<>();
        PDResources resources = page.getResources();
        if (resources == null) {
            return images;
        }
        for (var name : resources.getXObjectNames()) {
            PDXObject xObject = resources.getXObject(name);
            if (xObject instanceof PDImageXObject img) {
                images.add(EmbeddedImageInfo.builder()
                                            .pageNumber(pageNumber)
                                            .x(0)
                                            .y(0)
                                            .width(img.getWidth())
                                            .height(img.getHeight())
                                            .build());
            }
        }
        return images;
    }
}
