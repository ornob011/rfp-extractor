# Sprint 3 — Section Segmentation & Clause IDs

## 0) Sprint Intent

- Transform a classified PDF into a structured, hierarchical section tree — the backbone that all subsequent extraction
  sprints write into. Every section has a stable UUID, a human-readable title, a page range, and a confidence score
  indicating which strategy detected it.
- Assign deterministic, reproducible clause IDs to every paragraph within each section so that cross-run comparisons are
  possible and so that rule packs (Sprint 8) can address individual clauses by stable reference.
- Validate the entire RFP JSON output against the project's canonical JSON Schema so that downstream consumers (artifact
  generation, rule packs, frontend) can rely on a contract-backed data shape.
- Establish a deterministic fixture-based evaluation harness so segmentation quality is measurable from the start
  without blocking on manual annotation.

**Non-goals:**

- Entity extraction or LLM calls (Sprint 4).
- Table extraction (Sprint 5).
- OCR processing (Sprint 6).
- Repair loop or rule packs (Sprints 7/8).
- Artifact generation (Sprint 10).
- Full test coverage of all 64 rule pack rules (Sprint 8).

---

## 1) Entry Criteria

- Sprint 2 is fully complete: `POST /api/v1/rfp/submit` works, job state in Redis, page classification runs for all
  pages.
- `PdfDocumentLoader` is implemented with `loadPageText()`, `loadPageBoundingBoxes()`, `loadFontMetadata()`, and
  `getPageCount()` working.
- `PageClassificationResult` is available in domain model.
- `FileStoragePort` and `LocalFileStorageAdapter` are working (needed to retrieve stored PDFs for segmentation).
- `rfp-service` runs with no compilation errors.
- `schema/` directory exists at project root (create in this sprint).
- `testdata/` directory exists at project root (create in this sprint).
- Curated local fixture PDFs are available under `testdata/fixtures/` for deterministic tests.

---

## 2) Deliverables

- Six `HeadingStrategy` implementations: Bookmark, HeadingStyle, Numbered, Bangla, FontSize, AllCaps.
- `TocDetector` — finds TOC pages and extracts heading candidates from them.
- `SectionSegmenter` — chain-of-responsibility that picks the best strategy and builds the section tree.
- `ClauseIdAssigner` — deterministic ID scheme with fallback and audit logging.
- `RfpSchemaValidator` — validates RFP JSON against `schema/rfp-schema-v1.json` at startup.
- `schema/rfp-schema-v1.json` — complete JSON Schema for the full RFP entity model.
- Fixture dataset: `testdata/fixtures/` with expected section structures and deterministic assertions.
- `SectionExtractionEvaluator` + `FixtureExpectationLoader` — deterministic evaluation harness (non-blocking benchmark
  support for ground-truth profile remains optional).
- `ResultPage.tsx` with `SectionTree.tsx` rendering the section hierarchy.
- Integration of segmentation into the async pipeline (section tree stored in Redis job state by Sprint 3 end).
- At least 40 unit tests covering all strategies, the segmenter orchestrator, the ID assigner, and the schema validator.

---

## 3) Work Breakdown

### Epic 1 — Heading Detection Strategies

#### Story 1.1 — HeadingStrategy Interface and HeadingCandidate Model

**Description:**
Define the `HeadingStrategy` interface and the `HeadingCandidate` data model. These live in `rfp-core` because the
`SectionSegmenter` (application layer) depends on them without needing framework-specific code. The `detectedBy` field
records which strategy produced this candidate for audit and confidence scoring.

**Acceptance Criteria:**

```gherkin
Given a HeadingCandidate is created
When all fields are set
Then getLevel() returns an integer between 1 and 6 inclusive

Given a HeadingStrategy implementation
When detectHeadings() is called on a PdfDocumentLoader
Then the returned list contains only HeadingCandidate objects with non-null text
```

**Interfaces/Contracts:**

File: `rfp-core/src/main/java/com/dsi/rfp/domain/model/HeadingCandidate.java`:

```java
package com.dsi.rfp.domain.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HeadingCandidate {
    private int pageNumber;       // 0-based
    private String text;          // heading text content
    private int level;            // 1-6 (1=highest)
    private float startY;         // Y coordinate on page (PDF coordinate space)
    private String fontName;      // null if not applicable to this strategy
    private float fontSize;       // 0.0 if not applicable
    private String detectedBy;    // strategy simple class name, e.g. "BookmarkHeadingStrategy"
}
```

File: `rfp-service/src/main/java/com/dsi/rfp/adapter/extraction/HeadingStrategy.java`:

```java
package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.domain.model.HeadingCandidate;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Strategy for detecting headings in a PDF document.
 * Implementations must be stateless — safe for concurrent use.
 */
public interface HeadingStrategy {
    /**
     * Detects heading candidates from the given PDF.
     *
     * @param pdfPath path to the PDF file
     * @param loader  the PDF document loader for accessing document content
     * @return list of detected HeadingCandidate objects (may be empty, never null)
     * @throws IOException if the PDF cannot be read
     */
    List<HeadingCandidate> detectHeadings(Path pdfPath, PdfDocumentLoader loader) throws IOException;

    /**
     * Returns the human-readable name of this strategy for logging and confidence tracking.
     */
    String strategyName();
}
```

**Estimation:** 1 SP

---

#### Story 1.2 — BookmarkHeadingStrategy

**Description:**
Uses the PDF document outline (bookmarks) as heading candidates. PDFBox provides
`PDDocumentCatalog.getDocumentOutline()` which gives an `PDOutlineItem` tree. Traverses the tree recursively, assigning
`level` by depth (root items = level 1, children = level 2, etc., capped at 6). Skips outline items with null or blank
titles.

**Acceptance Criteria:**

```gherkin
Given a PDF with a two-level bookmark outline (3 top-level, 2 children each)
When BookmarkHeadingStrategy.detectHeadings() is called
Then 3 HeadingCandidate objects with level=1 are returned
And 6 HeadingCandidate objects with level=2 are returned

Given a PDF with no bookmarks
When BookmarkHeadingStrategy.detectHeadings() is called
Then an empty list is returned (not null, not exception)
```

**Interfaces/Contracts:**

File: `rfp-service/src/main/java/com/dsi/rfp/adapter/extraction/BookmarkHeadingStrategy.java`:

```java
package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.domain.model.HeadingCandidate;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@Order(1)
public class BookmarkHeadingStrategy implements HeadingStrategy {

    @Override
    public List<HeadingCandidate> detectHeadings(Path pdfPath, PdfDocumentLoader loader)
        throws IOException {
        List<HeadingCandidate> results = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdfPath.toFile())) {
            PDDocumentOutline outline = doc.getDocumentCatalog().getDocumentOutline();
            if (Objects.isNull(outline)) return results;
            traverseOutline(outline.getFirstChild(), 1, results);
        }
        log.debug("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=DEBUG BookmarkHeadingStrategy: found {} headings in {}",
            results.size(), pdfPath.getFileName());
        return results;
    }

    @Override
    public String strategyName() {
        return "BookmarkHeadingStrategy";
    }

    private void traverseOutline(PDOutlineItem item, int level,
                                 List<HeadingCandidate> results) {
        while (Objects.nonNull(item)) {
            String title = item.getTitle();
            if (Objects.nonNull(title) && StringUtils.hasText(title)) {
                results.add(HeadingCandidate.builder()
                    .text(title.strip())
                    .level(Math.min(level, 6))
                    .pageNumber(resolvePageNumber(item))
                    .startY(0.0f)
                    .detectedBy(strategyName())
                    .build());
            }
            if (item.hasChildren()) {
                traverseOutline(item.getFirstChild(), level + 1, results);
            }
            item = item.getNextSibling();
        }
    }

    private int resolvePageNumber(PDOutlineItem item) {
        var dest = item.getDestination();
        // Simplified: return 0 for Sprint 3. Exact bookmark destination mapping is deferred.
        return 0;
    }
}
```

**Test Plan:**
Class: `BookmarkHeadingStrategyTest`
Create test PDF fixtures using PDFBox `PDDocumentOutline` in `@BeforeAll`.

```java

@Test
void shouldReturnEmptyWhenPdfHasNoOutline()

@Test
void shouldReturnLevelOneForTopLevelBookmarks()

@Test
void shouldReturnLevelTwoForNestedBookmarks()

@Test
void shouldSkipBookmarksWithBlankTitles()

@Test
void shouldCapLevelAtSix()
```

**Estimation:** 3 SP

---

#### Story 1.3 — HeadingStyleStrategy

