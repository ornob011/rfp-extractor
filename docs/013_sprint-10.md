# Sprint 10 — Bid Clarity Pack Artifacts

## 0) Sprint Intent

- Generate the complete Bid Clarity Pack for every successfully processed RFP: a Clarification Questions DOCX (formal
  RFI letter), an Ambiguity Register XLSX, a Compliance Checklist XLSX, a Risk Log XLSX, and an HTML Audit Report — all
  downloadable from the frontend.
- Wire artifact generation into `FinalizeNode` so that every completed job automatically produces all five artifacts
  without a separate trigger.
- Implement `ArtifactPort` and a local file storage adapter, exposing artifact listing and streaming download via REST.
- Surface all artifacts in `ResultPage.tsx` with a new "Artifacts" tab and an inline Audit Report viewer.

**Non-goals:**

- Email delivery or external artifact publishing (not required).
- Digital signing of artifacts (not required for this sprint).
- Authentication/authorisation on the download endpoint (Sprint 11).
- Bangla language in generated documents (Sprint 13).

---

## 1) Entry Criteria

- Sprint 9 is merged and green on CI (`mvn clean verify` passes).
- `FinalizeNode` assembles `RfpDocument` and saves to `DocumentStoragePort` (Sprint 4).
- `RulePackResults` is populated in `ExtractionState` and accessible from `FinalizeNode` (Sprint 8).
- `LlmAdapter.judgeSnippet()` is Resilience4j-wrapped and available (Sprint 1).
- Apache POI `poi-ooxml` and Freemarker on classpath (Sprint 1 POM).
- `LocalDocumentStorageAdapter` stores files under `app.storage.base-path` (Sprint 2).
- `ArtifactPort` interface exists as a placeholder in `rfp-core/domain/port/` (stub from Sprint 4).
- `BidClarityPack`, `ClarificationQuestion`, `AmbiguityItem`, `ComplianceItem`, `RiskItem` domain models do **not**
  exist yet — create this sprint.
- `RfpDocument`, `RuleFinding`, `RulePackResults`, `Section`, `Clause` all exist in `rfp-core`.
- JUnit 5 + Mockito + AssertJ on classpath.
- Freemarker 2.3.x on classpath.

---

## 2) Deliverables

| #     | Deliverable                          | Type                | Location                                                 |
|-------|--------------------------------------|---------------------|----------------------------------------------------------|
| D-01  | `ClarificationQuestion` domain model | Java class          | `rfp-core/.../domain/model/ClarificationQuestion.java`   |
| D-02  | `QuestionType` enum                  | Java enum           | `rfp-core/.../domain/model/QuestionType.java`            |
| D-02a | `RiskImpact` enum                    | Java enum           | `rfp-core/.../domain/model/RiskImpact.java`              |
| D-02b | `ArtifactFileType` enum              | Java enum           | `rfp-core/.../domain/model/ArtifactFileType.java`        |
| D-03  | `AmbiguityItem` domain model         | Java class          | `rfp-core/.../domain/model/AmbiguityItem.java`           |
| D-04  | `ComplianceItem` domain model        | Java class          | `rfp-core/.../domain/model/ComplianceItem.java`          |
| D-05  | `RiskItem` domain model              | Java class          | `rfp-core/.../domain/model/RiskItem.java`                |
| D-06  | `BidClarityPack` domain model        | Java class          | `rfp-core/.../domain/model/BidClarityPack.java`          |
| D-07  | `ArtifactMetadata` value object      | Java class          | `rfp-core/.../domain/model/ArtifactMetadata.java`        |
| D-08  | `ArtifactPort` (updated)             | Java interface      | `rfp-core/.../domain/port/ArtifactPort.java`             |
| D-09  | `ClarificationTrigger` value object  | Java class          | `rfp-core/.../domain/model/ClarificationTrigger.java`    |
| D-10  | `ClarificationQuestionTrigger`       | Spring component    | `adapter/artifact/ClarificationQuestionTrigger.java`     |
| D-11  | `ClarificationQuestionsGenerator`    | Spring component    | `adapter/artifact/ClarificationQuestionsGenerator.java`  |
| D-12  | `ClarificationQuestionsDocxWriter`   | Spring component    | `adapter/artifact/ClarificationQuestionsDocxWriter.java` |
| D-13  | `AmbiguityRegisterXlsxWriter`        | Spring component    | `adapter/artifact/AmbiguityRegisterXlsxWriter.java`      |
| D-14  | `ComplianceChecklistXlsxWriter`      | Spring component    | `adapter/artifact/ComplianceChecklistXlsxWriter.java`    |
| D-15  | `RiskLogXlsxWriter`                  | Spring component    | `adapter/artifact/RiskLogXlsxWriter.java`                |
| D-16  | `AuditReportGenerator`               | Spring component    | `adapter/artifact/AuditReportGenerator.java`             |
| D-17  | `LocalArtifactStorageAdapter`        | Spring component    | `adapter/persistence/LocalArtifactStorageAdapter.java`   |
| D-18  | `ArtifactApplicationService`         | Application service | `application/service/ArtifactApplicationService.java`    |
| D-19  | `FinalizeNode` (updated)             | LangGraph4J node    | `agent/node/FinalizeNode.java`                           |
| D-20  | `clarification-question-gen-v1.md`   | Prompt file         | `prompts/clarification-question-gen-v1.md`               |
| D-21  | `risk-mitigation-suggestion-v1.md`   | Prompt file         | `prompts/risk-mitigation-suggestion-v1.md`               |
| D-22  | `clarification-questions.ftl`        | Freemarker template | `resources/templates/clarification-questions.ftl`        |
| D-23  | `audit-report.ftl`                   | Freemarker template | `resources/templates/audit-report.ftl`                   |
| D-24  | Artifact download REST endpoints     | Controller update   | `adapter/api/RfpController.java`                         |
| D-25  | `ArtifactDownload.tsx`               | React component     | `rfp-frontend/src/components/ArtifactDownload.tsx`       |
| D-26  | `AuditReportViewer.tsx`              | React component     | `rfp-frontend/src/components/AuditReportViewer.tsx`      |

