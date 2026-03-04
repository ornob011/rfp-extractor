# Sprint 8 — Rule Pack Engine & ICT Rules

## 0) Sprint Intent

- Implement the complete rule pack infrastructure: YAML loader (networknt-validated), JMESPath structural evaluator (
  `io.burt:jmespath-java`), and LLM judgment checker so that any pack can be plugged in and run without code changes.
- Write `bd-govt-ict-v1.yaml` containing all 64 ICT-specific rules (55 structural + 9 semantic) derived from PPR 2008 /
  CPTU standards and the project's entity schema.
- Replace the `RunRulePackNode` stub in the LangGraph4J graph with a fully operational node that classifies the document
  type, selects the matching rule pack, and stores `RulePackResults` in `ExtractionState`.
- Surface colour-coded rule findings in the React frontend as a new "Rule Pack" tab on `ResultPage`.

**Non-goals:**

- Works, consultancy, and goods rule packs (Sprint 9).
- Hot-reload and admin API for rule packs (Sprint 9).
- Artifact generation that references rule findings (Sprint 10).

---

## 1) Entry Criteria

- Sprint 7 is merged and green on CI (`mvn test` passes).
- `ExtractionGraph` compiles with `RunRulePackNode` wired as a stub returning empty `RulePackResults`.
- `ExtractionState.rulePackResults` field exists and is nullable.
- `LlmAdapter.judgeSnippet(String prompt, String snippet)` must be **updated this sprint** to return
  `LlmJudgmentResult` (replacing the `Optional<String>` signature defined in Sprint 1).
  Add `LlmJudgmentResult` to `rfp-core` domain model, update `LlmAdapter`, and update all existing
  callers before wiring `LlmJudgmentChecker`.
  <!-- FIX [J]: Sprint 1 defined judgeSnippet() → Optional<String>. Sprint 8 needs a
       structured return type. This sprint must explicitly deliver the breaking change and
       migrate callers — it was not clearly called out as a deliverable. -->
- `FinalizeNode` assembles `RfpDocument` and serialises to JSON (Sprint 4).
- `RfpJsonSchemaValidator` loads `classpath:schema/rfp-schema-v1.json` (Sprint 3).
- `io.burt:jmespath-java` and `networknt:json-schema-validator` on classpath (Sprint 1 POM).
- Jackson YAML (`jackson-dataformat-yaml`) on classpath.
- `RuleFinding`, `RulePackResults` domain models exist in `rfp-core` (placeholder from Sprint 4).
- JUnit 5 + Mockito + AssertJ on classpath.

---

## 2) Deliverables

| #     | Deliverable                             | Type             | Location                                              |
|-------|-----------------------------------------|------------------|-------------------------------------------------------|
| D-00a | `LlmJudgmentResult` domain model        | Java class       | `rfp-core/.../domain/model/LlmJudgmentResult.java`    |
| D-00b | `LlmAdapter` — `judgeSnippet()` updated | Java class       | `adapter/llm/LlmAdapter.java`                         |
| D-01  | `RulePackDefinition` domain model       | Java class       | `rfp-core/.../domain/model/RulePackDefinition.java`   |
| D-02  | `RuleDefinition` domain model           | Java class       | `rfp-core/.../domain/model/RuleDefinition.java`       |
| D-03  | `RuleSeverity` enum                     | Java enum        | `rfp-core/.../domain/model/RuleSeverity.java`         |
| D-04  | `CheckType` enum                        | Java enum        | `rfp-core/.../domain/model/CheckType.java`            |
| D-05  | `RfpType` enum                          | Java enum        | `rfp-core/.../domain/model/RfpType.java`              |
| D-06  | `RuleStatus` enum                       | Java enum        | `rfp-core/.../domain/model/RuleStatus.java`           |
| D-07  | `RuleFinding` (updated)                 | Java class       | `rfp-core/.../domain/model/RuleFinding.java`          |
| D-08  | `RulePackResults` (updated)             | Java class       | `rfp-core/.../domain/model/RulePackResults.java`      |
| D-09  | `RulePackPort` port interface           | Java interface   | `rfp-core/.../domain/port/RulePackPort.java`          |
| D-10  | `RulePackLoader`                        | Spring component | `adapter/rulepack/RulePackLoader.java`                |
| D-11  | `JmesPathEvaluator`                     | Spring component | `adapter/rulepack/JmesPathEvaluator.java`             |
| D-12  | `LlmJudgmentChecker`                    | Spring component | `adapter/rulepack/LlmJudgmentChecker.java`            |
| D-13  | `RulePackRunner`                        | Spring component | `adapter/rulepack/RulePackRunner.java`                |
| D-14  | `RfpTypeClassifier`                     | Spring component | `adapter/rulepack/RfpTypeClassifier.java`             |
| D-15  | `RunRulePackNode` (full)                | LangGraph4J node | `agent/node/RunRulePackNode.java`                     |
| D-16  | `bd-govt-ict-v1.yaml`                   | YAML rule pack   | `rules/bd-govt-ict-v1.yaml`                           |
| D-17  | `rule-schema-v1.json`                   | JSON Schema      | `schema/rule-schema-v1.json`                          |
| D-18  | `rule-judgment-v1.md`                   | Prompt file      | `prompts/rule-judgment-v1.md`                         |
| D-19  | `RulePackRunnerTest`                    | JUnit 5          | `rfp-service/src/test/.../RulePackRunnerTest.java`    |
| D-20  | `RuleDslValidationTest`                 | JUnit 5          | `rfp-service/src/test/.../RuleDslValidationTest.java` |
| D-21  | `RulePackResults.tsx`                   | React component  | `rfp-frontend/src/components/RulePackResults.tsx`     |

