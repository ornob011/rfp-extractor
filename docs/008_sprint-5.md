# Sprint 5 — Table Extraction

## 0) Sprint Intent

Extract all tables from digital and mixed PDF pages, handle merged cells, detect and merge multi-page table
continuations, link each table to its parent section and nearest preceding clause, and surface results in the React UI.
Scanned-page tables are intentionally deferred to Sprint 6. By end of sprint the `ExtractTablesNode` (previously a stub)
is fully operational and the `ExtractionGraph` produces a populated `state.tables` list for every document.

---

## 1) Entry Criteria

- Sprint 4 is merged and green on CI.
- `ExtractionGraph` compiles with `ExtractTablesNode` wired in as a stub that returns an empty list.
- `PdfDocumentLoader` exists and exposes `loadPageBoundingBoxes(PDPage)` returning `List<TextBlock>` where `TextBlock`
  has `x, y, width, height, text` fields.
- `PageClassifier` correctly labels pages as `DIGITAL`, `SCANNED`, or `MIXED`.
- `Section` and `Clause` domain objects exist with `pageStart`, `pageEnd`, and `id` (UUID) fields.
- PostgreSQL is running in Docker Compose.
- JUnit 5 + AssertJ + Mockito on classpath.
- `LlmAdapter` is Resilience4j-wrapped (Sprint 1).

---

## 2) Deliverables

| #    | Deliverable                          | Type              | Location                                                       |
|------|--------------------------------------|-------------------|----------------------------------------------------------------|
| D-01 | `TableCell` domain model             | Java record/class | `rfp-core/.../domain/model/TableCell.java`                     |
| D-02 | `TableType` enum                     | Java enum         | `rfp-core/.../domain/model/TableType.java`                     |
| D-03 | `ExtractionConfidence` value object  | Java class        | `rfp-core/.../domain/model/ExtractionConfidence.java`          |
| D-04 | `TableExtractionResult` domain model | Java class        | `rfp-core/.../domain/model/TableExtractionResult.java`         |
| D-05 | `LatticeTableExtractor`              | Java class        | `rfp-service/.../adapter/table/LatticeTableExtractor.java`     |
| D-06 | `StreamTableExtractor`               | Java class        | `rfp-service/.../adapter/table/StreamTableExtractor.java`      |
| D-07 | `TableExtractor` (orchestrator)      | Java class        | `rfp-service/.../adapter/table/TableExtractor.java`            |
| D-08 | `TableContinuationDetector`          | Java class        | `rfp-service/.../adapter/table/TableContinuationDetector.java` |
| D-09 | `TableSectionLinker`                 | Java class        | `rfp-service/.../adapter/table/TableSectionLinker.java`        |
| D-10 | `TableTypeClassifier`                | Java class        | `rfp-service/.../adapter/table/TableTypeClassifier.java`       |
| D-11 | `ExtractTablesNode` (full impl)      | Java class        | `rfp-service/.../agent/ExtractTablesNode.java`                 |
| D-12 | `TableViewer.tsx`                    | React component   | `rfp-frontend/src/components/TableViewer.tsx`                  |
| D-13 | `ResultPage.tsx` — Tables tab        | React update      | `rfp-frontend/src/pages/ResultPage.tsx`                        |
| D-14 | Unit tests                           | Java test classes | `rfp-service/src/test/java/.../adapter/table/`                 |
| D-15 | `ExtractionState.tables` type update | Refactor          | `rfp-service/.../agent/ExtractionState.java`                   |

**Breaking Change Note:** `ExtractionState.tables` is typed `List<Table>` in Sprint 4 (placeholder).
This sprint changes it to `List<TableExtractionResult>`. The placeholder `Table.java` domain model
is replaced by `TableExtractionResult.java`. Update `ExtractionState`, `FinalizeNode`,
`RfpDocumentAssembler`, and all tests that reference `state.getTables()`.

---

## 3) Work Breakdown

### Epic A — Domain Models

---

#### Story A-1: Define `TableCell`, `TableType`, `ExtractionConfidence`, and `TableExtractionResult`

**Description:** Introduce all value types needed to represent an extracted table. These live in `rfp-core` (no Spring
dependencies). Downstream adapters and the agent graph depend on these types.

**Acceptance Criteria (Gherkin):**

```gherkin
Scenario: Build a TableCell with default spans
  Given a TableCell builder with row=1, col=2, value="Project Name"
  When I call .build() without setting rowspan or colspan
  Then rowspan equals 1
  And colspan equals 1
  And isHeader equals false

Scenario: Build a TableExtractionResult
  Given a non-empty list of TableCell objects
  When I construct a TableExtractionResult with them
  Then tableId is a non-null UUID
  And confidence.method is one of "lattice", "stream", "ocr_llm_reconstruct"
  And type is one of the TableType enum values
```

**Interfaces / Contracts:**

```java
// rfp-core/.../domain/model/TableCell.java
@Data
@Builder
public class TableCell {
    private int row;
    private int col;
    private String value;
    @Builder.Default
    private final int rowspan = 1;
    @Builder.Default
    private final int colspan = 1;
    private boolean isHeader;
}

// rfp-core/.../domain/model/TableType.java
public enum TableType {
    DELIVERABLES, EVALUATION, PAYMENT, STAFFING, SCHEDULE, OTHER
}

// rfp-core/.../domain/model/ExtractionConfidence.java
@Data
@Builder
public class ExtractionConfidence {
    private double score;         // 0.0 – 1.0
    private String method;        // "lattice" | "stream" | "ocr_llm_reconstruct"
}

// rfp-core/.../domain/model/TableExtractionResult.java
@Data
@Builder
public class TableExtractionResult {
    private UUID tableId;
    private UUID sectionId;       // set by TableSectionLinker; null until linked
    private UUID clauseId;        // set by TableSectionLinker; null until linked
    private int pageStart;
    private int pageEnd;
    private String caption;
    private TableType type;
    private List<String> headers;
    private List<TableCell> grid;
    private ExtractionConfidence confidence;
}
```

**Implementation Plan:**

1. Create `rfp-core/src/main/java/com/dsi/rfp/domain/model/TableCell.java` — add Lombok `@Data @Builder`, ensure
   `@Builder.Default` on `rowspan` and `colspan`.
