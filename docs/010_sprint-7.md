# Sprint 7 — Repair Loop

## 0) Sprint Intent

Make the LangGraph4J repair loop fully operational: `ScoreConfidenceNode` populates a `lowConfidenceQueue` using
per-component scoring rules, `RepairLoopNode` executes a bounded deterministic decision-table strategy (max 3
retries/item, 20 total iterations), and `LlmSectionSegmentFallback` handles documents where heuristic segmentation found
too few sections. Every repair action writes a structured audit entry to `state.repairLog`. `ExtractionState` is
serialized to PostgreSQL checkpoints at every node boundary so the job is resumable on service restart. The job status
API is extended to
expose repair events in real time, and the React frontend displays them in a collapsible `AuditPanel`.

---

## 1) Entry Criteria

- Sprint 6 is merged and green on CI.
- `ExtractionState` exists in `rfp-service/agent/` (Sprint 4 placed it there; it will move to `rfp-core` once
  LangGraph4J compatibility is confirmed). Minimum required fields already present from Sprint 4:
  `sections`, `tables`, `entities`, `pageClassifications`, `confidenceMap`, `repairLog`, `lowConfidenceQueue`.
  This sprint adds: `pageTexts` (Map<Integer,String> of page-index → extracted text) and
  `pageConfidences` (Map<Integer,Double> of page-index → OCR confidence score).
- `ScoreConfidenceNode` has a basic stub (Sprint 4); full implementation is this sprint.
- `RepairLoopNode` has a basic stub (Sprint 4); full implementation is this sprint.
- `LangGraph4J` conditional edge exists:
  `ScoreConfidenceNode → RepairLoopNode if lowConfidenceQueue not empty AND totalRepairIterations < 20; else → RunRulePackNode`.
- `AnalysisJobRepository` exists (Sprint 2) and can serialize/deserialize `ExtractionJob`.
- `SectionSegmenter` (Sprint 3) with its chain-of-responsibility is available.
- `TableExtractor` (Sprint 5) with lattice/stream modes is available.
- `ScannedPageExtractor` (Sprint 6) is available.
- Entity sub-extractors (Sprint 4) accept a `contextWindow` parameter (or must be updated in this sprint to accept one).
- `LlmAdapter` is Resilience4j-wrapped.
- PostgreSQL is running and accessible at `spring.datasource.url`.

---

## 2) Deliverables

| #    | Deliverable                                   | Type              | Location                                                                       |
|------|-----------------------------------------------|-------------------|--------------------------------------------------------------------------------|
| D-01 | `RepairStrategy` enum                         | Java enum         | `rfp-core/.../domain/model/RepairStrategy.java`                                |
| D-02 | `RepairLogEntry` domain model                 | Java class        | `rfp-core/.../domain/model/RepairLogEntry.java`                                |
| D-03 | `RepairableComponent` domain model            | Java class        | `rfp-core/.../domain/model/RepairableComponent.java`                           |
| D-04 | `ExtractionState` updates                     | Java class        | `rfp-core/.../domain/model/ExtractionState.java`                               |
| D-05 | `RepairDecisionTable`                         | Java class        | `rfp-service/.../adapter/extraction/RepairDecisionTable.java`                  |
| D-06 | `LlmSectionSegmentFallback`                   | Java class        | `rfp-service/.../adapter/extraction/LlmSectionSegmentFallback.java`            |
| D-07 | `ScoreConfidenceNode` (full impl)             | Java class        | `rfp-service/.../agent/ScoreConfidenceNode.java`                               |
| D-08 | `RepairLoopNode` (full impl)                  | Java class        | `rfp-service/.../agent/RepairLoopNode.java`                                    |
| D-09 | `RepairAuditService`                          | Java class        | `rfp-service/.../application/service/RepairAuditService.java`                  |
| D-10 | `ExtractionStateCheckpointRepository`         | Java class        | `rfp-service/.../adapter/persistence/ExtractionStateCheckpointRepository.java` |
| D-11 | `AnalysisJobRepository` updates               | Java class        | `rfp-service/.../adapter/persistence/AnalysisJobRepository.java`               |
| D-12 | `RfpController` status endpoint update        | Java class        | `rfp-service/.../adapter/api/RfpController.java`                               |
| D-13 | `JobStatusDto` update                         | Java class        | `rfp-service/.../adapter/api/dto/JobStatusDto.java`                            |
| D-14 | `prompts/section-segmentation-fallback-v1.md` | Prompt file       | `prompts/section-segmentation-fallback-v1.md`                                  |
| D-15 | `AuditPanel.tsx`                              | React component   | `rfp-frontend/src/components/AuditPanel.tsx`                                   |
| D-16 | `JobStatusPage.tsx` update                    | React update      | `rfp-frontend/src/pages/JobStatusPage.tsx`                                     |
| D-17 | Unit tests                                    | Java test classes | `rfp-service/src/test/java/.../agent/` and `.../adapter/extraction/`           |

---

## 3) Work Breakdown

### Epic A — Domain Models

---

#### Story A-1: Define `RepairStrategy`, `RepairLogEntry`, `RepairableComponent` and update `ExtractionState`

**Description:** Introduce the typed vocabulary for the repair loop and add the required fields (`lowConfidenceQueue`,
`totalRepairIterations`, `repairLog`, `manualReviewRequired`, `confidenceMap`) to `ExtractionState`.

**Acceptance Criteria (Gherkin):**

```gherkin
Scenario: RepairLogEntry captures before and after confidence
  Given a RepairLogEntry builder with all fields set
  When built
  Then componentId, componentType, attemptNumber, strategy, beforeConfidence, afterConfidence, reason, and timestamp are all accessible

Scenario: ExtractionState has lowConfidenceQueue initialized as mutable list
  Given a freshly constructed ExtractionState
  When state.lowConfidenceQueue is accessed
  Then it is a non-null, empty, mutable List<String>

Scenario: ExtractionState totalRepairIterations starts at 0
  Given a freshly constructed ExtractionState
  When state.totalRepairIterations is accessed
  Then it equals 0
```

**Interfaces / Contracts:**

```java
// rfp-core/.../domain/model/RepairStrategy.java
public enum RepairStrategy {
    RETRY_SECTION_SEGMENTATION,
    SWITCH_TABLE_MODE,
    RETRY_SCANNED_TABLE_OCR_AT_HIGHER_DPI,
    WIDEN_ENTITY_CONTEXT,
    NO_OP
}

// rfp-core/.../domain/model/RepairLogEntry.java
@Data
@Builder
public class RepairLogEntry {
    private String componentId;       // matches an entity field path, sectionId, or tableId
    private String componentType;     // "section" | "table" | "entity"
    private int attemptNumber;
    private RepairStrategy strategy;
    private double beforeConfidence;
    private double afterConfidence;
    private String reason;
    private Instant timestamp;
}

// rfp-core/.../domain/model/RepairableComponent.java
@Data
@Builder
public class RepairableComponent {
    private String componentId;
    private String componentType;     // "section" | "table" | "entity"
    private String confidenceSource;  // "lattice" | "stream" | "ocr_llm_reconstruct" | "bookmark" | "heading_style" | etc.
    private double currentConfidence;
}
```

**ExtractionState additions** (add to the existing class — do not recreate the whole class):

```java
// Fields to add to ExtractionState in rfp-core/.../domain/model/ExtractionState.java
@Builder.Default
private final List<String> lowConfidenceQueue = new ArrayList<>();

@Builder.Default
private final int totalRepairIterations = 0;

@Builder.Default
private final List<RepairLogEntry> repairLog = new ArrayList<>();

@Builder.Default
private final List<String> manualReviewRequired = new ArrayList<>();

// Map from componentId -> current confidence score (updated by ScoreConfidenceNode and RepairLoopNode)
@Builder.Default
private final Map<String, Double> confidenceMap = new HashMap<>();

// Map from componentId -> RepairableComponent (set by ScoreConfidenceNode)
@Builder.Default
private final Map<String, RepairableComponent> repairableComponents = new HashMap<>();
```