---

## 3) Work Breakdown

### Epic 8.1 — Domain Models & Port Interface

#### Story 8.1.1 — Rule Pack Domain Models

**Acceptance Criteria (Gherkin):**

```gherkin
Given a YAML rule file is parsed into RuleDefinition objects
When the rule is a structural rule
Then checkType equals STRUCTURAL and condition is non-null

Given a YAML rule file is parsed into RuleDefinition objects
When the rule is a semantic rule
Then checkType equals SEMANTIC and llmPrompt is non-null
```

**Interfaces / Contracts:**

```java
// rfp-core/domain/model/RuleSeverity.java
public enum RuleSeverity {FATAL, HIGH, MEDIUM, LOW, INFO}

// rfp-core/domain/model/CheckType.java
public enum CheckType {STRUCTURAL, SEMANTIC}

// rfp-core/domain/model/RfpType.java
public enum RfpType {ICT, WORKS, CONSULTANCY, GOODS, UNKNOWN}

// rfp-core/domain/model/RuleStatus.java
public enum RuleStatus {PASS, FAIL, SKIPPED}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleDefinition {
    private String id;
    private String name;
    private String pack;
    private String version;
    private RuleSeverity severity;
    private CheckType checkType;
    private String condition;       // JMESPath expression (structural only)
    private String evidencePath;    // JMESPath path to extract evidence value
    private String llmPrompt;       // LLM prompt template (semantic only)
    private String message;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RulePackDefinition {
    private String packId;
    private String packVersion;
    private RfpType rfpType;
    private List<RuleDefinition> rules;
    private Instant loadedAt;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleFinding {
    private String ruleId;
    private RuleSeverity severity;
    private RuleStatus status;
    private String message;
    private String evidence;
    private Instant checkedAt;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RulePackResults {
    private String packId;
    private String packVersion;
    private Instant runTimestamp;
    private Map<RuleSeverity, Integer> summary;
    private List<RuleFinding> findings;
}

// rfp-core/domain/port/RulePackPort.java
public interface RulePackPort {
    Optional<RulePackDefinition> loadPack(String packId);

    List<RulePackDefinition> listPacks();

    RulePackResults runPack(RulePackDefinition pack, String rfpJson);
}
```

**Implementation Plan:**

1. Create all enum classes in `rfp-core/domain/model/`.
2. Create `RuleDefinition` with Lombok `@Data @Builder`. Include Jackson `@JsonProperty` annotations matching YAML field
   names (snake_case).
3. Create `RulePackDefinition` with `List<RuleDefinition>`.
4. Update `RuleFinding` to include `ruleId`, `severity`, `status`, `evidence`, `checkedAt`.
5. Update `RulePackResults` to include `packId`, `packVersion`, `runTimestamp`, `summary` map, `findings` list.
6. Create `RulePackPort` interface.

**Test Plan:**

- `shouldBuildRuleDefinitionWithAllFieldsWhenBuilderUsed()` — verify all fields accessible.
- `shouldComputeSummaryCorrectlyWhenFindingsContainMixedSeverities()` — test summary map population.

**Observability:** None at this layer (pure domain models).
**Story Points:** 3

---

### Epic 8.2 — Rule Pack Infrastructure

#### Story 8.2.1 — YAML Rule Pack Loader

**Acceptance Criteria (Gherkin):**

```gherkin
Given a valid YAML file exists in the rules/ directory
When RulePackLoader.loadAll() is called at startup
Then all rules are parsed into RuleDefinition objects and cached

Given a YAML file violates rule-schema-v1.json
When RulePackLoader loads the file
Then a RulePackLoadException is thrown with the validation errors listed
```

**Interfaces / Contracts:**

```java

@Component
@Slf4j
@RequiredArgsConstructor
public class RulePackLoader {
    // Scans classpath:rules/ directory for *.yaml files
    // Validates each against classpath:schema/rule-schema-v1.json
    // Caches in ConcurrentHashMap<String, RulePackDefinition>

    public Map<String, RulePackDefinition> loadAll();

    public Optional<RulePackDefinition> load(String packId);

    public void reload(String packId);    // re-reads single file from disk, updates cache
}
```

**Implementation Plan:**

1. In `@PostConstruct`, use `ResourcePatternResolver` to find all `classpath:rules/*.yaml` resources.
2. Parse each YAML file using `ObjectMapper` with `YAMLFactory`.
3. Convert parsed map to JSON string, validate against `JsonSchema` (networknt) loaded from
   `classpath:schema/rule-schema-v1.json`.
