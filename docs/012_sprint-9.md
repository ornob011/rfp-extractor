# Sprint 9 — Additional Rule Packs & Hot Reload

## 0) Sprint Intent

- Deliver three production-ready YAML rule packs covering GOB Works contracts (civil/construction), Consultancy/ToR
  contracts, and Goods procurement, each with 30+ rules authored according to the same schema as the Sprint 8 ICT pack.
- Verify and demonstrate end-to-end hot-reload: when any YAML file under `rules/` is modified on disk, the WatchService
  polling loop (already built in Sprint 8) detects the change within 30 seconds and reloads the pack without a service
  restart.
- Expose an Admin REST API (`/api/v1/admin/rule-packs`) backed by `RulePackAdminService` and `AdminRulePackController`
  that allows operators to list loaded packs and trigger a manual reload.
- Update `RfpTypeClassifier` with Works and Consultancy keyword patterns so that the correct pack is automatically
  selected when a document is submitted.
- Add `AdminPage.tsx` in the React frontend showing loaded rule packs and a "Reload All" button, and update
  `ResultPage.tsx` to display the detected RFP type badge.

**Non-goals:**

- Artifact generation (Sprint 10).
- Security and authentication (Sprint 11) — admin endpoints return 200 without auth check in this sprint; a `TODO`
  comment marks the gap.
- Monitoring and operational hardening (Sprint 12).

---

## 1) Entry Criteria

- Sprint 8 is complete and merged: `RulePackLoader`, `JmesPathEvaluator`, `LlmJudgmentChecker`, `RulePackRunner`,
  `RfpTypeClassifier` (ICT + GOODS patterns), `RunRulePackNode`, `rules/bd-govt-ict-v1.yaml` (64 rules),
  `RulePackApplicationService`, `RulePackResults.tsx`, and 64 parameterized JUnit tests are all present and green.
- `RulePackLoader` already implements `WatchService` polling on `classpath:rules/` or a configurable directory (
  `app.rules.directory`), reloading packs on change.
- `rule-schema-v1.json` (networknt) exists and validates `bd-govt-ict-v1.yaml` successfully.
- `mvn test` is green on the Sprint 8 codebase.
- The Sprint 8 `RfpTypeClassifier` recognizes `ICT` and `GOODS` types; the class is open for extension (keyword map is a
  mutable data structure, not a switch statement).

---

## 2) Deliverables

- `rules/bd-govt-works-v1.yaml` — 33 rules (30 structural + 3 semantic).
- `rules/bd-govt-consultancy-v1.yaml` — 33 rules (30 structural + 3 semantic).
- `rules/bd-govt-goods-v1.yaml` — 22 rules (20 structural + 2 semantic).
- `RfpTypeClassifier.java` updated with WORKS and CONSULTANCY keyword groups.
- `RulePackAdminService.java` — `application/service/`.
- `AdminRulePackController.java` — `adapter/api/`.
- `AdminPage.tsx` — React frontend.
- `ResultPage.tsx` — updated with RFP type badge and type-based rule pack tab label.
- 88 new parameterized JUnit 5 tests (33 + 33 + 22) in `RulePackWorksTest`, `RulePackConsultancyTest`,
  `RulePackGoodsTest`.
- `AdminRulePackControllerTest.java` — 6 unit tests.

---

## 3) Work Breakdown

### Epic 1 — Works Rule Pack (bd-govt-works-v1.yaml)

#### Story 1.1 — Author bd-govt-works-v1.yaml (30 Structural Rules)

**Description:**
Author a YAML rule pack for GOB civil/construction works contracts. The pack must conform to
`schema/rule-schema-v1.json` and be loadable by `RulePackLoader` without errors. All JMESPath expressions are verified
against the `RfpDocument` JSON structure established in Sprint 4. Structural rules use JMESPath; semantic rules invoke
the LLM judgment checker.

**Acceptance Criteria:**

```gherkin
Given rules/bd-govt-works-v1.yaml exists
When RulePackLoader.loadAll() is called
Then the pack with id "bd-govt-works-v1" is present in loadedPacks with ruleCount = 33

Given an RfpDocument with title = null
When RulePackRunner.run(doc, "bd-govt-works-v1") is called
Then finding for rule BD-W-001 has status = FAIL and severity = FATAL

Given an RfpDocument with entities.pricing_factors containing the string "liquidated damages"
When RulePackRunner.run(doc, "bd-govt-works-v1") is called
Then finding for rule BD-W-016 has status = PASS

Given rules/bd-govt-works-v1.yaml is written to disk
When mvn test -pl rfp-service -Dtest=RulePackWorksTest is run
Then all 33 parameterized tests pass with exit code 0
```

**Interfaces/Contracts:**

Rule pack YAML header contract (matches rule-schema-v1.json):

```yaml
id: bd-govt-works-v1
name: "BD Government Works Contracts"
version: "1.0.0"
rfpType: WORKS
description: "Rules for civil/construction works contracts under PPR 2008"
rules:
    -   id: BD-W-001
        name: "RFP Title Present"
        severity: FATAL
        type: STRUCTURAL
        jmesPath: "metadata.title"
        condition: NOT_NULL_OR_EMPTY
        message: "RFP title (metadata.title) is missing or empty."
        category: "MANDATORY_FIELDS"
```

`RulePackRunner.run(RfpDocument doc, String packId)` returns `RulePackResult` — unchanged from Sprint 8.

**Implementation Plan:**

File: `rfp-extractor/rules/bd-govt-works-v1.yaml`

Complete rule list:

```yaml
id: bd-govt-works-v1
name: "BD Government Works Contracts"
version: "1.0.0"
rfpType: WORKS
description: "Structural and semantic rules for GOB civil/construction works contracts under PPR 2008 and standard CPTU SBD-Works templates."
rules:

    # ── MANDATORY FIELDS ────────────────────────────────────────────────────────

    -   id: BD-W-001
        name: "RFP Title Present"
        severity: FATAL
        type: STRUCTURAL
        jmesPath: "metadata.title"
        condition: NOT_NULL_OR_EMPTY
        message: "RFP title (metadata.title) is missing or empty."
        category: "MANDATORY_FIELDS"

    -   id: BD-W-002
        name: "Procurement Reference Present"
        severity: FATAL
        type: STRUCTURAL
        jmesPath: "metadata.procurement_ref"
        condition: NOT_NULL_OR_EMPTY
        message: "Procurement reference number (metadata.procurement_ref) is missing."
        category: "MANDATORY_FIELDS"

    -   id: BD-W-003
        name: "Client / Employer Name Present"
        severity: FATAL
        type: STRUCTURAL
        jmesPath: "metadata.client_name"
        condition: NOT_NULL_OR_EMPTY
        message: "Client/employer name (metadata.client_name) is missing."
        category: "MANDATORY_FIELDS"

    -   id: BD-W-004
        name: "Submission Deadline Present"
        severity: FATAL
        type: STRUCTURAL
        jmesPath: "entities.submission_deadline"
        condition: NOT_NULL_OR_EMPTY
        message: "Submission deadline (entities.submission_deadline) is missing. This is a PPR 2008 mandatory field."
        category: "MANDATORY_FIELDS"

    -   id: BD-W-005
        name: "Contractor Eligibility / Experience Requirement Present"
        severity: FATAL
        type: STRUCTURAL
        jmesPath: "entities.eligibility_summary"
        condition: NOT_NULL_OR_EMPTY
        message: "Eligibility/experience requirements for the contractor (entities.eligibility_summary) are absent. Contractor qualification criteria (minimum years of experience, annual turnover) must be stated."
        category: "MANDATORY_FIELDS"

    -   id: BD-W-006
        name: "Scope of Work / Bill of Quantities Present"
        severity: FATAL
        type: STRUCTURAL
        jmesPath: "sections[?contains(heading, 'scope') || contains(heading, 'bill of quantities') || contains(heading, 'work schedule') || contains(heading, 'boq')]"
        condition: NOT_EMPTY_ARRAY
        message: "No section heading matching scope of work, bill of quantities, or work schedule was found. A BOQ or detailed work schedule is mandatory for works contracts."
        category: "SCOPE"

    -   id: BD-W-007
        name: "Evaluation Criteria Present"
        severity: FATAL
        type: STRUCTURAL
        jmesPath: "entities.evaluation_criteria"
        condition: NOT_NULL_OR_EMPTY
        message: "Evaluation criteria (entities.evaluation_criteria) are missing. PPR 2008 requires explicit evaluation criteria for works contracts."
        category: "EVALUATION"

    -   id: BD-W-009
        name: "Technical-Financial Split Specified"
        severity: FATAL
        type: STRUCTURAL
        jmesPath: "entities.technical_financial_split"
        condition: NOT_NULL_OR_EMPTY
        message: "Technical-financial split ratio (entities.technical_financial_split) is not specified. Works contracts must state whether evaluation is single-stage or two-envelope."
        category: "EVALUATION"

    -   id: BD-W-027
        name: "Method of Selection Specified"
        severity: HIGH
        type: STRUCTURAL
        jmesPath: "entities.method_of_selection"
        condition: NOT_NULL_OR_EMPTY
        message: "Method of selection (entities.method_of_selection) is not stated (e.g., LCB, ICB, NCB, Direct Contracting)."
        category: "PROCUREMENT_METHOD"

    -   id: BD-W-028
        name: "Procurement Method Stated"
        severity: HIGH
        type: STRUCTURAL
        jmesPath: "entities.procurement_method"
        condition: NOT_NULL_OR_EMPTY
        message: "Procurement method (entities.procurement_method) is missing (e.g., Open Tendering, Limited Tendering)."
        category: "PROCUREMENT_METHOD"

    # ── HIGH SEVERITY ────────────────────────────────────────────────────────────

    -   id: BD-W-008
        name: "Evaluation Criteria Weights Sum ~100"
        severity: HIGH
        type: STRUCTURAL
        jmesPath: "entities.evaluation_criteria_total_weight"
        condition: BETWEEN_95_AND_105
        message: "Evaluation criteria weights do not sum to approximately 100 (entities.evaluation_criteria_total_weight = {value}). Weights must sum to 100 ± 5."
        category: "EVALUATION"

    -   id: BD-W-010
        name: "Performance Security Percentage Stated"
        severity: HIGH
        type: STRUCTURAL
        jmesPath: "entities.performance_security_pct"
        condition: NOT_NULL_OR_EMPTY
        message: "Performance security percentage (entities.performance_security_pct) is missing. Standard GOB works contracts require 10% of contract price."
        category: "FINANCIAL_SECURITY"

    -   id: BD-W-011
        name: "Bank Guarantee Mentioned"
        severity: HIGH
        type: STRUCTURAL
        jmesPath: "entities.pricing_factors[?contains(@, 'bank guarantee') || contains(@, 'bank guaranty')]"
        condition: NOT_EMPTY_ARRAY
        message: "No mention of bank guarantee in pricing_factors. Works contracts typically require a bank guarantee as performance security."
        category: "FINANCIAL_SECURITY"

    -   id: BD-W-012
        name: "Payment Terms Stated"
        severity: HIGH
        type: STRUCTURAL
        jmesPath: "entities.payment_terms"
        condition: NOT_NULL_OR_EMPTY
        message: "Payment terms (entities.payment_terms) are absent. Payment milestones and schedule must be defined for works contracts."
        category: "FINANCIAL"

    -   id: BD-W-013
        name: "Project Duration / Completion Period Stated"
        severity: HIGH
        type: STRUCTURAL
        jmesPath: "entities.project_duration"
        condition: NOT_NULL_OR_EMPTY
        message: "Project duration/completion period (entities.project_duration) is not specified."
        category: "SCHEDULE"

    -   id: BD-W-014
        name: "Submission Address / Contact Stated"
        severity: HIGH
        type: STRUCTURAL
        jmesPath: "entities.submission_address"
        condition: NOT_NULL_OR_EMPTY
        message: "Submission address or contact information (entities.submission_address) is missing."
        category: "SUBMISSION"

    -   id: BD-W-015
        name: "Bid Validity Period Stated"
        severity: HIGH
        type: STRUCTURAL
        jmesPath: "entities.bid_validity_days"
        condition: NOT_NULL_OR_EMPTY
        message: "Bid validity period (entities.bid_validity_days) is not stated."
        category: "SUBMISSION"

    -   id: BD-W-016
        name: "Liquidated Damages Clause Present"
        severity: HIGH
        type: STRUCTURAL
        jmesPath: "entities.pricing_factors[?contains(@, 'liquidated') || contains(@, 'ld clause') || contains(@, 'penalty')]"
        condition: NOT_EMPTY_ARRAY
        message: "No liquidated damages clause found in pricing_factors. Works contracts must specify LD rates for delay."
        category: "CONTRACT_CONDITIONS"

    -   id: BD-W-017
        name: "Retention Money Clause Present"
        severity: HIGH
        type: STRUCTURAL
        jmesPath: "entities.pricing_factors[?contains(@, 'retention')]"
        condition: NOT_EMPTY_ARRAY
        message: "No retention money clause found in pricing_factors. Standard GOB works contracts retain 10% (5% released at completion, 5% at defects liability end)."
        category: "FINANCIAL"

    -   id: BD-W-018
        name: "Defects Liability Period Stated"
        severity: HIGH
        type: STRUCTURAL
        jmesPath: "entities.support_maintenance_period"
        condition: NOT_NULL_OR_EMPTY
        message: "Defects liability period (entities.support_maintenance_period or pricing_factors) is not stated."
        category: "CONTRACT_CONDITIONS"

    -   id: BD-W-022
        name: "Key Personnel / Staff Months Requirement Stated"
        severity: HIGH
        type: STRUCTURAL
        jmesPath: "entities.key_personnel"
        condition: NOT_NULL_OR_EMPTY
        message: "Key personnel or staff months requirement (entities.key_personnel) is not specified."
        category: "PERSONNEL"

    # ── MEDIUM SEVERITY ───────────────────────────────────────────────────────────

    -   id: BD-W-019
        name: "Site Inspection / Visit Requirement Stated"
        severity: MEDIUM
        type: STRUCTURAL
        jmesPath: "entities.other_info[?contains(@, 'site') || contains(@, 'site visit') || contains(@, 'site inspection')]"
        condition: NOT_EMPTY_ARRAY
        message: "Site inspection/visit requirement not found in other_info or pricing_factors."
        category: "SUBMISSION"

    -   id: BD-W-020
        name: "Pre-Bid Meeting Information Present"
        severity: MEDIUM
        type: STRUCTURAL
        jmesPath: "entities.pre_bid_meeting"
        condition: NOT_NULL_OR_EMPTY
        message: "Pre-bid meeting details (entities.pre_bid_meeting) are missing."
        category: "SUBMISSION"

    -   id: BD-W-021
        name: "Number of Bid Copies Stated"
        severity: MEDIUM
        type: STRUCTURAL
        jmesPath: "entities.number_of_copies"
        condition: NOT_NULL_OR_EMPTY
        message: "Number of bid copies required (entities.number_of_copies) is not stated."
        category: "SUBMISSION"

    -   id: BD-W-023
        name: "Reimbursable Expenses Policy Stated"
        severity: MEDIUM
        type: STRUCTURAL
        jmesPath: "entities.reimbursable_expenses"
        condition: NOT_NULL_OR_EMPTY
        message: "Reimbursable expenses policy (entities.reimbursable_expenses) is not defined."
        category: "FINANCIAL"

    -   id: BD-W-024
        name: "Extension of Time Clause Present"
        severity: MEDIUM
        type: STRUCTURAL
        jmesPath: "entities.pricing_factors[?contains(@, 'extension of time') || contains(@, 'eot')]"
        condition: NOT_EMPTY_ARRAY
        message: "No extension of time (EOT) clause found in pricing_factors or other_info."
        category: "CONTRACT_CONDITIONS"

    -   id: BD-W-025
        name: "Mobilization Advance Mentioned"
        severity: MEDIUM
        type: STRUCTURAL
        jmesPath: "entities.pricing_factors[?contains(@, 'mobilization') || contains(@, 'advance payment')]"
        condition: NOT_EMPTY_ARRAY
        message: "Mobilization advance/advance payment clause not found in pricing_factors."
        category: "FINANCIAL"

    -   id: BD-W-026
        name: "Insurance Requirements Stated"
        severity: MEDIUM
        type: STRUCTURAL
        jmesPath: "entities.pricing_factors[?contains(@, 'insurance')] || entities.other_info[?contains(@, 'insurance')]"
        condition: NOT_EMPTY_ARRAY
        message: "Insurance requirements are not specified in pricing_factors or other_info."
        category: "CONTRACT_CONDITIONS"

    -   id: BD-W-029
        name: "Programme of Work / Gantt Chart Required"
        severity: MEDIUM
        type: STRUCTURAL
        jmesPath: "entities.other_info[?contains(@, 'programme') || contains(@, 'gantt') || contains(@, 'work schedule')]"
        condition: NOT_EMPTY_ARRAY
        message: "No requirement for programme of work or Gantt chart found in other_info."
        category: "SCHEDULE"

    -   id: BD-W-030
        name: "On-Site Resource Requirements Stated"
        severity: MEDIUM
        type: STRUCTURAL
        jmesPath: "entities.onsite_resources"
        condition: NOT_NULL_OR_EMPTY
        message: "On-site resource requirements (entities.onsite_resources) are not specified."
        category: "PERSONNEL"

    # ── SEMANTIC RULES (LLM) ─────────────────────────────────────────────────────

    -   id: BD-W-031
        name: "Scope of Works Is Sufficiently Specific to Estimate"
        severity: HIGH
        type: SEMANTIC
        evidenceJmesPath: "entities.scope_of_work"
        promptKey: "rule-judgment-v1"
        judgmentInstruction: "Assess whether the scope of works described is sufficiently specific for a contractor to accurately prepare a cost estimate and bill of quantities. It must define work types, approximate quantities, site conditions, and materials. Vague language like 'miscellaneous civil works' without elaboration is insufficient. Return {pass: true/false, confidence: 0.0-1.0, reason: '...'}."
        message: "Scope of works is insufficiently specific for cost estimation. A contractor cannot reliably price the BOQ without more detail."
        category: "SEMANTIC_SCOPE"

    -   id: BD-W-032
        name: "Payment Terms Tied to Measurable Milestones"
        severity: MEDIUM
        type: SEMANTIC
        evidenceJmesPath: "entities.payment_terms"
        promptKey: "rule-judgment-v1"
        judgmentInstruction: "Determine whether the payment terms are clearly tied to measurable, objective milestones (e.g., completion of foundation work, structural work, finishing, commissioning) rather than vague time periods or discretionary events. Return {pass: true/false, confidence: 0.0-1.0, reason: '...'}."
        message: "Payment terms are not clearly tied to measurable milestones, creating dispute risk."
        category: "SEMANTIC_FINANCIAL"

    -   id: BD-W-033
        name: "Liquidated Damages Rate Is Proportional"
        severity: MEDIUM
        type: SEMANTIC
        evidenceJmesPath: "entities.pricing_factors[?contains(@, 'liquidated')]"
        promptKey: "rule-judgment-v1"
        judgmentInstruction: "Evaluate whether the liquidated damages (LD) rate per day is proportional and commercially reasonable (typically 0.05%–0.15% of contract value per day, up to a cap of 5%–10%). An LD rate exceeding 0.5% per day without a cap, or an uncapped LD clause, is excessive and constitutes a red flag. Return {pass: true/false, confidence: 0.0-1.0, reason: '...'}."
        message: "Liquidated damages rate appears disproportionate (too high or uncapped). Seek clarification before bidding."
        category: "SEMANTIC_FINANCIAL"
```

**Dependencies:** Sprint 8 `RulePackLoader`, `RulePackRunner`, `RuleFinding`, `rule-schema-v1.json`.

**Risks:**

- JMESPath expressions against array fields (e.g., `pricing_factors`) use string `contains()` which requires array
  elements to be strings. If `pricing_factors` is `List<Map<String, Object>>` rather than `List<String>`, expressions
  will fail silently returning empty arrays (PASS when should FAIL).
- Mitigation: Confirm `pricing_factors` in `RfpDocument` is `List<String>` (confirmed Sprint 4 schema). Add a note in
  `§7` if schema is ambiguous.

**Test Plan:**

Class: `rfp-service/src/test/java/com/dsi/rfp/adapter/rulepack/RulePackWorksTest.java`

```java

@ParameterizedTest
@MethodSource("worksRuleTestCases")
void testWorksRule(String ruleId, RfpDocument doc, RuleFindingStatus expectedStatus) {
    RulePackResult result = rulePackRunner.run(doc, "bd-govt-works-v1");
    RuleFinding finding = result.findById(ruleId);
    assertThat(finding.status()).isEqualTo(expectedStatus);
}

static Stream<Arguments> worksRuleTestCases() {
    return Stream.of(
        Arguments.of("BD-W-001", docWithTitle(null), FAIL),
        Arguments.of("BD-W-001", docWithTitle("Construction of School"), PASS),
        Arguments.of("BD-W-004", docWithDeadline(null), FAIL),
        Arguments.of("BD-W-016", docWithPricingFactors(List.of("liquidated damages 0.1% per day")), PASS),
        Arguments.of("BD-W-016", docWithPricingFactors(List.of("payment terms")), FAIL),
        // ... 28 more cases covering all structural rules
    );
}
```

Mock: `LlmJudgmentChecker` — stub semantic rules to return PASS with confidence 0.9 in unit tests. Semantic rule
correctness verified manually in demo script.

**Observability:**

```java
log.info("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=INFO [RulePackLoader] Loaded pack: id={}, version={}, rules={}",pack.getId(),pack.

getVersion(),pack.

getRules().

size());
```

**Story Points:** 8

---

#### Story 1.2 — Author bd-govt-consultancy-v1.yaml (30 Structural Rules + 3 Semantic)