---

## 3) Work Breakdown

### Epic 10.1 — Domain Models & Port Interface

#### Story 10.1.1 — Bid Clarity Pack Domain Models

**Acceptance Criteria (Gherkin):**

```gherkin
Given a list of ClarificationQuestion objects
When BidClarityPack is built using its builder
Then all lists are accessible and the generatedAt timestamp is set

Given a ClarificationQuestion with type MANDATORY_CLARIFICATION and priority 1
When stored in BidClarityPack
Then it is accessible via getClarificationQuestions()
```

**Interfaces / Contracts:**

```java
// QuestionType.java
public enum QuestionType {
    MANDATORY_CLARIFICATION, CONFIRMATION, AMBIGUITY, CONTRADICTION_RESOLUTION
}

public enum RiskImpact {
    HIGH, MEDIUM, LOW
}

public enum ArtifactFileType {
    DOCX, XLSX, HTML
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClarificationQuestion {
    private String id;             // UUID
    private String questionText;
    private String clauseId;
    private int page;
    private QuestionType questionType;
    private int priority;          // 1 (highest) to 5
    private String source;         // "rule:BD-ICT-001" or "entity:submission_deadline"
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmbiguityItem {
    private String id;
    private RuleSeverity severity;
    private String category;       // e.g. "Missing Field", "Vague Requirement", "Contradiction"
    private String description;
    private String sourceClauseId;
    private int page;
    private String recommendedAction;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceItem {
    private String id;
    private String requirement;
    private String sourceClauseId;
    private int page;
    private boolean mandatory;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskItem {
    private String id;
    private String riskDescription;
    private String source;
    private RiskImpact impact;
    private String mitigationSuggestion;
    private String owner;          // default: "Bid Team"
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BidClarityPack {
    private UUID jobId;
    private Instant generatedAt;
    private List<ClarificationQuestion> clarificationQuestions;
    private List<AmbiguityItem> ambiguityItems;
    private List<ComplianceItem> complianceItems;
    private List<RiskItem> riskItems;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtifactMetadata {
    private UUID jobId;
    private String filename;
    private ArtifactFileType fileType;
    private long sizeBytes;
    private Instant generatedAt;
    private String downloadUrl;
}

// ArtifactPort.java
public interface ArtifactPort {
    ArtifactMetadata storeArtifact(UUID jobId, String filename, byte[] content);

    List<ArtifactMetadata> listArtifacts(UUID jobId);

    byte[] loadArtifact(UUID jobId, String filename);
}
```

**Implementation Plan:**

1. Create all enum and model classes in `rfp-core/domain/model/`.
2. Update `ArtifactPort` with all three method signatures.

**Test Plan:**

- `shouldBuildBidClarityPackWithAllListsWhenBuilderUsed()`.

**Observability:** None at domain layer.
**Story Points:** 3

---

### Epic 10.2 — Clarification Question Generation

#### Story 10.2.1 — Trigger Detection

**Acceptance Criteria (Gherkin):**

```gherkin
Given rule findings containing FATAL failures
When ClarificationQuestionTrigger.detect(doc, results) is called
Then a MANDATORY_CLARIFICATION trigger is produced for each FATAL FAIL finding

Given entity submission_deadline has confidence < 0.5
When detect() is called
Then a CONFIRMATION trigger is produced for that entity

Given two entities with logically inconsistent values (issue_date after submission_deadline)
When detect() is called
Then a CONTRADICTION_RESOLUTION trigger is produced
```

**Interfaces / Contracts:**