2. Create `rfp-core/src/main/java/com/dsi/rfp/domain/model/TableType.java` — plain enum, no dependencies.
3. Create `rfp-core/src/main/java/com/dsi/rfp/domain/model/ExtractionConfidence.java` — `@Data @Builder`, two fields.
4. Create `rfp-core/src/main/java/com/dsi/rfp/domain/model/TableExtractionResult.java` — `@Data @Builder`, import UUID
   from `java.util`.
5. Verify `rfp-core` compiles with `mvn compile -pl rfp-core`.

**Dependencies:** Lombok on `rfp-core` classpath (already present from Sprint 2).

**Risks + Mitigations:**

| Risk                                                                                            | Mitigation                                                                                           |
|-------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------|
| Lombok `@Builder.Default` omitted causes `rowspan=0` in deserialized objects                    | Unit test asserts default values; CI catches on first run                                            |
| `TableExtractionResult.sectionId` null until linker runs causes NPE in downstream serialization | Jackson `@JsonInclude(NON_NULL)` on the class; document in Javadoc that null is valid pre-link state |

**Test Plan:**

- `TableCellTest.java`: assert `@Builder.Default` values for rowspan/colspan; assert isHeader=false by default.
- `TableExtractionResultTest.java`: build a result and assert non-null UUID generated if `tableId` not set (use
  `UUID.randomUUID()` as default via `@Builder.Default`).

**Observability:** No logging needed for value objects.

**Story Points:** 3

---

### Epic B — Lattice Table Extraction

---

#### Story B-1: Implement `LatticeTableExtractor`

**Description:** Use PDFBox path operations to detect horizontal and vertical rule lines on a page, build a grid of cell
bounding boxes from their intersections, extract text per cell, and detect merged cells where dividing lines are absent.

**Acceptance Criteria (Gherkin):**

```gherkin
Scenario: Grid table with no merged cells
  Given a PDPage containing a 3-row x 4-column grid with fully drawn borders
  When LatticeTableExtractor.extractFromPage(doc, pageNum, loader) is called
  Then it returns exactly one TableExtractionResult
  And the grid has 12 TableCell objects
  And every cell has rowspan=1 and colspan=1
  And confidence.method equals "lattice"
  And confidence.score is between 0.7 and 1.0

Scenario: Page with fewer than 4 detected lines
  Given a PDPage with only 2 horizontal lines and 1 vertical line
  When LatticeTableExtractor.extractFromPage(doc, pageNum, loader) is called
  Then it returns an empty list

Scenario: Table with a merged header cell spanning 2 columns
  Given a PDPage with a top cell spanning columns 1 and 2 (no vertical divider between them in row 1)
  When LatticeTableExtractor.extractFromPage(doc, pageNum, loader) is called
  Then the merged cell has colspan=2 and rowspan=1
  And the total cell count is (rowCount * colCount) - 1 (one cell replaced by merged)
```

**Interfaces / Contracts:**

```java
// rfp-service/.../adapter/table/LatticeTableExtractor.java
@Component
public class LatticeTableExtractor {

    private final PdfDocumentLoader loader;

    // Constructor injection
    public LatticeTableExtractor(PdfDocumentLoader loader) { ...}

    /**
     * Extract all lattice-style tables from a single page.
     * Returns empty list when < 4 grid lines are detected (signals: not a lattice table).
     */
    public List<TableExtractionResult> extractFromPage(PDDocument doc, int pageNum) { ...}

    // --- private helpers ---
    private List<Float> detectHorizontalLines(PDPage page) { ...}

    private List<Float> detectVerticalLines(PDPage page) { ...}

    private List<CellBounds> buildCellBounds(List<Float> hLines, List<Float> vLines) { ...}

    private String extractCellText(CellBounds bounds, List<TextBlock> pageBlocks) { ...}

    private int computeColspan(int colIdx, int rowIdx, List<Float> vLines, Set<LineSegment> presentVLines) { ...}

    private int computeRowspan(int rowIdx, int colIdx, List<Float> hLines, Set<LineSegment> presentHLines) { ...}

    private double computeConfidence(int detectedLines, int expectedLines) { ...}

    // Private inner records — stay within 250-line class budget
    private record CellBounds(float x0, float y0, float x1, float y1) {
    }

    private record LineSegment(float x0, float y0, float x1, float y1) {
    }
}
```

**Implementation Plan (step by step):**

1. **Line detection via PDFBox path walking.** PDFBox exposes `PDPageContentStream` for writing but for reading use
   `PDFStreamEngine` subclass. Create a private inner class `LineExtractorEngine extends PDFStreamEngine`. Override
   `processOperator`. Intercept path operations: operator `"l"` (lineto) and `"m"` (moveto) give segment endpoints;
   `"S"` (stroke) commits. Collect line segments into two lists: horizontal (|deltaY| < 2.0f) and vertical (|deltaX| <
   2.0f). Deduplicate by rounding coordinates to nearest integer.

2. **Grid construction.** After collecting `hLines` (sorted ascending by Y) and `vLines` (sorted ascending by X): total
   line count = hLines.size() + vLines.size(). If total < 4, return empty list. Build
   `(hLines.size()-1) × (vLines.size()-1)` cell bounding boxes. Each box: `x0=vLines[col]`, `x1=vLines[col+1]`,
   `y0=hLines[row]`, `y1=hLines[row+1]`.

3. **Cell text extraction.** Call `loader.loadPageBoundingBoxes(pageNum)` to get `List<TextBlock>`. For each cell
   bounding box, collect all `TextBlock` objects whose centroid `(x + w/2, y + h/2)` falls within the cell bounds. Sort
   collected blocks by y ascending then x ascending. Join their text with a single space. Assign to `TableCell.value`.

4. **Merged cell detection.** For each interior vertical line between column `c` and `c+1` at row `r`: check if the line
   segment spanning `[hLines[r], hLines[r+1]]` at x=`vLines[c+1]` is present in `presentVLines`. Absent → cells at
   `(r, c)` and `(r, c+1)` are merged → `colspan += 1`, mark `(r, c+1)` as absorbed. Apply same logic horizontally for
   rowspan using horizontal lines.

5. **Confidence calculation.** `expectedLines = (colCount+1) + (rowCount+1)`.
   `confidence = Math.min(1.0, (double) detectedLines / expectedLines)`. If < 0.4 → still return result but confidence
   will be low (ScoreConfidenceNode handles it).

