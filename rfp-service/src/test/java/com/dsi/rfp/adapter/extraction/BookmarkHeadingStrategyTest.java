package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.domain.model.HeadingCandidate;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BookmarkHeadingStrategyTest {

    @TempDir
    Path tempDir;
    private BookmarkHeadingStrategy strategy;
    private PdfDocumentLoader loader;

    @BeforeEach
    void setUp() {
        strategy = new BookmarkHeadingStrategy();
        loader = new PdfDocumentLoader();
    }

    @Test
    void shouldReturnEmptyWhenPdfHasNoOutline() throws IOException {
        Path pdf = createPdfWithoutOutline();
        List<HeadingCandidate> result = strategy.detectHeadings(pdf, loader);
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnLevelOneForTopLevelBookmarks() throws IOException {
        Path pdf = createPdfWithBookmarks();
        List<HeadingCandidate> result = strategy.detectHeadings(pdf, loader);
        long levelOneCount = result.stream()
                                   .filter(c -> c.getLevel() == 1)
                                   .count();
        assertThat(levelOneCount).isEqualTo(3);
    }

    @Test
    void shouldReturnLevelTwoForNestedBookmarks() throws IOException {
        Path pdf = createPdfWithNestedBookmarks();
        List<HeadingCandidate> result = strategy.detectHeadings(pdf, loader);
        long levelTwoCount = result.stream()
                                   .filter(c -> c.getLevel() == 2)
                                   .count();
        assertThat(levelTwoCount).isEqualTo(2);
    }

    @Test
    void shouldSkipBookmarksWithBlankTitles() throws IOException {
        Path pdf = createPdfWithBlankBookmark();
        List<HeadingCandidate> result = strategy.detectHeadings(pdf, loader);
        assertThat(result).allSatisfy(c ->
            assertThat(c.getText()).isNotBlank()
        );
    }

    @Test
    void shouldCapLevelAtSix() throws IOException {
        Path pdf = createDeeplyNestedBookmarks();
        List<HeadingCandidate> result = strategy.detectHeadings(pdf, loader);
        assertThat(result).allSatisfy(c ->
            assertThat(c.getLevel()).isLessThanOrEqualTo(6)
        );
    }

    private Path createPdfWithoutOutline() throws IOException {
        Path file = tempDir.resolve("no-outline.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.save(file.toFile());
        }
        return file;
    }

    private Path createPdfWithBookmarks() throws IOException {
        Path file = tempDir.resolve("bookmarks.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            PDDocumentOutline outline = new PDDocumentOutline();
            addOutlineItem(outline, "Introduction");
            addOutlineItem(outline, "Background");
            addOutlineItem(outline, "Requirements");
            doc.getDocumentCatalog().setDocumentOutline(outline);
            doc.save(file.toFile());
        }
        return file;
    }

    private Path createPdfWithNestedBookmarks() throws IOException {
        Path file = tempDir.resolve("nested.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            PDDocumentOutline outline = new PDDocumentOutline();
            PDOutlineItem parent = new PDOutlineItem();
            parent.setTitle("Chapter 1");
            PDOutlineItem child1 = new PDOutlineItem();
            child1.setTitle("Section 1.1");
            PDOutlineItem child2 = new PDOutlineItem();
            child2.setTitle("Section 1.2");
            parent.addFirst(child1);
            parent.addLast(child2);
            outline.addFirst(parent);
            doc.getDocumentCatalog().setDocumentOutline(outline);
            doc.save(file.toFile());
        }
        return file;
    }

    private Path createPdfWithBlankBookmark() throws IOException {
        Path file = tempDir.resolve("blank-title.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            PDDocumentOutline outline = new PDDocumentOutline();
            addOutlineItem(outline, "Valid Title");
            PDOutlineItem blank = new PDOutlineItem();
            blank.setTitle("   ");
            outline.addLast(blank);
            doc.getDocumentCatalog().setDocumentOutline(outline);
            doc.save(file.toFile());
        }
        return file;
    }

    private Path createDeeplyNestedBookmarks() throws IOException {
        Path file = tempDir.resolve("deep.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            PDDocumentOutline outline = new PDDocumentOutline();
            PDOutlineItem current = new PDOutlineItem();
            current.setTitle("Level 1");
            outline.addFirst(current);
            for (int i = 2; i <= 8; i++) {
                PDOutlineItem child = new PDOutlineItem();
                child.setTitle("Level " + i);
                current.addFirst(child);
                current = child;
            }
            doc.getDocumentCatalog().setDocumentOutline(outline);
            doc.save(file.toFile());
        }
        return file;
    }

    private void addOutlineItem(PDDocumentOutline outline, String title) {
        PDOutlineItem item = new PDOutlineItem();
        item.setTitle(title);
        outline.addLast(item);
    }
}