**Implementation Plan:**

1. Create `RepairStrategy.java` enum in `rfp-core` — 5 values, no additional fields.
2. Create `RepairLogEntry.java` — `@Data @Builder`. Add `@Builder.Default private Instant timestamp = Instant.now()`.
3. Create `RepairableComponent.java` — `@Data @Builder`.
4. Edit `ExtractionState.java`: add the 6 fields listed above. Use `@Builder.Default` with `new ArrayList<>()` /
   `new HashMap<>()` / `0` to ensure safe defaults. Do not remove any existing fields.
5. Verify `rfp-core` compiles: `mvn compile -pl rfp-core`.

**Dependencies:** Lombok on rfp-core classpath.

**Risks + Mitigations:**

| Risk                                                                                                                                        | Mitigation                                                                                                                                          |
|---------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| `ExtractionState` already uses `@Builder` — adding `@Builder.Default` fields after the fact can cause Lombok-generated builder to omit them | Compile and test immediately after change; if builder ignores new fields, annotate the class with `@SuperBuilder` or use explicit `@Builder` method |
| Jackson cannot serialize `Instant` to JSON by default                                                                                       | Add `@JsonSerialize(using=InstantSerializer.class)` / `@JsonDeserialize` or enable `JavaTimeModule` on the shared `ObjectMapper`                    |

**Test Plan — `ExtractionStateDomainTest.java`:**

```
shouldInitializeLowConfidenceQueueAsEmptyMutableList
shouldInitializeTotalRepairIterationsAtZero
shouldAccumulateRepairLogEntries
```

**Story Points:** 3

---

### Epic B — Repair Decision Table

---

#### Story B-1: Implement `RepairDecisionTable`

**Description:** A static method that maps `(componentType, confidenceSource, attemptNumber)` to a `RepairStrategy`. No
LLM, no I/O — pure decision logic.

**Acceptance Criteria (Gherkin):**

```gherkin
Scenario: Section, attempt 1
  When RepairDecisionTable.getStrategy("s-001", "section", "heading_style", 1) is called
  Then the result is RETRY_SECTION_SEGMENTATION

Scenario: Section, attempt 2
  When RepairDecisionTable.getStrategy("s-001", "section", "heading_style", 2) is called
  Then the result is RETRY_SECTION_SEGMENTATION

Scenario: Table with lattice source, attempt 1
  When RepairDecisionTable.getStrategy("t-001", "table", "lattice", 1) is called
  Then the result is SWITCH_TABLE_MODE

Scenario: Table with stream source, attempt 1
  When RepairDecisionTable.getStrategy("t-001", "table", "stream", 1) is called
  Then the result is SWITCH_TABLE_MODE

Scenario: Table with ocr_llm_reconstruct source, attempt 1
  When RepairDecisionTable.getStrategy("t-001", "table", "ocr_llm_reconstruct", 1) is called
  Then the result is RETRY_SCANNED_TABLE_OCR_AT_HIGHER_DPI

Scenario: Entity, attempt 1
  When RepairDecisionTable.getStrategy("e-001", "entity", "llm", 1) is called
  Then the result is WIDEN_ENTITY_CONTEXT

Scenario: Any component, attempt >= 3
  When RepairDecisionTable.getStrategy("x-001", "section", "any", 3) is called
  Then the result is NO_OP
```

**Interfaces / Contracts:**

```java
// rfp-service/.../adapter/extraction/RepairDecisionTable.java
public final class RepairDecisionTable {

    private RepairDecisionTable() {
    }  // utility class — no instantiation

    /**
     * Determines the repair strategy for a low-confidence component.
     * This is a static decision table. No LLM is involved.
     *
     * @param componentId   unique identifier (for logging only)
     * @param componentType "section" | "table" | "entity"
     * @param confidenceSource method that produced the current result
     * @param attemptNumber 1-based count of repair attempts already made
     * @return RepairStrategy — never null
     */
    public static RepairStrategy getStrategy(String componentId,
                                             String componentType,
                                             String confidenceSource,
                                             int attemptNumber) { ...}
}
```

**Implementation Plan:**

1. If `attemptNumber >= 3` → return `NO_OP`.
2. Switch on `componentType`:
    - `"section"` → `RETRY_SECTION_SEGMENTATION` (for both attempt 1 and 2; attempt 2 will trigger LLM fallback inside
      `RepairLoopNode`).
    - `"table"`:
        - `confidenceSource` equals `"ocr_llm_reconstruct"` → `RETRY_SCANNED_TABLE_OCR_AT_HIGHER_DPI`.
        - else → `SWITCH_TABLE_MODE` (switch between lattice and stream).
    - `"entity"` → `WIDEN_ENTITY_CONTEXT`.
    - default → `NO_OP`.
3. Class body: final, private constructor, single public static method. Under 60 lines.

**Dependencies:** `RepairStrategy` enum (A-1).

**Risks + Mitigations:**

| Risk                                                    | Mitigation                                                                  |
|---------------------------------------------------------|-----------------------------------------------------------------------------|
| New component types added in future sprints not handled | Default branch returns `NO_OP` and logs `WARN("Unknown componentType: {}")` |

**Test Plan — `RepairDecisionTableTest.java`:**

```
shouldReturnNoOpWhenAttemptNumberIsThreeOrMore
shouldReturnRetrySectionSegmentationForSectionAttempt1
shouldReturnRetrySectionSegmentationForSectionAttempt2
shouldReturnSwitchTableModeForLatticeTable
shouldReturnSwitchTableModeForStreamTable
shouldReturnRetryHigherDpiForOcrLlmReconstructTable
shouldReturnWidenEntityContextForEntityAttempt1
shouldReturnNoOpForUnknownComponentType
```

Pure unit test — no mocking needed.

**Story Points:** 3

---

### Epic C — LLM Section Segmentation Fallback

---

#### Story C-1: Write `prompts/section-segmentation-fallback-v1.md`

**Description:** Versioned prompt asking the LLM to identify section structure from partial document text.

**File content — `prompts/section-segmentation-fallback-v1.md`:**

```markdown
---
id: section-segmentation-fallback-v1
version: "1.0"
model: google/gemini-2.0-flash-001
max_tokens: 2048
temperature: 0.0
---

# Section Segmentation Fallback

## System

You are a document structure analyst specializing in Government of Bangladesh procurement documents (RFPs, ToRs, and
tenders). Your task is to identify the major section structure of a document from a partial text excerpt.

## Instructions

1. Read the document text below carefully.
2. Identify all major sections and subsections. A section is typically introduced by a numbered heading (e.g., "1.", "
   1.1", "Section 1"), an ALL-CAPS heading, or a bold heading phrase.
3. For each section you identify, provide:
    - The section title (exact text as it appears in the document)
    - The hierarchical level (1 = top-level chapter, 2 = subsection, 3 = sub-subsection)
    - The approximate page number where the section starts (estimate from the text if page numbers are visible;
      otherwise use 0)
4. Return ONLY the JSON object below. Do not include any explanation or markdown fences.
5. If you cannot identify any sections, return `{"sections": []}`.
6. Limit your response to the 20 most significant sections.

## Output Format

```json
{
  "sections": [
    {
      "title": "Section Title Here",
      "level": 1,
      "approximate_page": 1
    }
  ]
}
```

## Document Text (first 3 pages)

{{document_text}}

```

**Story Points:** 2

---

#### Story C-2: Implement `LlmSectionSegmentFallback`

**Description:** When heuristic `SectionSegmenter` found fewer than 3 sections in a document with more than 20 pages, call the LLM to suggest sections, then merge with heuristic findings.

**Acceptance Criteria (Gherkin):**