4. On validation failure: collect all `ValidationMessage` objects, throw `RulePackLoadException` listing each.
5. On success: map YAML content to `RulePackDefinition` using Jackson, store in `ConcurrentHashMap` keyed by `packId`.
6. Log `"Loaded rule pack: {} version: {} rules: {}"` for each pack.
7. `reload(packId)`: re-reads the specific file path, validates, replaces cache entry atomically.

**Test Plan:**

- `shouldLoadAllPacksWhenValidYamlFilesExist()` — mock `ResourcePatternResolver` with two valid YAML resources.
- `shouldThrowWhenYamlViolatesSchema()` — provide invalid YAML (missing required `id` field), assert
  `RulePackLoadException`.
- `shouldReloadPackWhenReloadCalled()` — mock file system, call `reload()`, verify cache updated.

**Observability:**

- Log at INFO: `"Rule pack loaded: packId={}, version={}, ruleCount={}"`.
- Log at WARN: `"Rule pack validation failed: file={}, errors={}"`  before throwing.

**Story Points:** 5

---

#### Story 8.2.2 — JMESPath Structural Rule Evaluator

**Acceptance Criteria (Gherkin):**

```gherkin
Given an RFP JSON with entities.general.client_name.value = "ICTD"
When JmesPathEvaluator.evaluateAsBoolean("entities.general.client_name.value != null", rfpJson) is called
Then true is returned

Given an RFP JSON where the field is null
When evaluateAsBoolean is called on a condition checking that field
Then false is returned (rule FAILS)

Given an invalid JMESPath expression
When evaluateAsBoolean is called
Then false is returned and a WARN log is emitted (rule SKIPPED)
```

**Interfaces / Contracts:**

```java

@Component
@Slf4j
public class JmesPathEvaluator {
    // Uses io.burt:jmespath-java

    /** Evaluates a JMESPath condition expression. Returns true = condition holds (rule PASSES). */
    public boolean evaluateAsBoolean(String condition, String rfpJson);

    /** Extracts a value at the given path. Returns empty if null or missing. */
    public Optional<String> extractEvidence(String evidencePath, String rfpJson);

    /** Raw JMESPath evaluation returning Object. */
    public Object evaluate(String expression, String rfpJson);
}
```

**Implementation Plan:**

1. Inject `JmesPath<JsonNode>` instance (from `io.burt:jmespath-java` using Jackson runtime).
2. `evaluate(expression, rfpJson)`: parse `rfpJson` with Jackson `ObjectMapper`, call
   `jmespath.compile(expression).search(node)`.
3. `evaluateAsBoolean(condition, rfpJson)`: call `evaluate()`, check result is non-null and not false/empty-string/0.
4. On `JmesPathException`: log WARN `"JMESPath error in condition '{}': {}"`, return `false`.
5. `extractEvidence(path, rfpJson)`: call `evaluate()`, convert result to string, return `Optional.ofNullable`.

**Test Plan:**

- `shouldReturnTrueWhenFieldPresentAndConditionMatches()` — inline JSON with known field.
- `shouldReturnFalseWhenFieldIsNull()` — JSON with null value.
- `shouldReturnFalseWhenExpressionIsInvalid()` — malformed JMESPath expression.
- `shouldExtractEvidenceStringWhenPathExists()` — verify `Optional.of("value")` returned.

**Observability:**

- WARN log on JMESPath parse error (includes expression and error message).

**Story Points:** 5

---

#### Story 8.2.3 — LLM Judgment Checker

**Acceptance Criteria (Gherkin):**

```gherkin
Given a semantic rule with an llmPrompt template containing {{evidence}}
When LlmJudgmentChecker.check(rule, "The scope is vague") is called
Then LlmAdapter.judgeSnippet() is called with the interpolated prompt and snippet

Given the LLM returns {"finding": true, "explanation": "...", "confidence": 0.9}
When check() parses the response
Then LlmJudgmentResult.finding is true

Given the LLM times out or returns invalid JSON
When check() is called
Then RuleStatus.SKIPPED is recorded and a WARN is logged (no exception propagated)
```

**Interfaces / Contracts:**

```java

@Data
@Builder
public class LlmJudgmentResult {
    private boolean finding;       // true = problem detected (rule FAILS)
    private String explanation;
    private double confidence;
    private RuleStatus status;     // PASS, FAIL, or SKIPPED
}

@Component
@Slf4j
@RequiredArgsConstructor
public class LlmJudgmentChecker {
    // Uses google/gemini-2.5-pro-preview-06-05 (judgment model)

    public LlmJudgmentResult check(RuleDefinition rule, String evidenceValue);

    /** Interpolates {{evidence}} placeholder in prompt template. */
    private String interpolatePrompt(String template, String evidence);
}
```

**Implementation Plan:**

1. `interpolatePrompt()`: replaces `{{evidence}}` in `rule.getLlmPrompt()` with `evidenceValue` (sanitized by
   `PromptInjectionFilter` — Sprint 11 will add this; for now wrap in `<document_content>` delimiters).