**Description:**
Scans PDFBox font metadata looking for fonts with names containing "Heading" followed by a digit (e.g., "Heading1", "
Heading2", "Arial-Heading1") or "H" followed by a digit used as a heading style. The level is extracted from the
trailing digit. Requires `PdfDocumentLoader.loadFontMetadata()`.

**Acceptance Criteria:**

```gherkin
Given a PDF where "Heading1" font is used for chapter titles and "Heading2" for section titles
When HeadingStyleStrategy.detectHeadings() is called
Then candidates with fontName "Heading1" have level=1
And candidates with fontName "Heading2" have level=2

Given a PDF with no heading-style fonts
When HeadingStyleStrategy.detectHeadings() is called
Then an empty list is returned
```

**Interfaces/Contracts:**

File: `rfp-service/src/main/java/com/dsi/rfp/adapter/extraction/HeadingStyleStrategy.java`:

```java

@Slf4j
@Component
@Order(2)
public class HeadingStyleStrategy implements HeadingStrategy {

    private static final Pattern HEADING_FONT_PATTERN =
        Pattern.compile("(?i)(heading|H)(\\d)", Pattern.CASE_INSENSITIVE);

    @Override
    public List<HeadingCandidate> detectHeadings(Path pdfPath, PdfDocumentLoader loader)
        throws IOException {
        Map<String, FontInfo> fontMap = loader.loadFontMetadata(pdfPath);
        List<HeadingCandidate> results = new ArrayList<>();

        for (Map.Entry<String, FontInfo> entry : fontMap.entrySet()) {
            String fontName = entry.getKey();
            Matcher m = HEADING_FONT_PATTERN.matcher(fontName);
            if (m.find()) {
                int level = Integer.parseInt(m.group(2));
                // We know the font is a heading style but need text blocks to find actual text.
                // Scan all pages for text blocks using this font.
                results.addAll(findTextWithFont(pdfPath, loader, fontName, Math.min(level, 6)));
            }
        }
        return results;
    }

    @Override
    public String strategyName() {
        return "HeadingStyleStrategy";
    }

    private List<HeadingCandidate> findTextWithFont(Path pdfPath, PdfDocumentLoader loader,
                                                    String targetFont, int level)
        throws IOException {
        List<HeadingCandidate> results = new ArrayList<>();
        int pageCount = loader.getPageCount(pdfPath);
        for (int i = 0; i < pageCount; i++) {
            List<TextBlock> blocks = loader.loadPageBoundingBoxes(pdfPath, i);
            // Group consecutive blocks with matching font into a single candidate
            StringBuilder current = new StringBuilder();
            float startY = 0;
            final int pageIdx = i;
            for (TextBlock block : blocks) {
                if (targetFont.equals(block.getFontName())) {
                    if (current.isEmpty()) startY = block.getY();
                    current.append(block.getText());
                } else if (!current.isEmpty()) {
                    results.add(buildCandidate(current.toString(), level, pageIdx,
                        startY, targetFont, 0));
                    current.setLength(0);
                }
            }
            if (!current.isEmpty()) {
                results.add(buildCandidate(current.toString(), level, i, startY, targetFont, 0));
            }
        }
        return results;
    }

    private HeadingCandidate buildCandidate(String text, int level, int page,
                                            float y, String font, float fontSize) {
        return HeadingCandidate.builder()
            .text(text.strip())
            .level(level)
            .pageNumber(page)
            .startY(y)
            .fontName(font)
            .fontSize(fontSize)
            .detectedBy(strategyName())
            .build();
    }
}
```

**Estimation:** 3 SP

---

#### Story 1.4 — NumberedHeadingStrategy

**Description:**
Applies three regex patterns to each line of page text to detect numbered headings. This is the most commonly useful
strategy for GOB RFPs which use numbered clause structures like "1.", "1.1", "2.3.4" and keyword headings like "SECTION
II" or "PART A".

**Acceptance Criteria:**

```gherkin
Given a PDF page containing "1. Scope of Work" on a line
When NumberedHeadingStrategy.detectHeadings() is called
Then a HeadingCandidate with level=1 and text="1. Scope of Work" is returned

Given a PDF page containing "2.3.1 Technical Requirements"
When NumberedHeadingStrategy.detectHeadings() is called
Then a HeadingCandidate with level=3 and text="2.3.1 Technical Requirements" is returned

Given a PDF page containing "SECTION II: ELIGIBILITY CRITERIA"
When NumberedHeadingStrategy.detectHeadings() is called
Then a HeadingCandidate with level=1 is returned

Given a line that is mid-sentence and starts with "1." accidentally
When NumberedHeadingStrategy processes it
Then it must start with \d at beginning of line (^ anchor) to match
```

**Interfaces/Contracts:**

File: `rfp-service/src/main/java/com/dsi/rfp/adapter/extraction/NumberedHeadingStrategy.java`:

```java

@Slf4j
@Component
@Order(3)
public class NumberedHeadingStrategy implements HeadingStrategy {

    // Pattern 1: numbered clauses — "1.2.3 Title" up to 3 levels deep
    private static final Pattern NUMBERED_PATTERN =
        Pattern.compile("^\\s*(\\d+\\.){1,3}\\s+[A-Z\\u0980-\\u09FF].*",
            Pattern.UNICODE_CHARACTER_CLASS);

    // Pattern 2: structural keywords — "PART II", "SECTION 3", "SCHEDULE A", "ANNEX B", "APPENDIX 1"
    private static final Pattern KEYWORD_PATTERN =
        Pattern.compile("^(PART|SECTION|SCHEDULE|ANNEX|APPENDIX)\\s+([IVX\\d]+).*",
            Pattern.CASE_INSENSITIVE);

    // Pattern 3: chapter headings — "Chapter 1 Introduction"
    private static final Pattern CHAPTER_PATTERN =
        Pattern.compile("^Chapter\\s+\\d+.*", Pattern.CASE_INSENSITIVE);

    @Override
    public List<HeadingCandidate> detectHeadings(Path pdfPath, PdfDocumentLoader loader)
        throws IOException {
        List<HeadingCandidate> results = new ArrayList<>();
        int pageCount = loader.getPageCount(pdfPath);
        for (int pageIdx = 0; pageIdx < pageCount; pageIdx++) {
            String pageText = loader.loadPageText(pdfPath, pageIdx);
            String[] lines = pageText.split("\\r?\\n");
            for (String line : lines) {
                detectInLine(line.strip(), pageIdx, results);
            }
        }
        return results;
    }

    @Override
    public String strategyName() {
        return "NumberedHeadingStrategy";
    }

    private void detectInLine(String line, int pageIdx, List<HeadingCandidate> results) {
        if (!StringUtils.hasText(line)) return;

        Matcher numbered = NUMBERED_PATTERN.matcher(line);
        if (numbered.matches()) {
            int dotCount = countDots(line);
            results.add(buildCandidate(line, Math.min(dotCount, 6), pageIdx));
            return;
        }

        Matcher keyword = KEYWORD_PATTERN.matcher(line);
        if (keyword.matches()) {
            results.add(buildCandidate(line, 1, pageIdx));
            return;
        }

        Matcher chapter = CHAPTER_PATTERN.matcher(line);
        if (chapter.matches()) {
            results.add(buildCandidate(line, 1, pageIdx));
        }
    }

    private int countDots(String line) {
        // "1." → 1, "1.2." → 2, "1.2.3 " → 3
        String prefix = line.replaceAll("^\\s*(\\d+\\.)+.*", "$0");
        long dots = line.chars().filter(c -> c == '.').count();
        return (int) Math.min(dots, 6);
    }

    private HeadingCandidate buildCandidate(String text, int level, int pageIdx) {
        return HeadingCandidate.builder()
            .text(text)
            .level(level)
            .pageNumber(pageIdx)
            .startY(0.0f)
            .detectedBy(strategyName())
            .build();
    }
}
```

**Test Plan:**

```java
class NumberedHeadingStrategyTest {
    // Pure logic tests — pass text directly, mock PdfDocumentLoader

    @Test
    void shouldDetectLevelOneForSingleDotPrefix()

    @Test
    void shouldDetectLevelTwoForTwoDotPrefix()

    @Test
    void shouldDetectLevelThreeForThreeDotPrefix()

    @Test
    void shouldDetectSectionKeywordAsLevelOne()

    @Test
    void shouldDetectPartKeywordAsLevelOne()

    @Test
    void shouldDetectChapterAsLevelOne()

    @Test
    void shouldIgnoreBlankLines()

    @Test
    void shouldIgnoreMidSentenceNumbers()

    @Test
    void shouldDetectBanglaStartingUppercaseAfterNumber()
}
```

**Estimation:** 3 SP

---

#### Story 1.5 — BanglaHeadingStrategy

**Description:**
Uses Unicode-safe regex patterns to detect Bangla language headings without requiring any NLP model. All patterns are
anchored at the line start to avoid false positives.

**Acceptance Criteria:**

```gherkin
Given a line starting with "ধারা ৩" (clause 3 in Bangla)
When BanglaHeadingStrategy detects it
Then level=2 HeadingCandidate is returned

Given a line starting with "অধ্যায় ১" (chapter 1 in Bangla)
When BanglaHeadingStrategy detects it
Then level=1 HeadingCandidate is returned

Given an English-only PDF
When BanglaHeadingStrategy.detectHeadings() runs
Then an empty list is returned
```

**Interfaces/Contracts:**

File: `rfp-service/src/main/java/com/dsi/rfp/adapter/extraction/BanglaHeadingStrategy.java`:

```java

@Slf4j
@Component
@Order(4)
public class BanglaHeadingStrategy implements HeadingStrategy {

    // Bangla numeral range: ০-৯ (U+09E6 to U+09EF), also allow ASCII digits
    private static final String BANGLA_NUM = "[\\d০-৯]+";

    // Level 1: অধ্যায় (chapter) + ordinal words
    private static final Pattern CHAPTER_PATTERN =
        Pattern.compile("^(অধ্যায়|প্রথম অধ্যায়|দ্বিতীয় অধ্যায়|তৃতীয় অধ্যায়)\\s*" + BANGLA_NUM + "?.*",
            Pattern.UNICODE_CHARACTER_CLASS);

    // Level 2: ধারা (section/clause)
    private static final Pattern DHARA_PATTERN =
        Pattern.compile("^ধারা\\s+" + BANGLA_NUM + ".*",
            Pattern.UNICODE_CHARACTER_CLASS);

    // Level 3: অনুচ্ছেদ (article/paragraph)
    private static final Pattern ARTICLE_PATTERN =
        Pattern.compile("^অনুচ্ছেদ\\s+" + BANGLA_NUM + ".*",
            Pattern.UNICODE_CHARACTER_CLASS);

    @Override
    public List<HeadingCandidate> detectHeadings(Path pdfPath, PdfDocumentLoader loader)
        throws IOException {
        List<HeadingCandidate> results = new ArrayList<>();
        int pageCount = loader.getPageCount(pdfPath);
        for (int pageIdx = 0; pageIdx < pageCount; pageIdx++) {
            String pageText = loader.loadPageText(pdfPath, pageIdx);
            String[] lines = pageText.split("\\r?\\n");
            for (String line : lines) {
                detectInLine(line.strip(), pageIdx, results);
            }
        }
        log.debug("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=DEBUG BanglaHeadingStrategy: found {} Bangla headings in {}",
            results.size(), pdfPath.getFileName());
        return results;
    }

    @Override
    public String strategyName() {
        return "BanglaHeadingStrategy";
    }

    private void detectInLine(String line, int pageIdx, List<HeadingCandidate> results) {
        if (!StringUtils.hasText(line)) return;
        if (CHAPTER_PATTERN.matcher(line).matches()) {
            results.add(buildCandidate(line, 1, pageIdx));
        } else if (DHARA_PATTERN.matcher(line).matches()) {
            results.add(buildCandidate(line, 2, pageIdx));
        } else if (ARTICLE_PATTERN.matcher(line).matches()) {
            results.add(buildCandidate(line, 3, pageIdx));
        }
    }

    private HeadingCandidate buildCandidate(String text, int level, int pageIdx) {
        return HeadingCandidate.builder()
            .text(text).level(level).pageNumber(pageIdx)
            .startY(0.0f).detectedBy(strategyName()).build();
    }
}
```

**Test Plan:**

```java
class BanglaHeadingStrategyTest {
    @Test
    void shouldDetectAdhyayAsLevelOne()

    @Test
    void shouldDetectDharaAsLevelTwo()

    @Test
    void shouldDetectAnuchchhedAsLevelThree()

    @Test
    void shouldNotDetectEnglishTextAsBanglaHeading()

    @Test
    void shouldHandleBanglaNumerals()

    @Test
    void shouldHandleMixedBanglaAndAsciiNumerals()
}
```

**Estimation:** 3 SP

---

#### Story 1.6 — FontSizeHeadingStrategy

**Description:**
Computes the median body font size from all text blocks in the document, then identifies lines whose font size is more
than 2pt above the median AND whose text length is under 100 characters as heading candidates. Level is determined by
bucketing relative font size difference.

**Acceptance Criteria:**

```gherkin
Given a PDF where body text is 10pt and a title line is 18pt
When FontSizeHeadingStrategy.detectHeadings() is called
Then the 18pt line is returned as a heading candidate (18 - 10 = 8pt > 2pt threshold)

Given a PDF where a long paragraph has large font (> 100 chars)
When FontSizeHeadingStrategy detects it
Then it is NOT returned as a heading (length check filters it out)

Given a PDF with uniform font size throughout
When FontSizeHeadingStrategy.detectHeadings() is called
Then an empty list is returned
```

**Interfaces/Contracts:**

File: `rfp-service/src/main/java/com/dsi/rfp/adapter/extraction/FontSizeHeadingStrategy.java`:

```java

@Slf4j
@Component
@Order(5)
public class FontSizeHeadingStrategy implements HeadingStrategy {

    private static final float HEADING_FONT_SIZE_DELTA = 2.0f;
    private static final int MAX_HEADING_LENGTH = 100;

    @Override
    public List<HeadingCandidate> detectHeadings(Path pdfPath, PdfDocumentLoader loader)
        throws IOException {
        int pageCount = loader.getPageCount(pdfPath);
        List<TextBlock> allBlocks = collectAllBlocks(pdfPath, loader, pageCount);
        if (allBlocks.isEmpty()) return List.of();

        float medianFontSize = computeMedianFontSize(allBlocks);
        log.debug("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=DEBUG FontSizeHeadingStrategy: medianFontSize={}", medianFontSize);

        return allBlocks.stream()
            .filter(block -> isHeadingCandidate(block, medianFontSize))
            .map(block -> buildCandidate(block, medianFontSize))
            .filter(c -> StringUtils.hasText(c.getText()))
            .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public String strategyName() {
        return "FontSizeHeadingStrategy";
    }

    private List<TextBlock> collectAllBlocks(Path pdfPath, PdfDocumentLoader loader,
                                             int pageCount) throws IOException {
        List<TextBlock> all = new ArrayList<>();
        for (int i = 0; i < pageCount; i++) {
            all.addAll(loader.loadPageBoundingBoxes(pdfPath, i));
        }
        return all;
    }

    private float computeMedianFontSize(List<TextBlock> blocks) {
        List<Float> sizes = blocks.stream()
            .map(TextBlock::getFontSize)
            .filter(s -> s > 0)
            .sorted()
            .collect(java.util.stream.Collectors.toList());
        if (sizes.isEmpty()) return 10.0f;
        return sizes.get(sizes.size() / 2);
    }

    private boolean isHeadingCandidate(TextBlock block, float medianSize) {
        return block.getFontSize() > (medianSize + HEADING_FONT_SIZE_DELTA)
            && Objects.nonNull(block.getText())
            && block.getText().length() <= MAX_HEADING_LENGTH
            && StringUtils.hasText(block.getText());
    }

    private HeadingCandidate buildCandidate(TextBlock block, float medianSize) {
        float delta = block.getFontSize() - medianSize;
        int level = fontDeltaToLevel(delta);
        return HeadingCandidate.builder()
            .text(block.getText().strip())
            .level(level)
            .pageNumber((int) block.getY())  // approximation: y used as page proxy
            .startY(block.getY())
            .fontName(block.getFontName())
            .fontSize(block.getFontSize())
            .detectedBy(strategyName())
            .build();
    }

    private int fontDeltaToLevel(float delta) {
        if (delta > 10) return 1;
        if (delta > 6) return 2;
        if (delta > 3) return 3;
        return 4;
    }
}
```

Note: `pageNumber` field uses a simplified approach since `TextBlock` doesn't carry page number. Update `TextBlock` to
include a `pageNumber` field (add to the domain model now):

```java
// Add to TextBlock.java in rfp-core:
private int pageNumber;  // 0-based, set when collecting blocks
```

Update `PdfDocumentLoader.loadPageBoundingBoxes()` to set `pageNumber = pageIndex` on each block.

**Estimation:** 3 SP

---

#### Story 1.7 — AllCapsHeadingStrategy

**Description:**
Last-resort strategy. Finds lines where ALL alphabetic characters are uppercase, the line is 5-80 characters long, and
the line is surrounded by blank lines or is at a page boundary. This catches standalone uppercase headings not detected
by other strategies.

**Acceptance Criteria:**

```gherkin
Given a page with a standalone line "TERMS AND CONDITIONS" (all caps, surrounded by blank lines)
When AllCapsHeadingStrategy.detectHeadings() runs
Then a level=1 HeadingCandidate with that text is returned

Given a line "SOME ACRONYM IN RUNNING TEXT" embedded in a paragraph (no surrounding blanks)
When AllCapsHeadingStrategy processes it
Then it is NOT returned as a heading candidate

Given a line of 81+ all-caps characters
When AllCapsHeadingStrategy processes it
Then it is NOT returned (exceeds max length)
```

**Interfaces/Contracts:**

File: `rfp-service/src/main/java/com/dsi/rfp/adapter/extraction/AllCapsHeadingStrategy.java`:

```java

@Slf4j
@Component
@Order(6)
public class AllCapsHeadingStrategy implements HeadingStrategy {

    private static final int MIN_LENGTH = 5;
    private static final int MAX_LENGTH = 80;

    @Override
    public List<HeadingCandidate> detectHeadings(Path pdfPath, PdfDocumentLoader loader)
        throws IOException {
        List<HeadingCandidate> results = new ArrayList<>();
        int pageCount = loader.getPageCount(pdfPath);
        for (int pageIdx = 0; pageIdx < pageCount; pageIdx++) {
            String pageText = loader.loadPageText(pdfPath, pageIdx);
            String[] lines = pageText.split("\\r?\\n");
            detectAllCapsInLines(lines, pageIdx, results);
        }
        return results;
    }

    @Override
    public String strategyName() {
        return "AllCapsHeadingStrategy";
    }

    private void detectAllCapsInLines(String[] lines, int pageIdx,
                                      List<HeadingCandidate> results) {
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            if (isAllCapsHeading(line, lines, i)) {
                results.add(HeadingCandidate.builder()
                    .text(line)
                    .level(1)
                    .pageNumber(pageIdx)
                    .startY(0.0f)
                    .detectedBy(strategyName())
                    .build());
            }
        }
    }

    private boolean isAllCapsHeading(String line, String[] lines, int index) {
        if (!StringUtils.hasText(line)) return false;
        if (line.length() < MIN_LENGTH || line.length() > MAX_LENGTH) return false;
        if (!isAllUppercase(line)) return false;
        return isSurroundedByBlanks(lines, index);
    }

    private boolean isAllUppercase(String line) {
        return line.chars()
            .filter(Character::isLetter)
            .allMatch(Character::isUpperCase);
    }

    private boolean isSurroundedByBlanks(String[] lines, int index) {
        boolean prevBlank = index == 0 || !StringUtils.hasText(lines[index - 1]);
        boolean nextBlank = index == lines.length - 1 || !StringUtils.hasText(lines[index + 1]);
        return prevBlank && nextBlank;
    }
}
```

**Estimation:** 2 SP

---

### Epic 2 — TOC Detector

#### Story 2.1 — TocDetector

**Description:**
Scans the first 10 pages of a PDF for a Table of Contents page. Detects TOC by looking for a page with more than 8 lines
matching the pattern `text ......... pageNum` (dotted leaders) or `text    pageNum` (right-aligned numbers). If found,
extracts the TOC entries as heading candidates, inferring level from indentation of the TOC line.

**Acceptance Criteria:**

```gherkin
Given a PDF whose page 2 has 15 lines matching the pattern "Introduction ......... 3"
When TocDetector.findToc() is called
Then Optional.of(list) is returned with 15 HeadingCandidate objects

Given a PDF with no TOC page
When TocDetector.findToc() is called
Then Optional.empty() is returned

Given a TOC line "  2.1 Technical Requirements ......... 15" (indented)
When TocDetector parses it
Then level=2 HeadingCandidate is returned (indentation of 2 spaces → child)
```

**Interfaces/Contracts:**

File: `rfp-service/src/main/java/com/dsi/rfp/adapter/extraction/TocDetector.java`:

```java

@Slf4j
@Component
public class TocDetector {

    private static final int SCAN_PAGE_LIMIT = 10;
    private static final int TOC_LINE_THRESHOLD = 8;

    // Matches: "Some Title ......... 12" or "Some Title    12"
    private static final Pattern TOC_LINE_PATTERN =
        Pattern.compile("^(\\s*)(.+?)\\s+\\.{3,}\\s+(\\d+)$|^(\\s*)(.+?)\\s{4,}(\\d+)$");

    /**
     * Searches the first SCAN_PAGE_LIMIT pages for a TOC.
     *
     * @param pdfPath path to the PDF
     * @param loader  document loader
     * @return Optional list of heading candidates parsed from TOC, or empty if no TOC found
     * @throws IOException if PDF cannot be read
     */
    public Optional<List<HeadingCandidate>> findToc(Path pdfPath, PdfDocumentLoader loader)
        throws IOException {
        int pageCount = Math.min(loader.getPageCount(pdfPath), SCAN_PAGE_LIMIT);
        for (int i = 0; i < pageCount; i++) {
            String pageText = loader.loadPageText(pdfPath, i);
            String[] lines = pageText.split("\\r?\\n");
            List<HeadingCandidate> candidates = extractTocEntries(lines, i);
            if (candidates.size() >= TOC_LINE_THRESHOLD) {
                log.info("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=INFO TOC detected on page {} with {} entries", i + 1, candidates.size());
                return Optional.of(candidates);
            }
        }
        return Optional.empty();
    }

    private List<HeadingCandidate> extractTocEntries(String[] lines, int pageIdx) {
        List<HeadingCandidate> entries = new ArrayList<>();
        for (String line : lines) {
            Matcher m = TOC_LINE_PATTERN.matcher(line);
            if (m.matches()) {
                String indent = Objects.nonNull(m.group(1)) ? m.group(1) : m.group(4);
                String text = Objects.nonNull(m.group(2)) ? m.group(2) : m.group(5);
                if (Objects.nonNull(text) && StringUtils.hasText(text)) {
                    int level = computeLevelFromIndent(indent);
                    entries.add(HeadingCandidate.builder()
                        .text(text.strip())
                        .level(level)
                        .pageNumber(pageIdx)
                        .startY(0.0f)
                        .detectedBy("TocDetector")
                        .build());
                }
            }
        }
        return entries;
    }

    private int computeLevelFromIndent(String indent) {
        if (Objects.isNull(indent)) return 1;
        int spaces = indent.length();
        if (spaces == 0) return 1;
        if (spaces <= 2) return 2;
        if (spaces <= 4) return 3;
        return Math.min(spaces / 2, 6);
    }
}
```

**Test Plan:**

```java
class TocDetectorTest {
    @Mock
    PdfDocumentLoader loader;
    private TocDetector detector;

    @Test
    void shouldReturnEmptyWhenNoTocPage()

    @Test
    void shouldReturnPresentWhenDottedLeaderTocDetected()

    @Test
    void shouldReturnPresentWhenSpacedTocDetected()

    @Test
    void shouldComputeLevelOneForUnindentedEntry()

    @Test
    void shouldComputeLevelTwoForTwoSpaceIndentedEntry()

    @Test
    void shouldReturnEmptyWhenFewerThanEightTocLines()
}
```

**Estimation:** 3 SP

---

### Epic 3 — Section Segmenter

#### Story 3.1 — SectionSegmenter: Chain-of-Responsibility + Section Tree Builder

**Description:**
`SectionSegmenter` orchestrates all strategies using a Chain-of-Responsibility pattern. It first checks for a TOC via
`TocDetector`. If found, those candidates are used directly. Otherwise, it tries each `HeadingStrategy` in priority
order (Bookmark → HeadingStyle → Numbered → Bangla → FontSize → AllCaps) and uses the result of the first that produces
3 or more candidates. From the winning candidate list, it builds a hierarchical `Section` tree with page ranges computed
from consecutive heading page numbers.

**Acceptance Criteria:**

```gherkin
Given a PDF with bookmarks (3+ bookmarks)
When SectionSegmenter.segment() is called
Then sections are built from bookmark strategy (highest priority)
And each section has a non-null UUID id

Given a PDF with no bookmarks but numbered headings
When SectionSegmenter.segment() is called
Then sections are built from NumberedHeadingStrategy (3rd priority)
And confidence.method = "NumberedHeadingStrategy"

Given a PDF with a TOC page
When SectionSegmenter.segment() is called
Then TOC entries are used regardless of other strategies
And confidence.method = "TocDetector"

Given a PDF where all strategies return fewer than 3 headings
When SectionSegmenter.segment() is called
Then an empty list is returned (no sections — caller must handle)
And a log.warn is emitted

Given two adjacent top-level sections starting on pages 5 and 12
When SectionSegmenter computes page ranges
Then section 1 has pageEnd=11 (one before section 2 starts)
And section 2 has pageEnd=lastPage
```

**Interfaces/Contracts:**

File: `rfp-core/src/main/java/com/dsi/rfp/domain/model/Section.java`:

```java
package com.dsi.rfp.domain.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class Section {
    private UUID id;
    private String title;
    private int level;
    private int pageStart;
    private int pageEnd;
    @Builder.Default
    private final List<Section> children = new java.util.ArrayList<>();
    private SectionConfidence confidence;
}
```

File: `rfp-core/src/main/java/com/dsi/rfp/domain/model/SectionConfidence.java`:

```java

@Data
@Builder
public class SectionConfidence {
    private double score;    // 0.0 to 1.0
    private String method;   // strategy name that detected this section
}
```

File: `rfp-service/src/main/java/com/dsi/rfp/adapter/extraction/SectionSegmenter.java`:

```java
package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.domain.model.HeadingCandidate;
import com.dsi.rfp.domain.model.Section;
import com.dsi.rfp.domain.model.SectionConfidence;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class SectionSegmenter {

    private static final int MIN_HEADINGS_TO_USE_STRATEGY = 3;

    private final TocDetector tocDetector;
    private final List<HeadingStrategy> strategies;  // injected in priority order via @Order

    /**
     * Segments a PDF into a hierarchical Section tree.
     *
     * @param pdfPath   path to the PDF
     * @param loader    the PDF document loader
     * @param totalPages total page count of the document
     * @return ordered list of top-level sections (each may have children)
     * @throws IOException if PDF cannot be read
     */
    public List<Section> segment(Path pdfPath, PdfDocumentLoader loader, int totalPages)
        throws IOException {

        Optional<List<HeadingCandidate>> tocResult = tocDetector.findToc(pdfPath, loader);
        if (tocResult.isPresent()) {
            return buildSectionTree(tocResult.get(), totalPages, "TocDetector", 1.0);
        }

        for (HeadingStrategy strategy : strategies) {
            List<HeadingCandidate> candidates = strategy.detectHeadings(pdfPath, loader);
            if (candidates.size() >= MIN_HEADINGS_TO_USE_STRATEGY) {
                log.info("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=INFO SectionSegmenter using strategy={} with {} candidates",
                    strategy.strategyName(), candidates.size());
                double confidence = computeConfidence(strategy, candidates.size());
                return buildSectionTree(candidates, totalPages, strategy.strategyName(), confidence);
            }
        }

        log.warn("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=WARN SectionSegmenter: no strategy produced {} headings — returning empty",
            MIN_HEADINGS_TO_USE_STRATEGY);
        return List.of();
    }

    private List<Section> buildSectionTree(List<HeadingCandidate> candidates,
                                           int totalPages,
                                           String method,
                                           double confidence) {
        // Sort candidates by pageNumber then startY
        List<HeadingCandidate> sorted = candidates.stream()
            .sorted(Comparator.comparingInt(HeadingCandidate::getPageNumber)
                .thenComparingDouble(HeadingCandidate::getStartY))
            .toList();

        List<Section> roots = new ArrayList<>();
        Deque<Section> stack = new ArrayDeque<>();

        for (int i = 0; i < sorted.size(); i++) {
            HeadingCandidate c = sorted.get(i);
            int pageEnd = computePageEnd(sorted, i, totalPages);
            Section section = Section.builder()
                .id(UUID.randomUUID())
                .title(c.getText())
                .level(c.getLevel())
                .pageStart(c.getPageNumber())
                .pageEnd(pageEnd)
                .confidence(SectionConfidence.builder()
                    .score(confidence)
                    .method(method)
                    .build())
                .build();

            placeInHierarchy(section, stack, roots);
        }
        return roots;
    }

    private void placeInHierarchy(Section section, Deque<Section> stack, List<Section> roots) {
        while (!stack.isEmpty() && stack.peek().getLevel() >= section.getLevel()) {
            stack.pop();
        }
        if (stack.isEmpty()) {
            roots.add(section);
        } else {
            stack.peek().getChildren().add(section);
        }
        stack.push(section);
    }

    private int computePageEnd(List<HeadingCandidate> sorted, int index, int totalPages) {
        // Page end = one before the next sibling or same-level candidate at a higher or equal level
        for (int j = index + 1; j < sorted.size(); j++) {
            if (sorted.get(j).getLevel() <= sorted.get(index).getLevel()) {
                return Math.max(sorted.get(index).getPageNumber(),
                    sorted.get(j).getPageNumber() - 1);
            }
        }
        return totalPages - 1;
    }

    private double computeConfidence(HeadingStrategy strategy, int candidateCount) {
        // Bookmark and TOC strategies get higher confidence
        if (strategy instanceof BookmarkHeadingStrategy) return 0.95;
        if (strategy instanceof HeadingStyleStrategy) return 0.90;
        if (candidateCount > 10) return 0.80;
        return 0.65;
    }
}
```

**Test Plan:**
Class: `SectionSegmenterTest`
Mocks: All `HeadingStrategy` instances (mocked), `TocDetector` (mocked), `PdfDocumentLoader` (mocked).

```java

@ExtendWith(MockitoExtension.class)
class SectionSegmenterTest {
    @Mock
    TocDetector tocDetector;
    @Mock
    BookmarkHeadingStrategy bookmarkStrategy;
    @Mock
    NumberedHeadingStrategy numberedStrategy;
    // ...
    private SectionSegmenter segmenter;

    @BeforeEach
    void setUp() {
        segmenter = new SectionSegmenter(tocDetector,
            List.of(bookmarkStrategy, numberedStrategy));
    }

    @Test
    void shouldUseTocWhenTocDetectorFindsResult()

    @Test
    void shouldUseBookmarkStrategyBeforeNumberedWhenBothPresent()

    @Test
    void shouldSkipStrategyWhenFewerThanThreeHeadingsReturned()

    @Test
    void shouldReturnEmptyWhenAllStrategiesProduceFewHeadings()

    @Test
    void shouldBuildChildSectionsWhenLevel2FollowsLevel1()

    @Test
    void shouldComputePageEndAsOneLessThanNextSiblingStart()

    @Test
    void shouldAssignUniqueUuidsToAllSections()

    @Test
    void shouldSetConfidenceMethodToWinningStrategy()
}
```

**Observability:**

- Log INFO: `"SectionSegmenter using strategy={} with {} candidates"`
- Log WARN: `"SectionSegmenter: no strategy produced {} headings — returning empty"`
- Micrometer counter: `section.detection` with tag `strategy=BookmarkHeadingStrategy|...`

**Estimation:** 8 SP

---

### Epic 4 — Clause ID Assigner

#### Story 4.1 — ClauseIdAssigner: Deterministic ID Generation

**Description:**
Generates stable, human-readable clause IDs using the document's procurement reference, the section's numbered prefix,
and the paragraph index within the section. The same input always produces the same output — verified by a unit test
that calls the method twice with the same arguments and asserts equality.

**Acceptance Criteria:**

```gherkin
Given procurementRef="CPTU-2025-ICT-001", sectionTitle="2.3 Technical Requirements", paragraphIndex=1
When assignClauseId() is called
Then the returned ID is "cptu-2025-ict-001:2.3:P1"

Given procurementRef="DSI/GOB/2024-01", sectionTitle="Scope of Work" (no number detected), sectionIndex=3, paragraphIndex=2
When assignClauseId() is called
Then the returned ID is "dsi-gob-2024-01:S3:P2"
And a WARN log is emitted: "ClauseIdAssigner: using fallback ID for section without numeric prefix"

Given the same inputs are provided twice in two separate calls
When assignClauseId() is called each time
Then both calls return identical strings
```

**Interfaces/Contracts:**

File: `rfp-service/src/main/java/com/dsi/rfp/adapter/extraction/ClauseIdAssigner.java`:

```java
package com.dsi.rfp.adapter.extraction;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class ClauseIdAssigner {

    private static final Pattern SECTION_NUMBER_PATTERN =
        Pattern.compile("^(\\d+(?:\\.\\d+)*)\\s+.*");
    private static final Pattern LETTERED_PATTERN =
        Pattern.compile("^([a-zA-Z])\\)\\s+.*");
    private static final int MAX_REF_LENGTH = 20;

    /**
     * Assigns a deterministic clause ID.
     *
     * @param procurementRef the document's procurement reference (e.g., "CPTU-2025-ICT-001")
     * @param sectionTitle   the title of the containing section (e.g., "2.3 Technical Requirements")
     * @param sectionIndex   0-based index of the section within its parent (for fallback IDs)
     * @param paragraphIndex 1-based index of the paragraph within the section
     * @return the generated clause ID string (never null, never blank)
     */
    public String assignClauseId(String procurementRef, String sectionTitle,
                                 int sectionIndex, int paragraphIndex) {
        String normalizedRef = normalizeRef(procurementRef);
        Optional<String> sectionNumber = extractSectionNumber(sectionTitle);

        if (sectionNumber.isPresent()) {
            return normalizedRef + ":" + sectionNumber.get() + ":P" + paragraphIndex;
        } else {
            log.warn("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=WARN ClauseIdAssigner: using fallback ID for section without numeric prefix: " +
                "procRef={} sectionTitle={}", procurementRef, sectionTitle);
            return normalizedRef + ":S" + (sectionIndex + 1) + ":P" + paragraphIndex;
        }
    }

    /**
     * Assigns a lettered sub-clause ID (e.g., for items labeled "a) First item").
     *
     * @param parentClauseId the ID of the parent clause
     * @param letter         the letter label (e.g., "a", "b")
     * @return parentClauseId + "." + letter (e.g., "cptu-2025-ict-001:2.3:P1.a")
     */
    public String assignSubClauseId(String parentClauseId, String letter) {
        return parentClauseId + "." + letter.toLowerCase();
    }

    private String normalizeRef(String ref) {
        if (Objects.isNull(ref) || !StringUtils.hasText(ref)) return "unknown";
        String normalized = ref.toLowerCase()
            .replaceAll("\\s+", "-")
            .replaceAll("[^a-z0-9\\-]", "");
        return normalized.length() > MAX_REF_LENGTH
            ? normalized.substring(0, MAX_REF_LENGTH)
            : normalized;
    }

    private Optional<String> extractSectionNumber(String sectionTitle) {
        if (Objects.isNull(sectionTitle)) return Optional.empty();
        Matcher m = SECTION_NUMBER_PATTERN.matcher(sectionTitle.strip());
        if (m.matches()) return Optional.of(m.group(1));
        return Optional.empty();
    }
}
```

**Test Plan:**

```java
class ClauseIdAssignerTest {
    private ClauseIdAssigner assigner;

    @BeforeEach
    void setUp() {
        assigner = new ClauseIdAssigner();
    }

    @Test
    void shouldProduceDeterministicIdForSameInputs() {
        String id1 = assigner.assignClauseId("CPTU-2025", "2.3 Requirements", 2, 1);
        String id2 = assigner.assignClauseId("CPTU-2025", "2.3 Requirements", 2, 1);
        assertThat(id1).isEqualTo(id2);
    }

    @Test
    void shouldNormalizeProcurementRefToLowercase()

    @Test
    void shouldReplaceSpacesWithHyphensInRef()

    @Test
    void shouldStripNonAlphanumericCharsExceptHyphens()

    @Test
    void shouldTruncateRefToTwentyChars()

    @Test
    void shouldExtractSectionNumberFromTitlePrefix()

    @Test
    void shouldUseFallbackIdWhenSectionHasNoNumericPrefix()

    @Test
    void shouldUseFallbackIdWhenSectionTitleIsNull()

    @Test
    void shouldProduceDifferentIdsForDifferentParagraphIndexes()

    @Test
    void shouldAssignSubClauseIdWithLetterSuffix()
}
```

**Estimation:** 3 SP

---

### Epic 5 — JSON Schema Validation

#### Story 5.1 — rfp-schema-v1.json and RfpSchemaValidator

**Description:**
Write the complete JSON Schema for the full RFP entity model and implement a validator service that loads it at startup.
On any schema load failure, the application must fail to start (fail-fast). The validator is used by `FinalizeNode` (
Sprint 4) before persisting the extracted RFP document.

**Acceptance Criteria:**

```gherkin
Given rfp-schema-v1.json exists and is valid JSON Schema draft-07
When RfpSchemaValidator initializes at startup
Then the JsonSchema object is cached and no exception is thrown

Given rfp-schema-v1.json is missing or malformed
When RfpSchemaValidator initializes at startup
Then RfpSchemaLoadException is thrown and the application does not start

Given a valid RFP JSON string conforming to the schema
When validate() is called
Then ValidationResult with valid=true is returned

Given an RFP JSON missing the required field "doc_meta.procurement_ref"
When validate() is called
Then ValidationResult with valid=false is returned
And errors[] contains a message referencing "procurement_ref"
```

**Interfaces/Contracts:**

File: `rfp-core/src/main/java/com/dsi/rfp/domain/exception/RfpSchemaLoadException.java`:

```java
public class RfpSchemaLoadException extends RuntimeException {
    public RfpSchemaLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

File: `rfp-core/src/main/java/com/dsi/rfp/domain/model/SchemaValidationResult.java`:

```java

@Data
@Builder
public class SchemaValidationResult {
    private boolean valid;
    @Builder.Default
    private final List<String> errors = new java.util.ArrayList<>();

    public static SchemaValidationResult ok() {
        return SchemaValidationResult.builder().valid(true).build();
    }

    public static SchemaValidationResult fail(List<String> errors) {
        return SchemaValidationResult.builder().valid(false).errors(errors).build();
    }
}
```

File: `rfp-service/src/main/java/com/dsi/rfp/adapter/RfpSchemaValidator.java`:

```java
package com.dsi.rfp.adapter;

import com.dsi.rfp.domain.model.SchemaValidationResult;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class RfpSchemaValidator {

    private final ObjectMapper objectMapper;

    @Value("${app.schema.rfp-schema-path:schema/rfp-schema-v1.json}")
    private String schemaPath;

    private JsonSchema jsonSchema;

    @PostConstruct
    void loadSchema() throws IOException {
        Path path = Path.of(schemaPath);
        InputStream schemaStream = Files.newInputStream(path);
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        jsonSchema = factory.getSchema(schemaStream);
        log.info("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=INFO RFP JSON Schema loaded successfully from {}", schemaPath);
    }

    /**
     * Validates an RFP JSON string against the loaded schema.
     *
     * @param rfpJson the JSON string to validate
     * @return SchemaValidationResult with valid flag and list of error messages
     */
    public SchemaValidationResult validate(String rfpJson) throws IOException {
        JsonNode node = objectMapper.readTree(rfpJson);
        Set<ValidationMessage> messages = jsonSchema.validate(node);
        if (messages.isEmpty()) return SchemaValidationResult.ok();
        List<String> errors = messages.stream()
            .map(ValidationMessage::getMessage)
            .collect(Collectors.toList());
        return SchemaValidationResult.fail(errors);
    }
}
```

Add config property to `application.properties`:

```properties
app.schema.rfp-schema-path=schema/rfp-schema-v1.json
```

**Schema file: `rfp-extractor/schema/rfp-schema-v1.json`** — complete JSON Schema:

```json
{
    "$schema": "http://json-schema.org/draft-07/schema#",
    "$id": "rfp-schema-v1",
    "title": "RFP Extraction Result",
    "type": "object",
    "required": [
        "doc_meta",
        "sections",
        "clauses",
        "tables",
        "entities",
        "rule_pack_results",
        "extraction_state"
    ],
    "properties": {
        "doc_meta": {
            "type": "object",
            "required": [
                "procurement_ref"
            ],
            "properties": {
                "title": {
                    "type": [
                        "string",
                        "null"
                    ]
                },
                "procurement_ref": {
                    "type": [
                        "string",
                        "null"
                    ]
                },
                "issue_date": {
                    "type": [
                        "string",
                        "null"
                    ],
                    "format": "date"
                },
                "rfp_type": {
                    "type": [
                        "string",
                        "null"
                    ]
                },
                "source_language": {
                    "type": [
                        "string",
                        "null"
                    ]
                },
                "extraction_model": {
                    "type": [
                        "string",
                        "null"
                    ]
                },
                "extraction_timestamp": {
                    "type": [
                        "string",
                        "null"
                    ],
                    "format": "date-time"
                }
            }
        },
        "sections": {
            "type": "array",
            "items": {
                "$ref": "#/definitions/section"
            }
        },
        "clauses": {
            "type": "array",
            "items": {
                "type": "object",
                "required": [
                    "id",
                    "text"
                ],
                "properties": {
                    "id": {
                        "type": "string"
                    },
                    "section_id": {
                        "type": [
                            "string",
                            "null"
                        ]
                    },
                    "page_start": {
                        "type": "integer"
                    },
                    "page_end": {
                        "type": "integer"
                    },
                    "text": {
                        "type": "string"
                    },
                    "text_language": {
                        "type": [
                            "string",
                            "null"
                        ]
                    },
                    "tags": {
                        "type": "array",
                        "items": {
                            "type": "string"
                        }
                    },
                    "references": {
                        "type": "array",
                        "items": {
                            "type": "string"
                        }
                    },
                    "confidence": {
                        "$ref": "#/definitions/confidence_with_ocr"
                    }
                }
            }
        },
        "tables": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "id": {
                        "type": "string"
                    },
                    "section_id": {
                        "type": [
                            "string",
                            "null"
                        ]
                    },
                    "clause_id": {
                        "type": [
                            "string",
                            "null"
                        ]
                    },
                    "page_start": {
                        "type": "integer"
                    },
                    "page_end": {
                        "type": "integer"
                    },
                    "caption": {
                        "type": [
                            "string",
                            "null"
                        ]
                    },
                    "type": {
                        "type": [
                            "string",
                            "null"
                        ]
                    },
                    "headers": {
                        "type": "array",
                        "items": {
                            "type": "string"
                        }
                    },
                    "rows": {
                        "type": "array",
                        "items": {
                            "type": "array"
                        }
                    },
                    "grid": {
                        "type": "array",
                        "items": {
                            "type": "object",
                            "properties": {
                                "row": {
                                    "type": "integer"
                                },
                                "col": {
                                    "type": "integer"
                                },
                                "value": {
                                    "type": [
                                        "string",
                                        "null"
                                    ]
                                },
                                "rowspan": {
                                    "type": "integer"
                                },
                                "colspan": {
                                    "type": "integer"
                                }
                            }
                        }
                    },
                    "confidence": {
                        "type": "number",
                        "minimum": 0,
                        "maximum": 1
                    }
                }
            }
        },
        "entities": {
            "type": "object",
            "properties": {
                "general": {
                    "$ref": "#/definitions/entities_general"
                },
                "submission": {
                    "$ref": "#/definitions/entities_submission"
                },
                "financial": {
                    "$ref": "#/definitions/entities_financial"
                },
                "ict": {
                    "$ref": "#/definitions/entities_ict"
                },
                "staffing": {
                    "$ref": "#/definitions/entities_staffing"
                },
                "support": {
                    "$ref": "#/definitions/entities_support"
                },
                "evaluation": {
                    "$ref": "#/definitions/entities_evaluation"
                },
                "pricing_factors": {
                    "type": "array",
                    "items": {
                        "type": "string"
                    }
                },
                "rfp_amendments": {
                    "type": "array"
                },
                "other_info": {
                    "type": [
                        "object",
                        "null"
                    ]
                }
            }
        },
        "rule_pack_results": {
            "type": "object",
            "properties": {
                "pack_id": {
                    "type": [
                        "string",
                        "null"
                    ]
                },
                "pack_version": {
                    "type": [
                        "string",
                        "null"
                    ]
                },
                "summary": {
                    "type": "object",
                    "properties": {
                        "fatal": {
                            "type": "integer"
                        },
                        "high": {
                            "type": "integer"
                        },
                        "medium": {
                            "type": "integer"
                        },
                        "low": {
                            "type": "integer"
                        },
                        "info": {
                            "type": "integer"
                        }
                    }
                },
                "findings": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "rule_id": {
                                "type": "string"
                            },
                            "severity": {
                                "type": "string",
                                "enum": [
                                    "FATAL",
                                    "HIGH",
                                    "MEDIUM",
                                    "LOW",
                                    "INFO"
                                ]
                            },
                            "status": {
                                "type": "string",
                                "enum": [
                                    "PASS",
                                    "FAIL",
                                    "WARN",
                                    "SKIP"
                                ]
                            },
                            "message": {
                                "type": "string"
                            },
                            "evidence": {
                                "type": [
                                    "string",
                                    "null"
                                ]
                            }
                        }
                    }
                }
            }
        },
        "extraction_state": {
            "type": "object",
            "properties": {
                "job_id": {
                    "type": "string"
                },
                "doc_completeness_score": {
                    "type": "number",
                    "minimum": 0,
                    "maximum": 1
                },
                "missing_fields": {
                    "type": "array",
                    "items": {
                        "type": "string"
                    }
                },
                "manual_review_required": {
                    "type": "array",
                    "items": {
                        "type": "string"
                    }
                },
                "page_summary": {
                    "type": "array"
                }
            }
        }
    },
    "definitions": {
        "section": {
            "type": "object",
            "required": [
                "id",
                "title",
                "level"
            ],
            "properties": {
                "id": {
                    "type": "string",
                    "format": "uuid"
                },
                "title": {
                    "type": "string"
                },
                "level": {
                    "type": "integer",
                    "minimum": 1,
                    "maximum": 6
                },
                "page_start": {
                    "type": "integer",
                    "minimum": 0
                },
                "page_end": {
                    "type": "integer",
                    "minimum": 0
                },
                "children": {
                    "type": "array",
                    "items": {
                        "$ref": "#/definitions/section"
                    }
                },
                "confidence": {
                    "type": "object",
                    "properties": {
                        "score": {
                            "type": "number",
                            "minimum": 0,
                            "maximum": 1
                        },
                        "method": {
                            "type": "string"
                        }
                    }
                }
            }
        },
        "confidence_with_ocr": {
            "type": "object",
            "properties": {
                "score": {
                    "type": "number",
                    "minimum": 0,
                    "maximum": 1
                },
                "method": {
                    "type": "string"
                },
                "ocr_confidence": {
                    "type": [
                        "number",
                        "null"
                    ],
                    "minimum": 0,
                    "maximum": 1
                }
            }
        },
        "entities_general": {
            "type": [
                "object",
                "null"
            ],
            "properties": {
                "client_name": {
                    "type": [
                        "string",
                        "null"
                    ]
                },
                "submission_deadline": {
                    "type": [
                        "string",
                        "null"
                    ]
                },
                "issue_date": {
                    "type": [
                        "string",
                        "null"
                    ]
                },
                "method_of_selection": {
                    "type": [
                        "string",
                        "null"
                    ]
                },
                "procurement_method": {
                    "type": [
                        "string",
                        "null"
                    ]
                },
                "project_duration": {
                    "type": [
                        "string",
                        "null"
                    ]
                },
                "pre_bid_meeting": {
                    "type": [
                        "object",
                        "null"
                    ],
                    "properties": {
                        "date": {
                            "type": [
                                "string",
                                "null"
                            ]
                        },
                        "venue": {
                            "type": [
                                "string",
                                "null"
                            ]
                        }
                    }
                },
                "contact": {
                    "type": [
                        "object",
                        "null"
                    ],
                    "properties": {
                        "name": {
                            "type": [
                                "string",
                                "null"
                            ]
                        },
                        "email": {
                            "type": [
                                "string",
                                "null"
                            ]
                        },
                        "phone": {
                            "type": [
                                "string",
                                "null"
                            ]
                        },
                        "address": {
                            "type": [
                                "string",
                                "null"
                            ]
                        }
                    }
                }
            }
        },
        "entities_submission": {
            "type": [
                "object",
                "null"
            ],
            "properties": {
                "guidelines_summary": {
                    "type": [
                        "string",
                        "null"
                    ]
                },
                "number_of_copies": {
                    "type": [
                        "integer",
                        "null"
                    ]
                },
                "soft_submission_required": {
                    "type": [
                        "object",
                        "null"
                    ],
                    "properties": {
                        "value": {
                            "type": [
                                "boolean",
                                "null"
                            ]
                        },
                        "email": {
                            "type": [
                                "string",
                                "null"
                            ]
                        }
                    }
                },
                "submission_address": {
                    "type": [
                        "string",
                        "null"
                    ]
                }
            }
        },
        "entities_financial": {
            "type": [
                "object",
                "null"
            ],
            "properties": {
                "technical_financial_split": {
                    "type": [
                        "object",
                        "null"
                    ],
                    "properties": {
                        "technical_weight": {
                            "type": [
                                "number",
                                "null"
                            ]
                        },
                        "financial_weight": {
                            "type": [
                                "number",
                                "null"
                            ]
                        }
                    }
                },
                "performance_security": {
                    "type": [
                        "object",
                        "null"
                    ],
                    "properties": {
                        "percentage": {
                            "type": [
                                "number",
                                "null"
                            ]
                        },
                        "type": {
                            "type": [
                                "string",
                                "null"
                            ]
                        }
                    }
                },
                "bank_guarantee": {
                    "type": [
                        "object",
                        "null"
                    ],
                    "properties": {
                        "required": {
                            "type": [
                                "boolean",
                                "null"
                            ]
                        },
                        "details": {
                            "type": [
                                "string",
                                "null"
                            ]
                        }
                    }
                },
                "payment_terms": {
                    "type": [
                        "string",
                        "null"
                    ]
                },
                "reimbursable_expenses": {
                    "type": [
                        "string",
                        "null"
                    ]
                },
                "bid_validity_period": {
                    "type": [
                        "string",
                        "null"
                    ]
                }
            }
        },
        "entities_ict": {
            "type": [
                "object",
                "null"
            ],
            "properties": {
                "total_users": {
                    "type": [
                        "integer",
                        "null"
                    ]
                },
                "concurrent_users": {
                    "type": [
                        "integer",
                        "null"
                    ]
                },
                "programming_language_preference": {
                    "type": [
                        "string",
                        "null"
                    ]
                },
                "system_language": {
                    "type": [
                        "string",
                        "null"
                    ]
                },
                "architecture": {
                    "type": [
                        "string",
                        "null"
                    ]
                },
                "tech_stack": {
                    "type": [
                        "string",
                        "null"
                    ]
                },
                "database": {
                    "type": [
                        "string",
                        "null"
                    ]
                },
                "hosting": {
                    "type": [
                        "object",
                        "null"
                    ],
                    "properties": {
                        "type": {
                            "type": [
                                "string",
                                "null"
                            ]
                        },
                        "details": {
                            "type": [
                                "string",
                                "null"
                            ]
                        }
                    }
                },
                "data_migration_required": {
                    "type": [
                        "object",
                        "null"
                    ],
                    "properties": {
                        "value": {
                            "type": [
                                "boolean",
                                "null"
                            ]
                        },
                        "details": {
                            "type": [
                                "string",
                                "null"
                            ]
                        }
                    }
                },
                "legacy_system": {
                    "type": [
                        "string",
                        "null"
                    ]
                },
                "hardware_requirements": {
                    "type": [
                        "string",
                        "null"
                    ]
                },
                "integrations": {
                    "type": "array",
                    "items": {
                        "type": "string"
                    }
                },
                "mobile_app_required": {
                    "type": [
                        "object",
                        "null"
                    ],
                    "properties": {
                        "value": {
                            "type": [
                                "boolean",
                                "null"
                            ]
                        },
                        "platforms": {
                            "type": "array",
                            "items": {
                                "type": "string"
                            }
                        }
                    }
                },
                "ui_mock_required": {
                    "type": [
                        "boolean",
                        "null"
                    ]
                },
                "presentation_required": {
                    "type": [
                        "boolean",
                        "null"
                    ]
                },
                "gantt_chart_required": {
                    "type": [
                        "boolean",
                        "null"
                    ]
                },
                "e_governance_compliance": {
                    "type": [
                        "object",
                        "null"
                    ],
                    "properties": {
                        "required": {
                            "type": [
                                "boolean",
                                "null"
                            ]
                        },
                        "framework": {
                            "type": [
                                "string",
                                "null"
                            ]
                        }
                    }
                }
            }
        },
        "entities_staffing": {
            "type": [
                "object",
                "null"
            ],
            "properties": {
                "staff_months": {
                    "type": [
                        "number",
                        "null"
                    ]
                },
                "onsite_resource_requirements": {
                    "type": [
                        "string",
                        "null"
                    ]
                },
                "marking_criteria": {
                    "type": [
                        "string",
                        "null"
                    ]
                }
            }
        },
        "entities_support": {
            "type": [
                "object",
                "null"
            ],
            "properties": {
                "training": {
                    "type": [
                        "object",
                        "null"
                    ],
                    "properties": {
                        "value": {
                            "type": [
                                "boolean",
                                "null"
                            ]
                        },
                        "duration": {
                            "type": [
                                "string",
                                "null"
                            ]
                        }
                    }
                },
                "support_maintenance": {
                    "type": [
                        "object",
                        "null"
                    ],
                    "properties": {
                        "value": {
                            "type": [
                                "boolean",
                                "null"
                            ]
                        },
                        "period": {
                            "type": [
                                "string",
                                "null"
                            ]
                        },
                        "sla": {
                            "type": [
                                "string",
                                "null"
                            ]
                        }
                    }
                },
                "warranty_period": {
                    "type": [
                        "string",
                        "null"
                    ]
                }
            }
        },
        "entities_evaluation": {
            "type": [
                "object",
                "null"
            ],
            "properties": {
                "criteria": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "name": {
                                "type": "string"
                            },
                            "weight": {
                                "type": [
                                    "number",
                                    "null"
                                ]
                            }
                        }
                    }
                },
                "eligibility_summary": {
                    "type": [
                        "string",
                        "null"
                    ]
                },
                "scope_summary": {
                    "type": [
                        "string",
                        "null"
                    ]
                }
            }
        }
    }
}
```

**Test Plan:**
Class: `RfpSchemaValidatorTest`
Mocks: None (use real ObjectMapper and a real schema file loaded from classpath test resources).

```java
class RfpSchemaValidatorTest {
    private RfpSchemaValidator validator;