**Description:**
Author the consultancy/ToR rule pack covering TOR objectives, team composition, reporting requirements, CV requirements,
methodology evaluation, and standard GOB consultancy procurement fields. All 33 rules must follow the
`rule-schema-v1.json` contract.

**Acceptance Criteria:**

```gherkin
Given rules/bd-govt-consultancy-v1.yaml exists and is valid per rule-schema-v1.json
When RulePackLoader.loadAll() is called
Then pack "bd-govt-consultancy-v1" is present with ruleCount = 33

Given an RfpDocument with entities.key_personnel = null
When RulePackRunner.run(doc, "bd-govt-consultancy-v1") is called
Then finding BD-C-007 has status = FAIL and severity = FATAL

Given all 33 parameterized test cases in RulePackConsultancyTest
When mvn test -Dtest=RulePackConsultancyTest is run
Then all 33 tests pass
```

**Interfaces/Contracts:** Same YAML schema as Works pack. `packId = "bd-govt-consultancy-v1"`, `rfpType = CONSULTANCY`.

**Implementation Plan:**

File: `rfp-extractor/rules/bd-govt-consultancy-v1.yaml`

```yaml
id: bd-govt-consultancy-v1
name: "BD Government Consultancy / ToR Contracts"
version: "1.0.0"
rfpType: CONSULTANCY
description: "Rules for GOB consultancy contracts and Terms of Reference (ToR) under PPR 2008, CPTU SBDS-Consulting."
rules:

    # ── MANDATORY FIELDS ────────────────────────────────────────────────────────

    -   id: BD-C-001
        name: "RFP Title Present"
        severity: FATAL
        type: STRUCTURAL
        jmesPath: "metadata.title"
        condition: NOT_NULL_OR_EMPTY
        message: "RFP title is missing (metadata.title)."
        category: "MANDATORY_FIELDS"

    -   id: BD-C-002
        name: "Procurement Reference Present"
        severity: FATAL
        type: STRUCTURAL
        jmesPath: "metadata.procurement_ref"
        condition: NOT_NULL_OR_EMPTY
        message: "Procurement reference number (metadata.procurement_ref) is missing."
        category: "MANDATORY_FIELDS"

    -   id: BD-C-003
        name: "Client Name Present"
        severity: FATAL
        type: STRUCTURAL
        jmesPath: "metadata.client_name"
        condition: NOT_NULL_OR_EMPTY
        message: "Client/agency name (metadata.client_name) is missing."
        category: "MANDATORY_FIELDS"

    -   id: BD-C-004
        name: "Submission Deadline Present"
        severity: FATAL
        type: STRUCTURAL
        jmesPath: "entities.submission_deadline"
        condition: NOT_NULL_OR_EMPTY
        message: "Submission deadline (entities.submission_deadline) is missing."
        category: "MANDATORY_FIELDS"

    -   id: BD-C-005
        name: "Method of Selection Specified"
        severity: FATAL
        type: STRUCTURAL
        jmesPath: "entities.method_of_selection"
        condition: NOT_NULL_OR_EMPTY
        message: "Method of selection (QCBS, QBS, LCS, CQS, SSS) is not specified (entities.method_of_selection)."
        category: "MANDATORY_FIELDS"

    -   id: BD-C-006
        name: "Staff Months Requirement Stated"
        severity: FATAL
        type: STRUCTURAL
        jmesPath: "entities.staff_months"
        condition: NOT_NULL_OR_EMPTY
        message: "Total staff months or person-days required (entities.staff_months) are not stated."
        category: "MANDATORY_FIELDS"

    -   id: BD-C-007
        name: "Team Composition / Key Expert Roles Specified"
        severity: FATAL
        type: STRUCTURAL
        jmesPath: "entities.key_personnel"
        condition: NOT_NULL_OR_EMPTY
        message: "Key expert roles and team composition (entities.key_personnel) are not specified in the ToR."
        category: "MANDATORY_FIELDS"

    -   id: BD-C-008
        name: "Methodology / Approach Requirement Present"
        severity: FATAL
        type: STRUCTURAL
        jmesPath: "sections[?contains(heading, 'methodology') || contains(heading, 'approach') || contains(heading, 'technical approach')]"
        condition: NOT_EMPTY_ARRAY
        message: "No section requiring a methodology or technical approach was found. QCBS evaluations require firms to submit a technical approach."
        category: "MANDATORY_FIELDS"

    -   id: BD-C-009
        name: "Reporting Requirements Specified"
        severity: FATAL
        type: STRUCTURAL
        jmesPath: "entities.reporting_requirements"
        condition: NOT_NULL_OR_EMPTY
        message: "Reporting requirements (inception report, progress reports, draft/final reports) are not specified (entities.reporting_requirements)."
        category: "DELIVERABLES"

    -   id: BD-C-010
        name: "Eligibility Criteria Present"
        severity: FATAL
        type: STRUCTURAL
        jmesPath: "entities.eligibility_summary"
        condition: NOT_NULL_OR_EMPTY
        message: "Firm eligibility criteria (entities.eligibility_summary) are missing. Must include firm registration, similar project experience requirements."
        category: "MANDATORY_FIELDS"

    # ── HIGH SEVERITY ────────────────────────────────────────────────────────────

    -   id: BD-C-011
        name: "Key Expert CV Requirements Stated (Years Experience)"
        severity: HIGH
        type: STRUCTURAL
        jmesPath: "entities.cv_requirements"
        condition: NOT_NULL_OR_EMPTY
        message: "Key expert CV requirements including minimum years of experience and qualification level (entities.cv_requirements) are not stated."
        category: "PERSONNEL"

    -   id: BD-C-012
        name: "Work Plan / Schedule Required"
        severity: HIGH
        type: STRUCTURAL
        jmesPath: "entities.work_plan_required"
        condition: NOT_NULL_OR_EMPTY
        message: "Requirement for a work plan/schedule from the firm is not mentioned (entities.work_plan_required)."
        category: "SCHEDULE"

    -   id: BD-C-013
        name: "Inception Report Delivery Date or Period Specified"
        severity: HIGH
        type: STRUCTURAL
        jmesPath: "entities.reporting_requirements[?contains(@, 'inception')]"
        condition: NOT_EMPTY_ARRAY
        message: "Inception report delivery requirements not found in entities.reporting_requirements."
        category: "DELIVERABLES"

    -   id: BD-C-014
        name: "Final Report Delivery Requirement Specified"
        severity: HIGH
        type: STRUCTURAL
        jmesPath: "entities.reporting_requirements[?contains(@, 'final report') || contains(@, 'final')]"
        condition: NOT_EMPTY_ARRAY
        message: "Final report delivery requirement not found in entities.reporting_requirements."
        category: "DELIVERABLES"

    -   id: BD-C-015
        name: "Payment Terms (Milestone-Based) Stated"
        severity: HIGH
        type: STRUCTURAL
        jmesPath: "entities.payment_terms"
        condition: NOT_NULL_OR_EMPTY
        message: "Payment terms (entities.payment_terms) are absent. Consultancy payments should be milestone-linked."
        category: "FINANCIAL"

    -   id: BD-C-016
        name: "Performance Security Requirement Stated"
        severity: HIGH
        type: STRUCTURAL
        jmesPath: "entities.performance_security_pct"
        condition: NOT_NULL_OR_EMPTY
        message: "Performance security requirement (entities.performance_security_pct) is not stated."
        category: "FINANCIAL_SECURITY"

    -   id: BD-C-017
        name: "Bank Guarantee Requirement Mentioned"
        severity: HIGH
        type: STRUCTURAL
        jmesPath: "entities.pricing_factors[?contains(@, 'bank guarantee')]"
        condition: NOT_EMPTY_ARRAY
        message: "Bank guarantee requirement not found in pricing_factors."
        category: "FINANCIAL_SECURITY"

    -   id: BD-C-018
        name: "Bid Validity Period Stated"
        severity: HIGH
        type: STRUCTURAL
        jmesPath: "entities.bid_validity_days"
        condition: NOT_NULL_OR_EMPTY
        message: "Bid/proposal validity period (entities.bid_validity_days) is not stated."
        category: "SUBMISSION"

    -   id: BD-C-019
        name: "Evaluation Criteria (Technical/Financial Split) Specified"
        severity: HIGH
        type: STRUCTURAL
        jmesPath: "entities.technical_financial_split"
        condition: NOT_NULL_OR_EMPTY
        message: "Technical/financial evaluation split (entities.technical_financial_split) is not specified (e.g., 80/20 for QCBS)."
        category: "EVALUATION"

    -   id: BD-C-020
        name: "Counterpart Support / Client Inputs Described"
        severity: HIGH
        type: STRUCTURAL
        jmesPath: "entities.counterpart_support"
        condition: NOT_NULL_OR_EMPTY
        message: "Client counterpart support and inputs to be provided (entities.counterpart_support) are not described."
        category: "SCOPE"

    -   id: BD-C-021
        name: "Intellectual Property / Data Rights Clause Present"
        severity: HIGH
        type: STRUCTURAL
        jmesPath: "entities.other_info[?contains(@, 'intellectual property') || contains(@, 'ip rights') || contains(@, 'data rights')]"
        condition: NOT_EMPTY_ARRAY
        message: "Intellectual property and data rights clause not found in other_info."
        category: "CONTRACT_CONDITIONS"

    -   id: BD-C-022
        name: "Conflict of Interest Clause Present"
        severity: HIGH
        type: STRUCTURAL
        jmesPath: "entities.other_info[?contains(@, 'conflict of interest') || contains(@, 'coi')]"
        condition: NOT_EMPTY_ARRAY
        message: "Conflict of interest declaration requirement not found."
        category: "COMPLIANCE"

    # ── MEDIUM SEVERITY ───────────────────────────────────────────────────────────

    -   id: BD-C-023
        name: "Data Collection Methods Described"
        severity: MEDIUM
        type: STRUCTURAL
        jmesPath: "entities.data_collection_methods"
        condition: NOT_NULL_OR_EMPTY
        message: "Data collection methods (entities.data_collection_methods) are not described in the ToR."
        category: "METHODOLOGY"

    -   id: BD-C-024
        name: "Submission Copies Requirement Stated"
        severity: MEDIUM
        type: STRUCTURAL
        jmesPath: "entities.number_of_copies"
        condition: NOT_NULL_OR_EMPTY
        message: "Number of proposal copies required (entities.number_of_copies) is not stated."
        category: "SUBMISSION"

    -   id: BD-C-025
        name: "Pre-Bid Meeting Details Present"
        severity: MEDIUM
        type: STRUCTURAL
        jmesPath: "entities.pre_bid_meeting"
        condition: NOT_NULL_OR_EMPTY
        message: "Pre-bid/pre-proposal meeting details (entities.pre_bid_meeting) are not specified."
        category: "SUBMISSION"

    -   id: BD-C-026
        name: "Similar Project Experience Requirement Quantified"
        severity: MEDIUM
        type: STRUCTURAL
        jmesPath: "entities.similar_project_experience"
        condition: NOT_NULL_OR_EMPTY
        message: "Similar project experience requirement (entities.similar_project_experience) is not quantified (e.g., minimum 3 similar projects in past 10 years)."
        category: "ELIGIBILITY"

    -   id: BD-C-027
        name: "Reimbursable Expenses Policy Defined"
        severity: MEDIUM
        type: STRUCTURAL
        jmesPath: "entities.reimbursable_expenses"
        condition: NOT_NULL_OR_EMPTY
        message: "Reimbursable expenses policy (entities.reimbursable_expenses) is not defined."
        category: "FINANCIAL"

    -   id: BD-C-028
        name: "Training / Capacity Building Requirement Stated (If Applicable)"
        severity: MEDIUM
        type: STRUCTURAL
        jmesPath: "entities.training_requirements"
        condition: NOT_NULL_OR_EMPTY
        message: "Training/capacity building requirement (entities.training_requirements) is not addressed. If not applicable, this should be explicitly stated."
        category: "DELIVERABLES"

    -   id: BD-C-029
        name: "Progress Report Frequency Stated"
        severity: MEDIUM
        type: STRUCTURAL
        jmesPath: "entities.reporting_requirements[?contains(@, 'progress')]"
        condition: NOT_EMPTY_ARRAY
        message: "Progress report frequency not mentioned in reporting_requirements."
        category: "DELIVERABLES"

    -   id: BD-C-030
        name: "Submission Address / Contact Stated"
        severity: MEDIUM
        type: STRUCTURAL
        jmesPath: "entities.submission_address"
        condition: NOT_NULL_OR_EMPTY
        message: "Submission address or contact details (entities.submission_address) are missing."
        category: "SUBMISSION"

    # ── SEMANTIC RULES (LLM) ─────────────────────────────────────────────────────

    -   id: BD-C-031
        name: "Scope of ToR Is Clear Enough to Propose Methodology"
        severity: HIGH
        type: SEMANTIC
        evidenceJmesPath: "entities.scope_of_work"
        promptKey: "rule-judgment-v1"
        judgmentInstruction: "Evaluate whether the Terms of Reference scope is sufficiently clear for a consulting firm to propose a relevant methodology. The ToR must describe: background context, specific objectives, key activities or tasks expected, geographic scope, and any constraints. Vague objectives without measurable outcomes are insufficient. Return {pass: true/false, confidence: 0.0-1.0, reason: '...'}."
        message: "Scope of ToR is insufficiently defined for firms to propose a credible methodology."
        category: "SEMANTIC_SCOPE"

    -   id: BD-C-032
        name: "Deliverables Have Clear Acceptance Criteria"
        severity: HIGH
        type: SEMANTIC
        evidenceJmesPath: "entities.reporting_requirements"
        promptKey: "rule-judgment-v1"
        judgmentInstruction: "Determine whether the stated deliverables (reports, studies, plans) include clear acceptance criteria: format requirements, minimum content specifications, review and approval timelines, and who approves each deliverable. Generic statements like 'satisfactory final report' without criteria are insufficient. Return {pass: true/false, confidence: 0.0-1.0, reason: '...'}."
        message: "Deliverable acceptance criteria are vague; disputes over deliverable approval are likely."
        category: "SEMANTIC_DELIVERABLES"

    -   id: BD-C-033
        name: "Team Composition Is Justified for Scope"
        severity: MEDIUM
        type: SEMANTIC
        evidenceJmesPath: "entities.key_personnel"
        promptKey: "rule-judgment-v1"
        judgmentInstruction: "Assess whether the proposed team composition (roles, number of experts, staff months) is logically justified by the scope of the ToR. An over-specified team (e.g., 15 senior experts for a 3-month assignment) or under-specified team (e.g., 1 expert for a complex 2-year assignment) is a concern. Return {pass: true/false, confidence: 0.0-1.0, reason: '...'}."
        message: "Team composition appears misaligned with the scope and complexity of the assignment."
        category: "SEMANTIC_PERSONNEL"
```