2. Call `LlmAdapter.judgeSnippet(interpolatedPrompt, evidenceValue)`.
3. Parse response JSON: `{"finding": boolean, "explanation": "...", "confidence": 0.0-1.0}`.
4. On parse failure or `LlmUnavailableException`: log WARN, return `LlmJudgmentResult` with `status=SKIPPED`.
5. Map `finding: true` → `status=FAIL`, `finding: false` → `status=PASS`.

**Test Plan:**

- `shouldReturnFailWhenLlmFindingIsTrue()` — mock `LlmAdapter` returning
  `{"finding":true,"explanation":"vague","confidence":0.9}`.
- `shouldReturnSkippedWhenLlmThrowsUnavailable()` — mock `LlmAdapter` throwing `LlmUnavailableException`.
- `shouldReturnSkippedWhenLlmReturnsInvalidJson()` — mock `LlmAdapter` returning non-JSON string.
- `shouldInterpolateEvidenceIntoPrompt()` — verify `{{evidence}}` replaced correctly.

**Observability:**

- INFO log per semantic check: `"Semantic rule {}: finding={}, confidence={}, latency_ms={}"`.
- WARN on SKIPPED: `"Semantic rule {} skipped: {}"`.

**Story Points:** 5

---

#### Story 8.2.4 — Rule Pack Runner

**Acceptance Criteria (Gherkin):**

```gherkin
Given a pack with 3 structural rules and 1 semantic rule
When RulePackRunner.run(pack, rfpJson) is called
Then 4 RuleFinding objects are returned with correct statuses

Given a structural rule where the JMESPath condition evaluates to false
When run() processes that rule
Then the finding has status=FAIL and evidence extracted from evidencePath

Given the evidence field for a semantic rule is null/empty
When run() encounters that rule
Then the finding has status=SKIPPED (not evaluated against LLM)
```

**Interfaces / Contracts:**

```java

@Component
@Slf4j
@RequiredArgsConstructor
public class RulePackRunner {
    // Max 250 lines. Delegates structural to JmesPathEvaluator, semantic to LlmJudgmentChecker.

    public RulePackResults run(RulePackDefinition pack, String rfpJson);

    private RuleFinding evaluateStructural(RuleDefinition rule, String rfpJson);

    private RuleFinding evaluateSemantic(RuleDefinition rule, String rfpJson);

    private Map<RuleSeverity, Integer> buildSummary(List<RuleFinding> findings);
}
```

**Implementation Plan:**

1. Iterate `pack.getRules()`. For each rule:
    - `STRUCTURAL`: call `JmesPathEvaluator.evaluateAsBoolean(rule.getCondition(), rfpJson)`. If false → FAIL. Extract
      evidence from `rule.getEvidencePath()`.
    - `SEMANTIC`: call `JmesPathEvaluator.extractEvidence(rule.getEvidencePath(), rfpJson)`. If empty → SKIPPED. Else
      call `LlmJudgmentChecker.check(rule, evidence)`.
2. Build `RuleFinding` for each rule with `ruleId`, `severity`, `status`, `message`, `evidence`,
   `checkedAt = Instant.now()`.
3. Compute summary: `Map<RuleSeverity, Integer>` counting findings by severity where status=FAIL.
4. Return `RulePackResults` with `packId`, `packVersion`, `runTimestamp = Instant.now()`, `summary`, `findings`.
5. Log: `"Rule pack {} completed: {} findings ({} FAIL, {} PASS, {} SKIPPED) in {}ms"`.

**Test Plan:**

- `shouldReturnPassFindingWhenStructuralConditionTrue()` — mock evaluator returns true.
- `shouldReturnFailFindingWhenStructuralConditionFalse()` — mock evaluator returns false.
- `shouldReturnSkippedWhenSemanticEvidenceIsEmpty()` — empty evidence path result.
- `shouldBuildCorrectSummaryWhenMixedFindings()` — verify summary map counts.
- `shouldRunAllRulesWhenPackHasMultipleRules()` — 3-rule pack, verify 3 findings.

**Observability:**

- INFO log per run (rule count, duration, FAIL/PASS/SKIPPED counts).
- Per-rule log at DEBUG: `"Rule {}: status={}, duration_ms={}"`.

**Story Points:** 8

---

#### Story 8.2.5 — RFP Type Classifier

**Acceptance Criteria (Gherkin):**

```gherkin
Given an RfpDocument with section titles containing "software" and "application"
When RfpTypeClassifier.classify(doc) is called
Then RfpType.ICT is returned

Given an RfpDocument with scope_summary containing "construction" and "civil works"
When classify() is called
Then RfpType.WORKS is returned

Given an RfpDocument with no matching keywords
When classify() is called
Then RfpType.UNKNOWN is returned
```

**Interfaces / Contracts:**

```java

@Component
@Slf4j
public class RfpTypeClassifier {
    public RfpType classify(RfpDocument doc);

    private int countKeywordMatches(String text, List<String> keywords);
}
```

**Implementation Plan:**

1. Collect all section titles from `doc.getSections()` +
   `doc.getEntities().getEvaluation().getScopeSummary().getValue()` into a single lowercase string.