6. **Header detection.** First row cells: `isHeader = true` when the first row's background shading or bold font weight
   is detected OR when `rowIdx == 0` (fallback heuristic).

7. **Table type and caption.** Caption: look for a `TextBlock` immediately above the top horizontal line (within 20pt).
   Delegate type classification to `TableTypeClassifier`.

8. **Return.** Build `TableExtractionResult` with `pageStart = pageNum`, `pageEnd = pageNum`,
   `tableId = UUID.randomUUID()`.

**Dependencies:** `PdfDocumentLoader` (Sprint 2), `PDFBox 3.x` on classpath, `TableTypeClassifier` (Story B-3).

**Risks + Mitigations:**

| Risk                                                                                          | Mitigation                                                                                                   |
|-----------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------|
| PDFBox path operator names differ between PDF spec versions                                   | Test on 5 known RFP samples; log unrecognized operators at DEBUG level                                       |
| Line coordinates in PDF user space (Y increases upward) vs Java screen (Y increases downward) | Normalize all Y coordinates: `normalizedY = page.getMediaBox().getHeight() - rawY` immediately on collection |
| Very dense tables (50+ rows) produce >250 cells — slow text extraction                        | Cap at 200 cells per table; log.warn if exceeded; return partial result                                      |

**Test Plan — `LatticeTableExtractorTest.java`:**

```
shouldReturnEmptyListWhenFewerThanFourLinesDetected
shouldExtractThreeByFourGridCorrectly
shouldAssignColspanTwoForMergedHeaderCell
shouldMarkFirstRowCellsAsHeaders
shouldComputeConfidenceFromLineRatio
```

Mock `PdfDocumentLoader.loadPageBoundingBoxes` to return fixed `List<TextBlock>`. Use a synthetic `PDDocument` built
programmatically (PDFBox API) with known line coordinates drawn via `PDPageContentStream`.

**Observability:**

```java
log.debug("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=DEBUG [LatticeTableExtractor] Page={} hLines={} vLines={} cells={}",
          pageNum, hLines.size(),vLines.

size(),grid.

size());
    log.

warn("[LatticeTableExtractor] Page={} cell cap exceeded; truncated to 200",pageNum);
```

**Story Points:** 13

---

#### Story B-2: Implement `StreamTableExtractor`

**Description:** When lattice detection fails (< 4 lines), fall back to stream-based column detection using text
bounding box X-coordinate gap analysis. All cells get `rowspan=1, colspan=1`.

**Acceptance Criteria (Gherkin):**

```gherkin
Scenario: Two-column table detected from text bounding boxes
  Given a page with 6 text blocks arranged in 3 rows and 2 columns with a visible gap at x=280
  When StreamTableExtractor.extractFromPage(doc, pageNum) is called
  Then it returns one TableExtractionResult with 6 cells (3 rows x 2 cols)
  And every cell has rowspan=1 and colspan=1
  And confidence.score equals 0.6
  And confidence.method equals "stream"

Scenario: Page with no column gap detected
  Given a page where all text x-positions are within a continuous band (no gap > 3% of page width)
  When StreamTableExtractor.extractFromPage(doc, pageNum) is called
  Then it returns an empty list
```

**Interfaces / Contracts:**

```java
// rfp-service/.../adapter/table/StreamTableExtractor.java
@Component
public class StreamTableExtractor {

    private final PdfDocumentLoader loader;

    public StreamTableExtractor(PdfDocumentLoader loader) { ...}

    /**
     * Returns empty list when no column gap is detected (page is not tabular).
     * Confidence is always 0.6 — stream mode is approximate.
     */
    public List<TableExtractionResult> extractFromPage(PDDocument doc, int pageNum) { ...}

    private List<Float> detectColumnBoundaries(List<TextBlock> blocks, float pageWidth) { ...}

    private int assignColumnIndex(float x, List<Float> boundaries) { ...}

    private List<List<TextBlock>> groupIntoRows(List<TextBlock> blocks, float yTolerancePts) { ...}

    private List<TableCell> buildGrid(List<List<TextBlock>> rows, int colCount) { ...}
}
```

**Implementation Plan:**

1. Call `loader.loadPageBoundingBoxes(pageNum)` → `List<TextBlock> blocks`.
2. Get `pageWidth` from `PDPage.getMediaBox().getWidth()`.
3. **Column boundaries:** collect all unique `TextBlock.x` values, sort ascending. Iterate sorted values; when
   `sortedX[i+1] - sortedX[i] > pageWidth * 0.03` → boundary at `(sortedX[i] + sortedX[i+1]) / 2`. If no boundary
   found → return empty list.
4. **Row grouping:** sort blocks by Y ascending. Group blocks where `|block.y - group.referenceY| <= 3.0f` into the same
   row. Each group is a row.
5. **Grid assembly:** for each row-group, assign each `TextBlock` to a column by finding which boundary interval its X
   falls in. Build `TableCell(row=rowIdx, col=colIdx, value=block.text, rowspan=1, colspan=1)`.
6. **Headers:** first row `isHeader=true`.
7. **Confidence:** always `ExtractionConfidence.builder().score(0.6).method("stream").build()`.
8. Return `TableExtractionResult` with `pageStart=pageNum, pageEnd=pageNum, tableId=UUID.randomUUID()`.

**Dependencies:** `PdfDocumentLoader` (Sprint 2).

**Risks + Mitigations:**

| Risk                                                                      | Mitigation                                                                         |
|---------------------------------------------------------------------------|------------------------------------------------------------------------------------|
| Multi-line cell values cause one logical cell to span multiple row-groups | Accept limitation; note in Javadoc; scanned/complex tables go to Sprint 6 LLM path |
| 3% gap threshold misses narrow tables                                     | Make threshold configurable: `app.table.stream.column-gap-ratio=0.03`              |

**Test Plan — `StreamTableExtractorTest.java`:**

```
shouldReturnEmptyWhenNoColumnGapDetected
shouldDetectTwoColumnTableFromBoundingBoxes
shouldGroupTextBlocksIntoRowsByYProximity
shouldAssignStreamConfidence060
```

Mock `loader.loadPageBoundingBoxes` to return hand-crafted `TextBlock` lists with known x/y coordinates.

**Observability:**