**Dependencies:** Same as Story 1.1.

**Risks:** Same as Story 1.1. Additionally, `reporting_requirements` is a `List<String>` field in `RfpDocument`;
JMESPath filtering by string content relies on this.

**Test Plan:**

Class: `rfp-service/src/test/java/com/dsi/rfp/adapter/rulepack/RulePackConsultancyTest.java`

Structure mirrors `RulePackWorksTest`. 33 parameterized cases covering all structural rules. Semantic rules mocked via
`LlmJudgmentChecker` stub.

Notable assertions:

```java
Arguments.of("BD-C-007",docWithKeyPersonnel(null),FAIL),
    Arguments.

of("BD-C-019",docWithTechnicalFinancialSplit("80/20"),PASS),
    Arguments.

of("BD-C-022",docWithOtherInfo(List.of("conflict of interest declaration required")),PASS),
```

**Observability:** Same `[RulePackLoader]` log line as Story 1.1.

**Story Points:** 8

---

#### Story 1.3 — Author bd-govt-goods-v1.yaml (20 Structural Rules + 2 Semantic)

**Description:**
Author the goods procurement rule pack covering technical specifications, delivery schedule, inspection, warranty,
incoterms, and standard GOB goods procurement fields.

**Acceptance Criteria:**

```gherkin
Given rules/bd-govt-goods-v1.yaml exists
When RulePackLoader.loadAll() is called
Then pack "bd-govt-goods-v1" has ruleCount = 22

Given an RfpDocument with entities.technical_specifications = null
When RulePackRunner.run(doc, "bd-govt-goods-v1") is called
Then finding BD-G-005 has status = FAIL and severity = FATAL

Given all 22 test cases in RulePackGoodsTest
When mvn test -Dtest=RulePackGoodsTest is run
Then all 22 pass
```

**Interfaces/Contracts:** Same YAML schema. `packId = "bd-govt-goods-v1"`, `rfpType = GOODS`.

**Implementation Plan:**

File: `rfp-extractor/rules/bd-govt-goods-v1.yaml`

```yaml
id: bd-govt-goods-v1
name: "BD Government Goods Procurement"
version: "1.0.0"
rfpType: GOODS
description: "Rules for GOB goods procurement contracts under PPR 2008 and CPTU SBD-Goods."
rules:

    # ── MANDATORY FIELDS ────────────────────────────────────────────────────────

    -   id: BD-G-001
        name: "RFP Title Present"
        severity: FATAL
        type: STRUCTURAL
        jmesPath: "metadata.title"
        condition: NOT_NULL_OR_EMPTY
        message: "RFP title (metadata.title) is missing."
        category: "MANDATORY_FIELDS"

    -   id: BD-G-002
        name: "Procurement Reference Present"
        severity: FATAL
        type: STRUCTURAL
        jmesPath: "metadata.procurement_ref"
        condition: NOT_NULL_OR_EMPTY
        message: "Procurement reference number (metadata.procurement_ref) is missing."
        category: "MANDATORY_FIELDS"

    -   id: BD-G-003
        name: "Client Name Present"
        severity: FATAL
        type: STRUCTURAL
        jmesPath: "metadata.client_name"
        condition: NOT_NULL_OR_EMPTY
        message: "Client/procuring entity name (metadata.client_name) is missing."
        category: "MANDATORY_FIELDS"

    -   id: BD-G-004
        name: "Submission Deadline Present"
        severity: FATAL
        type: STRUCTURAL
        jmesPath: "entities.submission_deadline"
        condition: NOT_NULL_OR_EMPTY
        message: "Submission deadline (entities.submission_deadline) is missing."
        category: "MANDATORY_FIELDS"

    -   id: BD-G-005
        name: "Technical Specifications Present"
        severity: FATAL
        type: STRUCTURAL
        jmesPath: "entities.technical_specifications"
        condition: NOT_NULL_OR_EMPTY
        message: "Technical specifications (entities.technical_specifications) are missing. Goods RFPs must include measurable technical specifications."
        category: "SPECIFICATIONS"

    -   id: BD-G-006
        name: "Delivery Schedule Stated"
        severity: FATAL
        type: STRUCTURAL
        jmesPath: "entities.delivery_schedule"
        condition: NOT_NULL_OR_EMPTY
        message: "Delivery schedule (entities.delivery_schedule) is not stated. Delivery timeline is mandatory for goods procurement."
        category: "MANDATORY_FIELDS"

    # ── HIGH SEVERITY ────────────────────────────────────────────────────────────

    -   id: BD-G-007
        name: "Inspection and Acceptance Criteria Stated"
        severity: HIGH
        type: STRUCTURAL
        jmesPath: "entities.inspection_criteria"
        condition: NOT_NULL_OR_EMPTY
        message: "Inspection and acceptance criteria (entities.inspection_criteria) are not specified."
        category: "QUALITY"

    -   id: BD-G-008
        name: "Warranty Period Stated"
        severity: HIGH
        type: STRUCTURAL
        jmesPath: "entities.warranty_period"
        condition: NOT_NULL_OR_EMPTY
        message: "Warranty period (entities.warranty_period) is not stated."
        category: "CONTRACT_CONDITIONS"

    -   id: BD-G-009
        name: "Incoterms (CIF/FOB/DDP) Specified"
        severity: HIGH
        type: STRUCTURAL
        jmesPath: "entities.incoterms"
        condition: NOT_NULL_OR_EMPTY
        message: "Incoterms (CIF/FOB/DDP etc.) are not specified (entities.incoterms). Delivery terms determine freight and insurance responsibility."
        category: "FINANCIAL"

    -   id: BD-G-010
        name: "Performance Security Requirement Stated"
        severity: HIGH
        type: STRUCTURAL
        jmesPath: "entities.performance_security_pct"
        condition: NOT_NULL_OR_EMPTY
        message: "Performance security requirement (entities.performance_security_pct) is not stated."
        category: "FINANCIAL_SECURITY"

    -   id: BD-G-011
        name: "Payment Terms Stated"
        severity: HIGH
        type: STRUCTURAL
        jmesPath: "entities.payment_terms"
        condition: NOT_NULL_OR_EMPTY
        message: "Payment terms (entities.payment_terms) are not specified."
        category: "FINANCIAL"

    -   id: BD-G-012
        name: "Delivery Address / Destination Stated"
        severity: HIGH
        type: STRUCTURAL
        jmesPath: "entities.delivery_address"
        condition: NOT_NULL_OR_EMPTY
        message: "Delivery address/destination (entities.delivery_address) is not specified."
        category: "LOGISTICS"

    -   id: BD-G-013
        name: "Bid Validity Period Stated"
        severity: HIGH
        type: STRUCTURAL
        jmesPath: "entities.bid_validity_days"
        condition: NOT_NULL_OR_EMPTY
        message: "Bid validity period (entities.bid_validity_days) is not stated."
        category: "SUBMISSION"

    -   id: BD-G-014
        name: "Bank Guarantee / Bid Bond Requirement Mentioned"
        severity: HIGH
        type: STRUCTURAL
        jmesPath: "entities.pricing_factors[?contains(@, 'bank guarantee') || contains(@, 'bid bond') || contains(@, 'bid security')]"
        condition: NOT_EMPTY_ARRAY
        message: "Bank guarantee or bid bond requirement not found in pricing_factors."
        category: "FINANCIAL_SECURITY"

    -   id: BD-G-015
        name: "After-Sales Service / Spare Parts Availability Stated"
        severity: HIGH
        type: STRUCTURAL
        jmesPath: "entities.after_sales_service"
        condition: NOT_NULL_OR_EMPTY
        message: "After-sales service and spare parts availability requirements (entities.after_sales_service) are not specified."
        category: "CONTRACT_CONDITIONS"

    # ── MEDIUM SEVERITY ───────────────────────────────────────────────────────────

    -   id: BD-G-016
        name: "Packaging Requirements Specified"
        severity: MEDIUM
        type: STRUCTURAL
        jmesPath: "entities.packaging_requirements"
        condition: NOT_NULL_OR_EMPTY
        message: "Packaging requirements (entities.packaging_requirements) are not specified."
        category: "LOGISTICS"

    -   id: BD-G-017
        name: "Pre-Shipment Inspection Requirement Stated"
        severity: MEDIUM
        type: STRUCTURAL
        jmesPath: "entities.other_info[?contains(@, 'pre-shipment') || contains(@, 'pre shipment') || contains(@, 'factory acceptance')]"
        condition: NOT_EMPTY_ARRAY
        message: "Pre-shipment inspection requirement not found in other_info."
        category: "QUALITY"

    -   id: BD-G-018
        name: "Country of Origin Requirements Stated"
        severity: MEDIUM
        type: STRUCTURAL
        jmesPath: "entities.country_of_origin"
        condition: NOT_NULL_OR_EMPTY
        message: "Country of origin requirements or restrictions (entities.country_of_origin) are not stated."
        category: "COMPLIANCE"

    -   id: BD-G-019
        name: "Insurance Requirements Stated"
        severity: MEDIUM
        type: STRUCTURAL
        jmesPath: "entities.pricing_factors[?contains(@, 'insurance')]"
        condition: NOT_EMPTY_ARRAY
        message: "Insurance requirements for transport/delivery not found in pricing_factors."
        category: "LOGISTICS"

    -   id: BD-G-020
        name: "Submission Copies / Format Stated"
        severity: MEDIUM
        type: STRUCTURAL
        jmesPath: "entities.number_of_copies"
        condition: NOT_NULL_OR_EMPTY
        message: "Number of bid copies or submission format (entities.number_of_copies) is not stated."
        category: "SUBMISSION"

    # ── SEMANTIC RULES (LLM) ─────────────────────────────────────────────────────

    -   id: BD-G-021
        name: "Technical Specifications Are Unambiguous"
        severity: HIGH
        type: SEMANTIC
        evidenceJmesPath: "entities.technical_specifications"
        promptKey: "rule-judgment-v1"
        judgmentInstruction: "Assess whether the technical specifications are unambiguous and objectively measurable. They must use specific values (dimensions, tolerances, standards references like ISO/BIS/BNBC) rather than subjective language like 'good quality' or 'suitable grade'. Proprietary brand-only specifications without 'or equivalent' are also a concern. Return {pass: true/false, confidence: 0.0-1.0, reason: '...'}."
        message: "Technical specifications contain ambiguous or subjective language that may lead to disputes or non-comparable bids."
        category: "SEMANTIC_SPECIFICATIONS"

    -   id: BD-G-022
        name: "Warranty Terms Clearly Define Scope of Coverage"
        severity: MEDIUM
        type: SEMANTIC
        evidenceJmesPath: "entities.warranty_period"
        promptKey: "rule-judgment-v1"
        judgmentInstruction: "Determine whether warranty terms clearly define: duration, what is covered (parts, labor, defects), exclusions, response time for warranty claims, and who bears transport costs for warranty repairs. Vague 'standard warranty' without specifics is insufficient. Return {pass: true/false, confidence: 0.0-1.0, reason: '...'}."
        message: "Warranty terms do not clearly define scope of coverage; warranty obligations may be disputed."
        category: "SEMANTIC_WARRANTY"
```