2. Count keyword matches:
    - ICT: `["software", "system", "application", "database", "ict", " it ", "digital", "platform", "portal"]`
    - WORKS: `["construction", "civil", "building", "road", "bridge", "infrastructure", "earthwork"]`
    - CONSULTANCY: `["consultancy", "consulting", "advisory", "terms of reference", "tor", "consultants"]`
    - GOODS: `["procurement of goods", "supply of", "equipment", "hardware supply", "goods"]`
3. Return type with highest match count. If tie or all zero → `UNKNOWN`.
4. Log: `"RFP type classified as {} (ICT={}, WORKS={}, CONSULTANCY={}, GOODS={})"`.

**Test Plan:**

- `shouldClassifyAsIctWhenSectionTitlesContainIctKeywords()`.
- `shouldClassifyAsWorksWhenScopeSummaryContainsConstructionKeywords()`.
- `shouldReturnUnknownWhenNoKeywordsMatch()`.
- `shouldBreakTiesByHighestMatchCount()`.

**Observability:** INFO log with match counts per type.
**Story Points:** 3

---

### Epic 8.3 — RunRulePackNode Integration

#### Story 8.3.1 — RunRulePackNode Full Implementation

**Acceptance Criteria (Gherkin):**

```gherkin
Given ExtractionState contains a fully assembled RfpDocument serialised to JSON
When RunRulePackNode.process(state) is called
Then RfpTypeClassifier classifies the document type
And the matching rule pack is loaded and run
And state.rulePackResults is populated

Given document type is UNKNOWN
When RunRulePackNode runs
Then all available packs are run and findings are merged (deduped by ruleId)
```

**Interfaces / Contracts:**

```java

@Component
@Slf4j
@RequiredArgsConstructor
public class RunRulePackNode implements NodeAction<ExtractionState> {

    @Override
    public Map<String, Object> apply(ExtractionState state, RunnableConfig config);

    private RulePackResults runAllPacks(String rfpJson);

    private RulePackResults mergeResults(List<RulePackResults> results);
}
```

**Implementation Plan:**

1. Serialise `state.getRfpDocument()` to JSON using `ObjectMapper`.
2. Call `RfpTypeClassifier.classify(state.getRfpDocument())` → `rfpType`.
3. If `rfpType != UNKNOWN`: call `RulePackLoader.load(packIdFor(rfpType))`, run `RulePackRunner.run(pack, json)`.
4. If `UNKNOWN`: call `RulePackLoader.listPacks()`, run all, merge findings (dedup by `ruleId`, keep FAIL over PASS over
   SKIPPED).
5. Store result in `state.rulePackResults`.
6. Return updated state fields as `Map`.

**Test Plan:**

- `shouldRunIctPackWhenRfpTypeIsIct()` — mock classifier returning ICT, verify ICT pack loaded and run.
- `shouldMergeAllPackResultsWhenTypeUnknown()` — mock all packs, verify merge logic.

**Observability:** INFO log: `"Rule pack run complete: type={}, packId={}, findings={}"`.
**Story Points:** 5

---

### Epic 8.4 — ICT Rule Pack YAML

#### Story 8.4.1 — Write bd-govt-ict-v1.yaml (All 64 Rules)

**Acceptance Criteria (Gherkin):**

```gherkin
Given bd-govt-ict-v1.yaml is loaded by RulePackLoader
When RuleDslValidationTest runs
Then all 64 rules validate against rule-schema-v1.json without errors

Given an RFP JSON missing doc_meta.title
When RulePackRunner runs bd-govt-ict-v1 against the JSON
Then BD-ICT-001 finding has status=FAIL and severity=FATAL
```

**rule-schema-v1.json content:**

```json
{
    "$schema": "http://json-schema.org/draft-07/schema#",
    "title": "Rule Definition Schema",
    "type": "object",
    "required": [
        "id",
        "name",
        "pack",
        "version",
        "severity",
        "check_type",
        "evidence_path",
        "message"
    ],
    "properties": {
        "id": {
            "type": "string",
            "pattern": "^BD-[A-Z]+-[0-9]+$"
        },
        "name": {
            "type": "string",
            "minLength": 3
        },
        "pack": {
            "type": "string"
        },
        "version": {
            "type": "string",
            "pattern": "^[0-9]+\\.[0-9]+\\.[0-9]+$"
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
        "check_type": {
            "type": "string",
            "enum": [
                "structural",
                "semantic"
            ]
        },
        "condition": {
            "type": "string"
        },
        "evidence_path": {
            "type": "string"
        },
        "llm_prompt": {
            "type": "string"
        },
        "message": {
            "type": "string",
            "minLength": 5
        }
    },
    "if": {
        "properties": {
            "check_type": {
                "const": "structural"
            }
        }
    },
    "then": {
        "required": [
            "condition"
        ]
    },
    "else": {
        "required": [
            "llm_prompt"
        ]
    }
}
```

**Sample rules for bd-govt-ict-v1.yaml (write all 64 in actual file):**