```java

@Data
@Builder
public class ClarificationTrigger {
    private QuestionType type;
    private String clauseId;
    private int page;
    private String context;        // short description for LLM prompt context
    private String ruleId;         // null for entity triggers
    private String entityField;    // null for rule triggers
}

@Component
@Slf4j
@RequiredArgsConstructor
public class ClarificationQuestionTrigger {

    public List<ClarificationTrigger> detect(
        RfpDocument doc, RulePackResults results, ExtractionState state);

    private List<ClarificationTrigger> fromRuleFindings(RulePackResults results);

    private List<ClarificationTrigger> fromLowConfidenceEntities(ExtractionState state);

    private List<ClarificationTrigger> fromContradictions(RfpDocument doc);
}
```

**Implementation Plan:**

1. `fromRuleFindings()`: filter findings where `status=FAIL`:
    - `FATAL` or `HIGH` → `MANDATORY_CLARIFICATION`.
    - Semantic rule `finding=true` → `AMBIGUITY`.
2. `fromLowConfidenceEntities()`: iterate FATAL entity fields from `state.confidenceMap`. If confidence < 0.5 →
   `CONFIRMATION` trigger.
3. `fromContradictions()`: check `entities.general.issue_date` < `entities.general.submission_deadline` using string
   date comparison (if both non-null). If violated → `CONTRADICTION_RESOLUTION`.
4. Return combined list, capped at 50 triggers (to avoid runaway LLM calls in Step 10.2.2).

**Test Plan:**

- `shouldProduceMandatoryTriggerForFatalFailFinding()`.
- `shouldProduceConfirmationTriggerForLowConfidenceEntity()`.
- `shouldProduceContradictionTriggerWhenIssueDateAfterDeadline()`.
- `shouldCapTriggersAtFiftyWhenManyFindings()`.

**Observability:** INFO log:
`"Identified {} clarification triggers: {} MANDATORY, {} CONFIRMATION, {} AMBIGUITY, {} CONTRADICTION"`.
**Story Points:** 5

---

#### Story 10.2.2 — LLM Question Generation

**Acceptance Criteria (Gherkin):**

```gherkin
Given a MANDATORY_CLARIFICATION trigger with a clauseId and context
When ClarificationQuestionsGenerator.generate() is called
Then LlmAdapter is called once per trigger with the clarification-question-gen prompt

Given two triggers for the same clauseId and questionType
When generate() deduplicates
Then only the trigger with higher priority (lower number) is kept

Given more than 30 questions would be generated
When generate() runs
Then the result is capped at 30, prioritising by priority ascending
```

**Interfaces / Contracts:**

```java

@Component
@Slf4j
@RequiredArgsConstructor
public class ClarificationQuestionsGenerator {
    // < 250 lines

    public List<ClarificationQuestion> generate(
        List<ClarificationTrigger> triggers,
        RfpDocument doc);

    private ClarificationQuestion questionFromTrigger(ClarificationTrigger trigger, RfpDocument doc);

    private List<ClarificationQuestion> deduplicate(List<ClarificationQuestion> questions);
}
```

**Implementation Plan:**

1. For each trigger: call `LlmAdapter.extractStructured(prompt, schema)` with prompt from
   `/prompts/clarification-question-gen-v1.md` interpolated with trigger context.
2. Parse LLM response: `{"questionText": "...", "priority": 1-5}`.
3. On LLM failure: generate a fallback question from the trigger context + rule message (no LLM). Log WARN.
4. `deduplicate()`: group by `clauseId + questionType`, keep lowest priority value (= most important).
5. Sort by priority ascending, cap at 30.
6. Return `List<ClarificationQuestion>`.

**Test Plan:**

- `shouldCallLlmOncePerTrigger()` — mock `LlmAdapter`, verify call count.
- `shouldDeduplicateQuestionsWithSameClauseAndType()`.
- `shouldCapResultAtThirtyWhenManyTriggers()`.
- `shouldUseFallbackTextWhenLlmFails()` — mock `LlmAdapter` throwing `LlmUnavailableException`.

**Observability:** INFO log: `"Generated {} clarification questions from {} triggers"`.
**Story Points:** 5

---

#### Story 10.2.3 — Clarification Questions DOCX Writer

**Acceptance Criteria (Gherkin):**

```gherkin
Given a list of 5 ClarificationQuestion objects
When ClarificationQuestionsDocxWriter.write(questions, doc) is called
Then a non-empty byte[] is returned

Given the output bytes are written to a .docx file
When the file is opened in MS Word or LibreOffice
Then the document contains a header with project title and procurement reference
And questions are numbered with source references [Section X.Y, Page N]
```

**Interfaces / Contracts:**

```java

@Component
@RequiredArgsConstructor
public class ClarificationQuestionsDocxWriter {

    public byte[] write(List<ClarificationQuestion> questions, RfpDocument doc);

    private void addHeader(XWPFDocument docx, RfpDocument doc);

    private void addQuestion(XWPFDocument docx, int index, ClarificationQuestion q);

    private void addFooter(XWPFDocument docx);
}
```

**Implementation Plan:**

1. Create `XWPFDocument`.
2. Add header paragraph: "REQUEST FOR INFORMATION (RFI)" — Bold, 16pt.
3. Add metadata: "Project: {title}", "Reference: {procurementRef}", "Date: {today}".
4. Add separator paragraph.
5. For each question (numbered): add paragraph `"Q{n}: {questionText}"` + source line
   `"Source: [Section {clauseId}, Page {page}]"` in italic smaller font.