```gherkin
Scenario: Fires when section count too low
  Given a document with 25 pages
  And state.sections has 2 entries
  When LlmSectionSegmentFallback.shouldFire(state) is called
  Then the result is true

Scenario: Does not fire when section count adequate
  Given a document with 25 pages
  And state.sections has 5 entries
  When LlmSectionSegmentFallback.shouldFire(state) is called
  Then the result is false

Scenario: Does not fire for short documents
  Given a document with 15 pages
  And state.sections has 1 entry
  When LlmSectionSegmentFallback.shouldFire(state) is called
  Then the result is false

Scenario: LLM suggests 4 sections, 1 already known (Levenshtein < 3)
  Given state.sections has title="Introduction"
  And LLM returns sections including title="Introduction" (distance 0), "Background", "Scope", "Evaluation"
  When LlmSectionSegmentFallback.execute(state) is called
  Then state.sections has 4 entries (Introduction deduplicated, 3 new added)
  And the new sections have their approximate_page as pageStart

Scenario: LLM call fails
  Given LlmAdapter throws a RuntimeException
  When LlmSectionSegmentFallback.execute(state) is called
  Then state.sections is unchanged
  And an error is logged
  And no exception propagates
```

**Interfaces / Contracts:**

```java
// rfp-service/.../adapter/extraction/LlmSectionSegmentFallback.java
@Component
public class LlmSectionSegmentFallback {

    private final LlmAdapter llmAdapter;
    private final DocumentChunkingService chunkingService;
    private final ObjectMapper objectMapper;
    private final LevenshteinDistance levenshtein = LevenshteinDistance.getDefaultInstance();

    private static final int MIN_PAGES_TO_TRIGGER = 20;
    private static final int MIN_SECTIONS_THRESHOLD = 3;
    private static final int MAX_TEXT_CHARS_FOR_PROMPT = 3500;
    private static final int DEDUP_LEVENSHTEIN_THRESHOLD = 3;

    public LlmSectionSegmentFallback(LlmAdapter llmAdapter,
                                     DocumentChunkingService chunkingService,
                                     ObjectMapper objectMapper) { ...}

    /**
     * Returns true when the fallback should run.
     */
    public boolean shouldFire(ExtractionState state) { ...}

    /**
     * Calls LLM, parses response, merges with existing sections.
     * Mutates state.sections. Never throws.
     */
    public void execute(ExtractionState state) { ...}

    private String buildFirstPagesText(ExtractionState state) { ...}

    private List<Section> parseLlmResponse(String llmJson) { ...}

    private boolean isDuplicate(Section candidate, List<Section> existing) { ...}
}
```

**Implementation Plan:**

1. `shouldFire(state)`:
    - `state.pageClassifications.size() > MIN_PAGES_TO_TRIGGER && state.sections.size() < MIN_SECTIONS_THRESHOLD`.

2. `buildFirstPagesText(state)`:
    - Collect text from pages 0, 1, 2 via `state.pageTexts.getOrDefault(page, "")`.
    - Concatenate. Truncate to `MAX_TEXT_CHARS_FOR_PROMPT` characters.
    - Return truncated text.

3. `execute(state)`:
    - Load prompt from `prompts/section-segmentation-fallback-v1.md`. Replace `{{document_text}}` with
      `buildFirstPagesText(state)`.
    - Call `llmAdapter.extractStructured(prompt, "google/gemini-2.0-flash-001")` → `String llmJson`.
    - Parse `parseLlmResponse(llmJson)` → `List<Section> llmSections`.
    - For each `llmSection`: if `!isDuplicate(llmSection, state.sections)` → add to `state.sections`.
    - Log merged count.
    - Do not use `try/catch(Exception e)` here. Let failures propagate and map them in the global exception handler.

4. `parseLlmResponse(llmJson)`:
    - `JsonNode root = objectMapper.readTree(llmJson)`.
    - Map `root.get("sections")` array → `Section` objects with `title=node.get("title").asText()`,
      `pageStart=node.get("approximate_page").asInt()`, `level=node.get("level").asInt()`.
    - Assign new `UUID` to each section's `id`.
    - Return list.

5. `isDuplicate(candidate, existing)`:
    - For each `s` in existing: if
      `levenshtein.apply(candidate.title().toLowerCase(), s.title().toLowerCase()) < DEDUP_LEVENSHTEIN_THRESHOLD` →
      return true.
    - Return false.

**Dependencies:** `LlmAdapter` (Sprint 1), `DocumentChunkingService` (Sprint 4), `Section` (Sprint 3),
`apache-commons-text` (Sprint 5).

**Test Plan — `LlmSectionSegmentFallbackTest.java`:**

```
shouldFireWhenMoreThan20PagesAndFewerThan3Sections
shouldNotFireWhenEnoughSectionsExist
shouldNotFireForShortDocuments
shouldMergeLlmSectionsDeduplicatingByLevenshtein
shouldNotModifyStateWhenLlmCallFails
shouldTruncateDocumentTextToMaxChars
```

Mock `LlmAdapter`. Build `ExtractionState` with specific `sections`, `pageTexts`, `pageClassifications`.

**Observability:**

```java
log.info("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=INFO [LlmSectionSegmentFallback] Fired: existingSections={} llmSuggestedNew={} totalAfterMerge={}",
         existingCount, newCount, state.getSections().

size());
    log.

error("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=ERROR [LlmSectionSegmentFallback] LLM call failed; sections unchanged: {}",e.getMessage());
```

**Story Points:** 8

---

### Epic D — Score Confidence Node (Full Implementation)

---

#### Story D-1: Implement `ScoreConfidenceNode` (full)

**Description:** Replace the Sprint 4 stub with a full implementation that computes per-entity, per-section, and
per-table confidence, builds the `confidenceMap`, populates `lowConfidenceQueue`, and identifies critical entities below
threshold for `manualReviewRequired`.

**Acceptance Criteria (Gherkin):**

```gherkin
Scenario: Critical entity missing (null value)
  Given state.entities.general.submission_deadline.value is null
  When ScoreConfidenceNode.execute(state) is called
  Then confidenceMap["entities.general.submission_deadline"] equals 0.0
  And "entities.general.submission_deadline" is in lowConfidenceQueue
  And "entities.general.submission_deadline" is in manualReviewRequired

Scenario: Entity present with LLM-expressed low confidence
  Given state.entities.financial.technical_financial_split.technicalWeight is present
  And the LLM response for that field contained confidence=0.45
  When ScoreConfidenceNode.execute(state) is called
  Then confidenceMap["entities.financial.technical_financial_split"] equals 0.5
  And that field IS in lowConfidenceQueue (0.5 < 0.6 threshold)

Scenario: Section extracted via bookmark strategy
  Given state.sections has one section with heading strategy "bookmark"
  When ScoreConfidenceNode.execute(state) is called
  Then confidenceMap[sectionId.toString()] equals 0.95
  And that section is NOT in lowConfidenceQueue (0.95 >= 0.6)

Scenario: Table with stream confidence
  Given state.tables has one table with confidence.method="stream" and confidence.score=0.6
  When ScoreConfidenceNode.execute(state) is called
  Then confidenceMap[tableId.toString()] equals 0.65
  And that table is NOT in lowConfidenceQueue (0.65 >= 0.6)

Scenario: doc_completeness_score computed
  Given 3 of 5 critical entities are non-null
  When ScoreConfidenceNode.execute(state) is called
  Then state.docCompletenessScore equals 0.6

Scenario: confidence threshold configurable
  Given app.extraction.confidence-threshold=0.75 in application.properties
  And an entity with confidence 0.7
  When ScoreConfidenceNode.execute(state) is called
  Then that entity is in lowConfidenceQueue
```

**Interfaces / Contracts:**