**Dependencies:** Same as Story 1.1.

**Test Plan:**

Class: `rfp-service/src/test/java/com/dsi/rfp/adapter/rulepack/RulePackGoodsTest.java`

22 parameterized cases. Key assertions:

```java
Arguments.of("BD-G-005",docWithTechnicalSpecifications(null),FAIL),
    Arguments.

of("BD-G-009",docWithIncoterms("CIF Chittagong"),PASS),
    Arguments.

of("BD-G-014",docWithPricingFactors(List.of("bid bond 2% of contract value")),PASS),
```

**Story Points:** 5

---

### Epic 2 — RfpTypeClassifier Update

#### Story 2.1 — Add WORKS and CONSULTANCY Keyword Groups

**Description:**
Update `RfpTypeClassifier` to recognize `WORKS` and `CONSULTANCY` RFP types by adding new keyword groups to the existing
keyword scoring map. The classifier must score keywords from the document title, scope section, and procurement method
fields and return the type with the highest score, or `UNKNOWN` if no type exceeds the threshold.

**Acceptance Criteria:**

```gherkin
Given an RfpDocument with title "Construction of District Hospital Building"
When RfpTypeClassifier.classify(doc) is called
Then the result is RfpType.WORKS

Given an RfpDocument with title "Consultancy Services for Preparation of DPP"
When RfpTypeClassifier.classify(doc) is called
Then the result is RfpType.CONSULTANCY

Given an RfpDocument with no relevant keywords
When RfpTypeClassifier.classify(doc) is called
Then the result is RfpType.UNKNOWN

Given a classified document of type WORKS
When RunRulePackNode selects the rule pack
Then it selects "bd-govt-works-v1" pack
```

**Interfaces/Contracts:**

File: `rfp-service/src/main/java/com/dsi/rfp/adapter/rulepack/RfpTypeClassifier.java`

```java
package com.dsi.rfp.adapter.rulepack;

import com.dsi.rfp.domain.model.RfpDocument;
import com.dsi.rfp.domain.model.RfpType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class RfpTypeClassifier {

    // threshold: document must score this many keyword points to be classified
    private static final int CLASSIFICATION_THRESHOLD = 2;

    // Map<RfpType, List<keyword>> — add to existing Sprint 8 map
    private static final Map<RfpType, List<String>> KEYWORD_GROUPS = Map.of(
        RfpType.ICT, List.of("software", "ict", "information technology",
            "hardware", "network", "system development",
            "data center", "cloud", "erp", "application"),
        RfpType.GOODS, List.of("supply of", "procurement of goods",
            "equipment", "vehicle", "furniture",
            "medical equipment", "laboratory equipment"),
        RfpType.WORKS, List.of("civil works", "construction", "infrastructure project",
            "building construction", "road construction",
            "bridge", "renovation", "rehabilitation",
            "bill of quantities", "boq", "contractor"),
        RfpType.CONSULTANCY, List.of("terms of reference", "tor",
            "consulting services", "consultancy",
            "technical assistance", "advisory services",
            "feasibility study", "environmental impact",
            "design services", "project management consultant")
    );

    public RfpType classify(RfpDocument doc) {
        String textToSearch = buildSearchText(doc);
        String lowerText = textToSearch.toLowerCase();

        RfpType bestType = RfpType.UNKNOWN;
        int bestScore = 0;

        for (Map.Entry<RfpType, List<String>> entry : KEYWORD_GROUPS.entrySet()) {
            int score = scoreKeywords(lowerText, entry.getValue());
            if (score > bestScore) {
                bestScore = score;
                bestType = entry.getKey();
            }
        }

        if (bestScore < CLASSIFICATION_THRESHOLD) {
            bestType = RfpType.UNKNOWN;
        }

        log.info("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=INFO [RfpTypeClassifier] Classified document={} as type={} score={}",
            doc.getJobId(), bestType, bestScore);
        return bestType;
    }

    private String buildSearchText(RfpDocument doc) {
        StringBuilder sb = new StringBuilder();
        if (Objects.nonNull(doc.getMetadata())) {
            appendIfPresent(sb, doc.getMetadata().getTitle());
            appendIfPresent(sb, doc.getMetadata().getProcurementMethod());
        }
        if (Objects.nonNull(doc.getEntities())) {
            appendIfPresent(sb, doc.getEntities().getScopeOfWork());
        }
        return sb.toString();
    }

    private void appendIfPresent(StringBuilder sb, String value) {
        if (Objects.nonNull(value) && StringUtils.hasText(value)) {
            sb.append(" ").append(value);
        }
    }

    private int scoreKeywords(String text, List<String> keywords) {
        return (int) keywords.stream().filter(text::contains).count();
    }
}
```

`RfpType` enum (in `rfp-core/domain/model/RfpType.java`): add `WORKS`, `CONSULTANCY` values if not already present.

**Implementation Plan:**

1. Open `RfpType.java` in `rfp-core/src/main/java/com/dsi/rfp/domain/model/`. Add `WORKS` and `CONSULTANCY` if missing.
2. Open `RfpTypeClassifier.java`. Replace the `KEYWORD_GROUPS` map with the complete version above.
3. Verify `RunRulePackNode` in `rfp-service/src/main/java/com/dsi/rfp/agent/RunRulePackNode.java` maps `RfpType.WORKS` →
   `"bd-govt-works-v1"` and `RfpType.CONSULTANCY` → `"bd-govt-consultancy-v1"`. Update the pack selection map:

```java
// In RunRulePackNode (or a separate RfpTypeToPackMapper component)
private static final Map<RfpType, String> TYPE_TO_PACK = Map.of(
        RfpType.ICT, "bd-govt-ict-v1",
        RfpType.GOODS, "bd-govt-goods-v1",
        RfpType.WORKS, "bd-govt-works-v1",
        RfpType.CONSULTANCY, "bd-govt-consultancy-v1"
    );
```

If `RfpType.UNKNOWN`, select `"bd-govt-ict-v1"` as default fallback and log a warning.

**Dependencies:** Sprint 8 `RfpTypeClassifier`, `RunRulePackNode`, `RfpType` enum.

**Test Plan:**

Class: `rfp-service/src/test/java/com/dsi/rfp/adapter/rulepack/RfpTypeClassifierTest.java`

```java

@Test
void classifiesWorks_whenTitleContainsCivilWorks() {
    RfpDocument doc = docWithTitle("Civil Works Construction of Upazila Complex");
    assertThat(classifier.classify(doc)).isEqualTo(RfpType.WORKS);
}

@Test
void classifiesConsultancy_whenTitleContainsTor() {
    RfpDocument doc = docWithTitle("Terms of Reference for DPP Preparation Consultant");
    assertThat(classifier.classify(doc)).isEqualTo(RfpType.CONSULTANCY);
}

@Test
void classifiesUnknown_whenNoKeywords() {
    RfpDocument doc = docWithTitle("General Procurement Notice 2024");
    assertThat(classifier.classify(doc)).isEqualTo(RfpType.UNKNOWN);
}

@Test
void doesNotMisclassifyIctAsWorks() {
    RfpDocument doc = docWithTitle("Supply and Installation of Network Infrastructure");
    // "network" is ICT keyword; "infrastructure" should not alone trigger WORKS
    RfpType result = classifier.classify(doc);
    assertThat(result).isNotEqualTo(RfpType.WORKS);
}
```