6. Add footer paragraph: "Please respond by [leave blank] to [contact email from doc]".
7. Serialise `XWPFDocument` to `ByteArrayOutputStream`, return `toByteArray()`.
8. Use Freemarker template `templates/clarification-questions.ftl` for the body content if the template approach is
   cleaner than programmatic POI (either is acceptable; use programmatic POI for simpler implementation).

**Test Plan:**

- `shouldReturnNonEmptyBytesWhenQuestionsProvided()` — assert `bytes.length > 0`.
- `shouldReturnEmptyDocWhenNoQuestionsProvided()` — zero questions, still valid DOCX.

**Observability:** DEBUG log: `"DOCX written: {} bytes, {} questions"`.
**Story Points:** 5

---

### Epic 10.3 — Register and Checklist Writers

#### Story 10.3.1 — Ambiguity Register XLSX Writer

**Acceptance Criteria (Gherkin):**

```gherkin
Given a list of AmbiguityItem objects with mixed severities
When AmbiguityRegisterXlsxWriter.write(items) is called
Then the returned bytes form a valid XLSX file

Given the XLSX is opened
When rows are examined
Then rows are sorted FATAL first, then HIGH, MEDIUM, LOW, INFO
And FATAL rows have a red fill, HIGH rows orange
```

**Interfaces / Contracts:**

```java

@Component
public class AmbiguityRegisterXlsxWriter {

    public byte[] write(List<AmbiguityItem> items);

    private void applyRowColor(XSSFRow row, RuleSeverity severity, XSSFWorkbook wb);

    private XSSFCellStyle createColorStyle(XSSFWorkbook wb, IndexedColors color);
}
```

**Implementation Plan:**

1. Create `XSSFWorkbook`, add sheet "Ambiguity Register".
2. Add header row: Issue ID | Severity | Category | Description | Source Clause | Page | Recommended Action. Bold,
   frozen row.
3. Sort items by severity ordinal.
4. For each item: add row. Apply background colour:
    - FATAL → `IndexedColors.RED` (light), HIGH → `IndexedColors.ORANGE`, MEDIUM → `IndexedColors.YELLOW`, LOW →
      `IndexedColors.LIGHT_BLUE`, INFO → no fill.
5. Auto-size columns (call `sheet.autoSizeColumn(i)` for i=0..6).
6. Serialise and return bytes.

**Test Plan:**

- `shouldReturnNonEmptyBytesWhenItemsProvided()`.
- `shouldSortBySeverityWithFatalFirst()` — verify row order via `sheet.getRow(1).getCell(1).getStringCellValue()`.

**Observability:** DEBUG log: `"Ambiguity Register XLSX: {} rows"`.
**Story Points:** 3

---

#### Story 10.3.2 — Compliance Checklist XLSX Writer

**Acceptance Criteria (Gherkin):**

```gherkin
Given RulePackResults with FATAL and HIGH findings (PASS and FAIL mixed)
When ComplianceChecklistXlsxWriter.write(results) is called
Then a row exists for every FATAL and HIGH rule finding

Given a FAIL finding for BD-ICT-001
When the corresponding row is examined
Then the Mandatory column shows "Yes" and Status column is blank (for bid team to fill)
```

**Interfaces / Contracts:**

```java

@Component
public class ComplianceChecklistXlsxWriter {

    public byte[] write(RulePackResults results, RfpDocument doc);

    private ComplianceItem toComplianceItem(RuleFinding finding, RfpDocument doc);
}
```

**Implementation Plan:**

1. Filter `results.getFindings()` to FATAL and HIGH severity.
2. Convert to `ComplianceItem` list.
3. Create `XSSFWorkbook`, sheet "Compliance Checklist".
4. Header row: # | Requirement | Source Clause | Page | Mandatory? | Compliance Status.
5. For each item: add row. "Compliance Status" cell left blank (for bid team).
6. Conditional formatting: FAIL rows tinted light-red background.
7. Return bytes.

**Test Plan:**

- `shouldIncludeAllFatalAndHighFindingsAsRows()`.
- `shouldLeaveStatusColumnBlank()`.

**Story Points:** 3

---

#### Story 10.3.3 — Risk Log XLSX Writer with LLM Mitigation

**Acceptance Criteria (Gherkin):**

```gherkin
Given a list of RiskItem objects
When RiskLogXlsxWriter.write(items, doc) is called
Then LlmAdapter is called for each item to generate a mitigation suggestion

Given the LLM returns a mitigation suggestion
When the XLSX row is written
Then the Mitigation Suggestion column contains the LLM-generated text
```

**Interfaces / Contracts:**

```java

@Component
@Slf4j
@RequiredArgsConstructor
public class RiskLogXlsxWriter {

    public byte[] write(List<RiskItem> items, RfpDocument doc);

    private String generateMitigation(RiskItem item);
}
```