```java
// rfp-service/.../agent/ScoreConfidenceNode.java
@Component
public class ScoreConfidenceNode implements NodeAction<ExtractionState> {

    @Value("${app.extraction.confidence-threshold:0.6}")
    private double confidenceThreshold;

    private static final List<String> CRITICAL_ENTITY_PATHS = List.of(
        "entities.general.submission_deadline",
        "entities.general.client_name",
        "doc_meta.procurement_ref",
        "entities.financial.technical_financial_split",
        "entities.evaluation.criteria"
    );

    private static final Map<String, Double> SECTION_STRATEGY_CONFIDENCE = Map.of(
        "bookmark", 0.95,
        "heading_style", 0.90,
        "numbered", 0.85,
        "bangla", 0.85,
        "font_size", 0.70,
        "caps", 0.55,
        "llm", 0.75
    );

    @Override
    public ExtractionState execute(ExtractionState state) { ...}

    private void scoreEntities(ExtractionState state) { ...}

    private void scoreSections(ExtractionState state) { ...}

    private void scoreTables(ExtractionState state) { ...}

    private void computeDocCompleteness(ExtractionState state) { ...}

    private double entityScore(Object entityValue, Double llmConfidence) { ...}

    private void enqueue(String componentId, String componentType, String source,
                         double score, ExtractionState state) { ...}
}
```

**Additional field needed on `ExtractionState`:**

```java
// Add to ExtractionState:
@Builder.Default
private final double docCompletenessScore = 0.0;
```

**Implementation Plan:**

1. `scoreEntities(state)`:
    - Use Jackson `ObjectMapper` to convert `state.entities` to a flat-path map: e.g.,
      `"entities.general.submission_deadline" → entityValueObject`.
    - For each path:
        - `entityValue` = the extracted value field (the string/list/object returned by the extractor).
        - `llmConfidence` = the `confidence` field if present on the entity DTO (Double; null if absent).
        - `score = entityScore(entityValue, llmConfidence)`.
        - `state.confidenceMap.put(path, score)`.
        -
      `state.repairableComponents.put(path, RepairableComponent.builder().componentId(path).componentType("entity").confidenceSource("llm").currentConfidence(score).build())`.
        - Call `enqueue(path, "entity", "llm", score, state)`.

2. `entityScore(entityValue, llmConfidence)`:
    - If `Objects.isNull(entityValue)` → `0.0`.
    - If `Objects.nonNull(llmConfidence) && llmConfidence < 0.7` → `0.5`.
    - Else → `1.0`.

3. `scoreSections(state)`:
    - For each `section` in `state.sections`:
        - `String strategy = section.getHeadingStrategy()` (field added to `Section` in Sprint 3 or added now — add if
          missing).
        - `double score = SECTION_STRATEGY_CONFIDENCE.getOrDefault(strategy, 0.6)`.
        - `state.confidenceMap.put(section.getId().toString(), score)`.
        - `state.repairableComponents.put(section.getId().toString(), RepairableComponent.builder()...build())`.
        - Call `enqueue(section.getId().toString(), "section", strategy, score, state)`.

4. `scoreTables(state)`:
    - For each `table` in `state.tables`:
        - `double score = tableScore(table.confidence)`.
        - Table score by method: `lattice→0.85`, `stream→0.65`, `ocr_llm_reconstruct→0.55`.
        - Use `table.confidence.score` directly if it is already within these ranges; otherwise apply the method-based
          floor.
        - `state.confidenceMap.put(table.tableId.toString(), score)`.
        - Call `enqueue(table.tableId.toString(), "table", table.confidence.method, score, state)`.

5. `computeDocCompleteness(state)`:
    - Count how many of `CRITICAL_ENTITY_PATHS` have `confidenceMap.getOrDefault(path, 0.0) > 0.0`.
    - `state.docCompletenessScore = (double) nonNullCount / CRITICAL_ENTITY_PATHS.size()`.

6. `enqueue(componentId, componentType, source, score, state)`:
    - If `score < confidenceThreshold` → `state.lowConfidenceQueue.add(componentId)`.
    - If `CRITICAL_ENTITY_PATHS.contains(componentId) && score < 0.5` → `state.manualReviewRequired.add(componentId)`.

**Dependencies:** `ExtractionState` (A-1 updates), `Section` (Sprint 3, add `headingStrategy` field if missing),
`TableExtractionResult` (Sprint 5).

**Test Plan — `ScoreConfidenceNodeTest.java`:**

```
shouldSetConfidenceZeroForNullEntity
shouldSetConfidenceHalfForLowLlmConfidenceEntity
shouldSetConfidenceOneForPresentEntity
shouldEnqueueNullCriticalEntityToManualReview
shouldUseBookmarkConfidence095ForSection
shouldUseCapsConfidence055ForSection
shouldEnqueueLowConfidenceSectionToQueue
shouldUseStreamConfidence065ForTable
shouldComputeDocCompletenessScoreCorrectly
shouldRespectConfigurableConfidenceThreshold
```

No mocking needed — pure computation on hand-built `ExtractionState`.

**Observability:**

```java
log.info("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=INFO [ScoreConfidenceNode] JobId={} entitiesScored={} sectionsScored={} tablesScored={} "+
             "lowConfidenceQueue={} manualReview={} docCompleteness={}",
         state.jobId, entityCount, sectionCount, tableCount,
         state.lowConfidenceQueue.size(),state.manualReviewRequired.

size(),

state.docCompletenessScore);
```

**Story Points:** 13

---

### Epic E — Repair Loop Node (Full Implementation)

---

#### Story E-1: Implement `RepairLoopNode` (full)

**Description:** Pop one item from `lowConfidenceQueue`, look up its strategy from `RepairDecisionTable`, execute the
repair action, re-score the repaired component, update `confidenceMap` and `repairLog`. LangGraph4J routes the graph
back to this node if the queue is still non-empty and iterations < 20.

**Acceptance Criteria (Gherkin):**

```gherkin
Scenario: Hard stop at 20 total iterations
  Given state.totalRepairIterations equals 20
  And state.lowConfidenceQueue has 3 items
  When RepairLoopNode.execute(state) is called
  Then state.lowConfidenceQueue is not changed
  And a WARN log is emitted containing "Repair hard stop"
  And state.totalRepairIterations remains 20

Scenario: Component at max retries (attemptNumber > 3)
  Given the repairLog shows componentId "e-001" has been attempted 3 times already
  When RepairLoopNode pops "e-001"
  Then strategy NO_OP is applied
  And "e-001" is added to manualReviewRequired
  And a RepairLogEntry is added with strategy=NO_OP and reason="max retries exhausted"

Scenario: Successful section repair via RETRY_SECTION_SEGMENTATION
  Given componentId is a sectionId with low confidence
  And attemptNumber is 1
  And RepairDecisionTable returns RETRY_SECTION_SEGMENTATION
  When RepairLoopNode.execute(state) is called
  Then SectionSegmenter is called with the next strategy in the chain
  And state.sections is updated
  And totalRepairIterations increments by 1
  And a RepairLogEntry is added with beforeConfidence and afterConfidence

Scenario: Component improves above threshold — removed from queue
  Given a table with current confidence 0.55 (below threshold 0.6)
  And after SWITCH_TABLE_MODE re-extraction, new confidence is 0.87
  When RepairLoopNode.execute(state) is called
  Then the componentId is NOT re-added to lowConfidenceQueue

Scenario: Component does not improve — stays in queue for next iteration
  Given an entity with confidence 0.45
  And WIDEN_ENTITY_CONTEXT re-extraction yields confidence 0.50 (still below 0.6)
  When RepairLoopNode.execute(state) is called
  Then the componentId remains in lowConfidenceQueue
  And totalRepairIterations increments
```

**Interfaces / Contracts:**