**Story Points:** 3

---

### Epic 3 — Admin Rule Pack API

#### Story 3.1 — RulePackAdminService

**Description:**
Create a thin application service that delegates to `RulePackLoader` for listing loaded packs and triggering a manual
reload. This service has no business logic beyond coordination and logging.

**Acceptance Criteria:**

```gherkin
Given RulePackLoader has loaded 4 packs (ICT, Works, Consultancy, Goods)
When RulePackAdminService.listLoadedPacks() is called
Then a list of 4 RulePackSummaryDto is returned

Given a YAML file on disk has been modified
When RulePackAdminService.reloadAll() is called
Then RulePackLoader.reloadAll() is invoked
And the returned ReloadResultDto contains the reloaded pack IDs and timestamp
```

**Interfaces/Contracts:**

File: `rfp-service/src/main/java/com/dsi/rfp/application/service/RulePackAdminService.java`

```java
package com.dsi.rfp.application.service;

import com.dsi.rfp.adapter.rulepack.RulePackLoader;
import com.dsi.rfp.adapter.api.dto.RulePackSummaryDto;
import com.dsi.rfp.adapter.api.dto.ReloadResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RulePackAdminService {

    private final RulePackLoader rulePackLoader;

    public List<RulePackSummaryDto> listLoadedPacks() {
        log.info("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=INFO [RulePackAdminService] Listing loaded rule packs");
        return rulePackLoader.getAllPackSummaries();
    }

    public ReloadResultDto reloadAll() {
        log.info("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=INFO [RulePackAdminService] Manual reload triggered");
        List<String> reloadedIds = rulePackLoader.reloadAll();
        log.info("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=INFO [RulePackAdminService] Reloaded packs: {}", reloadedIds);
        return new ReloadResultDto(reloadedIds, Instant.now());
    }
}
```

DTOs (file: `rfp-service/src/main/java/com/dsi/rfp/adapter/api/dto/RulePackSummaryDto.java`):

```java
package com.dsi.rfp.adapter.api.dto;

import java.time.Instant;

public record RulePackSummaryDto(
    String packId,
    String version,
    int ruleCount,
    Instant lastLoadedAt,
    String filePath
) {
}
```

DTOs (file: `rfp-service/src/main/java/com/dsi/rfp/adapter/api/dto/ReloadResultDto.java`):

```java
package com.dsi.rfp.adapter.api.dto;

import java.time.Instant;
import java.util.List;

public record ReloadResultDto(
    List<String> reloadedPacks,
    Instant timestamp
) {
}
```

`RulePackLoader` must expose (add these methods if not present from Sprint 8):

```java
// In RulePackLoader.java (adapter/rulepack/)
public List<RulePackSummaryDto> getAllPackSummaries()  // returns summary for each loaded pack

public List<String> reloadAll()                        // triggers reload of all packs, returns pack IDs
```

`getAllPackSummaries()` implementation: iterate `loadedPacks` (a `ConcurrentHashMap<String, RulePack>` from Sprint 8),
map each to `RulePackSummaryDto` using pack metadata and the stored `lastLoadedAt` timestamp (add this field to the
in-memory `LoadedPackEntry` wrapper if needed).

**Implementation Plan:**

1. In `RulePackLoader`, add `LoadedPackEntry` inner record: `(RulePack pack, Instant lastLoadedAt, String filePath)`.
   Change the map from `ConcurrentHashMap<String, RulePack>` to `ConcurrentHashMap<String, LoadedPackEntry>`.
2. On initial load and reload, populate `lastLoadedAt = Instant.now()`.
3. Implement `getAllPackSummaries()`: stream the map entries, map each `LoadedPackEntry` to `RulePackSummaryDto`.
4. Implement `reloadAll()`: call the existing reload logic for each file, return list of reloaded pack IDs.
5. Create `RulePackAdminService` as above.
6. Create both DTOs.

**Dependencies:** Sprint 8 `RulePackLoader`.

**Test Plan:**

Class: `rfp-service/src/test/java/com/dsi/rfp/application/service/RulePackAdminServiceTest.java`

```java

@ExtendWith(MockitoExtension.class)
class RulePackAdminServiceTest {

    @Mock
    RulePackLoader rulePackLoader;
    @InjectMocks
    RulePackAdminService service;

    @Test
    void listLoadedPacks_delegatesToLoader() {
        var summary = new RulePackSummaryDto("bd-govt-ict-v1", "1.0.0", 64, Instant.now(), "/rules/bd-govt-ict-v1.yaml");
        when(rulePackLoader.getAllPackSummaries()).thenReturn(List.of(summary));
        List<RulePackSummaryDto> result = service.listLoadedPacks();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).packId()).isEqualTo("bd-govt-ict-v1");
    }

    @Test
    void reloadAll_returnsReloadedIds() {
        when(rulePackLoader.reloadAll()).thenReturn(List.of("bd-govt-ict-v1", "bd-govt-works-v1"));
        ReloadResultDto result = service.reloadAll();
        assertThat(result.reloadedPacks()).containsExactlyInAnyOrder("bd-govt-ict-v1", "bd-govt-works-v1");
        assertThat(result.timestamp()).isNotNull();
    }
}
```

**Story Points:** 3

---

#### Story 3.2 — AdminRulePackController

**Description:**
Create a REST controller exposing two endpoints for rule pack management. Authentication is not enforced in this
sprint (Sprint 11 adds real security). Add a `TODO` comment marking where `@PreAuthorize("hasRole('ADMIN')")` will be
activated.

**Acceptance Criteria:**

```gherkin
Given the application is running with 4 loaded rule packs
When GET /api/v1/admin/rule-packs is called
Then response status is 200
And body contains an array of 4 pack summaries with packId, version, ruleCount, lastLoadedAt, filePath

Given the application is running
When POST /api/v1/admin/rule-packs/reload is called
Then response status is 200
And body contains { "reloadedPacks": [...], "timestamp": "..." }

Given AdminRulePackControllerTest
When mvn test -Dtest=AdminRulePackControllerTest
Then all 6 tests pass with MockMvc assertions
```

**Interfaces/Contracts:**

File: `rfp-service/src/main/java/com/dsi/rfp/adapter/api/AdminRulePackController.java`

```java
package com.dsi.rfp.adapter.api;

import com.dsi.rfp.adapter.api.dto.ReloadResultDto;
import com.dsi.rfp.adapter.api.dto.RulePackSummaryDto;
import com.dsi.rfp.application.service.RulePackAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/rule-packs")
@RequiredArgsConstructor
public class AdminRulePackController {

    private final RulePackAdminService rulePackAdminService;

    // TODO Sprint-11: Activate @PreAuthorize("hasRole('ADMIN')") on both endpoints
    //                 once Spring Security RBAC is wired in SecurityConfig.

    @GetMapping
    public ResponseEntity<List<RulePackSummaryDto>> listRulePacks() {
        log.info("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=INFO [AdminRulePackController] GET /api/v1/admin/rule-packs");
        return ResponseEntity.ok(rulePackAdminService.listLoadedPacks());
    }

    @PostMapping("/reload")
    public ResponseEntity<ReloadResultDto> reloadRulePacks() {
        log.info("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=INFO [AdminRulePackController] POST /api/v1/admin/rule-packs/reload");
        return ResponseEntity.ok(rulePackAdminService.reloadAll());
    }
}
```

GET `/api/v1/admin/rule-packs` response JSON example:

```json
[
    {
        "packId": "bd-govt-ict-v1",
        "version": "1.0.0",
        "ruleCount": 64,
        "lastLoadedAt": "2025-08-01T10:00:00Z",
        "filePath": "/app/rules/bd-govt-ict-v1.yaml"
    },
    {
        "packId": "bd-govt-works-v1",
        "version": "1.0.0",
        "ruleCount": 33,
        "lastLoadedAt": "2025-08-01T10:00:00Z",
        "filePath": "/app/rules/bd-govt-works-v1.yaml"
    }
]
```

POST `/api/v1/admin/rule-packs/reload` response JSON example:

```json
{
    "reloadedPacks": [
        "bd-govt-ict-v1",
        "bd-govt-works-v1",
        "bd-govt-consultancy-v1",
        "bd-govt-goods-v1"
    ],
    "timestamp": "2025-08-01T10:05:00Z"
}
```

**Implementation Plan:**

1. Create `AdminRulePackController.java` at path above.
2. Ensure `@RequestMapping("/api/v1/admin/rule-packs")` does not conflict with any existing mapping.
3. No Spring Security annotations active yet — controller is open. Add `TODO` comment.

**Dependencies:** Story 3.1 `RulePackAdminService`.

**Test Plan:**

Class: `rfp-service/src/test/java/com/dsi/rfp/adapter/api/AdminRulePackControllerTest.java`

```java

@WebMvcTest(AdminRulePackController.class)
class AdminRulePackControllerTest {

    @Autowired
    MockMvc mockMvc;
    @MockBean
    RulePackAdminService rulePackAdminService;

    @Test
    void listRulePacks_returns200WithPackList() throws Exception {
        var summary = new RulePackSummaryDto("bd-govt-ict-v1", "1.0.0", 64,
            Instant.parse("2025-08-01T10:00:00Z"),
            "/rules/bd-govt-ict-v1.yaml");
        when(rulePackAdminService.listLoadedPacks()).thenReturn(List.of(summary));
        mockMvc.perform(get("/api/v1/admin/rule-packs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].packId").value("bd-govt-ict-v1"))
            .andExpect(jsonPath("$[0].ruleCount").value(64));
    }

    @Test
    void listRulePacks_returnsEmptyList_whenNoPacks() throws Exception {
        when(rulePackAdminService.listLoadedPacks()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/admin/rule-packs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void reloadRulePacks_returns200WithReloadResult() throws Exception {
        var result = new ReloadResultDto(List.of("bd-govt-ict-v1", "bd-govt-works-v1"),
            Instant.parse("2025-08-01T10:05:00Z"));
        when(rulePackAdminService.reloadAll()).thenReturn(result);
        mockMvc.perform(post("/api/v1/admin/rule-packs/reload"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reloadedPacks").isArray())
            .andExpect(jsonPath("$.reloadedPacks[0]").value("bd-govt-ict-v1"))
            .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void reloadRulePacks_callsServiceReloadAll() throws Exception {
        when(rulePackAdminService.reloadAll())
            .thenReturn(new ReloadResultDto(List.of(), Instant.now()));
        mockMvc.perform(post("/api/v1/admin/rule-packs/reload"))
            .andExpect(status().isOk());
        verify(rulePackAdminService, times(1)).reloadAll();
    }

    @Test
    void listRulePacks_containsAllExpectedFields() throws Exception {
        var summary = new RulePackSummaryDto("bd-govt-works-v1", "1.0.0", 33,
            Instant.now(), "/rules/bd-govt-works-v1.yaml");
        when(rulePackAdminService.listLoadedPacks()).thenReturn(List.of(summary));
        mockMvc.perform(get("/api/v1/admin/rule-packs"))
            .andExpect(jsonPath("$[0].version").value("1.0.0"))
            .andExpect(jsonPath("$[0].filePath").value("/rules/bd-govt-works-v1.yaml"));
    }

    @Test
    void reloadRulePacks_returnsTimestamp() throws Exception {
        Instant now = Instant.parse("2025-08-15T12:00:00Z");
        when(rulePackAdminService.reloadAll())
            .thenReturn(new ReloadResultDto(List.of("bd-govt-ict-v1"), now));
        mockMvc.perform(post("/api/v1/admin/rule-packs/reload"))
            .andExpect(jsonPath("$.timestamp").value("2025-08-15T12:00:00Z"));
    }
}
```