**Implementation Plan:**

1. For each `RiskItem`: call `LlmAdapter.judgeSnippet()` with prompt from `/prompts/risk-mitigation-suggestion-v1.md` +
   item description.
2. Parse response: `{"mitigation": "...", "impact": "HIGH|MEDIUM|LOW"}`. On failure: use
   `"Manual assessment required"` and default impact to `MEDIUM`.
3. Create `XSSFWorkbook`, sheet "Risk & Assumptions Log".
4. Header: # | Risk/Assumption | Source | Impact | Mitigation Suggestion | Owner.
5. Add one row per item.
6. Return bytes.

**Test Plan:**

- `shouldCallLlmForMitigationPerRiskItem()` — mock LlmAdapter, verify call count.
- `shouldUseFallbackWhenLlmFails()`.

**Story Points:** 5

---

### Epic 10.4 — HTML Audit Report

#### Story 10.4.1 — AuditReportGenerator with Freemarker

**Acceptance Criteria (Gherkin):**

```gherkin
Given a completed RfpDocument and ExtractionState with 10 pages
When AuditReportGenerator.generate(doc, state, results) is called
Then a non-empty HTML string is returned

Given the HTML is opened in a browser
When the page-by-page table is examined
Then each row shows page number, classification, extraction method, confidence colour bar, retry count

Given a page with confidence 0.3
When rendered in the audit report
Then the confidence bar has red styling (class "conf-low")
```

**Interfaces / Contracts:**

```java

@Component
@Slf4j
@RequiredArgsConstructor
public class AuditReportGenerator {

    /** Returns a self-contained HTML string (inline CSS, no external dependencies). */
    public String generate(RfpDocument doc, ExtractionState state, RulePackResults results);

    private Map<String, Object> buildTemplateModel(
        RfpDocument doc, ExtractionState state, RulePackResults results);
}
```

**Implementation Plan:**

1. Configure `freemarker.template.Configuration` bean in `ArtifactConfig.java` (classpath template loader, `templates/`
   directory).
2. `buildTemplateModel()`: assemble a `Map` with:
    - `title`, `procurementRef`, `generatedAt`
    - `pageSummaries`: list of page #, classification, method, confidence, retries
    - `entitySummary`: list of entity name, value (truncated 50 chars), confidence
    - `repairLog`: list of component, strategy, attempt, before/after confidence
    - `ruleFindings`: grouped by severity
    - `stats`:
      `{totalPages, digitalPages, scannedPages, mixedPages, tablesFound, entitiesExtracted, fatalFail, highFail}`
3. Load `templates/audit-report.ftl`, process with `freemarker.template.Template.process()`.
4. Return HTML string.
5. `audit-report.ftl` includes:
    - Inline CSS: `.conf-high {color:green}`, `.conf-mid {color:orange}`, `.conf-low {color:red}`
    - Summary stats box at top
    - `<table>` for page-by-page breakdown
    - `<div>` for entity extraction results
    - `<table>` for repair log
    - `<table>` for rule findings (colour-coded by severity)

**Test Plan:**

- `shouldReturnNonEmptyHtmlWhenDocumentProvided()`.
- `shouldIncludePageCountInStats()` — check HTML contains `state.getPageClassifications().size()`.
- `shouldClassifyConfidenceCorrectly()` — verify `conf-low` class for confidence < 0.5.

**Observability:** DEBUG log: `"Audit report generated: {} chars"`.
**Story Points:** 8

---

### Epic 10.5 — Storage, REST API & Frontend

#### Story 10.5.1 — Local Artifact Storage Adapter

**Acceptance Criteria (Gherkin):**

```gherkin
Given a jobId and filename and byte content
When LocalArtifactStorageAdapter.storeArtifact(jobId, filename, content) is called
Then the file is written to {base-path}/{jobId}/artifacts/{filename}

When listArtifacts(jobId) is called on a job with 3 stored artifacts
Then a list of 3 ArtifactMetadata objects is returned with correct filenames
```

**Interfaces / Contracts:**

```java

@Component
@Slf4j
@RequiredArgsConstructor
public class LocalArtifactStorageAdapter implements ArtifactPort {

    // base path from @Value("${app.storage.base-path}")

    @Override
    public ArtifactMetadata storeArtifact(UUID jobId, String filename, byte[] content);

    @Override
    public List<ArtifactMetadata> listArtifacts(UUID jobId);

    @Override
    public byte[] loadArtifact(UUID jobId, String filename);

    private Path artifactDir(UUID jobId);

    private String detectFileType(String filename);
}
```

**Implementation Plan:**

1. `artifactDir(jobId)` = `Paths.get(basePath, jobId.toString(), "artifacts")`.
2. `storeArtifact()`: create directories, write bytes with `Files.write()`. Return `ArtifactMetadata` with size and
   `generatedAt=Instant.now()`.