```java
// rfp-service/.../agent/RepairLoopNode.java
@Component
public class RepairLoopNode implements NodeAction<ExtractionState> {

    private final SectionSegmenter sectionSegmenter;
    private final TableExtractor tableExtractor;
    private final ScannedPageExtractor scannedPageExtractor;
    private final ScannedTableReconstructor scannedTableReconstructor;
    private final EntityExtractionOrchestrator entityOrchestrator;
    private final LlmSectionSegmentFallback llmSectionFallback;

    @Value("${app.extraction.confidence-threshold:0.6}")
    private double confidenceThreshold;

    private static final int MAX_TOTAL_ITERATIONS = 20;
    private static final int MAX_RETRIES_PER_ITEM = 3;
    private static final int HIGH_DPI_FOR_RETRY = 450;

    public RepairLoopNode(SectionSegmenter sectionSegmenter,
                          TableExtractor tableExtractor,
                          ScannedPageExtractor scannedPageExtractor,
                          ScannedTableReconstructor scannedTableReconstructor,
                          EntityExtractionOrchestrator entityOrchestrator,
                          LlmSectionSegmentFallback llmSectionFallback) { ...}

    @Override
    public ExtractionState execute(ExtractionState state) { ...}

    private void executeStrategy(String componentId, RepairStrategy strategy,
                                 int attemptNumber, ExtractionState state) { ...}

    private void retrySectionSegmentation(String componentId, int attempt, ExtractionState state) { ...}

    private void switchTableMode(String componentId, ExtractionState state) { ...}

    private void retryScannedTableHigherDpi(String componentId, ExtractionState state) { ...}

    private void widenEntityContext(String componentId, ExtractionState state) { ...}

    private double rescoreComponent(String componentId, ExtractionState state) { ...}

    private int countPreviousAttempts(List<RepairLogEntry> repairLog, String componentId) { ...}

    private RepairLogEntry buildLogEntry(String componentId, RepairStrategy strategy,
                                         int attempt, double before, double after, String reason) { ...}
}
```

**Implementation Plan:**

1. **`execute(state)` — top-level flow (≤ 20 lines):**

```java
public ExtractionState execute(ExtractionState state) {
    if (state.getLowConfidenceQueue().isEmpty()) return state;
    if (state.getTotalRepairIterations() >= MAX_TOTAL_ITERATIONS) {
        log.warn("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=WARN [RepairLoopNode] Repair hard stop reached: totalIterations={}", MAX_TOTAL_ITERATIONS);
        return state;
    }
    String componentId = state.getLowConfidenceQueue().remove(0);
    int attemptNumber = countPreviousAttempts(state.getRepairLog(), componentId) + 1;
    double before = state.getConfidenceMap().getOrDefault(componentId, 0.0);
    RepairableComponent comp = state.getRepairableComponents().get(componentId);
    if (Objects.isNull(comp)) {
        state.getManualReviewRequired().add(componentId);
        return state;
    }
    if (attemptNumber > MAX_RETRIES_PER_ITEM) {
        state.getManualReviewRequired().add(componentId);
        state.getRepairLog().add(buildLogEntry(componentId, NO_OP, attemptNumber, before, before, "max retries exhausted"));
        return state;
    }
    RepairStrategy strategy = RepairDecisionTable.getStrategy(
        componentId, comp.getComponentType(), comp.getConfidenceSource(), attemptNumber);
    executeStrategy(componentId, strategy, attemptNumber, state);
    double after = rescoreComponent(componentId, state);
    state.getConfidenceMap().put(componentId, after);
    state.setTotalRepairIterations(state.getTotalRepairIterations() + 1);
    state.getRepairLog().add(buildLogEntry(componentId, strategy, attemptNumber, before, after, strategy.name()));
    if (after < confidenceThreshold) {
        state.getLowConfidenceQueue().add(componentId);  // back in queue for next iteration
    }
    return state;
}
```

2. **`retrySectionSegmentation(componentId, attempt, state)`:**
    - `attempt == 1`: find the `Section` with `id == UUID.fromString(componentId)`. Remove it from `state.sections`.
      Re-run `sectionSegmenter.segmentWithNextStrategy(state.documentPath, state.sections)` — this requires
      `SectionSegmenter` to expose a method that tries the next HeadingStrategy in the priority chain (skipping the one
      that produced the current result). Add this method to `SectionSegmenter` if not present.
    - `attempt == 2`: call `llmSectionFallback.execute(state)` (fires the LLM fallback).
    - Either: update `state.sections`.

3. **`switchTableMode(componentId, state)`:**
    - Find `TableExtractionResult` with `tableId == UUID.fromString(componentId)`.
    - Current method = `table.confidence.method`.
    - New call: if current is `"lattice"` → call `streamExtractor.extractFromPage(doc, table.pageStart)`. If current is
      `"stream"` → call `latticeExtractor.extractFromPage(doc, table.pageStart)`.
    - Replace table in `state.tables`.
    - Note: requires opening `PDDocument` from `state.documentPath`. Open in try-with-resources within this method.
      Close after extraction.

4. **`retryScannedTableHigherDpi(componentId, state)`:**
    - Find table. Get `table.pageStart`.
    - Re-render page at `HIGH_DPI_FOR_RETRY` (450) DPI: `PDFRenderer.renderImageWithDPI(pageNum, 450, ImageType.RGB)`.
    - Re-OCR via `OcrSidecarClient.extractPage(pngBytes, "eng+ben")`.
    - Re-reconstruct via `scannedTableReconstructor.reconstructTable(ocrText, pageNum, newOcrConf)`.
    - If `Optional.present()` → replace table in `state.tables`.

5. **`widenEntityContext(componentId, state)`:**
    - `componentId` is an entity path like `"entities.general.submission_deadline"`.
    - Find the clauses belonging to the section containing the entity. Expand context: collect text from the entity's
      section + the previous and next section.
    - Call `entityOrchestrator.reExtractWithContext(componentId, widerText)` — add this method to
      `EntityExtractionOrchestrator` (accepts entity path + context string, returns updated `RfpEntities`).
    - Update `state.entities` with the re-extracted value.

6. **`rescoreComponent(componentId, state)`:**
    - If component is an entity: re-evaluate using same `entityScore` logic as `ScoreConfidenceNode`.
    - If section: look up new section's `headingStrategy` in confidence map.
    - If table: use `table.confidence.score` from newly stored table.

7. **`countPreviousAttempts(repairLog, componentId)`:**
    - `return (int) repairLog.stream().filter(e -> e.getComponentId().equals(componentId)).count()`.

8. **`buildLogEntry(...)`:**
    - Builder pattern. Under 10 lines.

**Dependencies:** `SectionSegmenter` (Sprint 3), `TableExtractor` (Sprint 5), `ScannedPageExtractor` (Sprint 6),
`ScannedTableReconstructor` (Sprint 6), `EntityExtractionOrchestrator` (Sprint 4, needs `reExtractWithContext`),
`LlmSectionSegmentFallback` (C-2), `RepairDecisionTable` (B-1).

**Risks + Mitigations:**

| Risk                                                                       | Mitigation                                                                                                                                              |
|----------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| `RepairLoopNode` exceeds 250 lines due to 4 strategy implementations       | Extract each strategy into a private inner class (not a Spring bean — no Spring dependency); or into package-private helper classes in the same package |
| Opening `PDDocument` inside `switchTableMode` may fail if file was deleted | Check file existence first; on failure: log error, leave table unchanged, return current confidence                                                     |
| `EntityExtractionOrchestrator.reExtractWithContext` not yet implemented    | Add a minimal implementation in this sprint: takes entity path + context text, extracts just that entity, merges result back into `RfpEntities`         |

**Test Plan — `RepairLoopNodeTest.java`:**

```
shouldReturnStateUnchangedWhenQueueIsEmpty
shouldReturnStateUnchangedAndWarnWhenHardStopReached
shouldApplyNoOpAndAddToManualReviewWhenMaxRetriesExceeded
shouldCallSectionSegmenterForSectionRepair
shouldCallTableExtractorForTableRepair
shouldCallLlmFallbackForSectionAttemptTwo
shouldIncrementTotalRepairIterations
shouldRemoveComponentFromQueueWhenConfidenceImproves
shouldLeaveComponentInQueueWhenConfidenceStillBelowThreshold
shouldWriteRepairLogEntry
```

Mock all collaborators. Build `ExtractionState` for each test case.

**Observability:**