**Story Points:** 3

---

### Epic 4 — Hot Reload Verification

#### Story 4.1 — Hot Reload End-to-End Verification Test

**Description:**
Verify that `RulePackLoader`'s `WatchService` (Sprint 8) detects a file change and reloads within 30 seconds. This is an
integration-style test that writes a modified YAML to a temp directory, waits, and asserts the new rule count is
reflected.

**Acceptance Criteria:**

```gherkin
Given RulePackLoader is initialized with a temp rules directory
When a YAML file in that directory is overwritten with a new version (e.g., ruleCount changes)
And 35 seconds elapse
Then RulePackLoader.getAllPackSummaries() returns the pack with the new ruleCount
```

**Interfaces/Contracts:** No new API surface — tests the existing `RulePackLoader` behavior.

**Implementation Plan:**

1. In `RulePackLoaderHotReloadTest`, set `app.rules.directory` to a `@TempDir` path using `@TestPropertySource`.
2. Write initial YAML (5 rules) to temp dir.
3. Instantiate `RulePackLoader` via `@SpringBootTest` with the temp dir property.
4. Assert initial load shows 5 rules.
5. Overwrite the file with 6 rules.
6. `Thread.sleep(35_000)` (acceptable in unit tests).
7. Assert `getAllPackSummaries()` shows 6 rules for that pack.

File: `rfp-service/src/test/java/com/dsi/rfp/adapter/rulepack/RulePackLoaderHotReloadTest.java`

```java

@SpringBootTest
@TestPropertySource(properties = "app.rules.directory=${java.io.tmpdir}/rfp-rules-test")
class RulePackLoaderHotReloadTest {
    @Autowired
    RulePackLoader rulePackLoader;
    @TempDir
    Path tempRulesDir;

    @Test
    @Timeout(60)
    void hotReload_detectsFileChange_within35Seconds() throws Exception {
        // write initial pack with 1 rule
        Path packFile = tempRulesDir.resolve("test-pack-v1.yaml");
        Files.writeString(packFile, buildMinimalYaml("test-pack-v1", 1));
        rulePackLoader.loadAll(); // force initial load from tempDir

        assertThat(rulePackLoader.getAllPackSummaries())
            .anyMatch(s -> s.packId().equals("test-pack-v1") && s.ruleCount() == 1);

        // overwrite with 2 rules
        Files.writeString(packFile, buildMinimalYaml("test-pack-v1", 2));

        Thread.sleep(35_000); // wait for WatchService poll cycle

        assertThat(rulePackLoader.getAllPackSummaries())
            .anyMatch(s -> s.packId().equals("test-pack-v1") && s.ruleCount() == 2);
    }
}
```

**Dependencies:** Sprint 8 `RulePackLoader` WatchService implementation.

**Risks:** `WatchService` events may be delayed on Linux `inotify` if the JVM poll interval > 30s. Mitigation: configure
`app.rules.watch.poll-interval-seconds=10` in test properties and verify the Sprint 8 WatchService respects this
property.

**Story Points:** 3

---

### Epic 5 — Frontend Updates

#### Story 5.1 — AdminPage.tsx

**Description:**
Create `AdminPage.tsx` at route `/admin`. It shows a table of loaded rule packs and a "Reload All" button. The page is
accessible to all users in Sprint 9; Sprint 11 will gate it behind the ADMIN role.

**Acceptance Criteria:**

```gherkin
Given the application is running with 4 loaded rule packs
When the user navigates to /admin
Then a table with 4 rows is displayed showing Pack ID, Version, Rule Count, Last Loaded

When the user clicks "Reload All"
Then POST /api/v1/admin/rule-packs/reload is called
And a success toast appears: "Rule packs reloaded successfully"
And the table refreshes with updated Last Loaded timestamps
```

**Interfaces/Contracts:**

File: `rfp-extractor/rfp-frontend/src/pages/AdminPage.tsx`

```tsx
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {toast} from 'sonner';
import {Button} from '@/components/ui/button';
import {Skeleton} from '@/components/ui/skeleton';
import {Table, TableBody, TableCell, TableHead, TableHeader, TableRow} from '@/components/ui/table';
import {listRulePacks, reloadRulePacks} from '../api/rfpClient';

interface RulePackSummary {
    packId: string;
    version: string;
    ruleCount: number;
    lastLoadedAt: string;
    filePath: string;
}

export function AdminPage() {
    const queryClient = useQueryClient();
    const {data: packs, isLoading} = useQuery({
        queryKey: ['rulePacks'],
        queryFn: listRulePacks,
    });

    const reloadMutation = useMutation({
        mutationFn: reloadRulePacks,
        onSuccess: (data) => {
            toast.success(`Rule packs reloaded successfully. Packs: ${data.reloadedPacks.join(', ')}`);
            queryClient.invalidateQueries({queryKey: ['rulePacks']});
        },
        onError: () => {
            toast.error('Reload failed. Check server logs.');
        },
    });

    return (
        <div className="max-w-6xl mx-auto">
            <div className="flex items-center justify-between mb-6">
                <h1 className="text-2xl font-bold">Admin — Rule Pack Management</h1>
                <Button
                    onClick={() => reloadMutation.mutate()}
                    disabled={reloadMutation.isPending}
                >
                    {reloadMutation.isPending ? 'Reloading…' : 'Reload All'}
                </Button>
            </div>

            {isLoading ? (
                <Skeleton className="h-48 w-full"/>
            ) : (
                <Table>
                    <TableHeader>
                        <TableRow>
                            <TableHead>Pack ID</TableHead>
                            <TableHead>Version</TableHead>
                            <TableHead className="text-right">Rule Count</TableHead>
                            <TableHead>Last Loaded</TableHead>
                            <TableHead>File Path</TableHead>
                        </TableRow>
                    </TableHeader>
                    <TableBody>
                        {packs?.map((pack) => (
                            <TableRow key={pack.packId}>
                                <TableCell className="font-mono text-blue-700">{pack.packId}</TableCell>
                                <TableCell>{pack.version}</TableCell>
                                <TableCell className="text-right">{pack.ruleCount}</TableCell>
                                <TableCell>{new Date(pack.lastLoadedAt).toLocaleString()}</TableCell>
                                <TableCell
                                    className="font-mono text-xs text-muted-foreground">{pack.filePath}</TableCell>
                            </TableRow>
                        ))}
                    </TableBody>
                </Table>
            )}
        </div>
    );
}
```

Add route in `App.tsx`:

```tsx
<Route path="/admin" element={<AdminPage/>}/>
```

Navigation to `/admin` is provided by the `AppLayout` sidebar (introduced in Sprint 3, see `docs/002_plan.md` §6.3).
No separate `Navbar.tsx` or `<a>` link is needed.

Add `<Toaster />` from `sonner` in `main.tsx` (render once at the app root). All future toasts use `toast()` from
`sonner` — no `useState(toast)` pattern.

Add `listRulePacks()` and `reloadRulePacks()` functions to `rfpClient.ts`:
- `listRulePacks()` → `GET /api/v1/admin/rule-packs`
- `reloadRulePacks()` → `POST /api/v1/admin/rule-packs/reload`

**Dependencies:** `rfpClient` axios instance (Sprint 2). `rfpClient` base URL is `/api/v1`.

**Story Points:** 3

---

#### Story 5.2 — ResultPage.tsx RFP Type Badge

**Description:**
Update the "Quality Gate" tab on `ResultPage.tsx` to display the detected RFP type as a colored badge at the top. If
type is `UNKNOWN`, show a warning with a suggestion to check the document.

**Acceptance Criteria:**

```gherkin
Given an RfpDocument with rfpType = "ICT"
When the user views the Quality Gate tab
Then a blue badge reading "ICT" appears at the top of the tab

Given an RfpDocument with rfpType = "UNKNOWN"
When the user views the Quality Gate tab
Then a yellow warning banner appears reading "RFP type could not be detected. Rule pack selection defaulted to ICT. Review document classification."
```

**Interfaces/Contracts:**

File: `rfp-extractor/rfp-frontend/src/pages/ResultPage.tsx` — add the following in the Quality Gate tab section:

```tsx
import { Badge } from '@/components/ui/badge';
import { Alert, AlertDescription } from '@/components/ui/alert';

// RFP Type badge — uses shadcn Badge with variant="outline" and className overrides
const RFP_TYPE_BADGE_CLASS: Record<string, string> = {
    ICT: 'text-blue-700 border-blue-300',
    WORKS: 'text-orange-700 border-orange-300',
    CONSULTANCY: 'text-purple-700 border-purple-300',
    GOODS: 'text-green-700 border-green-300',
    UNKNOWN: 'text-gray-600',
};

// In the Quality Gate tab render:
<div className="mb-4 flex items-center gap-3">
    <span className="text-sm text-muted-foreground font-medium">Detected RFP Type:</span>
    <Badge variant="outline" className={RFP_TYPE_BADGE_CLASS[doc.rfpType] ?? ''}>
        {doc.rfpType}
    </Badge>
    {doc.rfpType === 'UNKNOWN' && (
        <Alert className="ml-4 border-yellow-200 bg-yellow-50 text-yellow-800">
            <AlertDescription>
                RFP type could not be detected. Rule pack selection defaulted to ICT. Review document
                classification and resubmit if incorrect.
            </AlertDescription>
        </Alert>
    )}
</div>
```

The `doc` object is the `RfpDocument` fetched from `GET /api/v1/rfp/result/{jobId}`. Ensure `rfpType` field is included
in the response DTO from `RfpController` (add to `RfpResultDto` if not already present).

**Dependencies:** Sprint 4 `ResultPage.tsx`, Sprint 8 `RulePackResults.tsx`, `RfpDocument.rfpType` field (populated by
`RunRulePackNode`).

**Story Points:** 2

---

## 4) PR Plan

### PR 1: Rule Packs — Works, Consultancy, Goods

**Title:** `feat(rules): add Works, Consultancy, Goods rule packs (88 rules total)`

**Contents:**