```yaml
pack_id: bd-govt-ict-v1
pack_version: "1.0.0"
rfp_type: ICT
rules:
    -   id: BD-ICT-001
        name: RFP Title Present
        pack: bd-govt-ict-v1
        version: "1.0.0"
        severity: FATAL
        check_type: structural
        condition: "doc_meta.title != null && doc_meta.title != ''"
        evidence_path: "doc_meta.title"
        message: "RFP Title is missing from the document"

    -   id: BD-ICT-002
        name: RFP Identification Number Present
        pack: bd-govt-ict-v1
        version: "1.0.0"
        severity: FATAL
        check_type: structural
        condition: "doc_meta.procurement_ref != null && doc_meta.procurement_ref != ''"
        evidence_path: "doc_meta.procurement_ref"
        message: "RFP Identification Number (procurement reference) is missing"

    # ... (all 64 rules follow this pattern)
```

**Implementation Plan:**

1. Create `rules/bd-govt-ict-v1.yaml` with pack header and all 64 rules.
2. Create `schema/rule-schema-v1.json` as above.
3. For structural rules (BD-ICT-001 to BD-ICT-055): write JMESPath `condition` targeting the correct field in the RFP
   JSON schema.
4. For semantic rules (BD-ICT-056 to BD-ICT-064): write `llm_prompt` template with `{{evidence}}` placeholder.
5. Verify all 64 rules have required fields by running `RuleDslValidationTest`.

**All 64 rule IDs (structural + semantic):**

Structural (FATAL): BD-ICT-001 (title), BD-ICT-002 (ref), BD-ICT-004 (client), BD-ICT-005 (deadline), BD-ICT-014 (
tech/fin split), BD-ICT-040 (staffing criteria), BD-ICT-045 (evaluation criteria), BD-ICT-050 (eligibility)

Structural (HIGH): BD-ICT-003 (issue date), BD-ICT-006 (submission time), BD-ICT-007 (method of selection), BD-ICT-008 (
procurement method), BD-ICT-009 (project duration), BD-ICT-011 (submission guidelines), BD-ICT-015 (weights sum 100),
BD-ICT-016 (performance security), BD-ICT-017 (contact info), BD-ICT-018 (total users), BD-ICT-019 (concurrent users),
BD-ICT-021 (system language), BD-ICT-025 (staff months), BD-ICT-026 (integrations), BD-ICT-027 (hosting type),
BD-ICT-035 (training), BD-ICT-036 (support/maintenance), BD-ICT-037 (warranty), BD-ICT-038 (pricing factors),
BD-ICT-041 (payment terms), BD-ICT-042 (bank guarantee), BD-ICT-044 (e-governance), BD-ICT-046 (SLA), BD-ICT-047 (source
code ownership), BD-ICT-048 (data ownership), BD-ICT-049 (acceptance testing), BD-ICT-051 (tech qualification),
BD-ICT-052 (staff CVs), BD-ICT-053 (bid validity)

Structural (MEDIUM): BD-ICT-010 (pre-bid), BD-ICT-012 (copies), BD-ICT-013 (soft submission), BD-ICT-020 (prog
language), BD-ICT-022 (architecture), BD-ICT-023 (tech stack), BD-ICT-024 (database), BD-ICT-028 (data migration),
BD-ICT-029 (legacy system), BD-ICT-030 (hardware), BD-ICT-031 (onsite), BD-ICT-032 (UI mock), BD-ICT-033 (presentation),
BD-ICT-034 (reimbursable), BD-ICT-039 (gantt), BD-ICT-043 (mobile), BD-ICT-054 (deadline consistency)

Structural (INFO): BD-ICT-055 (amendments)

Semantic (HIGH): BD-ICT-056 (scope specific), BD-ICT-057 (acceptance measurable), BD-ICT-058 (no contradictions),
BD-ICT-062 (SLA quantified), BD-ICT-063 (integration API detail)

Semantic (MEDIUM): BD-ICT-059 (SLA realistic), BD-ICT-060 (payment tied to deliverables), BD-ICT-061 (training scope
bounded), BD-ICT-064 (deadline not too short)

**Test Plan:** Covered by `RuleDslValidationTest` below.
**Story Points:** 8

---

#### Story 8.4.2 — rule-judgment-v1.md Prompt File

**Implementation Plan:**

1. Create `/prompts/rule-judgment-v1.md` with frontmatter:

```markdown
---
id: rule-judgment
version: 1.0.0
model: google/gemini-2.5-pro-preview-06-05
max_tokens: 512
temperature: 0.0
---

You are a Government of Bangladesh procurement compliance expert specialising in PPR 2008 / CPTU standards.

Evaluate the following evidence extracted from an RFP/ToR document against the stated criterion.

Criterion: {{criterion}}

Evidence:
<document_content>
{{evidence}}
</document_content>

Reply ONLY with a JSON object. Do not include markdown code fences:
{"finding": true/false, "explanation": "one sentence", "confidence": 0.0-1.0}

finding=true means there IS a compliance problem.
finding=false means the criterion is satisfied.
```

**Story Points:** 1

---

### Epic 8.5 — Rule Pack Tests

#### Story 8.5.1 — RulePackRunnerTest

**Acceptance Criteria (Gherkin):**