    @BeforeEach
    void setUp() {
        // Place schema in src/test/resources/schema/rfp-schema-v1.json
        // Use ReflectionTestUtils to set schemaPath = "src/test/resources/schema/..."
    }

    @Test
    void shouldReturnValidWhenRfpJsonConformsToSchema()

    @Test
    void shouldReturnInvalidWhenRequiredFieldMissing()

    @Test
    void shouldReturnInvalidWhenJsonIsMalformed()

    @Test
    void shouldReturnAllValidationErrors()
}
```

**Estimation:** 5 SP

---

### Epic 6 — Evaluation Harness and Optional Benchmark Dataset

#### Story 6.0 — Optional Ground Truth Benchmark Track (deferred to wishlist, non-blocking)

**Description:**
Collect, annotate, and validate a real-document benchmark dataset for future reporting. This is **not** a Sprint 3
prerequisite. Sprint 3 acceptance uses deterministic fixtures and schema checks; real-document benchmarking is deferred
to `wishlist/001_wishlist.md`.

**Assignment:** Backlog item tracked in `wishlist/001_wishlist.md`; no Sprint 3 staffing dependency.

**Annotation format** — each file at `testdata/ground-truth/{doc-id}.json` must include:

1. Section boundaries: `title`, `level` (1–3), `page_start`, `page_end`, `children[]`.
2. Entity values for all fields present in the doc (use `null` for absent fields, not omit them).
3. First table structure: `headers[]`, `rows[][]` (minimum one table per ICT/Works doc; skip if no table).
4. Expected rule findings: `expected_rule_failures: ["BD-ICT-001", "BD-ICT-005"]` (which rules SHOULD fire on this doc).

**Deliverable:** 5 fully annotated JSON files (all 4 fields above) + 10 partially annotated (sections + entities only)
in `testdata/ground-truth/` when wishlist benchmark work starts.

**Annotation JSON schema location:** `testdata/README.md` (see Story 6.1 below) must be committed before any JSON files
are created, so annotators and the evaluator code share a single source of truth.

**Acceptance Criterion (wishlist benchmark track):**

- `GroundTruthLoader.load("testdata/ground-truth")` returns 15 documents without exceptions.
- At least 5 documents have non-empty `entities` and at least 1 `expected_rule_failures` entry.

---

#### Story 6.1 — Fixture Infrastructure + Optional Ground Truth Compatibility

**Description:**
Create the fixture directory structure, expected-output template, and README for deterministic tests. Optional
ground-truth compatibility remains supported but is not part of Sprint 3 exit criteria.

**File: `rfp-extractor/testdata/ground-truth/README.md`:**

```markdown
# Ground Truth Dataset