```java
log.info("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=INFO [RepairLoopNode] JobId={} componentId={} type={} strategy={} attempt={} before={} after={} queueRemaining={}",
         state.jobId, componentId, comp.componentType, strategy, attemptNumber,
         String.format("%.3f", before),String.

format("%.3f",after),
    state.lowConfidenceQueue.

size());
```

**Story Points:** 21

---

### Epic F — State Persistence

---

#### Story F-1: Persist `ExtractionState` to PostgreSQL checkpoints at node boundaries

**Description:** Persist `ExtractionState` as JSONB checkpoint rows after each LangGraph4J node completes, keyed by
`job_id` and `checkpoint_sequence`; retention is controlled by DB retention policy. Load state on `RepairLoopNode`
entry if the job resumes.

**Acceptance Criteria (Gherkin):**

```gherkin
Scenario: State checkpoint written after each node
  Given a document is being processed
  When ScoreConfidenceNode completes
  Then checkpoint row exists for jobId
  And the value is valid JSON deserializable to ExtractionState

Scenario: State recoverable after service restart
  Given RepairLoopNode is mid-iteration and the service restarts
  When the job is resubmitted with the same jobId
  Then the ExtractionState is loaded from PostgreSQL checkpoints
  And totalRepairIterations continues from where it left off

Scenario: Old checkpoints are removed by retention policy
  Given a job completed 2 hours and 1 minute ago
  When checkpoint retention window is checked
  Then the old checkpoint rows are removed according to retention policy
```

**Interfaces / Contracts:**

```java
// rfp-service/.../adapter/persistence/ExtractionStateCheckpointRepository.java
@Component
public class ExtractionStateCheckpointRepository {

    private final ObjectMapper objectMapper;
    private final ExtractionStateCheckpointJpaRepository checkpointRepository;

    public ExtractionStateCheckpointRepository(ObjectMapper objectMapper,
                                               ExtractionStateCheckpointJpaRepository checkpointRepository) { ...}

    public void save(String jobId, ExtractionState state) { ...}

    public Optional<ExtractionState> load(String jobId) { ...}

    public void deleteByJobId(String jobId) { ...}
}
```

**Implementation Plan:**

1. `save(jobId, state)`:
    - `String json = objectMapper.writeValueAsString(state)`.
    - Persist checkpoint row with `jobId`, `sequence`, `stateJson`, and `createdAt`.
    - On `JsonProcessingException` or `DataAccessException` → `log.error(...)`, do not rethrow (state persistence
      failure must not abort extraction).

2. `load(jobId)`:
    - Query latest checkpoint row by `jobId` and descending sequence.
    - If missing → return `Optional.empty()`.
    - `return Optional.of(objectMapper.readValue(stateJson, ExtractionState.class))`.
    - On parse error → `log.error(...)`, return `Optional.empty()`.

3. `deleteByJobId(jobId)`: called on job finalization (`FinalizeNode`).

4. **Wire into agent nodes:** Add `ExtractionStateCheckpointRepository` as a collaborator to `ScoreConfidenceNode` and
   `RepairLoopNode`. At the end of each `execute(state)` call, call `checkpointRepository.save(state.jobId, state)`.

5. `ExtractionState` must be Jackson-serializable. Ensure all fields have either public getters (via Lombok `@Data` or
   `@Getter`) or `@JsonProperty`. `UUID` and `Instant` require `JavaTimeModule` + `JavaUUIDModule` on the shared
   `ObjectMapper` — confirm in `DatabasePersistenceConfig`.

**Test Plan — `ExtractionStateCheckpointRepositoryTest.java`:**

```text
shouldSaveAndLoadExtractionState
shouldReturnEmptyOptionalWhenCheckpointMissing
shouldRespectRetentionPolicyOnOldCheckpoints
shouldHandleJsonSerializationErrorGracefully
```

Mock `ExtractionStateCheckpointJpaRepository` in unit tests.

**Observability:**

```java
log.debug("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=DEBUG [ExtractionStateCheckpointRepository] Saved checkpoint for jobId={} size={}bytes",jobId, json.length());
```

**Story Points:** 5

---

### Epic G — Repair Audit Service

---

#### Story G-1: Implement `RepairAuditService`

**Description:** Format `state.repairLog` entries into human-readable strings for display in the audit report. No
external calls.

**Acceptance Criteria (Gherkin):**

```gherkin
Scenario: Format a successful repair entry
  Given a RepairLogEntry with strategy=SWITCH_TABLE_MODE, before=0.55, after=0.87
  When RepairAuditService.formatEntry(entry) is called
  Then the result contains "SWITCH_TABLE_MODE"
  And contains "0.550 → 0.870"
  And contains "IMPROVED"

Scenario: Format a failed repair entry (no improvement)
  Given a RepairLogEntry with strategy=WIDEN_ENTITY_CONTEXT, before=0.45, after=0.50
  When RepairAuditService.formatEntry(entry) is called
  Then the result contains "WIDEN_ENTITY_CONTEXT"
  And contains "NOT IMPROVED"

Scenario: Format summary
  Given a repairLog with 3 entries (2 improved, 1 not improved)
  When RepairAuditService.formatSummary(state) is called
  Then the result contains "3 repairs attempted"
  And contains "2 improved"
  And contains "manualReviewRequired=X"
```

**Interfaces / Contracts:**

```java
// rfp-service/.../application/service/RepairAuditService.java
@Service
public class RepairAuditService {

    public String formatEntry(RepairLogEntry entry) { ...}

    public String formatSummary(ExtractionState state) { ...}

    public List<String> formatAll(List<RepairLogEntry> entries) { ...}
}
```

**Implementation Plan:**

1. `formatEntry(entry)`:
    - `boolean improved = entry.afterConfidence > entry.beforeConfidence`.
    - Return `String.format("[%s] component=%s strategy=%s attempt=%d %.3f → %.3f %s reason=%s",
       entry.timestamp(), entry.componentId(), entry.strategy(), entry.attemptNumber(),
       entry.beforeConfidence(), entry.afterConfidence(), improved ? "IMPROVED" : "NOT IMPROVED", entry.reason())`.

2. `formatSummary(state)`:
    - `long improved = state.repairLog.stream().filter(e -> e.afterConfidence > e.beforeConfidence).count()`.
    - Return `String.format("Repair summary: %d repairs attempted, %d improved, manualReviewRequired=%d, totalIterations=%d, docCompleteness=%.2f",
       state.repairLog.size(), improved, state.manualReviewRequired.size(), state.totalRepairIterations, state.docCompletenessScore)`.

3. `formatAll(entries)`: return `entries.stream().map(this::formatEntry).toList()`.

**Test Plan — `RepairAuditServiceTest.java`:**

```
shouldFormatImprovedEntryWithImprovedLabel
shouldFormatNoImprovementEntryWithNotImprovedLabel
shouldFormatSummaryWithCorrectCounts
```

No mocking — pure string computation.

**Story Points:** 3

---

### Epic H — API Extension

---

#### Story H-1: Extend `GET /api/v1/rfp/status/{jobId}` with repair event fields

**Description:** The job status endpoint now returns additional fields: `repairEvents`, `totalRepairIterations`,
`lowConfidenceCount`, loaded from `ExtractionStateCheckpointRepository`.

**Acceptance Criteria (Gherkin):**

```gherkin
Scenario: Status response includes repair events during repair phase
  Given a job in REPAIR state with 2 repair log entries in checkpoint state
  When GET /api/v1/rfp/status/{jobId} is called
  Then the response body contains "repairEvents" array with 2 entries
  And each entry has fields: componentId, attempt, strategy, result ("IMPROVED"|"NOT IMPROVED")
  And "totalRepairIterations" equals 2
  And "lowConfidenceCount" equals the current queue size

Scenario: Status response shows empty repairEvents for COMPLETED job with no repairs
  Given a job that completed with no low-confidence items
  When GET /api/v1/rfp/status/{jobId} is called
  Then "repairEvents" is an empty array
  And "totalRepairIterations" equals 0
```

**Interfaces / Contracts:**