```gherkin
Given an RFP JSON with doc_meta.title = "ICT Procurement"
When RulePackRunnerTest runs the PASS case for BD-ICT-001
Then the finding status is PASS

Given an RFP JSON with doc_meta.title = null
When RulePackRunnerTest runs the FAIL case for BD-ICT-001
Then the finding status is FAIL and severity is FATAL
```

**Implementation Plan:**

1. `@ExtendWith(MockitoExtension.class)` test class.
2. Mock `JmesPathEvaluator` and `LlmJudgmentChecker`.
3. `@ParameterizedTest` with a `@MethodSource` providing pairs of `(RuleDefinition, expectedStatus)`:
    - BD-ICT-001 PASS: mock evaluator returns `true` → assert `PASS`.
    - BD-ICT-001 FAIL: mock evaluator returns `false` → assert `FAIL, severity=FATAL`.
    - BD-ICT-005 PASS/FAIL: submission deadline.
    - BD-ICT-014 PASS/FAIL: tech/financial split.
    - BD-ICT-040 PASS/FAIL: staffing criteria.
4. Semantic test: mock `LlmJudgmentChecker.check()` returning `finding=true` → assert `FAIL`.
5. Semantic SKIPPED: mock empty evidence → assert `SKIPPED` without calling LLM.

**Story Points:** 5

---

#### Story 8.5.2 — RuleDslValidationTest

**Acceptance Criteria (Gherkin):**

```gherkin
Given all *.yaml files in src/main/resources/rules/
When RuleDslValidationTest runs
Then every rule in every file validates against rule-schema-v1.json with zero errors
```

**Implementation Plan:**

1. Use `Paths.get("src/main/resources/rules")` to find all `*.yaml` files.
2. Parse each with `ObjectMapper(YAMLFactory)` → `List<Map>` of rule objects.
3. For each rule map: serialise to JSON, validate against `JsonSchema` loaded from
   `src/main/resources/schema/rule-schema-v1.json`.
4. Collect all validation errors. Assert `errors.isEmpty()` with
   `assertThat(errors).as("Rule validation errors in %s", yamlFile).isEmpty()`.

**Story Points:** 3

---

### Epic 8.6 — Frontend Rule Pack Results

#### Story 8.6.1 — RulePackResults React Component

**Acceptance Criteria (Gherkin):**

```gherkin
Given rule pack results with findings of mixed severities
When RulePackResults is rendered
Then findings are grouped by severity in order FATAL → HIGH → MEDIUM → LOW → INFO
And each severity group has a color-coded header
And FAIL findings show a red badge, PASS show green, SKIPPED show grey
```

**Interfaces / Contracts:**

```typescript
// types/rulepack.ts
export interface RuleFinding {
    ruleId: string;
    severity: 'FATAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO';
    status: 'PASS' | 'FAIL' | 'SKIPPED';
    message: string;
    evidence?: string;
}

export interface RulePackResults {
    packId: string;
    packVersion: string;
    runTimestamp: string;
    summary: Record<string, number>;
    findings: RuleFinding[];
}

interface RulePackResultsProps {
    results: RulePackResults;
}
```

**Implementation Plan:**

1. Create `src/types/rulepack.ts` with interfaces above.
2. `RulePackResults.tsx` — uses shadcn `Table` and `Badge`:
    - Group findings by severity using `Object.groupBy` or `reduce`.
    - Render severity groups in FATAL → INFO order.
    - Severity group headers: styled divs with semantic className mapping:
      `FATAL: border-red-200 bg-red-50 text-red-800`, `HIGH: border-orange-200 bg-orange-50 text-orange-800`,
      `MEDIUM: border-yellow-200 bg-yellow-50 text-yellow-800`, `LOW: border-blue-200 bg-blue-50 text-blue-800`,
      `INFO: border-gray-200 bg-gray-50 text-gray-700`.
    - Summary bar at top: count of FATAL FAIL, HIGH FAIL, etc.
    - Each finding row: rendered using shadcn `<Table>`. Rule ID (monospace), name, status badge, evidence (truncated
      80 chars with expand button).
    - Status badges using shadcn `<Badge>`:
        - `FAIL` → `<Badge variant="destructive">FAIL</Badge>`
        - `PASS` → `<Badge variant="outline" className="text-green-700 border-green-300">PASS</Badge>`
        - `SKIPPED` → `<Badge variant="secondary">SKIPPED</Badge>`
3. Add "Rule Pack" `<TabsTrigger>` and `<TabsContent>` to the existing shadcn `<Tabs>` in `ResultPage.tsx`.

**Story Points:** 5

---

## 4) PR Plan