```java
log.debug("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=DEBUG [StreamTableExtractor] Page={} columnBoundaries={} rows={} cells={}",
          pageNum, boundaries.size(),rows.

size(),grid.

size());
```

**Story Points:** 8

---

#### Story B-3: Implement `TableTypeClassifier`

**Description:** Classify each extracted table into one of the `TableType` enum values using keyword scoring on column
headers and the caption string.

**Acceptance Criteria (Gherkin):**

```gherkin
Scenario: Headers contain "deliverable" and "milestone"
  Given headers = ["Deliverable", "Milestone", "Due Date"]
  When TableTypeClassifier.classify(headers, caption) is called
  Then the result is DELIVERABLES

Scenario: Headers contain "weight" and caption contains "evaluation"
  Given headers = ["Criteria", "Weight", "Score"]
  And caption = "Evaluation Criteria Table"
  When TableTypeClassifier.classify(headers, caption) is called
  Then the result is EVALUATION

Scenario: No matching keywords
  Given headers = ["Item", "Remarks"]
  And caption = ""
  When TableTypeClassifier.classify(headers, caption) is called
  Then the result is OTHER
```

**Interfaces / Contracts:**

```java
// rfp-service/.../adapter/table/TableTypeClassifier.java
@Component
public class TableTypeClassifier {

    /**
     * @param headers list of header cell values (may be empty)
     * @param caption table caption string (may be null or blank)
     * @return TableType — never null, defaults to OTHER
     */
    public TableType classify(List<String> headers, String caption) { ...}

    private int score(String combined, List<String> keywords) { ...}
}
```

**Implementation Plan:**

1. Lowercase all headers; join with space. Append lowercased caption. Combined string = input for scoring.
2. Score maps (keyword → type):
    - `DELIVERABLES`: `["deliverable", "milestone", "output", "work package"]`
    - `EVALUATION`: `["evaluation", "criteria", "weight", "score", "mark", "technical"]`
    - `PAYMENT`: `["payment", "invoice", "amount", "fee", "bill"]`
    - `STAFFING`: `["staff", "team", "personnel", "resource", "expert", "cv"]`
    - `SCHEDULE`: `["schedule", "timeline", "gantt", "phase", "activity"]`
3. For each keyword present in combined string: add 1 to that type's score.
4. Return the type with the highest score. If tie or all zero → `OTHER`.
5. Class is a plain `@Component` with no constructor arguments. Max 60 lines.

**Dependencies:** None beyond Java std lib.

**Test Plan — `TableTypeClassifierTest.java`:**

```
shouldClassifyAsDeliverablesWhenHeadersContainMilestone
shouldClassifyAsEvaluationWhenCaptionContainsEvaluation
shouldClassifyAsOtherWhenNoKeywordsMatch
shouldBreakTiesByReturningFirstHighestScore
```

**Story Points:** 3

---

### Epic C — Table Orchestration

---

#### Story C-1: Implement `TableExtractor` (orchestrator)

**Description:** Orchestrate lattice → stream fallback per page for DIGITAL and MIXED pages. Skip SCANNED pages (return
empty stub). Tag results with method used.

**Acceptance Criteria (Gherkin):**

```gherkin
Scenario: DIGITAL page — lattice succeeds
  Given a DIGITAL page where LatticeTableExtractor returns a non-empty list
  When TableExtractor.extractFromDocument(doc, pageClassifications) is called
  Then the returned table for that page has confidence.method = "lattice"
  And StreamTableExtractor is never called for that page

Scenario: DIGITAL page — lattice fails, stream succeeds
  Given a DIGITAL page where LatticeTableExtractor returns an empty list
  And StreamTableExtractor returns a non-empty list
  When TableExtractor.extractFromDocument(doc, pageClassifications) is called
  Then the returned table has confidence.method = "stream"

Scenario: SCANNED page is skipped
  Given page 3 is classified SCANNED
  When TableExtractor.extractFromDocument(doc, pageClassifications) is called
  Then no tables are returned for page 3
  And no extractor is called for page 3
```

**Interfaces / Contracts:**

```java
// rfp-service/.../adapter/table/TableExtractor.java
@Component
public class TableExtractor {

    private final LatticeTableExtractor latticeExtractor;
    private final StreamTableExtractor streamExtractor;

    public TableExtractor(LatticeTableExtractor latticeExtractor,
                          StreamTableExtractor streamExtractor) { ...}

    /**
     * @param doc open PDDocument
     * @param pageClassifications Map<Integer, PageClass> where key is 0-based page index
     * @return all tables found across all non-SCANNED pages; never null, may be empty
     */
    public List<TableExtractionResult> extractFromDocument(
        PDDocument doc,
        Map<Integer, PageClass> pageClassifications) { ...}

    private List<TableExtractionResult> extractFromPage(PDDocument doc, int pageNum) { ...}
}
```

**Implementation Plan:**

1. Iterate pages 0 to `doc.getNumberOfPages() - 1`.
2. Lookup `pageClassifications.get(pageNum)`. If `SCANNED` → `log.debug` and continue.
3. For `DIGITAL` or `MIXED`: call `extractFromPage(doc, pageNum)`.
4. `extractFromPage`: call `latticeExtractor.extractFromPage(doc, pageNum)`. If result list is non-empty → return it.
   Else call `streamExtractor.extractFromPage(doc, pageNum)` → return that result.
5. Accumulate all results into a single `List<TableExtractionResult>`.
6. Class must stay under 100 lines.

**Dependencies:** `LatticeTableExtractor` (B-1), `StreamTableExtractor` (B-2), `PageClass` enum (Sprint 2).

**Risks + Mitigations:**

| Risk                                                                       | Mitigation                                                                                         |
|----------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| Both lattice and stream return results for the same page (double-counting) | Return lattice result if non-empty; stream is only called if lattice is empty — mutually exclusive |

**Test Plan — `TableExtractorTest.java`:**

```
shouldUseLatticeWhenLatticeReturnsResults
shouldFallBackToStreamWhenLatticeReturnsEmpty
shouldSkipScannedPages
shouldReturnEmptyListWhenNoTablesFound
```

Mock both sub-extractors. Verify interaction counts.

**Observability:**

```java
log.info("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=INFO [TableExtractor] JobId={} totalPages={} digitalPages={} tablesFound={}",
         jobId, totalPages, digitalCount, tables.size());
```

**Story Points:** 5