```java
// rfp-service/.../adapter/api/dto/RepairEventDto.java
public record RepairEventDto(
        String componentId,
        int attempt,
        String strategy,
        String result  // "IMPROVED" | "NOT IMPROVED" | "MAX_RETRIES"
    ) {
}

// rfp-service/.../adapter/api/dto/JobStatusDto.java
// Add to existing JobStatusDto:
//   List<RepairEventDto> repairEvents
//   int totalRepairIterations
//   int lowConfidenceCount
```

**Implementation Plan:**

1. In `RfpJobService.getJobStatus(jobId)` (or equivalent application service):
    - Load `ExtractionState` from `ExtractionStateCheckpointRepository.load(jobId)`.
    - If `Optional.present()`: map `state.repairLog` → `List<RepairEventDto>` using `RepairAuditService` result
      categorization.
    - Set `totalRepairIterations = state.totalRepairIterations`.
    - Set `lowConfidenceCount = state.lowConfidenceQueue.size()`.
    - If `Optional.empty()`: default all to empty/0.

2. Update `JobStatusDto` record/class with the three new fields (with JSON default of empty array / 0 for backward
   compatibility).

3. Update `RfpController.getStatus` — no logic change needed; delegates to `RfpJobService`.

**Test Plan — `RfpControllerStatusTest.java` (additions):**

```
shouldReturnRepairEventsInStatusResponse
shouldReturnZeroIterationsWhenNoRepairOccurred
shouldHandleMissingCheckpointStateGracefully
```

Use MockMvc. Mock `ExtractionStateCheckpointRepository`.

**Story Points:** 5

---

### Epic I — Frontend

---

#### Story I-1: Implement `AuditPanel.tsx` and update `JobStatusPage.tsx`

**Description:** Collapsible panel showing the full repair log. `JobStatusPage.tsx` polls the status endpoint every 2
seconds and shows repair events as they arrive.

**Acceptance Criteria (Gherkin):**

```gherkin
Scenario: AuditPanel renders repair events
  Given repairEvents has 3 entries
  When AuditPanel is rendered
  Then 3 rows are shown with componentId, strategy, and result
  And IMPROVED rows have green styling
  And NOT IMPROVED rows have orange styling

Scenario: AuditPanel is collapsed by default
  Given AuditPanel is rendered
  Then the repair event list is not visible
  And a toggle button reads "Show Repair Audit (3 events)"

Scenario: JobStatusPage shows live repair counter
  Given totalRepairIterations = 5 and lowConfidenceCount = 2
  When JobStatusPage renders the status
  Then "Repair iterations: 5" is visible
  And "Items pending: 2" is visible
```

**Implementation Plan:**

1. **`AuditPanel.tsx`:**

```typescript
// rfp-frontend/src/components/AuditPanel.tsx
interface RepairEvent {
    componentId: string;
    attempt: number;
    strategy: string;
    result: 'IMPROVED' | 'NOT IMPROVED' | 'MAX_RETRIES';
}

interface AuditPanelProps {
    repairEvents: RepairEvent[];
    totalRepairIterations: number;
}

export function AuditPanel({repairEvents, totalRepairIterations}: AuditPanelProps): JSX.Element {
    const [open, setOpen] = useState(false);
    // ...
}
```

2. `AuditPanel` rendering:
    - Toggle button: `className="text-sm font-medium text-slate-600 underline cursor-pointer"`.
    - When open: render `<table>` with columns: Component, Attempt, Strategy, Result.
    - Row color: `result === 'IMPROVED' ? 'bg-green-50' : result === 'MAX_RETRIES' ? 'bg-red-50' : 'bg-orange-50'`.

3. **`JobStatusPage.tsx` update:**
    - Extend React Query poll to also extract `repairEvents`, `totalRepairIterations`, `lowConfidenceCount` from
      `/api/v1/rfp/status/{jobId}`.
    - Render `<AuditPanel repairEvents={...} totalRepairIterations={...} />` below the existing status indicator.
    - Show: `<p>Repair iterations: {totalRepairIterations} | Items pending: {lowConfidenceCount}</p>`.

**Story Points:** 5

---

## 4) PR Plan

### PR 1 — Domain Models + Decision Table + Prompt (Stories A-1, B-1, C-1)

**Title:**
`feat(repair): RepairStrategy enum, RepairLogEntry, RepairDecisionTable, and section-segmentation-fallback prompt`

**Contents:**

- `RepairStrategy.java`
- `RepairLogEntry.java`
- `RepairableComponent.java`
- `ExtractionState.java` (additions only)
- `RepairDecisionTable.java`
- `prompts/section-segmentation-fallback-v1.md`
- Unit tests: `RepairDecisionTableTest`, `ExtractionStateDomainTest`

**Review Checklist:**

- [ ] `RepairDecisionTable` is final with private constructor (no instantiation)
- [ ] `attemptNumber >= 3` guard is checked FIRST before all other conditions
- [ ] `ExtractionState` new fields all have `@Builder.Default` with safe empty collections
- [ ] Prompt file has valid YAML frontmatter (id, version, model, max_tokens, temperature)
- [ ] No Spring annotations in `rfp-core` domain classes

---

### PR 2 — ScoreConfidenceNode + LlmSectionSegmentFallback + RepairLoopNode (Stories C-2, D-1, E-1)

**Title:** `feat(repair): Full ScoreConfidenceNode, LlmSectionSegmentFallback, and RepairLoopNode implementation`

**Contents:**

- `LlmSectionSegmentFallback.java`
- `ScoreConfidenceNode.java` (replacing Sprint 4 stub)
- `RepairLoopNode.java` (replacing Sprint 4 stub)
- Unit tests: `LlmSectionSegmentFallbackTest`, `ScoreConfidenceNodeTest`, `RepairLoopNodeTest`
- Any updates to `SectionSegmenter` (add `segmentWithNextStrategy` method if missing)
- Any updates to `EntityExtractionOrchestrator` (add `reExtractWithContext` method)

**Review Checklist:**

- [ ] `ScoreConfidenceNode` `CRITICAL_ENTITY_PATHS` list has exactly 5 entries matching project spec
- [ ] `RepairLoopNode.execute` is ≤ 20 lines (strategy dispatch delegated to private methods)
- [ ] Hard stop at 20 iterations is the FIRST check in `execute` (before queue check)
- [ ] Each private strategy method has its own try/catch — one failing strategy does not abort others
- [ ] `LlmSectionSegmentFallback.shouldFire` checks BOTH page count > 20 AND section count < 3
- [ ] `totalRepairIterations` is incremented exactly once per `execute` call

---

### PR 3 — Checkpoint Persistence + API Extension + Frontend (Stories F-1, G-1, H-1, I-1)

**Title:**
`feat(repair): ExtractionState checkpoint persistence, audit service, extended status API, AuditPanel frontend`

**Contents:**

- `ExtractionStateCheckpointRepository.java`
- `RepairAuditService.java`
- Updated `RfpController.java` and `JobStatusDto.java`
- `RepairEventDto.java`
- `AuditPanel.tsx`
- Updated `JobStatusPage.tsx`
- Unit tests: `ExtractionStateCheckpointRepositoryTest`, `RepairAuditServiceTest`, `RfpControllerStatusTest` (additions)

**Review Checklist:**

- [ ] Checkpoint retention policy is configured and verified
- [ ] 
- [ ] `ExtractionStateCheckpointRepository.save` does NOT throw on transient DB persistence failure (logs and returns)
- [ ] `JobStatusDto` new fields have JSON defaults (empty array, 0) for backward compatibility
- [ ] `AuditPanel` is collapsed by default (`useState(false)`)
- [ ] TypeScript strict mode: no `any` types in new frontend files

---

## 5) Validation & Demo Script

### Step 1: Submit a document known to have low-confidence sections

```bash
curl -X POST http://localhost:8080/api/v1/rfp/submit \
  -F "file=@testdata/ground-truth/low-quality-scan.pdf" \
  -F "procRef=REPAIR-TEST-001" \
  | jq .

# Expected:
# {"jobId": "e5f6a7b8-...", "status": "SUBMITTED"}
```