## Directory Structure
- testdata/pdfs/         — raw PDF files (gitignored)
- testdata/ground-truth/ — JSON annotations (committed)

## Sourcing PDFs
PDFs must be obtained from:
1. CPTU (Central Procurement Technical Unit) Bangladesh: https://cptu.gov.bd/
2. Client-provided RFP archives.
3. IMED (Implementation Monitoring and Evaluation Division): https://imed.gov.bd/

Store PDFs as testdata/pdfs/{doc-id}.pdf (e.g., cptu-2024-ict-001.pdf).
PDFs are gitignored — annotators must share via Google Drive or S3.

## Annotation Format
See sample-annotation-template.json for the schema.

## Target
- 15 PDF documents total
- 5 fully annotated (sections + entities)
- 10 partially annotated (sections only)
```

**File: `rfp-extractor/testdata/ground-truth/sample-annotation-template.json`:**

```json
{
  "doc_id": "cptu-2024-ict-001",
  "pdf_filename": "cptu-2024-ict-001.pdf",
  "annotator": "name@example.com",
  "annotation_date": "2025-06-01",
  "sections": [
    {
      "title": "1. Background",
      "level": 1,
      "page_start": 1,
      "page_end": 3,
      "children": []
    },
    {
      "title": "2. Scope of Work",
      "level": 1,
      "page_start": 4,
      "page_end": 12,
      "children": [
        {
          "title": "2.1 Technical Requirements",
          "level": 2,
          "page_start": 4,
          "page_end": 8,
          "children": []
        }
      ]
    }
  ],
  "entities": {
    "submission_deadline": "2025-08-15T17:00:00+06:00",
    "client_name": "Ministry of ICT, Bangladesh",
    "procurement_ref": "CPTU-2024-ICT-001"
  },
  "tables": [
    {
      "page": 7,
      "headers": ["Item", "Quantity", "Unit Price (BDT)"],
      "rows": [
        ["Laptop", "10", "85000"],
        ["Server", "2", "450000"]
      ]
    }
  ],
  "expected_rule_failures": ["BD-ICT-001", "BD-ICT-014"],
  "notes": "Add any annotator notes here. Use null (not omit) for entity fields absent from this document."
}
```

Add to `.gitignore`:

```
testdata/pdfs/
```

**File: `rfp-service/src/main/java/com/dsi/rfp/adapter/extraction/evaluation/GroundTruthLoader.java`:**

```java
package com.dsi.rfp.adapter.extraction.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class GroundTruthLoader {

    private final ObjectMapper objectMapper;

    /**
     * Loads all ground truth annotations from the given directory.
     *
     * @param groundTruthDir path to the directory containing .json annotation files
     * @return list of GroundTruthAnnotation objects
     * @throws IOException if the directory cannot be read
     */
    public List<GroundTruthAnnotation> loadAll(Path groundTruthDir) throws IOException {
        List<GroundTruthAnnotation> annotations = new ArrayList<>();
        try (var stream = Files.list(groundTruthDir)) {
            for (Path path : stream
                .filter(p -> p.toString().endsWith(".json"))
                .filter(p -> !p.getFileName().toString().startsWith("sample-"))
                .toList()) {
                annotations.add(readAnnotation(path));
            }
        }
        return annotations;
    }

    private GroundTruthAnnotation readAnnotation(Path path) throws IOException {
        return objectMapper.readValue(path.toFile(), GroundTruthAnnotation.class);
    }
}
```

**File: `rfp-core/src/main/java/com/dsi/rfp/domain/model/GroundTruthAnnotation.java`** (or in evaluation package):

```java
@Data
public class GroundTruthAnnotation {
    @JsonProperty("doc_id")
    private String docId;
    @JsonProperty("pdf_filename")
    private String pdfFilename;
    private final List<AnnotatedSection> sections = new ArrayList<>();
    private final Map<String, Object> entities = new HashMap<>();
}