---

### Epic D — Multi-Page Table Continuation

---

#### Story D-1: Implement `TableContinuationDetector`

**Description:** After all pages are processed, detect tables that span page boundaries. Merge their grids, re-index row
numbers, and remove duplicate header rows.

**Acceptance Criteria (Gherkin):**

```gherkin
Scenario: Two fragments meet continuation criteria — all three signals present
  Given tableA on page 3 with no "Total" row and headers ["Item", "Cost"]
  And tableB on page 4 starting at the top with the same headers ["Item", "Cost"]
  When TableContinuationDetector.detect(tables) is called
  Then the two tables are merged into one with pageStart=3 and pageEnd=4
  And the merged grid row count = tableA.rowCount + tableB.rowCount - 1 (duplicate header removed)
  And the total table count in the returned list is reduced by 1

Scenario: Two fragments that do not meet criteria — only one signal
  Given tableA on page 3 and tableB on page 4 with completely different headers (Levenshtein > 3)
  And tableA has a "Total" footer row
  When TableContinuationDetector.detect(tables) is called
  Then the tables remain separate
  And the returned list has the same count as input

Scenario: Three-page spanning table
  Given fragments on pages 5, 6, 7 each meeting continuation criteria with the next
  When TableContinuationDetector.detect(tables) is called
  Then all three are merged into one table spanning pages 5–7
```

**Interfaces / Contracts:**

```java
// rfp-service/.../adapter/table/TableContinuationDetector.java
@Component
public class TableContinuationDetector {

    // Apache Commons Text for Levenshtein distance
    private final LevenshteinDistance levenshtein = LevenshteinDistance.getDefaultInstance();

    /**
     * @param tables sorted by pageStart ascending (caller's responsibility)
     * @return new list with multi-page fragments merged; input list is not mutated
     */
    public List<TableExtractionResult> detect(List<TableExtractionResult> tables) { ...}

    private boolean isContinuation(TableExtractionResult prev, TableExtractionResult next) { ...}

    private TableExtractionResult merge(TableExtractionResult prev, TableExtractionResult next) { ...}

    private boolean hasFooterRow(TableExtractionResult table) { ...}

    private int headerSimilarityScore(List<String> headersA, List<String> headersB) { ...}

    private List<TableCell> reindexRows(List<TableCell> cells, int rowOffset) { ...}
}
```

**Implementation Plan:**

1. Sort input list by `pageStart` ascending (use `Comparator.comparingInt(TableExtractionResult::getPageStart)`).
2. Iterate with index `i`. For each consecutive pair `(tables[i], tables[i+1])`:
    - Check signal 1: `tables[i].pageEnd == tables[i+1].pageStart - 1` (adjacent pages).
    - Check signal 2: `!hasFooterRow(tables[i])`. `hasFooterRow` returns true if any cell in the last row has value
      matching regex `(?i)\b(total|sub.?total|grand total)\b`.
    - Check signal 3: `headerSimilarityScore(tables[i].headers, tables[i+1].headers) >= 1`. `headerSimilarityScore`
      counts pairs of corresponding headers where `levenshtein.apply(a, b) < 3`.
    - If at least 2 of 3 signals are true → merge.
3. **Merge logic:** `merge(prev, next)`:
    - Combined grid = `prev.grid` + `reindexRows(next.grid without duplicate header, prev.maxRowIndex + 1)`.
    - If `next.grid` starts with a row whose cells match `prev.headers` (Levenshtein < 3 each) → skip that row.
    - Set `pageEnd = next.pageEnd`.
    - Keep `tableId` of `prev`.
    - Set `confidence.score = min(prev.confidence.score, next.confidence.score)`.
4. Return merged list. Continue iterating from the merged result.

**Dependencies:** `apache-commons-text` on classpath (add to `rfp-service/pom.xml` if not present:
`org.apache.commons:commons-text:1.12.0`).

**Risks + Mitigations:**

| Risk                                                | Mitigation                                                                                                   |
|-----------------------------------------------------|--------------------------------------------------------------------------------------------------------------|
| False positive merges two unrelated adjacent tables | Require signals 1+2 or 1+3 — adjacency alone is never enough; log each merge decision at INFO with reasoning |
| Infinite loop on circular merge condition           | Input is a finite list; loop is linear; no recursion                                                         |

**Test Plan — `TableContinuationDetectorTest.java`:**

```
shouldMergeWhenAllThreeSignalsPresent
shouldMergeWhenTwoOfThreeSignalsPresent
shouldNotMergeWhenOnlyOneSignalPresent
shouldRemoveDuplicateHeaderRowOnMerge
shouldMergeThreePageSpanningTable
shouldReindexRowNumbersAfterMerge
```

Build `TableExtractionResult` objects with known grids. Do not mock Levenshtein — use real implementation.

**Observability:**

```java
log.info("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=INFO [TableContinuationDetector] Merged tables: pageStart={} pageEnd={} signals=[adjacent={}, noFooter={}, sameHeaders={}]",
         prev.getPageStart(),next.

getPageEnd(),signal1,signal2,signal3);
```

**Story Points:** 8

---

### Epic E — Section & Clause Linking

---

#### Story E-1: Implement `TableSectionLinker`

**Description:** For each extracted table, find the `Section` whose page range contains the table's `pageStart`. Also
find the nearest preceding `Clause` on the same page. Set `sectionId` and `clauseId` on the table.

**Acceptance Criteria (Gherkin):**

```gherkin
Scenario: Table falls within a section's page range
  Given a table with pageStart=5
  And a Section with pageStart=4 and pageEnd=7 and id=UUID("aaa")
  When TableSectionLinker.link(tables, sections, clauses) is called
  Then the table's sectionId equals UUID("aaa")

Scenario: Table has a preceding clause on its page
  Given a table with pageStart=5
  And a Clause on page 5 at clauseStart=5 with id=UUID("bbb")
  And another Clause on page 6 at clauseStart=6
  When TableSectionLinker.link(tables, sections, clauses) is called
  Then the table's clauseId equals UUID("bbb")
  And not the clause on page 6

Scenario: Table has no matching section
  Given a table with pageStart=99 where no section covers page 99
  When TableSectionLinker.link(tables, sections, clauses) is called
  Then the table's sectionId remains null
  And a WARN log is emitted
```

**Interfaces / Contracts:**