- `rules/bd-govt-works-v1.yaml` (33 rules)
- `rules/bd-govt-consultancy-v1.yaml` (33 rules)
- `rules/bd-govt-goods-v1.yaml` (22 rules)
- `RfpTypeClassifier.java` updated (WORKS, CONSULTANCY keywords)
- `RfpType.java` enum updated (WORKS, CONSULTANCY values)
- `RunRulePackNode.java` updated (TYPE_TO_PACK map)
- `RulePackWorksTest.java` (33 tests)
- `RulePackConsultancyTest.java` (33 tests)
- `RulePackGoodsTest.java` (22 tests)
- `RfpTypeClassifierTest.java` (updated with new type assertions)

**Review Checklist:**

- [ ] All YAML files validate against `schema/rule-schema-v1.json` (run `RulePackLoader` startup validation in local
  test)
- [ ] JMESPath expressions use only `NOT_NULL_OR_EMPTY`, `NOT_EMPTY_ARRAY`, `BETWEEN_95_AND_105` conditions supported by
  `JmesPathEvaluator`
- [ ] No rule IDs duplicated within a pack or across packs
- [ ] All 88 new parameterized tests pass (
  `mvn test -Dtest=RulePackWorksTest+RulePackConsultancyTest+RulePackGoodsTest`)
- [ ] `RfpTypeClassifier` tests cover edge cases (no keywords, ambiguous keywords)
- [ ] No field injection (all constructors use `@RequiredArgsConstructor`)
- [ ] No class exceeds 250 lines

### PR 2: Admin API, Hot Reload Test, Frontend

**Title:** `feat(admin): rule pack admin API, hot-reload unit test, AdminPage`

**Contents:**

- `RulePackLoader.java` updated (LoadedPackEntry wrapper, `getAllPackSummaries()`, `reloadAll()`)
- `RulePackAdminService.java`
- `AdminRulePackController.java`
- `RulePackSummaryDto.java`, `ReloadResultDto.java`
- `RulePackLoaderHotReloadTest.java`
- `AdminRulePackControllerTest.java` (6 tests)
- `RulePackAdminServiceTest.java` (2 tests)
- `AdminPage.tsx`
- `ResultPage.tsx` (RFP type badge)
- `App.tsx` (new /admin route)

**Review Checklist:**

- [ ] `AdminRulePackController` has `TODO Sprint-11` comment for `@PreAuthorize` activation
- [ ] `RulePackLoader` thread safety: `getAllPackSummaries()` reads from `ConcurrentHashMap`, safe for concurrent access
- [ ] Hot reload unit test has `@Timeout(60)` to prevent CI hang
- [ ] All 6 `AdminRulePackControllerTest` tests pass with `@WebMvcTest`
- [ ] Frontend `AdminPage` handles API error (try/catch on both fetchPacks and handleReloadAll)
- [ ] `rfpClient` base URL matches the controller mapping (`/api/v1/admin/rule-packs`)
- [ ] No class exceeds 250 lines (`AdminRulePackController` is < 50 lines — check after full implementation)
- [ ] `RulePackSummaryDto` and `ReloadResultDto` are Java records (not classes) — immutable

---

## 5) Validation & Demo Script

### Step 1: Build and Start

```bash
cd rfp-extractor
mvn test -q
docker-compose up -d
# Wait for health checks
curl http://localhost:8080/api/v1/health
```

Expected:

```json
{
    "status": "UP",
    "provider": "openrouter",
    "model": "google/gemini-2.0-flash-001"
}
```

### Step 2: Verify All 4 Rule Packs Loaded

```bash
curl -s http://localhost:8080/api/v1/admin/rule-packs | jq '.'
```

Expected:

```json
[
    {
        "packId": "bd-govt-ict-v1",
        "version": "1.0.0",
        "ruleCount": 64,
        "lastLoadedAt": "...",
        "filePath": "..."
    },
    {
        "packId": "bd-govt-works-v1",
        "version": "1.0.0",
        "ruleCount": 33,
        "lastLoadedAt": "...",
        "filePath": "..."
    },
    {
        "packId": "bd-govt-consultancy-v1",
        "version": "1.0.0",
        "ruleCount": 33,
        "lastLoadedAt": "...",
        "filePath": "..."
    },
    {
        "packId": "bd-govt-goods-v1",
        "version": "1.0.0",
        "ruleCount": 22,
        "lastLoadedAt": "...",
        "filePath": "..."
    }
]
```

### Step 3: Submit a Works RFP

```bash
curl -s -X POST http://localhost:8080/api/v1/rfp/submit \
  -F "file=@testdata/ground-truth/sample-works-rfp.pdf" \
  | jq '.jobId'
# Save jobId as WORKS_JOB_ID
```

Poll status:

```bash
curl -s http://localhost:8080/api/v1/rfp/status/$WORKS_JOB_ID | jq '.status'
# Wait until "COMPLETED"
```

Get result and verify rfpType:

```bash
curl -s http://localhost:8080/api/v1/rfp/result/$WORKS_JOB_ID | jq '.rfpType'
```

Expected: `"WORKS"`

Get rule pack results:

```bash
curl -s http://localhost:8080/api/v1/rfp/result/$WORKS_JOB_ID | jq '.rulePackResults.findings | group_by(.severity) | map({severity: .[0].severity, count: length})'
```

### Step 4: Verify Rule Pack Selection

```bash
curl -s http://localhost:8080/api/v1/rfp/result/$WORKS_JOB_ID \
  | jq '.rulePackResults.packId'
```

Expected: `"bd-govt-works-v1"`

### Step 5: Hot Reload Demonstration

```bash
# Get current ruleCount for works pack
curl -s http://localhost:8080/api/v1/admin/rule-packs | jq '.[] | select(.packId=="bd-govt-works-v1") | .ruleCount'
# Should be 33

# Add a comment to the YAML file (to trigger WatchService without breaking the pack)
echo "" >> rfp-extractor/rules/bd-govt-works-v1.yaml

# Wait 35 seconds
sleep 35

# Check last loaded timestamp has updated
curl -s http://localhost:8080/api/v1/admin/rule-packs | jq '.[] | select(.packId=="bd-govt-works-v1") | .lastLoadedAt'
# Should show a newer timestamp than the initial load
```

### Step 6: Manual Reload via API

```bash
curl -s -X POST http://localhost:8080/api/v1/admin/rule-packs/reload | jq '.'
```

Expected:

```json
{
    "reloadedPacks": [
        "bd-govt-ict-v1",
        "bd-govt-works-v1",
        "bd-govt-consultancy-v1",
        "bd-govt-goods-v1"
    ],
    "timestamp": "2025-08-15T..."
}
```

### Step 7: Frontend Walkthrough

1. Open browser: `http://localhost:3000`
2. Navigate to `/admin` — confirm table shows 4 rule packs with correct rule counts.
3. Click "Reload All" — confirm success toast appears and table refreshes.
4. Upload `sample-works-rfp.pdf` via Upload page.
5. On Result page, go to "Quality Gate" tab — confirm orange "WORKS" badge at top.
6. If submitting a document with no clear type — confirm yellow warning banner appears.

---

## 6) Exit Criteria (NON-NEGOTIABLE)

- [ ] `rules/bd-govt-works-v1.yaml` passes `RulePackLoader` schema validation with 0 errors. Pack has exactly 33 rules (
  30 structural + 3 semantic). `mvn test -Dtest=RulePackWorksTest` shows 33/33 green.
- [ ] `rules/bd-govt-consultancy-v1.yaml` passes schema validation with 0 errors. Pack has exactly 33 rules.
  `mvn test -Dtest=RulePackConsultancyTest` shows 33/33 green.
- [ ] `rules/bd-govt-goods-v1.yaml` passes schema validation with 0 errors. Pack has exactly 22 rules.
  `mvn test -Dtest=RulePackGoodsTest` shows 22/22 green.
- [ ] `GET /api/v1/admin/rule-packs` returns 200 with an array of 4 objects, each containing `packId`, `version`,
  `ruleCount`, `lastLoadedAt`, `filePath`.
- [ ] `POST /api/v1/admin/rule-packs/reload` returns 200 with `reloadedPacks` array of 4 IDs and a non-null `timestamp`.
- [ ] Hot reload: after modifying a YAML file on disk, `lastLoadedAt` updates within 35 seconds as verified by
  `GET /api/v1/admin/rule-packs`.
- [ ] A Works RFP document is classified as `RfpType.WORKS` and `rulePackResults.packId` = `"bd-govt-works-v1"`.
- [ ] A Consultancy RFP document is classified as `RfpType.CONSULTANCY` and `rulePackResults.packId` =
  `"bd-govt-consultancy-v1"`.
- [ ] `AdminPage.tsx` renders at `/admin` with the rule pack table and working "Reload All" button.
- [ ] ResultPage Quality Gate tab shows the RFP type badge with correct color per type; shows yellow warning for
  UNKNOWN.
- [ ] `mvn test` is green: all 88 new rule pack tests + 6 controller tests + 2 service tests pass with no failures.
- [ ] `AdminRulePackController` has a `TODO Sprint-11` comment for `@PreAuthorize` and does NOT have a real
  `@PreAuthorize` annotation (Sprint 11 activates it).
- [ ] No class introduced in this sprint exceeds 250 lines.

---

## 7) Notes: Assumptions / Open Questions

**Assumption:** `RulePackLoader` from Sprint 8 polls the rules directory every 30 seconds using `WatchService`. It
exposes a `reloadAll()` method that triggers immediate reload of all files in the directory. If this method does not
exist, it must be added before Story 3.1 can be implemented.

**Assumption:** `pricing_factors` in `RfpDocument` is `List<String>` where each element is a plain string description of
a pricing factor (e.g., "liquidated damages 0.1% per day"). JMESPath `contains(@, 'liquidated')` filtering works on
string arrays. If the field is `List<Map<String, Object>>`, the JMESPath expressions in the Works/Goods packs must be
revised to use `contains(value, 'liquidated')` or equivalent.

**Assumption:** `RfpType` enum is defined in `rfp-core/src/main/java/com/dsi/rfp/domain/model/RfpType.java`. `WORKS` and
`CONSULTANCY` values do not exist yet. If they do exist, skip enum modification.

**Assumption:** The `rfpClient` Axios instance in the frontend has its base URL set to `http://localhost:8080/api/v1` in
development and `/api/v1` in production (proxied by Vite/Nginx). The `AdminPage` calls
`rfpClient.get('/admin/rule-packs')` which resolves correctly.

**Assumption:** The hot reload test (`RulePackLoaderHotReloadTest`) is a unit-level test placed in the `src/test/java`
directory with standard unit-test annotations so it runs in normal `mvn test`
execution. The CI pipeline runs unit tests only.

**Decision:** The admin API returns HTTP 200 with an empty array when no packs are loaded. Include a descriptive message
field in the response payload; do not treat empty pack state as server error.

**Decision:** WatchService poll interval is configurable via `app.rules.watch.poll-interval-seconds` with default `30`.
Set it to `10` seconds in test properties to speed `RulePackLoaderHotReloadTest`.