@Data
class AnnotatedSection {
    private String title;
    private int level;
    @JsonProperty("page_start") private int pageStart;
    @JsonProperty("page_end") private int pageEnd;
    private final List<AnnotatedSection> children = new ArrayList<>();
}
```

**File: `rfp-service/src/main/java/com/dsi/rfp/adapter/extraction/evaluation/SectionExtractionEvaluator.java`:**

```java
package com.dsi.rfp.adapter.extraction.evaluation;

import com.dsi.rfp.domain.model.AnnotatedSection;
import com.dsi.rfp.domain.model.GroundTruthAnnotation;
import com.dsi.rfp.domain.model.Section;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.List;

@Slf4j
@Component
public class SectionExtractionEvaluator {

    private static final int PAGE_PROXIMITY_TOLERANCE = 1; // pages
    static final double REQUIRED_F1_THRESHOLD = 0.80;

    /**
     * Computes the F1 score comparing extracted sections against a ground truth annotation.
     * A match is defined as: same title (case-insensitive, stripped) AND
     * |extracted.pageStart - truth.pageStart| <= PAGE_PROXIMITY_TOLERANCE.
     *
     * @param extracted list of extracted top-level sections
     * @param truth     the ground truth annotation
     * @return F1 score between 0.0 and 1.0
     */
    public double computeF1(List<Section> extracted, GroundTruthAnnotation truth) {
        List<Section> flatExtracted = flatten(extracted);
        List<AnnotatedSection> flatTruth = flattenAnnotated(truth.getSections());

        int truePositives = countTruePositives(flatExtracted, flatTruth);
        double precision = flatExtracted.isEmpty() ? 0.0
            : (double) truePositives / flatExtracted.size();
        double recall = flatTruth.isEmpty() ? 0.0
            : (double) truePositives / flatTruth.size();

        if (precision + recall == 0) return 0.0;
        double f1 = 2 * precision * recall / (precision + recall);
        log.info("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=INFO F1 evaluation: docId={} precision={:.3f} recall={:.3f} f1={:.3f}",
            truth.getDocId(), precision, recall, f1);
        return f1;
    }