```java
// rfp-service/.../adapter/table/TableSectionLinker.java
@Component
public class TableSectionLinker {

    /**
     * Mutates the sectionId and clauseId fields of each TableExtractionResult.
     * Returns the same list (for chaining convenience).
     */
    public List<TableExtractionResult> link(
        List<TableExtractionResult> tables,
        List<Section> sections,
        List<Clause> clauses) { ...}

    private Optional<UUID> findSectionId(int tablePage, List<Section> sections) { ...}

    private Optional<UUID> findPrecedingClauseId(int tablePage, List<Clause> clauses) { ...}
}
```

**Implementation Plan:**

1. For each `table` in `tables`:
    - `findSectionId(table.pageStart, sections)`: iterate sections, return `Optional.of(section.id)` where
      `section.pageStart <= table.pageStart && table.pageStart <= section.pageEnd`. If multiple match (nested
      sections) → return innermost (highest `pageStart`). If none → `Optional.empty()`, emit `log.warn`.
    - `findPrecedingClauseId(table.pageStart, clauses)`: filter clauses with `clauseStart <= table.pageStart`, sort by
      `clauseStart` descending, return `Optional.of(first.id)`.
    - Apply results: `table.setSectionId(sectionOpt.orElse(null))`, `table.setClauseId(clauseOpt.orElse(null))`.
2. Return mutated list.
3. Class: under 80 lines.

**Dependencies:** `Section` and `Clause` domain models (Sprint 3).

**Risks + Mitigations:**

| Risk                                                            | Mitigation                                                              |
|-----------------------------------------------------------------|-------------------------------------------------------------------------|
| Section page ranges overlap (nested sections) — ambiguous match | Always prefer innermost (highest `pageStart`) — consistently documented |

**Test Plan — `TableSectionLinkerTest.java`:**

```
shouldSetSectionIdWhenTablePageIsWithinSectionRange
shouldSetNullSectionIdAndLogWarnWhenNoSectionCoversPage
shouldSelectNearestPrecedingClause
shouldIgnoreClausesAfterTablePage
```

Use builder-constructed domain objects. No mocking needed.

**Story Points:** 5

---

### Epic F — Agent Graph Integration

---

#### Story F-1: Implement `ExtractTablesNode` (full, replacing Sprint 4 stub)

**Description:** Wire `TableExtractor`, `TableContinuationDetector`, `TableSectionLinker` into the LangGraph4J node.
Update `ExtractionState.tables`. Replace the no-op stub from Sprint 4.

**Acceptance Criteria (Gherkin):**

```gherkin
Scenario: Node runs successfully on a digital document
  Given ExtractionState has a non-empty sections list and pageClassifications map
  And TableExtractor returns 3 tables
  And TableContinuationDetector merges 2 into 1 (net 2 tables)
  When ExtractTablesNode.execute(state) is called
  Then state.tables has 2 TableExtractionResult objects
  And each table has a non-null sectionId (assuming sections cover the pages)
  And state.errors does not contain any table-related entries

Scenario: Node handles extractor throwing RuntimeException
  Given TableExtractor.extractFromDocument throws a RuntimeException
  When ExtractTablesNode.execute(state) is called
  Then state.tables is empty
  And state.errors contains an entry with component="table_extraction"
  And the node does not rethrow the exception (graph continues)
```

**Interfaces / Contracts:**

```java
// rfp-service/.../agent/ExtractTablesNode.java
@Component
public class ExtractTablesNode implements NodeAction<ExtractionState> {

    private final TableExtractor tableExtractor;
    private final TableContinuationDetector continuationDetector;
    private final TableSectionLinker sectionLinker;

    public ExtractTablesNode(TableExtractor tableExtractor,
                             TableContinuationDetector continuationDetector,
                             TableSectionLinker sectionLinker) { ...}

    @Override
    public ExtractionState execute(ExtractionState state) { ...}
}
```

**Implementation Plan:**

1. In `execute(state)`:
    - Open `PDDocument` from `state.documentPath` using `Loader.loadPDF(new File(state.documentPath))` (PDFBox 3.x).
    - Call `tableExtractor.extractFromDocument(doc, state.pageClassifications)` → `rawTables`.
    - Call `continuationDetector.detect(rawTables)` → `mergedTables` (sort by pageStart first).
    - Call `sectionLinker.link(mergedTables, state.sections, state.clauses)` → `linkedTables`.
    - Set `state.tables = linkedTables`.
    - Close `PDDocument` in `finally` block.
2. Do not wrap the body in `try/catch(Exception e)`. Let failures propagate and map them via the global exception
   handler (`@RestControllerAdvice` + typed `@ExceptionHandler` methods).
3. `state.pageClassifications` is a `Map<Integer, PageClass>` populated by `ClassifyPagesNode` in Sprint 2.

**Dependencies:** `TableExtractor` (C-1), `TableContinuationDetector` (D-1), `TableSectionLinker` (E-1),
`ExtractionState` (Sprint 4), `PDFBox Loader`.

**Test Plan — `ExtractTablesNodeTest.java`:**

```
shouldPopulateStateTables
shouldHandleExtractorExceptionGracefully
shouldCallContinuationDetectorAfterExtractor
shouldCallSectionLinkerAfterContinuationDetector
```

Mock all three collaborators. Assert `state.tables` and `state.errors` contents.

**Observability:**

```java
log.info("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=INFO [ExtractTablesNode] JobId={} rawTables={} afterMerge={} withSectionLinks={}",
         state.jobId, rawTables.size(),mergedTables.

size(),linkedWithSections);
```

**Story Points:** 5

---

### Epic G — Frontend: TableViewer

---

#### Story G-1: Build `TableViewer.tsx` and integrate in `ResultPage.tsx`

**Description:** Render a `TableExtractionResult` as an interactive grid with merged cell support (CSS grid
`gridColumn: span N` / `gridRow: span N`), confidence badge, and table type chip. Integrate in ResultPage.tsx as the "
Tables" tab.

**Acceptance Criteria (Gherkin):**