3. `listArtifacts()`: use `Files.list(artifactDir)` filtered to regular files. Map each to `ArtifactMetadata`.
4. `loadArtifact()`: `Files.readAllBytes(artifactDir.resolve(filename))`. Throw `ResourceNotFoundException` if missing.
5. `detectFileType()`: map extension: `.docx`→`DOCX`, `.xlsx`→`XLSX`, `.html`→`HTML`.

**Test Plan:**

- `shouldWriteFileToCorrectPathWhenStoreArtifactCalled()` — use temp directory.
- `shouldListAllArtifactsWhenDirectoryHasFiles()`.
- `shouldThrowWhenArtifactNotFound()`.

**Story Points:** 3

---

#### Story 10.5.2 — ArtifactApplicationService Orchestrator

**Acceptance Criteria (Gherkin):**

```gherkin
Given a completed job with jobId and ExtractionState
When ArtifactApplicationService.generateAll(jobId) is called
Then all 5 artifacts are generated and stored

Given generateAll completes
When listArtifacts(jobId) is called
Then 5 ArtifactMetadata objects are returned
```

**Interfaces / Contracts:**

```java

@Service
@Slf4j
@RequiredArgsConstructor
public class ArtifactApplicationService {

    public BidClarityPack generateAll(UUID jobId, RfpDocument doc,
                                      ExtractionState state, RulePackResults results);

    private List<AmbiguityItem> buildAmbiguityItems(RulePackResults results, RfpDocument doc);

    private List<RiskItem> buildRiskItems(RulePackResults results, RfpDocument doc);

    private List<ComplianceItem> buildComplianceItems(RulePackResults results, RfpDocument doc);
}
```

**Implementation Plan:**

1. Call `ClarificationQuestionTrigger.detect()` → triggers.
2. Call `ClarificationQuestionsGenerator.generate(triggers, doc)` → questions.
3. Build `AmbiguityItem` list from FAIL findings (one item per finding).
4. Build `ComplianceItem` list from FATAL + HIGH findings.
5. Build `RiskItem` list from HIGH + FATAL findings (description from `finding.getMessage()`).
6. Call each writer: `docxWriter.write()`, `ambiguityWriter.write()`, `checklistWriter.write()`, `riskWriter.write()`,
   `auditReportGenerator.generate()`.
7. Call `ArtifactPort.storeArtifact()` for each.
8. Return assembled `BidClarityPack`.
9. Service is `< 250 lines`; delegate all construction to private methods.

**Test Plan:**

- `shouldStoreAllFiveArtifactsWhenGenerateAllCalled()` — mock all writers and storage, verify 5 `storeArtifact()` calls.
- `shouldBuildAmbiguityItemsFromFailFindings()`.

**Observability:** INFO log: `"Bid Clarity Pack generated for job {}: {} clarification questions, {} artifacts stored"`.
**Story Points:** 5

---

#### Story 10.5.3 — FinalizeNode Integration

**Acceptance Criteria (Gherkin):**

```gherkin
Given ExtractionState with rulePackResults populated
When FinalizeNode.apply(state) completes
Then ArtifactApplicationService.generateAll() is called
And ExtractionJob status is updated to COMPLETED
```

**Implementation Plan:**

1. In `FinalizeNode.apply()`, after assembling `RfpDocument` and saving it:
2. Call `artifactApplicationService.generateAll(jobId, doc, state, state.getRulePackResults())`.
3. Update `ExtractionJob` status to `COMPLETED` via `JobStatePort`.
4. Set `state.progress = 100`.
5. Log: `"Finalize complete: job={}, doc_completeness={}"`.

**Test Plan:**

- `shouldCallGenerateAllWhenFinalizeNodeRuns()` — mock service, verify called.

**Story Points:** 3

---

#### Story 10.5.4 — REST Artifact Endpoints

**Acceptance Criteria (Gherkin):**

```gherkin
Given a completed job with 5 artifacts stored
When GET /api/v1/rfp/artifacts/{jobId} is called
Then a JSON array of 5 ArtifactMetadata objects is returned

When GET /api/v1/rfp/artifacts/{jobId}/clarification-questions.docx is called
Then a binary DOCX file is streamed with Content-Type application/vnd.openxmlformats-officedocument.wordprocessingml.document
```

**Interfaces / Contracts:**

```java
// Added to RfpController.java

@GetMapping("/rfp/artifacts/{jobId}")
public ResponseEntity<List<ArtifactMetadataDto>> listArtifacts(@PathVariable UUID jobId);

@GetMapping("/rfp/artifacts/{jobId}/{filename}")
public ResponseEntity<Resource> downloadArtifact(
    @PathVariable UUID jobId, @PathVariable String filename);
```

**Implementation Plan:**