| PR#     | Title                                            | Files Changed                                                                                                | Merge Order | Dependencies     |
|---------|--------------------------------------------------|--------------------------------------------------------------------------------------------------------------|-------------|------------------|
| PR-8-01 | feat: rule pack domain models & port interface   | `rfp-core/domain/model/Rule*.java`, `RuleStatus.java`, `CheckType.java`, `RfpType.java`, `RulePackPort.java` | 1st         | None             |
| PR-8-02 | feat: JMESPath evaluator & YAML rule pack loader | `JmesPathEvaluator.java`, `RulePackLoader.java`, `rule-schema-v1.json`                                       | 2nd         | PR-8-01          |
| PR-8-03 | feat: LLM judgment checker & rule pack runner    | `LlmJudgmentChecker.java`, `RulePackRunner.java`, `RfpTypeClassifier.java`                                   | 3rd         | PR-8-02          |
| PR-8-04 | feat: bd-govt-ict-v1.yaml (64 rules) + schema    | `rules/bd-govt-ict-v1.yaml`, `schema/rule-schema-v1.json`, `prompts/rule-judgment-v1.md`                     | 4th         | PR-8-02          |
| PR-8-05 | feat: RunRulePackNode integration                | `RunRulePackNode.java`                                                                                       | 5th         | PR-8-03, PR-8-04 |
| PR-8-06 | test: RulePackRunnerTest & RuleDslValidationTest | `*Test.java` files                                                                                           | 6th         | PR-8-03, PR-8-04 |
| PR-8-07 | feat: RulePackResults React component            | `RulePackResults.tsx`, `rulepack.ts`, `ResultPage.tsx`                                                       | 7th         | PR-8-05          |

---

## 5) Validation & Demo Script

```bash
# 1. Build and verify all tests pass
cd rfp-extractor
mvn test

# 2. Start all services
docker-compose up -d
sleep 15

# 3. Health check
curl -s http://localhost:8080/api/v1/health | jq .

# 4. Submit an ICT RFP missing the mandatory title field
# Prepare a minimal test PDF or use existing test fixture
curl -s -F "file=@testdata/sample-ict-rfp.pdf" \
  http://localhost:8080/api/v1/rfp/submit | jq .

# 5. Poll until completed
JOB_ID="<from above>"
watch -n 3 "curl -s http://localhost:8080/api/v1/rfp/status/$JOB_ID | jq .status"

# 6. Retrieve result and inspect rule pack findings
curl -s http://localhost:8080/api/v1/rfp/result/$JOB_ID | \
  jq '.rule_pack_results.findings[] | select(.status == "FAIL" and .severity == "FATAL")'

# Expected: BD-ICT-001, BD-ICT-002, BD-ICT-004, BD-ICT-005 listed as FATAL FAIL
# if document is missing those fields

# 7. Verify RuleDslValidationTest passes explicitly
mvn -pl rfp-service test -Dtest=RuleDslValidationTest

# Expected: BUILD SUCCESS, 0 failures
```

---

## 6) Exit Criteria

- [ ] `mvn test` passes with zero test failures.
- [ ] `RuleDslValidationTest` asserts all 64 ICT rules validate against `rule-schema-v1.json`.
- [ ] `RulePackRunnerTest` covers BD-ICT-001, BD-ICT-005, BD-ICT-014, BD-ICT-040 with both PASS and FAIL cases.
- [ ] Submitting a test document produces `rule_pack_results` in the JSON response with `packId: "bd-govt-ict-v1"`.
- [ ] FATAL rules fire on a document deliberately missing title, client name, submission deadline, and evaluation
  criteria.
- [ ] `RunRulePackNode` in the LangGraph4J graph is no longer a stub — it calls `RulePackRunner`.
- [ ] `RulePackResults.tsx` renders findings grouped by severity, colour-coded.
- [ ] No class exceeds 250 lines. No method exceeds 20 lines.
- [ ] No `@Autowired` field injection anywhere.
- [ ] All new config keys documented in `docs/configuration.md`.

---

## 7) Notes & Assumptions

- **JMESPath condition semantics**: `condition` evaluates to `true` = rule PASSES (field present/valid). `false` = rule
  FAILS. This is the opposite of some rule engines; we chose "condition = desired state" for readability.
- **Semantic rule short-circuit**: If `evidence_path` resolves to null/empty for a semantic rule, the rule is SKIPPED (
  not FAIL) — we cannot judge evidence that doesn't exist.
- **UNKNOWN type**: When `RfpTypeClassifier` returns UNKNOWN, all loaded packs run. Findings are merged; if the same
  `ruleId` exists in multiple packs, FAIL takes precedence over PASS over SKIPPED.
- **BD-ICT-054 simplification**: The deadline < bid validity end check is simplified to a presence check (both fields
  non-null). Full date arithmetic is deferred — it requires parsing multiple date formats from LLM-extracted strings (
  brittle without a dedicated date normaliser).
- **YAML top-level structure**: The YAML file has a `pack_id`, `pack_version`, `rfp_type` header and a `rules:` list.
  `RulePackLoader` reads the header to populate `RulePackDefinition`, then maps each item in `rules:` to
  `RuleDefinition`.
- **Model for judgment**: `google/gemini-2.5-pro-preview-06-05` is used for all `LlmJudgmentChecker` calls (the judgment
  model). Extraction calls still use `google/gemini-2.0-flash-001`.
- **Resilience4j on judgment calls**: The existing `LlmAdapter` Resilience4j wrapper (Sprint 1) covers all LLM calls
  including judgment. No additional wrapping needed in `LlmJudgmentChecker`.