```gherkin
Scenario: Render a 3x3 table with a merged header
  Given a TableExtractionResult with 8 cells (one header spanning 2 columns)
  When TableViewer renders the result
  Then the merged cell has CSS style gridColumn="span 2"
  And regular cells have no span style
  And the header row has a different background color

Scenario: Confidence badge shows method
  Given a table with confidence.score=0.85 and confidence.method="lattice"
  When TableViewer renders
  Then a badge reading "lattice" is visible
  And a progress-bar or numeric label showing "85%" confidence is visible

Scenario: ResultPage Tables tab shows count
  Given a result with 3 tables
  When the user clicks the "Tables" tab
  Then a tab label shows "Tables (3)"
  And all 3 TableViewer instances are rendered
```

**Interfaces / Contracts (TypeScript):**

```typescript
// rfp-frontend/src/types/table.ts
export interface TableCell {
    row: number;
    col: number;
    value: string;
    rowspan: number;
    colspan: number;
    isHeader: boolean;
}

export interface ExtractionConfidence {
    score: number;
    method: 'lattice' | 'stream' | 'ocr_llm_reconstruct';
}

export interface TableExtractionResult {
    tableId: string;
    sectionId: string | null;
    clauseId: string | null;
    pageStart: number;
    pageEnd: number;
    caption: string | null;
    type: 'DELIVERABLES' | 'EVALUATION' | 'PAYMENT' | 'STAFFING' | 'SCHEDULE' | 'OTHER';
    headers: string[];
    grid: TableCell[];
    confidence: ExtractionConfidence;
}

// rfp-frontend/src/components/TableViewer.tsx
interface TableViewerProps {
    table: TableExtractionResult;
}

export function TableViewer({table}: TableViewerProps): JSX.Element { ...
}
```

**Implementation Plan (TableViewer.tsx):**

1. Compute `maxRow = Math.max(...grid.map(c => c.row))`, `maxCol = Math.max(...grid.map(c => c.col))`.
2. Render a `<div>` with `display: grid`, `gridTemplateColumns: repeat(${maxCol+1}, minmax(100px, 1fr))`.
3. Sort `grid` by `row` ascending, then `col` ascending.
4. For each cell: render a `<div>` with:
    - `gridColumn: ${cell.col + 1} / span ${cell.colspan}`
    - `gridRow: ${cell.row + 1} / span ${cell.rowspan}`
    - Background: `cell.isHeader ? 'bg-slate-700 text-white font-semibold' : 'bg-white'`
    - Border: `border border-slate-300`
    - Padding: `p-2 text-sm`
5. Above grid: render caption, type chip (Tailwind badge), confidence badge.
6. Confidence badge: method label + `w-full bg-gray-200 rounded-full h-1.5` progress bar filled to `score * 100%`.
7. Method color: `lattice=green-500`, `stream=yellow-500`, `ocr_llm_reconstruct=orange-500`.

**Implementation Plan (ResultPage.tsx update):**

1. Add "Tables" tab to existing tab list. Label: `Tables (${tables.length})`.
2. Import `TableViewer` and `TableExtractionResult`.
3. Map `rfpResult.tables` → `<TableViewer key={t.tableId} table={t} />` inside a `<div className="space-y-6">`.
4. Show empty state `<p>No tables extracted</p>` if `tables.length === 0`.

**Dependencies:** `rfpResult` type from existing API response type.

**Risks + Mitigations:**

| Risk                                                 | Mitigation                                                                                               |
|------------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| Overlapping grid areas when rowspan+colspan conflict | Sort cells and place them in grid — CSS grid handles overlap gracefully; add `overflow: hidden` on cells |

**Test Plan:** Manual smoke test in browser with a known multi-table document. No automated browser tests in Sprint 5.

**Story Points:** 8

---

## 4) PR Plan

### PR 1 — Domain Models + Extractors (D-01 through D-10, Stories A-1 through B-3)

**Title:** `feat(table): Add TableCell/TableExtractionResult models and Lattice+Stream extractors`

**Contents:**

- All four domain model files in `rfp-core`
- `LatticeTableExtractor.java`
- `StreamTableExtractor.java`
- `TableTypeClassifier.java`
- Unit tests: `TableCellTest`, `LatticeTableExtractorTest`, `StreamTableExtractorTest`, `TableTypeClassifierTest`

**Review Checklist:**

- [ ] `@Builder.Default` used on `rowspan` and `colspan` in `TableCell`
- [ ] `LatticeTableExtractor` returns empty list (not null) when < 4 lines detected
- [ ] Y-coordinate normalization applied (PDF user space vs screen space)
- [ ] `StreamTableExtractor.confidence.score` hardcoded to `0.6`
- [ ] No Spring annotations in `rfp-core` domain model classes
- [ ] All classes under 250 lines; all methods under 20 lines

---

### PR 2 — Orchestration + Agent Node + Continuation (D-07 through D-11, Stories C-1 through F-1)

**Title:** `feat(table): TableExtractor orchestrator, continuation detection, section linking, and ExtractTablesNode`

**Contents:**

- `TableExtractor.java`
- `TableContinuationDetector.java`
- `TableSectionLinker.java`
- `ExtractTablesNode.java` (replaces Sprint 4 stub)
- Unit tests: `TableExtractorTest`, `TableContinuationDetectorTest`, `TableSectionLinkerTest`, `ExtractTablesNodeTest`
- `pom.xml` update adding `commons-text:1.12.0` if not present

**Review Checklist:**

- [ ] `TableExtractor` never calls `StreamTableExtractor` when `LatticeTableExtractor` returns non-empty
- [ ] `TableContinuationDetector` requires ≥ 2 of 3 signals — not just adjacency
- [ ] Levenshtein distance threshold of 3 is used (not equals-zero)
- [ ] `ExtractTablesNode` does not use `catch (Exception e)` and relies on the global exception handler contract
- [ ] `PDDocument` closed in `finally` block

---

### PR 3 — Frontend TableViewer (D-12 through D-13, Story G-1)

**Title:** `feat(frontend): TableViewer component with merged cell support`

**Contents:**

- `rfp-frontend/src/types/table.ts`
- `rfp-frontend/src/components/TableViewer.tsx`
- Updated `rfp-frontend/src/pages/ResultPage.tsx`

**Review Checklist:**

- [ ] CSS grid used (not HTML `<table>` — flexbox does not support rowspan/colspan)
- [ ] Cells sorted by row then col before rendering
- [ ] Confidence bar reflects `score * 100` as percentage width
- [ ] Empty state renders gracefully when `tables = []`
- [ ] TypeScript strict mode: no `any` types

---