### Step 2: Poll status and observe repair events accumulating

```bash
JOB_ID="e5f6a7b8-..."

# Poll every 3 seconds
for i in {1..10}; do
  curl -s http://localhost:8080/api/v1/rfp/status/$JOB_ID | jq '{status, totalRepairIterations, lowConfidenceCount, repairEvents: (.repairEvents | length)}'
  sleep 3
done

# Expected mid-extraction output:
# {"status":"RUNNING","totalRepairIterations":3,"lowConfidenceCount":2,"repairEvents":3}
# Then eventually:
# {"status":"COMPLETED","totalRepairIterations":5,"lowConfidenceCount":0,"repairEvents":5}
```

### Step 3: Inspect repair events in final status

```bash
curl http://localhost:8080/api/v1/rfp/status/$JOB_ID | jq '.repairEvents'

# Expected:
# [
#   {
#     "componentId": "entities.general.submission_deadline",
#     "attempt": 1,
#     "strategy": "WIDEN_ENTITY_CONTEXT",
#     "result": "IMPROVED"
#   },
#   {
#     "componentId": "b2c3d4e5-....",
#     "attempt": 1,
#     "strategy": "SWITCH_TABLE_MODE",
#     "result": "IMPROVED"
#   }
# ]
```

### Step 4: Verify checkpoint rows exist during processing

```bash
docker exec -it rfp-postgres psql -U rfp -d rfpdb -c "SELECT job_id, checkpoint_sequence FROM extraction_state_checkpoints ORDER BY created_at DESC LIMIT 5;"
# Expected: at least one row for the active job

docker exec -it rfp-postgres psql -U rfp -d rfpdb -c "SELECT job_id, created_at FROM extraction_state_checkpoints WHERE job_id = 'e5f6a7b8-...' ORDER BY created_at DESC LIMIT 1;"
# Expected: recent timestamp for active checkpoint
```

### Step 5: Verify LLM section fallback fires on known sparse document

```bash
curl -X POST http://localhost:8080/api/v1/rfp/submit \
  -F "file=@testdata/ground-truth/sparse-sections.pdf" \
  -F "procRef=SPARSE-001" \
  | jq .

JOB_ID="<new-job-id>"
curl http://localhost:8080/api/v1/rfp/result/$JOB_ID | jq '.sections | length'
# Expected: > 3 (LLM fallback added sections)
```

### Step 6: Verify hard stop — max iterations not exceeded

```bash
# Submit a pathological document expected to generate many low-confidence items
curl -X POST http://localhost:8080/api/v1/rfp/submit \
  -F "file=@testdata/ground-truth/worst-case-scan.pdf" \
  -F "procRef=HARDSTOP-001" | jq .

JOB_ID="<new-job-id>"
# Wait for completion
curl http://localhost:8080/api/v1/rfp/status/$JOB_ID \
  | jq '.totalRepairIterations'
# Expected: <= 20 (hard stop enforced)
```

### Step 7: Verify manual review list

```bash
curl http://localhost:8080/api/v1/rfp/result/$JOB_ID \
  | jq '.extractionMetadata.manualReviewRequired'
# Expected: array of componentIds that could not be repaired within 3 attempts
```

---

## 6) Exit Criteria (NON-NEGOTIABLE)

| #     | Criterion                                                                                                       | Measure                                                                                                                   |
|-------|-----------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------|
| EC-01 | `RepairLoopNode` never exceeds 20 total iterations on any document                                              | `state.totalRepairIterations <= 20` asserted in `RepairLoopNodeTest.shouldReturnStateUnchangedAndWarnWhenHardStopReached` |
| EC-02 | `RepairDecisionTable` test covers all 8 decision paths                                                          | `RepairDecisionTableTest` has exactly 8 test methods, all passing                                                         |
| EC-03 | `ScoreConfidenceNode` correctly identifies low-confidence entities on 3 deterministic fixture documents         | Manual check: `confidenceMap` values match expected scores for known entities                                             |
| EC-04 | `ExtractionState` survives serialize → deserialize round-trip through PostgreSQL checkpoints with no field loss | `ExtractionStateCheckpointRepositoryTest.shouldSaveAndLoadExtractionState` passes with all fields asserted                |
| EC-05 | Checkpoint retention policy is applied correctly                                                                | `ExtractionStateCheckpointRepositoryTest.shouldExpireAfterTwoHours` passes                                                |
| EC-06 | `LlmSectionSegmentFallback` deduplicates by Levenshtein < 3 (not exact match)                                   | `LlmSectionSegmentFallbackTest.shouldMergeLlmSectionsDeduplicatingByLevenshtein` passes                                   |
| EC-07 | `GET /api/v1/rfp/status/{jobId}` returns `repairEvents`, `totalRepairIterations`, `lowConfidenceCount` fields   | `RfpControllerStatusTest.shouldReturnRepairEventsInStatusResponse` passes                                                 |
| EC-08 | `AuditPanel.tsx` is collapsed by default; toggle opens it                                                       | Code review confirms `useState(false)` initial state                                                                      |
| EC-09 | All Java unit tests pass with `mvn test`                                                                        | 0 failures                                                                                                                |
| EC-10 | No class exceeds 250 lines; `RepairLoopNode.execute` is ≤ 20 lines                                              | Manual code review; `execute` method line count verified                                                                  |
| EC-11 | `RepairAuditService` formats "IMPROVED"/"NOT IMPROVED" label based on before/after confidence delta             | `RepairAuditServiceTest.shouldFormatImprovedEntryWithImprovedLabel` passes                                                |
| EC-12 | On service restart mid-repair, job can resume from PostgreSQL checkpoint state (manual test)                    | Stop Spring Boot during repair phase, restart, re-poll status — status continues incrementing `totalRepairIterations`     |

---

## 7) Notes: Assumptions / Open Questions

**Assumption:** `Section` domain model (Sprint 3) has a `headingStrategy: String` field populated by `SectionSegmenter`
at the time of extraction. If this field does not exist, add it to `Section` in `rfp-core` and update `SectionSegmenter`
to set it when building sections.

**Assumption:** `SectionSegmenter` can be called with a specific starting `HeadingStrategy` (to skip already-tried
strategies). If `SectionSegmenter` currently picks the strategy automatically, add a method
`segmentWithStrategy(String documentPath, HeadingStrategyType strategyType, List<Section> existingSections)` or an
equivalent override.

**Assumption:** `EntityExtractionOrchestrator.reExtractWithContext(String entityPath, String contextText)` does not yet
exist. It must be added in this sprint. The implementation should: identify which sub-extractor handles the given entity
path, call that sub-extractor with `contextText` as input, and return updated `RfpEntities`. Estimated effort: 3 SP
added to `RepairLoopNode` story.

**Assumption:** `ExtractionState.jobId` is a `String` (not a UUID object) for easy use as checkpoint identifier. If it
is a
`UUID`, call `.toString()` before persistence.

**Assumption:** `JavaTimeModule` is already registered on the shared `ObjectMapper` bean (needed for `Instant`
serialization in `RepairLogEntry`). If not, add `objectMapper.registerModule(new JavaTimeModule())` in the
`ObjectMapper` `@Bean` definition in a config class.

**Open Question:** Should `manualReviewRequired` items be surfaced in the final RFP result JSON (the
`GET /api/v1/rfp/result/{jobId}` response)? Current plan: yes, add a `extractionMetadata.manualReviewRequired` array to
the result DTO. Confirm with product owner before implementation.

**Open Question:** Should `ScoreConfidenceNode` expose queue depth in the status API for UI-only visibility? Deferred to
wishlist (unit-test-only baseline; no Actuator).

**Non-Goal:** LLM-based routing of repair strategies. The decision table is permanently static. Adding an LLM-based "
meta-planner" is explicitly out of scope for this project.
