package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.domain.model.EmbeddedImageInfo;
import com.dsi.rfp.domain.model.FontInfo;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public Map<String, FontInfo> loadFontMetadata(Path filePath) throws IOException {
        Map<String, FontInfo> fontMap = new HashMap<>();

        int pageCount = getPageCount(filePath);

        for (int i = 0; i < pageCount; i++) {
            List<TextBlock> blocks = loadPageBoundingBoxes(
                filePath,
                i
            );

            aggregateFontInfo(
                blocks,
                fontMap
            );
        }

        return fontMap;
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

    private void aggregateFontInfo(
        List<TextBlock> blocks,
        Map<String, FontInfo> fontMap
    ) {
        for (TextBlock block : blocks) {
            String name = block.getFontName();

            float size = block.getFontSize();

            fontMap.merge(
                name, FontInfo.builder()
                              .fontName(name)
                              .minFontSize(size)
                              .maxFontSize(size)
                              .averageFontSize(size)
                              .occurrenceCount(1)
                              .build(),
                this::mergeFontInfo
            );
        }
    }

    private FontInfo mergeFontInfo(
        FontInfo existing,
        FontInfo incoming
    ) {
        int newCount = existing.getOccurrenceCount() + 1;

        float newAvg = (existing.getAverageFontSize() * existing.getOccurrenceCount()
                        + incoming.getAverageFontSize()) / newCount;

        return FontInfo.builder()
                       .fontName(existing.getFontName())
                       .minFontSize(Math.min(existing.getMinFontSize(), incoming.getMinFontSize()))
                       .maxFontSize(Math.max(existing.getMaxFontSize(), incoming.getMaxFontSize()))
                       .averageFontSize(newAvg)
                       .occurrenceCount(newCount)
                       .build();
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