1. `listArtifacts()`: call `ArtifactPort.listArtifacts(jobId)`, map to `ArtifactMetadataDto`, return 200.
2. `downloadArtifact()`: call `ArtifactPort.loadArtifact(jobId, filename)`, wrap in `ByteArrayResource`, set
   `Content-Type` from filename extension:
    - `.docx` → `application/vnd.openxmlformats-officedocument.wordprocessingml.document`
    - `.xlsx` → `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
    - `.html` → `text/html`
3. Add `Content-Disposition: attachment; filename="{filename}"` header.
4. Return `ResponseEntity<Resource>` with 200.
5. On `ResourceNotFoundException` from storage adapter → return 404.

**Test Plan:**

- `shouldReturn200WithMetadataListWhenArtifactsExist()`.
- `shouldStreamDocxWithCorrectContentType()`.
- `shouldReturn404WhenArtifactNotFound()`.

**Story Points:** 3

---

#### Story 10.5.5 — Frontend Artifact Tab

**Acceptance Criteria (Gherkin):**

```gherkin
Given a completed job with 5 artifacts
When the Artifacts tab is opened in ResultPage
Then 5 artifact cards are displayed with filename, type icon, size, and Download button

When the Download button for clarification-questions.docx is clicked
Then the browser downloads the file
```

**Interfaces / Contracts:**

```typescript
// components/ArtifactDownload.tsx
interface ArtifactMetadata {
    jobId: string;
    filename: string;
    fileType: ArtifactFileType;
    sizeBytes: number;
    generatedAt: string;
    downloadUrl: string;
}

type ArtifactFileType = 'DOCX' | 'XLSX' | 'HTML';

interface ArtifactDownloadProps {
    jobId: string;
}

// components/AuditReportViewer.tsx
interface AuditReportViewerProps {
    jobId: string;
}
```

**Implementation Plan:**

1. `ArtifactDownload.tsx`:
    - Use `useQuery` to fetch `GET /api/v1/rfp/artifacts/{jobId}`.
    - Render a card grid (2 columns). Each card: file type icon (DOCX → blue doc icon, XLSX → green grid icon, HTML →
      orange code icon), filename, size formatted as KB/MB, generated-at relative time.
    - "Download" button: `<a href={downloadUrl} download>` pattern. `downloadUrl` =
      `/api/v1/rfp/artifacts/{jobId}/{filename}`.
2. `AuditReportViewer.tsx`:
    - Render
      `<iframe src="/api/v1/rfp/artifacts/{jobId}/audit-report.html" className="w-full h-[600px] border rounded">`.
3. `ResultPage.tsx`:
    - Add "Artifacts" tab → renders `<ArtifactDownload jobId={jobId} />`.
    - Add "Audit Report" tab → renders `<AuditReportViewer jobId={jobId} />`.

**Test Plan:** Manual visual verification.
**Story Points:** 5

---

## 4) PR Plan

| PR#      | Title                                                    | Files Changed                                                                                                                                                            | Merge Order | Dependencies                           |
|----------|----------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------|----------------------------------------|
| PR-10-01 | feat: bid clarity pack domain models                     | `rfp-core/domain/model/Clarification*.java`, `Ambiguity*.java`, `Compliance*.java`, `RiskItem.java`, `BidClarityPack.java`, `ArtifactMetadata.java`, `ArtifactPort.java` | 1st         | None                                   |
| PR-10-02 | feat: clarification question trigger & generator         | `ClarificationQuestionTrigger.java`, `ClarificationQuestionsGenerator.java`, `prompts/clarification-question-gen-v1.md`                                                  | 2nd         | PR-10-01                               |
| PR-10-03 | feat: DOCX writer for clarification questions            | `ClarificationQuestionsDocxWriter.java`, `templates/clarification-questions.ftl`                                                                                         | 3rd         | PR-10-01                               |
| PR-10-04 | feat: XLSX writers (ambiguity, compliance, risk)         | `AmbiguityRegisterXlsxWriter.java`, `ComplianceChecklistXlsxWriter.java`, `RiskLogXlsxWriter.java`, `prompts/risk-mitigation-suggestion-v1.md`                           | 3rd         | PR-10-01                               |
| PR-10-05 | feat: HTML audit report generator                        | `AuditReportGenerator.java`, `templates/audit-report.ftl`                                                                                                                | 3rd         | PR-10-01                               |
| PR-10-06 | feat: local artifact storage & application service       | `LocalArtifactStorageAdapter.java`, `ArtifactApplicationService.java`                                                                                                    | 4th         | PR-10-02, PR-10-03, PR-10-04, PR-10-05 |
| PR-10-07 | feat: FinalizeNode artifact integration + REST endpoints | `FinalizeNode.java`, `RfpController.java`, `ArtifactMetadataDto.java`                                                                                                    | 5th         | PR-10-06                               |
| PR-10-08 | feat: Artifacts tab & AuditReportViewer frontend         | `ArtifactDownload.tsx`, `AuditReportViewer.tsx`, `ResultPage.tsx`                                                                                                        | 6th         | PR-10-07                               |

---

## 5) Validation & Demo Script

```bash
# 1. Build and verify
cd rfp-extractor
mvn clean verify

# 2. Start services
docker-compose up -d
sleep 15