    /**
     * Returns true if F1 score meets the required threshold (0.80).
     */
    public boolean meetsThreshold(List<Section> extracted, GroundTruthAnnotation truth) {
        return computeF1(extracted, truth) >= REQUIRED_F1_THRESHOLD;
    }

    private int countTruePositives(List<Section> extracted, List<AnnotatedSection> truth) {
        int count = 0;
        for (AnnotatedSection t : truth) {
            boolean matched = extracted.stream().anyMatch(e -> isMatch(e, t));
            if (matched) count++;
        }
        return count;
    }

    private boolean isMatch(Section extracted, AnnotatedSection truth) {
        boolean titleMatch = extracted.getTitle().strip()
            .equalsIgnoreCase(truth.getTitle().strip());
        boolean pageMatch = Math.abs(extracted.getPageStart() - truth.getPageStart())
            <= PAGE_PROXIMITY_TOLERANCE;
        return titleMatch && pageMatch;
    }

    private List<Section> flatten(List<Section> sections) {
        List<Section> all = new ArrayList<>();
        for (Section s : sections) {
            all.add(s);
            all.addAll(flatten(s.getChildren()));
        }
        return all;
    }

    private List<AnnotatedSection> flattenAnnotated(List<AnnotatedSection> sections) {
        List<AnnotatedSection> all = new ArrayList<>();
        for (AnnotatedSection s : sections) {
            all.add(s);
            all.addAll(flattenAnnotated(s.getChildren()));
        }
        return all;
    }
}
```

**Test Plan:**

```java
class SectionExtractionEvaluatorTest {
    @Test
    void shouldReturnOneWhenExtractedMatchesAllGroundTruth()