## 5) Validation & Demo Script

### Step 1: Submit a document known to have tables

```bash
curl -X POST http://localhost:8080/api/v1/rfp/submit \
  -F "file=@testdata/ground-truth/ict-sample-tables.pdf" \
  -F "procRef=ICT-2025-001" \
  | jq .

# Expected:
# {
#   "jobId": "c3d7e9f1-...",
#   "status": "SUBMITTED"
# }
```

### Step 2: Poll for completion

```bash
JOB_ID="c3d7e9f1-..."
curl http://localhost:8080/api/v1/rfp/status/$JOB_ID | jq .

# Expected when complete:
# {
#   "jobId": "c3d7e9f1-...",
#   "status": "COMPLETED",
#   "pageCount": 42
# }
```

### Step 3: Retrieve result and verify tables

```bash
curl http://localhost:8080/api/v1/rfp/result/$JOB_ID | jq '.tables | length'
# Expected: >= 1 (at least one table found)

curl http://localhost:8080/api/v1/rfp/result/$JOB_ID | jq '.tables[0]'
# Expected output shape:
# {
#   "tableId": "f1a2b3c4-...",
#   "sectionId": "a1b2c3d4-...",
#   "pageStart": 12,
#   "pageEnd": 13,
#   "caption": "Table 3: Evaluation Criteria",
#   "type": "EVALUATION",
#   "headers": ["Criteria", "Weight", "Max Score"],
#   "grid": [
#     {"row": 0, "col": 0, "value": "Criteria", "rowspan": 1, "colspan": 1, "isHeader": true},
#     {"row": 0, "col": 1, "value": "Weight",   "rowspan": 1, "colspan": 1, "isHeader": true},
#     {"row": 0, "col": 2, "value": "Max Score","rowspan": 1, "colspan": 1, "isHeader": true},
#     {"row": 1, "col": 0, "value": "Technical Capability", "rowspan": 1, "colspan": 1, "isHeader": false},
#     ...
#   ],
#   "confidence": {"score": 0.87, "method": "lattice"}
# }
```

### Step 4: Verify multi-page merge

```bash
curl http://localhost:8080/api/v1/rfp/result/$JOB_ID \
  | jq '[.tables[] | select(.pageStart != .pageEnd)]'
# Expected: array of tables where pageStart < pageEnd (multi-page merges)
```

### Step 5: Verify frontend renders

Open browser at `http://localhost:5173`. Navigate to the job result. Click "Tables" tab. Verify:

- Tables listed with caption and type badge.
- Merged cells span visually (wider cells in grid).
- Confidence badge shows method and score bar.

---

## 6) Exit Criteria (NON-NEGOTIABLE)

| #     | Criterion                                                                                                                                     | Measure                                                                               |
|-------|-----------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------|
| EC-01 | All 14 unit test classes pass with `mvn test`                                                                                                 | 0 test failures                                                                       |
| EC-02 | `LatticeTableExtractor` extracts correct cell count from a programmatic PDF with a 3×4 grid                                                   | Asserted in `LatticeTableExtractorTest.shouldExtractThreeByFourGridCorrectly`         |
| EC-03 | `TableContinuationDetector` merges two-page-spanning table fragments in 3 of 3 deterministic fixture documents with known continuation tables | Manual verification against `testdata/fixtures/`                                      |
| EC-04 | Every `TableExtractionResult` in the API response has a non-null `sectionId` when a section covers its page                                   | Verified via `jq '[.tables[]                                                          | select(.sectionId == null)] | length'` == 0 on 10 test docs |
| EC-05 | `ExtractTablesNode` does not throw exceptions; all errors captured in `state.errors`                                                          | Integration verified by running full graph on a corrupt-table PDF                     |
| EC-06 | `TableViewer.tsx` renders merged cells using CSS grid `gridColumn: span N`                                                                    | Code review + visual browser inspection                                               |
| EC-07 | `TableType` classification: EVALUATION correctly identified on 8 of 10 fixture tables                                                         | Manual check against fixture expectations                                             |
| EC-08 | No class exceeds 250 lines; no method exceeds 20 lines                                                                                        | `mvn checkstyle:check` or manual audit                                                |
| EC-09 | `rfp-core` has zero Spring framework imports in domain model classes                                                                          | `grep -r "springframework" rfp-core/src/main/java/com/dsi/rfp/domain` returns 0 lines |
| EC-10 | Sprint 4 `ExtractionGraph` still passes all Sprint 4 entity extraction tests after `ExtractTablesNode` replacement                            | `mvn test` green on entity extractor tests                                            |

---

## 7) Notes: Assumptions / Open Questions

**Assumption:** `PdfDocumentLoader.loadPageBoundingBoxes(int pageNum)` returns `List<TextBlock>` where `TextBlock` has
public fields `x` (float), `y` (float), `width` (float), `height` (float), `text` (String). If the method signature
differs, `LatticeTableExtractor` and `StreamTableExtractor` must be updated to match the actual API.

**Assumption:** `PDFStreamEngine` is available in PDFBox 3.x under package `org.apache.pdfbox.contentstream`. The path
operator interception mechanism (`processOperator`) works identically to PDFBox 2.x. Verify against PDFBox 3.x changelog
before implementation starts.

**Assumption:** `Section` has fields `pageStart: int`, `pageEnd: int`, `id: UUID`. `Clause` has fields
`clauseStart: int` (page number), `id: UUID`. If field names differ in Sprint 3 implementation, `TableSectionLinker`
must be updated.

**Assumption:** `apache-commons-text` is already a transitive dependency via Spring Boot or LangChain4J. If not, add
`org.apache.commons:commons-text:1.12.0` explicitly to `rfp-service/pom.xml`.

**Open Question:** Should `TableViewer.tsx` support horizontal scrolling for tables wider than the viewport? Current
plan: add `overflow-x: auto` on the container. Confirm with UX before implementation.

**Open Question:** Should tables extracted from MIXED pages (text layer + OCR) be tagged with a different
`confidence.method`? Currently they use the same lattice/stream methods since they run on the text layer. Revisit in
Sprint 6 when `MixedPageExtractor` is introduced.

**Non-Goal:** Scanned-page table extraction (OCR → LLM reconstruction) is Sprint 6. `ExtractTablesNode` explicitly skips
SCANNED pages and stores empty list for those pages.