# 3. Submit a real ICT RFP
curl -s -F "file=@testdata/sample-ict-rfp.pdf" \
  http://localhost:8080/api/v1/rfp/submit | jq .

JOB_ID="<paste jobId>"

# 4. Poll until COMPLETED
while true; do
  STATUS=$(curl -s http://localhost:8080/api/v1/rfp/status/$JOB_ID | jq -r .status)
  echo "Status: $STATUS"
  [ "$STATUS" = "COMPLETED" ] && break
  sleep 5
done

# 5. List artifacts
curl -s http://localhost:8080/api/v1/rfp/artifacts/$JOB_ID | jq '[.[] | {filename, fileType, sizeBytes}]'

# Expected output (5 artifacts):
# [
#   {"filename":"clarification-questions.docx","fileType":"DOCX","sizeBytes":12345},
#   {"filename":"ambiguity-register.xlsx","fileType":"XLSX","sizeBytes":8901},
#   {"filename":"compliance-checklist.xlsx","fileType":"XLSX","sizeBytes":6789},
#   {"filename":"risk-log.xlsx","fileType":"XLSX","sizeBytes":5432},
#   {"filename":"audit-report.html","fileType":"HTML","sizeBytes":45678}
# ]

# 6. Download DOCX and verify it opens
curl -s -o /tmp/clarification-questions.docx \
  http://localhost:8080/api/v1/rfp/artifacts/$JOB_ID/clarification-questions.docx
file /tmp/clarification-questions.docx
# Expected: .../clarification-questions.docx: Microsoft Word 2007+

# 7. Download and open HTML audit report in browser
curl -s -o /tmp/audit-report.html \
  http://localhost:8080/api/v1/rfp/artifacts/$JOB_ID/audit-report.html
wc -l /tmp/audit-report.html
# Expected: > 100 lines

# 8. Open React UI → submit same doc → check "Artifacts" tab
echo "Open http://localhost:3000, navigate to completed job, click Artifacts tab"
echo "Verify 5 download cards appear. Click Download on DOCX."

# 9. Open "Audit Report" tab in UI
echo "Click Audit Report tab. Verify iframe loads with page breakdown table."
```

---

## 6) Exit Criteria

- [ ] `mvn clean verify` passes with zero failures.
- [ ] `docker-compose up` starts all services healthy.
- [ ] Submitting a test RFP produces all 5 artifacts under `{storage-base-path}/{jobId}/artifacts/`.
- [ ] `GET /api/v1/rfp/artifacts/{jobId}` returns 5 entries.
- [ ] `GET /api/v1/rfp/artifacts/{jobId}/clarification-questions.docx` streams a valid DOCX (not 404, not HTML error
  page).
- [ ] DOCX opens in LibreOffice/Word with numbered questions and clause source references.
- [ ] XLSX Ambiguity Register has colour-coded rows sorted by severity.
- [ ] HTML audit report renders in browser with summary stats and page-by-page table.
- [ ] `ArtifactApplicationService` unit test asserts 5 artifacts stored.
- [ ] `ClarificationQuestionTrigger` unit test covers MANDATORY, CONFIRMATION, and CONTRADICTION cases.
- [ ] Frontend "Artifacts" tab shows 5 download cards.
- [ ] Frontend "Audit Report" tab loads the HTML in an iframe.
- [ ] No class exceeds 250 lines. No method exceeds 20 lines.
- [ ] All new configuration keys documented in `docs/configuration.md`.

---

## 7) Notes & Assumptions

- **Freemarker vs programmatic POI for DOCX**: For the Clarification Questions document, the recommendation is
  programmatic Apache POI XWPF (not Freemarker). Freemarker templating of OOXML requires understanding of XML structure
  and is fragile. Use Freemarker only for the HTML audit report where templating is natural.
- **Self-contained HTML**: `audit-report.html` must have all CSS inline (`<style>` block) and no external font/script
  dependencies. This ensures it renders correctly when opened as a downloaded file with no internet connection.
- **Risk item source**: `RiskItem` objects are derived from FAIL rule findings (one risk item per HIGH/FATAL FAIL
  finding). The `riskDescription` is the `finding.getMessage()` value.
- **LLM call count for Risk Log**: If a document has many FATAL/HIGH failures, the risk log may trigger many LLM calls.
  Cap: generate mitigation suggestions for at most 10 risk items (highest severity first). For remaining items, use
  `"Manual assessment required"`.
- **ArtifactPort vs EncryptedArtifactStorageAdapter**: Sprint 11 will wrap `LocalArtifactStorageAdapter` with
  encryption. This sprint uses the plain adapter; the `@Primary` bean will be swapped in Sprint 11 without changing any
  service code.
- **Job ID in download URL**: The download URL format `/api/v1/rfp/artifacts/{jobId}/{filename}` is stable. Frontend
  stores the `jobId` from the job status query result.
- **Compliance checklist distinction**: The `ComplianceItem.mandatory` field is set to `true` for FATAL findings and
  `false` for HIGH findings. This reflects PPR 2008 mandatory vs advisory compliance requirements.