    @Test
    void shouldReturnZeroWhenNoMatchesFound()

    @Test
    void shouldTolerateOnePageDifferenceInPageStart()

    @Test
    void shouldNotMatchWhenPageDifferenceExceedsTolerance()

    @Test
    void shouldReturnZeroWhenExtractedListIsEmpty()

    @Test
    void shouldMatchCaseInsensitively()

    @Test
    void shouldReturnTrueForMeetsThresholdWhenF1AbovePoint8()

    @Test
    void shouldReturnFalseForMeetsThresholdWhenF1BelowPoint8()
}
```

**Estimation:** 5 SP

---

### Epic 7 — Pipeline Integration

#### Story 7.1 — Wire SectionSegmenter into ExtractionPipelineService

**Description:**
Update `ExtractionPipelineService` to call `SectionSegmenter` after page classification. Store the resulting sections in
the job state (serialized as JSON in Redis). Section tree will be fetched and used by Sprint 4's agent graph.

**Acceptance Criteria:**

```gherkin
Given a PDF upload completes page classification
When the pipeline runs Sprint 3 steps
Then SectionSegmenter.segment() is called
And the job status progresses from RUNNING to COMPLETED
And a log shows how many sections were detected

Given the PDF has a bookmarked outline
When pipeline completes
Then sections[] in job state has confidence.method = "BookmarkHeadingStrategy"
```

**Implementation Plan:**

- Add `SectionSegmenter` injection to `ExtractionPipelineService`.
- After page classification, call `segmenter.segment(documentPath, pdfLoader, pageCount)`.
- Serialize `List<Section>` to JSON string.
- Store in Redis under a new key `rfp:sections:{jobId}` with 24h TTL (or add `sections` field to `ExtractionJob` as a
  JSON string — simpler for Sprint 3).
- Add `String sectionsJson` field to `ExtractionJob`.
- Update `ExtractionPipelineService` to set `sectionsJson` on the job.

**Estimation:** 3 SP

---

### Epic 8 — Frontend Section Tree

#### Story 8.1 — ResultPage with SectionTree Component

**Description:**
Create `SectionTree.tsx` — a recursive collapsible tree component. Integrate it into `ResultPage.tsx` under the "
Sections" tab. Data comes from `GET /api/v1/rfp/result/{jobId}` (stub endpoint added in this sprint returning the stored
sections).

**Acceptance Criteria:**

```gherkin
Given the result page loads for a COMPLETED job
When the "Sections" tab is active
Then a collapsible tree of sections is shown
And level-1 sections are expanded by default
And level-2+ sections are collapsed by default

Given a section with children
When the expand/collapse icon is clicked
Then children are shown/hidden
```

Update `GET /api/v1/rfp/result/{jobId}` in `RfpController`:

```java
@GetMapping("/result/{jobId}")
public ResponseEntity<Map<String, Object>> getResult(@PathVariable UUID jobId) {
    // Sprint 3: return sections from job state
    return jobService.findById(jobId)
        .map(job -> {
            // Parse sectionsJson from job, return basic result
            ...
        })
        .orElse(ResponseEntity.notFound().build());
}
```

File: `rfp-frontend/src/components/SectionTree.tsx`:

```tsx
import { useState } from 'react';
import { Section } from '../types/rfp';

interface SectionTreeProps {
    sections: Section[];
    depth?: number;
}

export function SectionTree({ sections, depth = 0 }: SectionTreeProps) {
    return (
        <ul className={`space-y-1 ${depth > 0 ? 'ml-4 mt-1' : ''}`}>
            {sections.map((section) => (
                <SectionNode key={section.id} section={section} depth={depth} />
            ))}
        </ul>
    );
}

function SectionNode({ section, depth }: { section: Section; depth: number }) {
    const [expanded, setExpanded] = useState(depth === 0);
    const hasChildren = section.children && section.children.length > 0;
    const confidence = section.confidence?.score ?? 0;
    const confidenceColor = confidence >= 0.8 ? 'text-green-600'
        : confidence >= 0.5 ? 'text-yellow-600' : 'text-red-600';

    return (
        <li>
            <div
                className="flex items-center gap-2 py-1 px-2 rounded hover:bg-gray-50 cursor-pointer"
                onClick={() => hasChildren && setExpanded(!expanded)}
            >
                <span className="text-gray-400 w-4 text-center">
                    {hasChildren ? (expanded ? '▼' : '▶') : '•'}
                </span>
                <span className={`font-medium text-sm text-gray-700 ${
                    depth === 0 ? 'font-semibold' : ''}`}>
                    {section.title}
                </span>
                <span className="ml-auto text-xs text-gray-400">
                    p.{section.pageStart}–{section.pageEnd}
                </span>
                <span className={`text-xs ${confidenceColor}`}>
                    {(confidence * 100).toFixed(0)}%
                </span>
            </div>
            {expanded && hasChildren && (
                <SectionTree sections={section.children} depth={depth + 1} />
            )}
        </li>
    );
}
```

File: `rfp-frontend/src/pages/ResultPage.tsx` (updated):

```tsx
import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { SectionTree } from '../components/SectionTree';
import { getRfpResult } from '../api/rfpClient';

export function ResultPage() {
    const { jobId } = useParams<{ jobId: string }>();
    const [activeTab, setActiveTab] = useState<'sections' | 'entities' | 'tables'>('sections');
    const { data } = useQuery({
        queryKey: ['rfpResult', jobId],
        queryFn: () => getRfpResult(jobId!),
        enabled: !!jobId,
    });

    const tabs = [
        { key: 'sections', label: 'Sections' },
        { key: 'entities', label: 'Entities' },
        { key: 'tables', label: 'Tables' },
    ] as const;

    return (
        <div className="max-w-4xl mx-auto p-8">
            <h1 className="text-2xl font-bold mb-2">Extraction Result</h1>
            <p className="text-sm text-gray-500 mb-6 font-mono">{jobId}</p>

            <div className="border-b border-gray-200 mb-6">
                <nav className="flex gap-4">
                    {tabs.map(tab => (
                        <button
                            key={tab.key}
                            onClick={() => setActiveTab(tab.key)}
                            className={`pb-2 px-1 text-sm font-medium border-b-2 transition-colors ${
                                activeTab === tab.key
                                    ? 'border-blue-500 text-blue-600'
                                    : 'border-transparent text-gray-500 hover:text-gray-700'
                            }`}
                        >
                            {tab.label}
                        </button>
                    ))}
                </nav>
            </div>

            {activeTab === 'sections' && data?.sections && (
                <SectionTree sections={data.sections} />
            )}
            {activeTab === 'entities' && (
                <p className="text-gray-500">Entity extraction — implemented in Sprint 4.</p>
            )}
            {activeTab === 'tables' && (
                <p className="text-gray-500">Table extraction — implemented in Sprint 5.</p>
            )}
        </div>
    );
}
```

**Estimation:** 3 SP

---

## 4) PR Plan

### PR 1: `feat/sprint3-heading-strategies` — All Six Strategies + TocDetector

**Contains:**

- `HeadingStrategy` interface.
- `HeadingCandidate` domain model.
- `BookmarkHeadingStrategy`, `HeadingStyleStrategy`, `NumberedHeadingStrategy`, `BanglaHeadingStrategy`,
  `FontSizeHeadingStrategy`, `AllCapsHeadingStrategy`.
- `TocDetector`.
- Updated `TextBlock` (added `pageNumber` field).
- Updated `PdfDocumentLoader` (sets `pageNumber` on each block).
- Unit tests for all strategies and TocDetector.

**Review Checklist:**

- [ ] All strategies implement `HeadingStrategy` interface.
- [ ] All strategies are annotated with `@Order` (1-6) for injection ordering.
- [ ] `BookmarkHeadingStrategy` uses try-with-resources on PDDocument.
- [ ] `NumberedHeadingStrategy` patterns are anchored with `^`.
- [ ] `BanglaHeadingStrategy` patterns use `Pattern.UNICODE_CHARACTER_CLASS`.
- [ ] `AllCapsHeadingStrategy` requires BOTH all-uppercase AND surrounding blank lines.
- [ ] `FontSizeHeadingStrategy` uses median (not mean) font size.
- [ ] `TocDetector` scans only first 10 pages.
- [ ] No strategy returns null (always returns a List, possibly empty).
- [ ] Strategies have zero internal mutable state (safe for concurrent use).
- [ ] All unit tests named `should{Behaviour}When{Condition}`.

---

### PR 2: `feat/sprint3-segmenter-evaluator` — SectionSegmenter, ClauseIdAssigner, Schema, Fixture Evaluator

**Contains:**

- `Section`, `SectionConfidence` domain models.
- `SectionSegmenter` (full implementation).
- `ClauseIdAssigner`.
- `RfpSchemaValidator` + `schema/rfp-schema-v1.json`.
- `FixtureExpectationLoader` + `SectionExtractionEvaluator`.
- Optional benchmark models (`GroundTruthAnnotation`, `AnnotatedSection`) gated behind benchmark profile.
- `testdata/fixtures/README.md` + `sample-fixture-template.json`.
- `.gitignore` updated to exclude `testdata/pdfs/`.
- `SchemaValidationResult`, `RfpSchemaLoadException` domain classes.
- Unit tests for all above.

**Review Checklist:**

- [ ] `SectionSegmenter` injects `List<HeadingStrategy>` — Spring will inject all `@Component` implementations ordered
  by `@Order`.
- [ ] `SectionSegmenter.segment()` tries TOC first, then strategies in priority order.
- [ ] Strategy skipped if it returns fewer than 3 candidates.
- [ ] `ClauseIdAssigner.assignClauseId()` is deterministic — same input = same output (unit test).
- [ ] `ClauseIdAssigner` logs WARN for fallback IDs.
- [ ] `RfpSchemaValidator` throws `RfpSchemaLoadException` (not `RuntimeException`) on schema load failure.
- [ ] `schema/rfp-schema-v1.json` is valid JSON Schema draft-07 (validate with online validator).
- [ ] All `entities.*` fields allow `null` values in schema.
- [ ] `SectionExtractionEvaluator` uses deterministic fixture assertions (title, level, page ranges, tree structure).
- [ ] No Sprint 3 acceptance check depends on manually annotated files.

---

### PR 3: `feat/sprint3-pipeline-frontend` — Pipeline Integration + SectionTree Frontend

**Contains:**

- Updated `ExtractionPipelineService` (calls segmenter, stores sections in Redis job state).
- Updated `ExtractionJob` (added `sectionsJson` field).
- Updated `RfpController` (`GET /result/{jobId}` returns sections).
- `ResultPage.tsx` (3 tabs).
- `SectionTree.tsx` component.
- Updated `rfpClient.ts` (`getRfpResult` function).
- Updated `rfp.ts` TypeScript types.

**Review Checklist:**

- [ ] Pipeline does not fail if `SectionSegmenter` returns empty list — job still COMPLETED.
- [ ] Sections serialized/deserialized correctly through Redis (UUID fields preserved).
- [ ] `SectionTree` renders without crashing when `children` is empty.
- [ ] Confidence score percentage is rounded to 0 decimal places.
- [ ] Collapsing level-1 sections hides all descendants.
- [ ] Page range "p.start–end" displayed correctly.
- [ ] `npm run build` exits 0 with zero TypeScript errors.

---

## 5) Validation & Demo Script

### Step 1: Build and Test

```bash
cd rfp-extractor
mvn clean verify
# Expected: BUILD SUCCESS, minimum 40 new unit tests, zero failures
```

### Step 2: Start Stack

```bash
docker compose up --build -d
docker compose ps
# Expected: all services healthy
```

### Step 3: Submit an RFP with Bookmarks

```bash
curl -s -X POST http://localhost:8080/api/v1/rfp/submit \
  -F "file=@/path/to/cptu-rfp-with-bookmarks.pdf" | jq .
```

Expected: `{"jobId":"<uuid>","status":"QUEUED"}`

### Step 4: Wait for Completion and Check Sections

```bash
JOB_ID="<uuid from above>"
# Wait ~15s then:
curl -s http://localhost:8080/api/v1/rfp/result/$JOB_ID | jq '.sections | length'
```

Expected: `> 0` (number of top-level sections detected)

### Step 5: Inspect Section Tree

```bash
curl -s http://localhost:8080/api/v1/rfp/result/$JOB_ID | jq '.sections[0]'
```

Expected output:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "title": "1. Background",
  "level": 1,
  "pageStart": 1,
  "pageEnd": 4,
  "children": [],
  "confidence": {
    "score": 0.95,
    "method": "BookmarkHeadingStrategy"
  }
}
```

### Step 6: Test with a Numbered-Only PDF

```bash
curl -s -X POST http://localhost:8080/api/v1/rfp/submit \
  -F "file=@/path/to/numbered-only-rfp.pdf" | jq .jobId
# Wait then:
curl -s http://localhost:8080/api/v1/rfp/result/$JOB_ID | jq '.sections[0].confidence.method'
```

Expected: `"NumberedHeadingStrategy"`

### Step 7: Schema Validation Test

```bash
# Send invalid JSON to confirm schema validator is wired
curl -s -X POST http://localhost:8080/api/v1/rfp/submit \
  -F "file=@/path/to/corrupt.pdf" | jq .
# Expected: 422 with errorCode CORRUPT
```

### Step 8: Run Evaluation Harness (Developer Only)

```bash
# From rfp-service tests directory:
mvn test -pl rfp-service -Dtest=SectionExtractionEvaluatorTest -Dtest.fixture.path=testdata/fixtures
# Expected: fixture assertions pass for the curated Sprint 3 fixture set
```

### Step 9: Frontend Section Tree

1. Open `http://localhost:3000`.
2. Upload a PDF with bookmarks.
3. Navigate to `/result/{jobId}`.
4. Click "Sections" tab.
5. Verify collapsible tree renders with confidence percentages.
6. Click a parent section — children collapse/expand.

### Performance Checks:

- 50-page PDF with bookmarks: segmentation completes in < 5s (most time is page text loading).
- `ClauseIdAssigner.assignClauseId()` for 100 calls: < 10ms total.
- `RfpSchemaValidator.validate()` on a 10KB JSON: < 50ms.
- Section tree with 30 sections renders in < 100ms in browser (React DevTools).

---

## 6) Exit Criteria (NON-NEGOTIABLE)

- [ ] `mvn clean verify` exits with code 0. Minimum 40 new unit tests. Zero failures.
- [ ] `SectionSegmenter` uses `BookmarkHeadingStrategy` for a bookmarked PDF — verified by checking `confidence.method`
  in the result JSON.
- [ ] `SectionSegmenter` skips a strategy that returns fewer than 3 headings — verified by unit test
  `shouldSkipStrategyWhenFewerThanThreeHeadingsReturned`.
- [ ] `ClauseIdAssigner.assignClauseId()` is deterministic — unit test `shouldProduceDeterministicIdForSameInputs`
  passes.
- [ ] `ClauseIdAssigner` emits WARN log for sections without numeric prefix — verified by unit test using log capture.
- [ ] `RfpSchemaValidator` throws `RfpSchemaLoadException` at startup if `schema/rfp-schema-v1.json` is missing —
  verified by temporarily renaming the file and checking startup fails.
- [ ] `schema/rfp-schema-v1.json` validates against JSON Schema draft-07 specification (
  use https://www.jsonschemavalidator.net/ or similar).
- [ ] `SectionExtractionEvaluator` passes deterministic fixture assertions for at least 5 fixture documents.
- [ ] `BanglaHeadingStrategy` detects "ধারা" prefix as level 2 — verified by unit test `shouldDetectDharaAsLevelTwo`.
- [ ] `AllCapsHeadingStrategy` rejects lines NOT surrounded by blank lines — verified by unit test.
- [ ] `GET /api/v1/rfp/result/{jobId}` returns `sections` array with at least 1 element for a typical GOB RFP — verified
  by curl demo.
- [ ] `SectionTree` component collapses children when parent is clicked — verified in browser.
- [ ] `testdata/pdfs/` is in `.gitignore` — verified by `git check-ignore testdata/pdfs/`.
- [ ] No class exceeds 250 lines. No method exceeds 20 lines. Verified during code review.
- [ ] `rfp-schema-v1.json` allows `null` for all entity fields — verified by submitting an RFP JSON with all entity
  fields set to `null` and confirming validation passes.

---

## 7) Notes: Assumptions / Open Questions

**Assumption:** Spring injects `List<HeadingStrategy>` ordered by `@Order` annotation value when `SectionSegmenter`
declares `List<HeadingStrategy> strategies` as a constructor parameter. If Spring does not respect `@Order` for list
injection, use `@Autowired List<HeadingStrategy>` with `@Order` on each bean, or explicitly order the list in a
`@Configuration` class that creates the `SectionSegmenter` bean manually.

**Assumption:** `PDOutlineItem` page resolution (getting the actual PDF page number from an outline item) is simplified
in Sprint 3 to return 0. The `SectionSegmenter` then orders sections by their heading candidate's `pageNumber` from text
scanning, not from bookmark resolution. Precise bookmark page number resolution requires navigating `PDNamedDestination`
or `PDPageDestination` — implement in Sprint 5 if needed.

**Assumption:** Real-document benchmark annotations are not available during Sprint 3 and are intentionally deferred to
`wishlist/001_wishlist.md`. This does not block Sprint 3-12 delivery because fixture-based gates are the
acceptance baseline.

**Open Question:** Should `SectionSegmenter` merge adjacent identical-level sections that span fewer than 2 lines? E.g.,
some PDFs emit "SECTION" on one line and "I" on the next. Decision: deferred to Sprint 5 (table extraction) when section
quality needs to be higher for table-to-section mapping.

**Open Question:** The `RfpSchemaValidator` loads the schema from a file path configured in `application.properties`. In
production Docker containers, this path must be inside the container. Should the schema be bundled as a classpath
resource? Decision: bundle `rfp-schema-v1.json` as a Spring Boot resource in `rfp-service/src/main/resources/schema/`
and use `ClassPathResource` for loading. Update `RfpSchemaValidator` to try classpath first, then fallback to filesystem
path.

**Assumption:** The `SectionSegmenter` page range computation for the last section is `totalPages - 1` (the last page
index, 0-based). This may be off-by-one if the PDF uses 1-based page numbers. The `PdfDocumentLoader.getPageCount()`
returns 0-based count. Use `pageCount - 1` consistently as the last page index throughout the segmenter.

**Assumption:** `TextBlock.pageNumber` is added to the domain model in Sprint 3 (not Sprint 2). This is a breaking
change to `PdfDocumentLoader.loadPageBoundingBoxes()` which must now set `pageNumber = pageIndex` on each `TextBlock`.
All existing callers (`PageClassifier`, `HeadingStyleStrategy`, `FontSizeHeadingStrategy`) must be updated.
