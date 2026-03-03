# Sprint 4 — LangGraph4J Agent + Full Entity Extraction

## 0) Sprint Intent

- Wire a complete LangGraph4J `StateGraph<ExtractionState>` with 10 named nodes, where every node reads and writes a
  single immutable state object, replacing the ad-hoc service call chain used in Sprint 3.
- Deliver 7 domain-specific entity extractors (General, Submission, Financial, ICT, Staffing, Support, Evaluation) each
  using the Template Method pattern via `BaseEntityExtractor`, so adding a new extractor in a later sprint requires only
  one new subclass.
- Introduce a `ScoreConfidenceNode` that computes per-field confidence scores and populates a `lowConfidenceQueue` fed
  into the (stubbed) repair loop, ensuring the confidence model is in place before Sprint 7's repair logic activates it.
- Deliver 7 functional LLM prompt files with frontmatter metadata and in-context few-shot examples so that the
  extraction quality is measurable immediately against real RFP PDFs.
- Update the React frontend to render all extracted entity fields grouped by domain category in a tabbed `EntityTable`
  component with confidence badges, making the extraction output visible to users this sprint.

**Non-goals:**

- Table extraction (ExtractTablesNode is a stub returning empty list; full implementation is Sprint 5).
- Repair loop activation (RepairLoopNode is a stub that passes through; active in Sprint 7).
- Rule-pack evaluation (RunRulePackNode is a stub; active in Sprint 8).
- Authentication or JWT (Sprint 11).
- Export to DOCX/XLSX (Sprint 10).

---

## 1) Entry Criteria

- Sprint 3 is merged and green on CI.
- `SectionSegmenter`, `TocDetector`, `ClauseIdAssigner` are available in `rfp-core`.
- `RfpSubmissionService` and `RfpJobService` are operational with REST endpoints `POST /submit`, `GET /status/{jobId}`,
  `GET /jobs`.
- `LlmAdapter` (Spring AI, Resilience4j-wrapped) is available in `rfp-service` adapter layer: 30s timeout, 3 retries,
  circuit breaker, 60/min rate limiter.
- `PageClassifier` and `PageClassificationTaskRunner` are operational.
- `JpaJobStateRepository` implements `JobStatePort`, `LocalDocumentStorageAdapter` implements `DocumentStoragePort`.
- All domain models (`RfpDocument`, `Section`, `Clause`, `ExtractionJob`, `JobStatus`, `PageClassification`,
  `PageSummary`, `RfpEntities`) are compiled and present in `rfp-core`.
- Port interfaces `ExtractionPort`, `JobStatePort`, `DocumentStoragePort`, `SectionSegmentationPort` are defined in
  `rfp-core`.
- `RfpJsonSchemaValidator` (networknt) is wired and operational.
- `UploadPage`, `JobStatusPage`, `ResultPage` (with `SectionTree`) are shipping in the React frontend.
- LangGraph4J dependency `1.8.4` is declared in root `pom.xml` but no graph code exists yet.
- `mvn test` passes with no failures.

---

## 2) Deliverables

| #    | Deliverable                                          | Type                    | Location                                                                                        |
|------|------------------------------------------------------|-------------------------|-------------------------------------------------------------------------------------------------|
| D-01 | `ExtractionState.java`                               | LangGraph4J state class | `rfp-service/src/main/java/com/dsi/rfp/agent/ExtractionState.java`                              |
| D-02 | `ExtractionGraph.java`                               | LangGraph4J graph def   | `rfp-service/src/main/java/com/dsi/rfp/agent/ExtractionGraph.java`                              |
| D-03 | `ExtractionOrchestrationService.java`                | Application service     | `rfp-service/src/main/java/com/dsi/rfp/application/service/ExtractionOrchestrationService.java` |
| D-04 | `ValidateNode.java`                                  | Graph node              | `rfp-service/src/main/java/com/dsi/rfp/agent/node/ValidateNode.java`                            |
| D-05 | `ClassifyPagesNode.java`                             | Graph node              | `rfp-service/src/main/java/com/dsi/rfp/agent/node/ClassifyPagesNode.java`                       |
| D-06 | `ExtractTextNode.java`                               | Graph node              | `rfp-service/src/main/java/com/dsi/rfp/agent/node/ExtractTextNode.java`                         |
| D-07 | `SegmentSectionsNode.java`                           | Graph node              | `rfp-service/src/main/java/com/dsi/rfp/agent/node/SegmentSectionsNode.java`                     |
| D-08 | `ExtractTablesNode.java` (stub)                      | Graph node stub         | `rfp-service/src/main/java/com/dsi/rfp/agent/node/ExtractTablesNode.java`                       |
| D-09 | `ExtractEntitiesNode.java`                           | Graph node              | `rfp-service/src/main/java/com/dsi/rfp/agent/node/ExtractEntitiesNode.java`                     |
| D-10 | `ScoreConfidenceNode.java`                           | Graph node              | `rfp-service/src/main/java/com/dsi/rfp/agent/node/ScoreConfidenceNode.java`                     |
| D-11 | `RepairLoopNode.java` (stub)                         | Graph node stub         | `rfp-service/src/main/java/com/dsi/rfp/agent/node/RepairLoopNode.java`                          |
| D-12 | `RunRulePackNode.java` (stub)                        | Graph node stub         | `rfp-service/src/main/java/com/dsi/rfp/agent/node/RunRulePackNode.java`                         |
| D-13 | `FinalizeNode.java`                                  | Graph node              | `rfp-service/src/main/java/com/dsi/rfp/agent/node/FinalizeNode.java`                            |
| D-14 | `DocumentChunk.java`                                 | Domain value object     | `rfp-core/src/main/java/com/dsi/rfp/domain/model/DocumentChunk.java`                            |
| D-15 | `DocumentChunkingService.java`                       | Adapter service         | `rfp-service/src/main/java/com/dsi/rfp/adapter/extraction/DocumentChunkingService.java`         |
| D-16 | `BaseEntityExtractor.java`                           | Abstract adapter class  | `rfp-service/src/main/java/com/dsi/rfp/adapter/entity/BaseEntityExtractor.java`                 |
| D-17 | `GeneralEntityExtractor.java`                        | Concrete extractor      | `rfp-service/src/main/java/com/dsi/rfp/adapter/entity/GeneralEntityExtractor.java`              |
| D-18 | `SubmissionEntityExtractor.java`                     | Concrete extractor      | `rfp-service/src/main/java/com/dsi/rfp/adapter/entity/SubmissionEntityExtractor.java`           |
| D-19 | `FinancialEntityExtractor.java`                      | Concrete extractor      | `rfp-service/src/main/java/com/dsi/rfp/adapter/entity/FinancialEntityExtractor.java`            |
| D-20 | `IctEntityExtractor.java`                            | Concrete extractor      | `rfp-service/src/main/java/com/dsi/rfp/adapter/entity/IctEntityExtractor.java`                  |
| D-21 | `StaffingEntityExtractor.java`                       | Concrete extractor      | `rfp-service/src/main/java/com/dsi/rfp/adapter/entity/StaffingEntityExtractor.java`             |
| D-22 | `SupportEntityExtractor.java`                        | Concrete extractor      | `rfp-service/src/main/java/com/dsi/rfp/adapter/entity/SupportEntityExtractor.java`              |
| D-23 | `EvaluationEntityExtractor.java`                     | Concrete extractor      | `rfp-service/src/main/java/com/dsi/rfp/adapter/entity/EvaluationEntityExtractor.java`           |
| D-24 | `EntityExtractor.java`                               | Adapter orchestrator    | `rfp-service/src/main/java/com/dsi/rfp/adapter/entity/EntityExtractor.java`                     |
| D-25 | `entity-general-v1.md`                               | Prompt file             | `rfp-service/src/main/resources/prompts/entity-general-v1.md`                                   |
| D-26 | `entity-submission-v1.md`                            | Prompt file             | `rfp-service/src/main/resources/prompts/entity-submission-v1.md`                                |
| D-27 | `entity-financial-v1.md`                             | Prompt file             | `rfp-service/src/main/resources/prompts/entity-financial-v1.md`                                 |
| D-28 | `entity-ict-v1.md`                                   | Prompt file             | `rfp-service/src/main/resources/prompts/entity-ict-v1.md`                                       |
| D-29 | `entity-staffing-v1.md`                              | Prompt file             | `rfp-service/src/main/resources/prompts/entity-staffing-v1.md`                                  |
| D-30 | `entity-support-v1.md`                               | Prompt file             | `rfp-service/src/main/resources/prompts/entity-support-v1.md`                                   |
| D-31 | `entity-evaluation-v1.md`                            | Prompt file             | `rfp-service/src/main/resources/prompts/entity-evaluation-v1.md`                                |
| D-32 | `RepairLogEntry.java`                                | Domain value object     | `rfp-core/src/main/java/com/dsi/rfp/domain/model/RepairLogEntry.java`                           |
| D-33 | `RulePackResults.java`                               | Domain value object     | `rfp-core/src/main/java/com/dsi/rfp/domain/model/RulePackResults.java`                          |
| D-34 | `EntityTable.tsx`                                    | React component         | `rfp-frontend/src/components/EntityTable.tsx`                                                   |
| D-35 | `ResultPage.tsx` (updated)                           | React page              | `rfp-frontend/src/pages/ResultPage.tsx`                                                         |
| D-36 | Unit tests (agent + extractors)                      | JUnit 5 tests           | `rfp-service/src/test/java/com/dsi/rfp/agent/` and `.../adapter/entity/`                        |
| D-37 | Delete `ExtractionPipelineService`                   | Refactor (deletion)     | `rfp-service/src/main/java/com/dsi/rfp/application/service/ExtractionPipelineService.java`      |
| D-38 | `MimeTypePort` interface                             | Domain port             | `rfp-core/src/main/java/com/dsi/rfp/domain/port/MimeTypePort.java`                              |
| D-39 | `PageSummary` rename from `PageClassificationResult` | Refactor                | `rfp-core/src/main/java/com/dsi/rfp/domain/model/PageSummary.java`                              |

---

## 3) Work Breakdown

### Epic 4.1 — LangGraph4J Graph Skeleton

#### Story 4.1.1 — ExtractionState typed state class

**Acceptance Criteria (Gherkin):**

```gherkin
Given a new ExtractionJob is created with jobId "job-001" and documentPath "/docs/rfp.pdf"
When ExtractionState.initial(jobId, documentPath) is called
Then the state has jobId "job-001", documentPath "/docs/rfp.pdf"
And pageClassifications, sections, tables, clauses are empty lists
And entities is null, confidenceMap is empty map, repairLog is empty list
And lowConfidenceQueue, manualReviewRequired are empty lists
And totalRepairIterations is 0, repairExhausted is false
And rulePackResults is null

Given an ExtractionState with sections populated
When state.withSections(newSections) is called
Then a new ExtractionState is returned with the updated sections field
And the original state is unchanged (immutable record semantics)
```

**Interfaces / Contracts:**

```java
// rfp-service/src/main/java/com/dsi/rfp/agent/ExtractionState.java
package com.dsi.rfp.agent;

import com.dsi.rfp.domain.model.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.util.*;

@Value
@Builder(toBuilder = true)
public class ExtractionState {

    @JsonProperty("jobId")
    String jobId;

    @JsonProperty("documentPath")
    String documentPath;

    // NOTE: Sprint 2 defines PageClassificationResult (not PageSummary). Either rename
    // PageClassificationResult to PageSummary at Sprint 4 (document as a refactoring task),
    // OR use PageClassificationResult here consistently. Do not introduce a second type
    // for the same concept. The chosen name must be used uniformly across all sprints.
    @JsonProperty("pageClassifications")
    @Builder.Default
    List<PageSummary> pageClassifications = List.of();  // PageSummary = PageClassificationResult renamed

    @JsonProperty("sections")
    @Builder.Default
    List<Section> sections = List.of();

    @JsonProperty("tables")
    @Builder.Default
    List<Table> tables = List.of();           // empty this sprint

    @JsonProperty("clauses")
    @Builder.Default
    List<Clause> clauses = List.of();

    @JsonProperty("entities")
    RfpEntities entities;                      // null until ExtractEntitiesNode

    @JsonProperty("confidenceMap")
    @Builder.Default
    Map<String, Double> confidenceMap = Map.of();

    @JsonProperty("repairLog")
    @Builder.Default
    List<RepairLogEntry> repairLog = List.of();

    @JsonProperty("lowConfidenceQueue")
    @Builder.Default
    List<String> lowConfidenceQueue = List.of();   // component IDs

    @JsonProperty("manualReviewRequired")
    @Builder.Default
    List<String> manualReviewRequired = List.of();

    @JsonProperty("totalRepairIterations")
    @Builder.Default
    int totalRepairIterations = 0;

    @JsonProperty("repairExhausted")
    @Builder.Default
    boolean repairExhausted = false;

    @JsonProperty("rulePackResults")
    RulePackResults rulePackResults;               // null this sprint

    public static ExtractionState initial(String jobId, String documentPath) {
        return ExtractionState.builder()
            .jobId(jobId)
            .documentPath(documentPath)
            .build();
    }
}
```

**Implementation Plan:**

1. Create `rfp-core/.../domain/model/RepairLogEntry.java` as a `@Value @Builder` Lombok class with fields:
   `fieldId (String)`, `previousValue (String)`, `newValue (String)`, `repairReason (String)`, `iterationNumber (int)`,
   `timestamp (Instant)`.
2. Create `rfp-core/.../domain/model/RulePackResults.java` as a `@Value @Builder` Lombok class with fields:
   `packId (String)`, `passedRules (List<String>)`, `failedRules (List<String>)`, `warnRules (List<String>)`,
   `executedAt (Instant)`. Mark all list fields with `@Builder.Default List.of()`.
3. Create `rfp-core/.../domain/model/Table.java` placeholder with fields: `tableId (String)`, `pageNumber (int)`,
   `sectionId (String)`, `headers (List<String>)`, `rows (List<List<String>>)`, `tableType (String)`. Mark
   `@Value @Builder`.
4. Create `ExtractionState.java` in `rfp-service/.../agent/` using `@Value @Builder(toBuilder=true)`. All `List<>` and
   `Map<>` fields must declare `@Builder.Default` with immutable defaults.
5. Add Jackson `com.fasterxml.jackson.annotation` to `rfp-core` pom if not present — needed for LangGraph4J JSON
   serialization of state.
6. Write unit test `ExtractionStateTest` verifying `initial()` factory, `toBuilder()` copy semantics, and that lists are
   never null.

**Test Plan:**

- `shouldInitialiseWithEmptyCollectionsWhenFactoryMethodCalled()` — all list/map fields non-null and empty, scalars at
  defaults
- `shouldReturnNewInstanceWhenToBuilderMutationApplied()` — original unchanged, new instance has updated field
- `shouldSerialiseAndDeserialiseViaJacksonWithoutDataLoss()` — round-trip `ObjectMapper` JSON

**Observability:** No runtime logging needed for a value class. Serialization errors caught at graph compile time.

**Story Points:** 3

---

#### Story 4.1.2 — ExtractionGraph definition and compilation

**Acceptance Criteria (Gherkin):**

```gherkin
Given all 10 node beans are registered in the Spring context
When ExtractionGraph.build() is called
Then a compiled StateGraph<ExtractionState> is returned without exception
And the graph has exactly 10 nodes registered by name
And the edge START -> VALIDATE_NODE -> CLASSIFY_PAGES -> EXTRACT_TEXT -> SEGMENT_SECTIONS -> EXTRACT_TABLES -> EXTRACT_ENTITIES -> SCORE_CONFIDENCE -> REPAIR_LOOP -> RUN_RULE_PACK -> FINALIZE -> END is defined
And a conditional edge exists from SCORE_CONFIDENCE to REPAIR_LOOP when anyFieldBelowThreshold returns true
And the same conditional edge routes to RUN_RULE_PACK when anyFieldBelowThreshold returns false
```

**Interfaces / Contracts:**

```java
// rfp-service/src/main/java/com/dsi/rfp/agent/ExtractionGraph.java
package com.dsi.rfp.agent;

import com.dsi.rfp.agent.node.*;
import lombok.RequiredArgsConstructor;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

@Component
@RequiredArgsConstructor
public class ExtractionGraph {

    public static final String VALIDATE_NODE = "VALIDATE";
    public static final String CLASSIFY_PAGES = "CLASSIFY_PAGES";
    public static final String EXTRACT_TEXT = "EXTRACT_TEXT";
    public static final String SEGMENT_SECTIONS = "SEGMENT_SECTIONS";
    public static final String EXTRACT_TABLES = "EXTRACT_TABLES";
    public static final String EXTRACT_ENTITIES = "EXTRACT_ENTITIES";
    public static final String SCORE_CONFIDENCE = "SCORE_CONFIDENCE";
    public static final String REPAIR_LOOP = "REPAIR_LOOP";
    public static final String RUN_RULE_PACK = "RUN_RULE_PACK";
    public static final String FINALIZE = "FINALIZE";

    private final ValidateNode validateNode;
    private final ClassifyPagesNode classifyPagesNode;
    private final ExtractTextNode extractTextNode;
    private final SegmentSectionsNode segmentSectionsNode;
    private final ExtractTablesNode extractTablesNode;
    private final ExtractEntitiesNode extractEntitiesNode;
    private final ScoreConfidenceNode scoreConfidenceNode;
    private final RepairLoopNode repairLoopNode;
    private final RunRulePackNode runRulePackNode;
    private final FinalizeNode finalizeNode;

    // CRITICAL: LangGraph4J requires the state class to implement AgentState (or provide a
    // StateFactory). ExtractionState uses Lombok @Value (immutable, final fields).
    // LangGraph4J nodes return Map<String,Object> updates; the framework merges these into
    // the state via reflection or AgentState.update(). With @Value + final fields, reflection
    // cannot set the fields — this will fail at runtime with IllegalAccessException.
    //
    // CORRECT DESIGN: ExtractionState must implement AgentState. The AgentState interface
    // in LangGraph4J exposes an update(Map<String,Object>) method (or equivalent) for merging
    // node outputs into state. Alternatively, use the LangGraph4J state builder / factory pattern.
    // Verify the exact API against the chosen LangGraph4J version before implementing.
    // The @Value + @Builder(toBuilder=true) pattern alone is NOT sufficient.
    //
    // Also: ExtractionState is currently defined in rfp-service/agent/ but it is a domain
    // concept. It should be in rfp-core/domain/model/ so that both rfp-core and rfp-service
    // components can reference it without creating upward dependency cycles. Sprint 7 entry
    // criteria also assumes it is in rfp-core.
    public StateGraph<ExtractionState> build() throws Exception {
        var graph = new StateGraph<>(ExtractionState.class);
        // register nodes ...
        // add edges ...
        // add conditional edge from SCORE_CONFIDENCE ...
        return graph;
    }

    private String routeAfterScoring(ExtractionState state) {
        return ConfidenceRouter.anyFieldBelowThreshold(state) ? REPAIR_LOOP : RUN_RULE_PACK;
    }
}

// rfp-service/src/main/java/com/dsi/rfp/agent/ConfidenceRouter.java
package com.dsi.rfp.agent;

public final class ConfidenceRouter {
    public static final double LOW_CONFIDENCE_THRESHOLD = 0.6;

    private ConfidenceRouter() {
    }

    public static boolean anyFieldBelowThreshold(ExtractionState state) {
        return state.getConfidenceMap().values().stream()
            .anyMatch(score -> score < LOW_CONFIDENCE_THRESHOLD);
    }
}
```

**Implementation Plan:**

1. Add `langgraph4j-core 1.8.4` dependency to `rfp-service/pom.xml` if not already declared in root BOM.
2. Create the `ExtractionGraph` Spring `@Component`. Inject all 10 node beans via constructor injection with Lombok
   `@RequiredArgsConstructor`.
3. Inside `build()`: call `graph.addNode(VALIDATE_NODE, validateNode::apply)` for each of the 10 nodes. Each node bean
   implements `NodeAction<ExtractionState>` (functional interface with `apply(ExtractionState) throws Exception`
   returning `Map<String,Object>`).
4. Add linear edges: `START→VALIDATE_NODE`, `VALIDATE_NODE→CLASSIFY_PAGES`, ..., `REPAIR_LOOP→RUN_RULE_PACK`,
   `RUN_RULE_PACK→FINALIZE`, `FINALIZE→END`.
5. Replace the `SCORE_CONFIDENCE→REPAIR_LOOP` simple edge with a conditional edge:
   `graph.addConditionalEdges(SCORE_CONFIDENCE, this::routeAfterScoring, Map.of(REPAIR_LOOP, REPAIR_LOOP, RUN_RULE_PACK, RUN_RULE_PACK))`.
6. Create `ConfidenceRouter` as a package-private utility class with `anyFieldBelowThreshold(ExtractionState)` and
   `LOW_CONFIDENCE_THRESHOLD = 0.6`.
7. Write `ExtractionGraphTest` that calls `build()` and asserts no exception; use Mockito to stub all 10 node beans.

**Test Plan:**

- `shouldBuildGraphWithoutExceptionWhenAllNodeBeansPresent()` — `graph.build()` completes, non-null compiled graph
- `shouldRouteToRepairLoopWhenAnyFieldBelowThreshold()` — `ConfidenceRouter.anyFieldBelowThreshold` returns true given
  map with entry `< 0.6`
- `shouldRouteToRulePackWhenAllFieldsAboveThreshold()` — all entries `>= 0.6` → returns false

**Observability:**
`log.info("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=INFO graph=ExtractionGraph compiled nodes={}", nodeCount)`
at startup.

**Story Points:** 5

---

#### Story 4.1.3 — ExtractionOrchestrationService and graph invocation

**Acceptance Criteria (Gherkin):**

```gherkin
Given a valid jobId and documentPath stored in PostgreSQL
When ExtractionOrchestrationService.runExtraction(jobId, documentPath) is called
Then an ExtractionState.initial() is built and passed to the compiled graph
And graph.invoke(initialState) is called exactly once
And the job status in PostgreSQL is updated to RUNNING before invocation (JobStatus.RUNNING — not IN_PROGRESS)
And the job status is updated to COMPLETED after successful invocation
And if graph.invoke() throws an exception, AsyncUncaughtExceptionHandler sets the job status to FAILED
And the exception message is stored in the job's errorMessage field by JobStateAsyncExceptionHandler
```

**Interfaces / Contracts:**

```java
// rfp-service/src/main/java/com/dsi/rfp/application/service/ExtractionOrchestrationService.java
package com.dsi.rfp.application.service;

import com.dsi.rfp.agent.ExtractionGraph;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.port.JobStatePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExtractionOrchestrationService {

    private final ExtractionGraph extractionGraph;
    private final JobStatePort jobStatePort;

    @Async("extractionExecutor")
    public void runExtraction(String jobId, String documentPath) {
        // 1. update job status to IN_PROGRESS
        // 2. build compiled graph
        // 3. build initial state
        // 4. invoke graph
        // 5. update status to COMPLETED or FAILED
    }
}

// application.yml additions:
// spring.task.execution.pool.core-size: 4
// spring.task.execution.pool.max-size: 8
// spring.task.execution.pool.queue-capacity: 20
// spring.task.execution.thread-name-prefix: extraction-
```

**Implementation Plan:**

1. Create `ExtractionOrchestrationService` in `rfp-service/.../application/service/`. Inject `ExtractionGraph` and
   `JobStatePort` only — keep to 2 dependencies.
2. Annotate `runExtraction()` with `@Async("extractionExecutor")`. Add `@EnableAsync` to a Spring config class in the
   `adapter/config/` package.
3. Define the `extractionExecutor` bean in `AsyncConfig.java`: `ThreadPoolTaskExecutor` with core=4, max=8,
   queue-capacity=20, thread-name-prefix `extraction-`, `RejectedExecutionHandler = AbortPolicy` (default — lets
   `JobQueueGuard` in Sprint 12 handle overflow via HTTP 503; never use `CallerRunsPolicy` which would block a REST
   thread for the full extraction duration).
4. In `runExtraction()`: do NOT wrap in try-catch — per the exception policy, @Async void methods
   must NOT use catch (Exception e). Instead, `AsyncUncaughtExceptionHandler` (Sprint 2) handles
   unhandled exceptions and marks the job FAILED. On entry call
   `jobStatePort.updateStatus(jobId, JobStatus.RUNNING)` — NOTE: the correct enum value is RUNNING
   (NOT IN_PROGRESS; the JobStatus enum defined in Sprint 2 has QUEUED, RUNNING, COMPLETED, FAILED, PARTIAL).
   Build `ExtractionState.initial(jobId, documentPath)`. Call `extractionGraph.build().invoke(initialState)`. On success
   call `jobStatePort.updateStatus(jobId, JobStatus.COMPLETED)`. Any exception propagates to the
   `JobStateAsyncExceptionHandler` which stores the error message and sets FAILED.
5. Update `RfpSubmissionService.submit()` to call `extractionOrchestrationService.runExtraction(jobId, path)` instead of
   any prior ad-hoc chain.
   **IMPORTANT — remove `ExtractionPipelineService` from Sprint 2**: Once `ExtractionOrchestrationService` is wired,
   `ExtractionPipelineService` must be deleted (or marked `@Deprecated` as a first step). Leaving both active creates
   a zombie service — both would run on submit, doubling async jobs. Sprint 4 deliverables must include:
   "Delete `ExtractionPipelineService` and all references to it."
6. Wire `@EnableAsync` on `AsyncConfig.java`, not on the main application class.

**Test Plan:**

- `shouldUpdateStatusToInProgressThenCompletedWhenGraphSucceeds()` — mock `ExtractionGraph.build().invoke()`, verify
  `jobStatePort` called with IN_PROGRESS then COMPLETED
- `shouldUpdateStatusToFailedWhenGraphThrowsException()` — mock graph throws `RuntimeException`, verify FAILED status
  and `errorMessage` stored
- `shouldCallRunExtractionAsyncWhenSubmitInvoked()` — integration via `@SpringBootTest` with `@Async` disabled, verify
  method is called

**Observability:**

-

`log.info("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=INFO extraction.start jobId={} documentPath={}", jobId, documentPath)`
before graph invocation

-

`log.info("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=INFO extraction.complete jobId={} durationMs={}", jobId, elapsed)`
on success

-

`log.error("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=ERROR extraction.failed jobId={} error={}", jobId, e.getMessage())`
on failure

- Micrometer counter: `rfp.extraction.started`, `rfp.extraction.completed`, `rfp.extraction.failed`

**Story Points:** 5

---

#### Story 4.1.4 — Graph node stubs (ValidateNode, ClassifyPagesNode, ExtractTextNode, SegmentSectionsNode, ExtractTablesNode stub, FinalizeNode)

**Acceptance Criteria (Gherkin):**

```gherkin
Given ExtractionState with documentPath pointing to a file that does not exist
When ValidateNode.apply(state) is called
Then an IllegalStateException is thrown with message containing "Document not found"

Given ExtractionState with a valid documentPath
When ClassifyPagesNode.apply(state) is called
Then state.pageClassifications is populated with one PageSummary per page
And each PageSummary has a non-null classification (DIGITAL, SCANNED, or MIXED)

Given ExtractionState after ClassifyPagesNode
When ExtractTextNode.apply(state) is called
Then state.clauses is populated with Clause objects for DIGITAL pages

Given ExtractionState after ExtractTextNode
When SegmentSectionsNode.apply(state) is called
Then state.sections contains Section objects with non-null sectionIds
And state.clauses is updated with clause-to-section assignments

Given ExtractionState after SegmentSectionsNode
When ExtractTablesNode.apply(state) is called (stub)
Then state.tables is an empty list and no exception is thrown

Given a complete ExtractionState
When FinalizeNode.apply(state) is called
Then an RfpDocument is assembled from state
And the document is saved via DocumentStoragePort
And the job status is updated to COMPLETED via JobStatePort
```

**Interfaces / Contracts:**

```java
// Common node interface (LangGraph4J NodeAction)
// rfp-service/src/main/java/com/dsi/rfp/agent/node/ValidateNode.java
package com.dsi.rfp.agent.node;

import com.dsi.rfp.agent.ExtractionState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ValidateNode implements NodeAction<ExtractionState> {

    @Override
    public Map<String, Object> apply(ExtractionState state) throws Exception {
        Path doc = Path.of(state.getDocumentPath());
        if (!Files.exists(doc)) {
            throw new IllegalStateException("Document not found: " + state.getDocumentPath());
        }
        log.info("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=INFO validate.ok jobId={} path={}", state.getJobId(), state.getDocumentPath());
        return Map.of(); // no state mutation needed
    }
}

// FinalizeNode signature
public class FinalizeNode implements NodeAction<ExtractionState> {
    private final DocumentStoragePort documentStoragePort;
    private final JobStatePort jobStatePort;
    private final RfpDocumentAssembler assembler;

    @Override
    public Map<String, Object> apply(ExtractionState state) throws Exception { ...}
}

// RfpDocumentAssembler.java — adapter/entity/
public class RfpDocumentAssembler {
    public RfpDocument assemble(ExtractionState state) { ...}
}
```

**Implementation Plan:**

1. Create each node class in `rfp-service/.../agent/node/` as a `@Component @RequiredArgsConstructor`. Each implements
   `NodeAction<ExtractionState>` and returns a `Map<String,Object>` of state field updates — LangGraph4J merges these
   into the next state.
2. `ValidateNode`: check `Files.exists(Path.of(state.getDocumentPath()))`. Throw `IllegalStateException` on miss.
3. `ClassifyPagesNode`: inject `PageClassifier`. Call
   `pageClassifier.classifyAllPages(Path.of(state.getDocumentPath()), state.getJobId())`. Return
   `Map.of("pageClassifications", results)`.
4. `ExtractTextNode`: inject `PdfDocumentLoader`. Iterate `state.getPageClassifications()`, call
   `pdfDocumentLoader.loadPageText(path, pageNum)` for DIGITAL pages. Build `List<Clause>` from text. Return
   `Map.of("clauses", clauses)`.
5. `SegmentSectionsNode`: inject `SectionSegmenter` and `ClauseIdAssigner`. Call
   `sectionSegmenter.segment(state.getClauses())`. Assign clause IDs. Return
   `Map.of("sections", sections, "clauses", updatedClauses)`.
6. `ExtractTablesNode` (stub): log warning, return `Map.of("tables", List.of())`.
7. `FinalizeNode`: inject `DocumentStoragePort`, `JobStatePort`, `RfpDocumentAssembler`. Call
   `assembler.assemble(state)`. Save via `documentStoragePort.save(doc)`. Update job status. Return `Map.of()`.
8. Create `RfpDocumentAssembler` in `adapter/entity/` — pure mapping from state fields to `RfpDocument`.

**Test Plan:**

- `shouldThrowIllegalStateExceptionWhenDocumentPathMissing()` — `ValidateNode.apply()` with non-existent path
- `shouldReturnEmptyTableListWhenExtractTablesNodeInvoked()` — stub node returns empty
- `shouldAssembleRfpDocumentFromStateWhenFinalizeNodeInvoked()` — mock ports, verify `documentStoragePort.save()` called
  once
- `shouldPopulatePageClassificationsWhenClassifyPagesNodeInvoked()` — mock `PageClassifier`, verify list populated

**Observability:**

-

`log.info("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=INFO node.start node={} jobId={}", nodeName, jobId)`
at entry of each non-trivial node

-

`log.info("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=INFO node.complete node={} jobId={} items={}", nodeName, jobId, resultCount)`
at exit

- Database row `agent_steps` updated at each node entry for progress tracking

**Story Points:** 8

---

### Epic 4.2 — Entity Extractor Framework (BaseEntityExtractor + Template Method)

#### Story 4.2.1 — DocumentChunk value object and DocumentChunkingService

**Acceptance Criteria (Gherkin):**

```gherkin
Given a PDF with 20 pages of text
When DocumentChunkingService.chunkDocument(pdfPath, sections) is called
Then the result is a List<DocumentChunk> where each chunk has tokenEstimate <= 3500
And each chunk has a non-null contextHeader containing title and procurementRef
And adjacent chunks share at least 200 tokens of overlap
And chunkIndex is sequential starting from 0

Given sections with title "Ministry of ICT RFP" and procurementRef "ICT/2024/001"
When DocumentChunkingService.chunkDocument() is called
Then every chunk's contextHeader contains "Ministry of ICT RFP" and "ICT/2024/001"
```

**Interfaces / Contracts:**

```java
// rfp-core/src/main/java/com/dsi/rfp/domain/model/DocumentChunk.java
package com.dsi.rfp.domain.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class DocumentChunk {
    int chunkIndex;
    List<Section> sections;
    String rawText;
    int tokenEstimate;
    String contextHeader;      // prepended to every prompt using this chunk
}

// rfp-service/src/main/java/com/dsi/rfp/adapter/extraction/DocumentChunkingService.java
package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.domain.model.DocumentChunk;
import com.dsi.rfp.domain.model.Section;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.nio.file.Path;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentChunkingService {

    private static final int MAX_TOKENS_PER_CHUNK = 3500;
    private static final int OVERLAP_TOKENS = 200;

    // Uses LangChain4J RecursiveCharacterTextSplitter internally
    public List<DocumentChunk> chunkDocument(Path pdfPath, List<Section> sections) { ...}

    private String buildContextHeader(List<Section> sections) { ...}

    private int estimateTokens(String text) {
        return (int) Math.ceil(text.length() / 4.0); // 4 chars ≈ 1 token
    }
}
```

**Implementation Plan:**

1. Create `DocumentChunk.java` in `rfp-core/domain/model/` as `@Value @Builder`. List field `sections` needs
   `@Builder.Default List.of()`.
2. Do NOT add an explicit LangChain4J version to `rfp-service/pom.xml`. Use `dev.langchain4j:langchain4j` with version
   managed by the root POM property `${langchain4j.version}` (pinned in Sprint 1 parent POM — verify the exact version
   in `rfp-extractor/pom.xml` before coding; the root POM is the single source of truth for dependency versions).
   Adding an explicit version here would create a classpath conflict with the root POM-managed version.
3. In `DocumentChunkingService.chunkDocument()`: extract full text from all sections' clauses (concatenate clause
   bodies). Instantiate
   `RecursiveCharacterTextSplitter.builder().maxSegmentSizeInChars(14000).maxOverlapSizeInChars(800).build()` (4 chars
   per token × 3500 = 14000).
4. Split the concatenated text. For each segment, build `DocumentChunk` with `chunkIndex`, `rawText = segment`,
   `tokenEstimate = estimateTokens(segment)`, `contextHeader = buildContextHeader(sections)`.
5. `buildContextHeader()` extracts from `sections.get(0)` (or first non-null): `title`, `procurementRef`, `rfpType`,
   `issuer`. Format:
   `"# Document Context\nTitle: {title}\nRef: {procurementRef}\nType: {rfpType}\nIssuer: {issuer}\n---\n"`.
6. Section-to-chunk mapping: include sections whose clause text falls within the segment's character range.

**Test Plan:**

- `shouldProducedChunksWithTokenEstimateBelowMaxWhenDocumentIsChunked()` — large synthetic text, all chunks `<= 3500`
  tokens
- `shouldIncludeContextHeaderInEveryChunkWhenSectionsHaveMetadata()` — header contains title and procurementRef
- `shouldStartChunkIndexAtZeroAndBeSequentialWhenMultipleChunksProduced()` — chunkIndex 0, 1, 2, ...

**Observability:**

-

`log.info("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=INFO chunking.complete jobId={} chunkCount={} totalTokens={}", jobId, chunks.size(), totalTokens)` —
note: jobId
passed as optional context param

**Story Points:** 3

---

#### Story 4.2.2 — BaseEntityExtractor abstract class with Template Method

**Acceptance Criteria (Gherkin):**

```gherkin
Given a concrete extractor that extends BaseEntityExtractor
When extract(sections, clauses, state) is called
Then chunkSections() is called first to produce a list of DocumentChunks
And buildPrompt(chunk) is called for each chunk
And callLlm(prompt) is called for each chunk
And parseResponse(json) is called for each LLM response
And validateFields(parsed) is called on each parsed result
And the results are merged and returned

Given callLlm returns invalid JSON for a chunk
When extract() processes that chunk
Then parseResponse is retried once
And if the retry also fails the field confidence is set to 0.0
And extraction continues for remaining chunks (no exception propagation)

Given a concrete extractor with temperature=0
When callLlm(prompt) is called
Then LlmAdapter is called with temperature 0.0
And the model ID is google/gemini-2.0-flash-001
```

**Interfaces / Contracts:**

```java
// rfp-service/src/main/java/com/dsi/rfp/adapter/entity/BaseEntityExtractor.java
package com.dsi.rfp.adapter.entity;

import com.dsi.rfp.adapter.extraction.DocumentChunkingService;
import com.dsi.rfp.adapter.llm.LlmAdapter;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.Clause;
import com.dsi.rfp.domain.model.DocumentChunk;
import com.dsi.rfp.domain.model.Section;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public abstract class BaseEntityExtractor {

    protected static final String EXTRACTION_MODEL = "google/gemini-2.0-flash-001";
    protected static final double TEMPERATURE = 0.0;

    protected final LlmAdapter llmAdapter;
    protected final DocumentChunkingService chunkingService;
    protected final ObjectMapper objectMapper;

    // Template method — final so subclasses cannot override the algorithm
    public final Map<String, Object> extract(
        List<Section> sections,
        List<Clause> clauses,
        ExtractionState state) {

        List<DocumentChunk> chunks = chunkSections(sections);
        Map<String, Object> accumulated = new java.util.LinkedHashMap<>();

        for (DocumentChunk chunk : chunks) {
            String prompt = buildPrompt(chunk);
            String json = callLlmWithRetry(prompt, state.getJobId());
            Map<String, Object> parsed = parseResponseSafe(json, state.getJobId());
            validateFields(parsed);
            mergeInto(accumulated, parsed);
        }
        return accumulated;
    }

    protected List<DocumentChunk> chunkSections(List<Section> sections) {
        return chunkingService.chunkDocument(null, sections);
    }

    protected abstract String buildPrompt(DocumentChunk chunk);

    protected abstract void validateFields(Map<String, Object> parsed);

    protected abstract String promptResourcePath();

    private String callLlmWithRetry(String prompt, String jobId) { ...}

    private Map<String, Object> parseResponseSafe(String json, String jobId) { ...}

    private void mergeInto(Map<String, Object> target, Map<String, Object> source) { ...}
}
```

**Implementation Plan:**

1. Create `BaseEntityExtractor.java` in `rfp-service/.../adapter/entity/`. Mark `@Slf4j @RequiredArgsConstructor`. Make
   `extract()` `final` — this is the template method.
2. `chunkSections()` delegates to `DocumentChunkingService.chunkDocument(null, sections)`. `null` path is acceptable
   when sections already contain raw clause text.
3. `callLlmWithRetry(prompt, jobId)`: call `llmAdapter.chat(EXTRACTION_MODEL, prompt, TEMPERATURE)`. If
   `JsonParseException` on parse, retry once via `llmAdapter.chat()`. On second failure log warning and return `"{}"`.
4. `parseResponseSafe(json, jobId)`: wrap `objectMapper.readValue(json, Map.class)` in try-catch. On failure log
   `"entity.parse.failed jobId={} extractor={}"`, return `Map.of("_parseError", true)`.
5. `validateFields(Map)` is abstract — subclasses log warnings for missing mandatory fields and insert `null` with
   confidence `0.0` via a sentinel key convention `_confidence_{fieldName} = 0.0`.
6. `mergeInto(target, source)`: `source.forEach(target::putIfAbsent)` — first chunk wins for any field. Subclasses may
   override merge strategy.
7. Load prompt files from classpath using `new ClassPathResource(promptResourcePath())` in `buildPrompt()`'s default
   scaffolding. Concrete class supplies path.

**Test Plan:**

- `shouldCallChunkSectionsThenBuildPromptThenCallLlmInOrderWhenExtractInvoked()` — verify call order with Mockito
  InOrder
- `shouldRetryOnceAndSetConfidenceZeroWhenJsonParseFailsTwice()` — stub LLM to return invalid JSON twice, verify
  `_parseError` in result and no exception thrown
- `shouldNotPropagateExceptionFromOneChunkToNextWhenChunkFails()` — 3 chunks, second fails JSON parse, third still
  processed

**Observability:**

-

`log.debug("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=DEBUG entity.extract.start extractor={} jobId={} chunkCount={}", className, jobId, chunks.size())`
-
`log.warn("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=WARN entity.parse.failed extractor={} jobId={} chunk={}", className, jobId, chunkIndex)`
-
`log.info("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=INFO entity.extract.done extractor={} jobId={} fields={}", className, jobId, resultSize)`

**Story Points:** 5

---

### Epic 4.3 — Seven Domain-Specific Entity Extractors

#### Story 4.3.1 — GeneralEntityExtractor and entity-general-v1.md prompt

**Acceptance Criteria (Gherkin):**

```gherkin
Given sections containing "Submission Deadline: 15 March 2025" and "Client: Ministry of ICT"
When GeneralEntityExtractor.extract(sections, clauses, state) is called
Then the LLM prompt contains the chunk text and instruction to extract client_name, submission_deadline, issue_date, method_of_selection, procurement_method, project_duration, pre_bid_meeting, contact
And the returned map contains key "client_name" with value "Ministry of ICT"
And the returned map contains key "submission_deadline" with value "2025-03-15" (ISO date)

Given the LLM returns a JSON object with all required fields
When GeneralEntityExtractor.validateFields(parsed) is called
Then no warning is logged (all fields present)
```

**Interfaces / Contracts:**

```java
// rfp-service/src/main/java/com/dsi/rfp/adapter/entity/GeneralEntityExtractor.java
package com.dsi.rfp.adapter.entity;

import com.dsi.rfp.adapter.extraction.DocumentChunkingService;
import com.dsi.rfp.adapter.llm.LlmAdapter;
import com.dsi.rfp.domain.model.DocumentChunk;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GeneralEntityExtractor extends BaseEntityExtractor {

    private static final List<String> REQUIRED_FIELDS = List.of(
        "client_name", "submission_deadline", "issue_date", "method_of_selection",
        "procurement_method", "project_duration", "pre_bid_meeting", "contact"
    );

    public GeneralEntityExtractor(LlmAdapter llmAdapter,
                                  DocumentChunkingService chunkingService,
                                  ObjectMapper objectMapper) {
        super(llmAdapter, chunkingService, objectMapper);
    }

    @Override
    protected String buildPrompt(DocumentChunk chunk) { ...}

    @Override
    protected void validateFields(Map<String, Object> parsed) {
        REQUIRED_FIELDS.stream()
            .filter(f -> !parsed.containsKey(f))
            .forEach(f -> log.warn("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=WARN entity.field.missing extractor=General field={}", f));
    }

    @Override
    protected String promptResourcePath() {
        return "prompts/entity-general-v1.md";
    }
}
```

**Prompt file (`rfp-service/src/main/resources/prompts/entity-general-v1.md`):**

```markdown
---
id: entity-general
version: 1.0.0
model: google/gemini-2.0-flash-001
max_tokens: 2048
temperature: 0.0
---

You are an expert procurement analyst. Extract the following fields from the RFP document chunk below.
Return ONLY valid JSON. Do not add explanation or markdown fencing.

## Fields to Extract

| Field | Description | Format |
|-------|-------------|--------|
| client_name | Full legal name of the procuring entity | String |
| submission_deadline | Final deadline for bid submission | ISO 8601 date-time or date string |
| issue_date | Date the RFP was issued | ISO 8601 date string |
| method_of_selection | Procurement method (e.g., QCBS, LCS, SSS) | String |
| procurement_method | Procurement category (Goods/Works/Services) | String |
| project_duration | Duration of the project in months | String |
| pre_bid_meeting | Pre-bid meeting date and location | String or null |
| contact | Name, email, phone of primary contact | Object {name, email, phone} or String |

## Few-Shot Example

Input:
"Ministry of Digital Affairs invites proposals for an FMIS system. The RFP is issued on 2 January 2025 and
proposals must be submitted by 3 March 2025, 5:00 PM BST. The method of selection is QCBS. A pre-bid
meeting will be held on 20 January 2025 at the Ministry's conference room. Contact: procurement@mda.gov.bd."

Output:
{
"client_name": "Ministry of Digital Affairs",
"submission_deadline": "2025-03-03T17:00:00",
"issue_date": "2025-01-02",
"method_of_selection": "QCBS",
"procurement_method": "Services",
"project_duration": null,
"pre_bid_meeting": "2025-01-20, Ministry conference room",
"contact": {"name": null, "email": "procurement@mda.gov.bd", "phone": null}
}

## Document Context

{{contextHeader}}

## Document Chunk

{{chunkText}}

## Output

Return JSON only. Use null for fields not found. Do not invent values.
```

**Implementation Plan:**

1. Create `GeneralEntityExtractor` extending `BaseEntityExtractor`. Constructor injection only — no field injection.
2. `buildPrompt(chunk)`: load `entity-general-v1.md` via `ClassPathResource`, read as UTF-8 string, replace
   `{{contextHeader}}` with `chunk.getContextHeader()` and `{{chunkText}}` with `chunk.getRawText()`.
3. `validateFields(parsed)`: stream over `REQUIRED_FIELDS`, log warn for each missing key.
4. Create the prompt file at `rfp-service/src/main/resources/prompts/entity-general-v1.md` with full content including
   frontmatter, field table, and few-shot example.
5. Write unit test with stubbed `LlmAdapter` returning a canned JSON response; assert returned map has all 8 keys.

**Test Plan:**

- `shouldExtractClientNameAndDeadlineWhenLlmReturnsParsableJson()` — stub LLM, verify map keys
- `shouldLogMissingFieldWarningWhenLlmResponseOmitsRequiredField()` — stub missing key, verify `log.warn` captured by
  Slf4j test appender
- `shouldReturnPromptContainingContextHeaderWhenBuildPromptCalled()` — verify `{{contextHeader}}` replaced

**Observability:** Inherits from `BaseEntityExtractor`. No additional metrics.

**Story Points:** 3

---

#### Story 4.3.2 — Remaining six entity extractors (Submission, Financial, ICT, Staffing, Support, Evaluation)

**Acceptance Criteria (Gherkin):**

```gherkin
Given valid RFP sections with financial terms
When FinancialEntityExtractor.extract(sections, clauses, state) is called
Then the returned map contains keys: technical_financial_split, performance_security, bank_guarantee, payment_terms, reimbursable_expenses, bid_validity_period

Given valid RFP sections with ICT requirements
When IctEntityExtractor.extract(sections, clauses, state) is called
Then the returned map contains at minimum: total_users, concurrent_users, programming_language_preference, database, hosting
And the returned map contains boolean fields: data_migration_required, mobile_app_required, ui_mock_required, gantt_chart_required

Given an evaluator that extends BaseEntityExtractor
When any extractor's promptResourcePath() is called
Then it returns a path matching "prompts/entity-{domain}-v1.md"
```

**Interfaces / Contracts:**

```java
// SubmissionEntityExtractor — fields
private static final List<String> REQUIRED_FIELDS = List.of(
        "guidelines_summary", "number_of_copies", "soft_submission_required", "submission_address"
    );

// FinancialEntityExtractor — fields
private static final List<String> REQUIRED_FIELDS = List.of(
    "technical_financial_split", "performance_security", "bank_guarantee",
    "payment_terms", "reimbursable_expenses", "bid_validity_period"
);

// IctEntityExtractor — fields
private static final List<String> REQUIRED_FIELDS = List.of(
    "total_users", "concurrent_users", "programming_language_preference", "system_language",
    "architecture", "tech_stack", "database", "hosting", "data_migration_required",
    "legacy_system", "hardware_requirements", "integrations", "mobile_app_required",
    "ui_mock_required", "presentation_required", "gantt_chart_required", "e_governance_compliance"
);

// StaffingEntityExtractor — fields
private static final List<String> REQUIRED_FIELDS = List.of(
    "staff_months", "onsite_resource_requirements", "marking_criteria"
);

// SupportEntityExtractor — fields
private static final List<String> REQUIRED_FIELDS = List.of(
    "training", "support_maintenance", "warranty_period"
);

// EvaluationEntityExtractor — fields
private static final List<String> REQUIRED_FIELDS = List.of(
    "criteria", "eligibility_summary", "scope_summary"
);
```

**Implementation Plan:**

1. Create all 6 extractor classes in `rfp-service/.../adapter/entity/`, each extending `BaseEntityExtractor`, each
   annotated `@Component`.
2. Each constructor takes `(LlmAdapter, DocumentChunkingService, ObjectMapper)` — super call only.
3. Each `promptResourcePath()` returns the appropriate `"prompts/entity-{domain}-v1.md"` path.
4. Each `buildPrompt(chunk)` loads the prompt file via `ClassPathResource`, replaces `{{contextHeader}}` and
   `{{chunkText}}`.
5. Create all 6 prompt files in `rfp-service/src/main/resources/prompts/`. Each must have frontmatter, field table
   describing each field with type annotation, and at least one few-shot example derived from realistic government RFP
   text.
6. For `IctEntityExtractor`: annotate boolean fields in the prompt as `boolean (true/false)` to guide LLM output.
7. For `EvaluationEntityExtractor`: `criteria` field should be `Array of {criterion, weight, description}`.

**Prompt highlights for each file:**

`entity-submission-v1.md` — instructs extraction of how many physical copies, whether digital submission is required,
address for delivery. Few-shot: "Submit 3 hard copies and 1 soft copy on USB to Room 412, ICTD Building, Dhaka".

`entity-financial-v1.md` — instructs extraction of technical/financial weight split (e.g., 70/30), performance
security %, bank guarantee requirements, payment schedule terms. Few-shot: "Technical: 70%, Financial: 30%. Performance
Security: 5% of contract value. Payment: 30% advance, 50% on delivery, 20% on UAT acceptance."

`entity-ict-v1.md` — instructs extraction of user counts, language preferences (Java, Python, etc.), architecture
pattern (microservices, monolith), database type, cloud/on-prem hosting. Few-shot with multi-field JSON output.

`entity-staffing-v1.md` — instructs extraction of staff-month totals, onsite/remote split, marking criteria (experience,
qualification, methodology weights). Few-shot: "Team Leader: 6 SM, Sr Developer: 12 SM. Experience: 30%, Qualification:
20%, Methodology: 50%."

`entity-support-v1.md` — instructs extraction of training days, support tiers (L1/L2/L3), warranty duration. Few-shot: "
3-year warranty. 5-day user training. 24/7 L1 support. Annual maintenance contract."

`entity-evaluation-v1.md` — instructs extraction of evaluation criteria with weights summing to 100. eligibility
requirements (years of experience, turnover threshold). scope summary paragraph. Few-shot with criteria array.

**Test Plan:**

- `shouldExtractTechnicalFinancialSplitWhenFinancialExtractorCalled()` — stub LLM, verify key present
- `shouldExtractDataMigrationRequiredAsBooleanWhenIctExtractorCalled()` — verify boolean type in result map
- `shouldExtractCriteriaAsListWhenEvaluationExtractorCalled()` — verify `criteria` is `List`
- `shouldLogWarnForEachMissingRequiredFieldWhenLlmResponseIncomplete()` — per-extractor test with partial JSON

**Observability:** Inherits base observability. Extractor class name included in every log line.

**Story Points:** 13

---

#### Story 4.3.3 — EntityExtractor orchestrator

**Acceptance Criteria (Gherkin):**

```gherkin
Given all 7 sub-extractors are Spring-injected
When EntityExtractor.extractAll(sections, clauses, state) is called
Then all 7 sub-extractors are called with the same sections and clauses
And the results are merged into a single RfpEntities object
And if one sub-extractor throws an exception the others still run
And the failing extractor's fields are present with null values in RfpEntities
```

**Interfaces / Contracts:**

```java
// rfp-service/src/main/java/com/dsi/rfp/adapter/entity/EntityExtractor.java
package com.dsi.rfp.adapter.entity;

import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.Clause;
import com.dsi.rfp.domain.model.RfpEntities;
import com.dsi.rfp.domain.model.Section;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EntityExtractor {

    private final GeneralEntityExtractor generalExtractor;
    private final SubmissionEntityExtractor submissionExtractor;
    private final FinancialEntityExtractor financialExtractor;
    private final IctEntityExtractor ictExtractor;
    private final StaffingEntityExtractor staffingExtractor;
    private final SupportEntityExtractor supportExtractor;
    private final EvaluationEntityExtractor evaluationExtractor;
    private final RfpEntitiesMapper entitiesMapper;

    public RfpEntities extractAll(List<Section> sections, List<Clause> clauses, ExtractionState state) {
        Map<String, Object> merged = new HashMap<>();
        runExtractor(generalExtractor, sections, clauses, state, merged, "general");
        runExtractor(submissionExtractor, sections, clauses, state, merged, "submission");
        runExtractor(financialExtractor, sections, clauses, state, merged, "financial");
        runExtractor(ictExtractor, sections, clauses, state, merged, "ict");
        runExtractor(staffingExtractor, sections, clauses, state, merged, "staffing");
        runExtractor(supportExtractor, sections, clauses, state, merged, "support");
        runExtractor(evaluationExtractor, sections, clauses, state, merged, "evaluation");
        return entitiesMapper.fromMap(merged);
    }

    private void runExtractor(BaseEntityExtractor extractor, List<Section> sections,
                              List<Clause> clauses, ExtractionState state,
                              Map<String, Object> target, String name) {
        target.putAll(extractor.extract(sections, clauses, state));
    }
}
```

**Implementation Plan:**

1. Create `EntityExtractor` with 7 sub-extractor injections + `RfpEntitiesMapper`. Keep to under 250 lines.
2. Create `RfpEntitiesMapper.java` in `adapter/entity/` — a `@Component` that maps a raw `Map<String,Object>` to
   `RfpEntities` domain object using safe casts and null defaults.
3. `runExtractor()` does not catch generic exceptions. Failures propagate and are mapped by the global exception
   handler.
4. `extractAll()` method is under 20 lines — loop over a `List<Pair<BaseEntityExtractor, String>>` if needed to keep it
   clean. If inline calls keep the method under 20 lines, use inline form shown above.
5. Create `ExtractEntitiesNode` in `agent/node/`: inject `EntityExtractor`, call
   `extractAll(state.getSections(), state.getClauses(), state)`, return `Map.of("entities", rfpEntities)`.

**Test Plan:**

- `shouldCallAllSevenExtractorsWhenExtractAllInvoked()` — mock all 7, verify each called once
- `shouldContinueExtractingWhenOneExtractorThrowsException()` — one extractor throws, other 6 still called
- `shouldMergeResultsIntoRfpEntitiesWhenAllExtractorsSucceed()` — verify mapped RfpEntities non-null

**Observability:**

-

`log.info("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=INFO entity.extractAll.start jobId={}", state.getJobId())`
-
`log.info("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=INFO entity.extractAll.done jobId={} fields={}", state.getJobId(), merged.size())`

- Micrometer timer: `rfp.entity.extraction.duration` tagged `extractor=general|submission|...`

**Story Points:** 5

---

### Epic 4.4 — ScoreConfidenceNode and FinalizeNode

#### Story 4.4.1 — ScoreConfidenceNode per-field scoring

**Acceptance Criteria (Gherkin):**

```gherkin
Given an ExtractionState with RfpEntities where client_name is "Ministry of ICT"
When ScoreConfidenceNode.apply(state) is called
Then confidenceMap contains key "client_name" with value 1.0

Given an ExtractionState with RfpEntities where submission_deadline is null
When ScoreConfidenceNode.apply(state) is called
Then confidenceMap contains key "submission_deadline" with value 0.0
And "submission_deadline" is in lowConfidenceQueue

Given an ExtractionState where 5 of 7 critical fields are non-null
When ScoreConfidenceNode.apply(state) is called
Then confidenceMap contains key "doc_completeness_score" with value approximately 0.714 (5/7)

Given confidenceMap has any field with score < 0.6
When ConfidenceRouter.anyFieldBelowThreshold(state) is called
Then it returns true
```

**Interfaces / Contracts:**

```java
// rfp-service/src/main/java/com/dsi/rfp/agent/node/ScoreConfidenceNode.java
package com.dsi.rfp.agent.node;

import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.RfpEntities;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class ScoreConfidenceNode implements NodeAction<ExtractionState> {

    static final List<String> CRITICAL_FIELDS = List.of(
        "client_name", "submission_deadline", "procurement_ref",
        "technical_financial_split", "marking_criteria",
        "criteria", "eligibility_summary"
    );
    static final double LOW_CONFIDENCE_THRESHOLD = 0.6;

    @Override
    public Map<String, Object> apply(ExtractionState state) throws Exception {
        Map<String, Double> scores = new LinkedHashMap<>();
        List<String> lowConfQueue = new ArrayList<>();
        RfpEntities entities = state.getEntities();

        scoreField(scores, lowConfQueue, "client_name", entities.getClientName());
        scoreField(scores, lowConfQueue, "submission_deadline", entities.getSubmissionDeadline());
        scoreField(scores, lowConfQueue, "procurement_ref", entities.getProcurementRef());
        scoreField(scores, lowConfQueue, "technical_financial_split", entities.getTechnicalFinancialSplit());
        scoreField(scores, lowConfQueue, "marking_criteria", entities.getMarkingCriteria());
        scoreField(scores, lowConfQueue, "criteria", entities.getCriteria());
        scoreField(scores, lowConfQueue, "eligibility_summary", entities.getEligibilitySummary());
        // ... all other entity fields ...

        double completeness = computeCompleteness(scores);
        scores.put("doc_completeness_score", completeness);

        log.info("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=INFO confidence.score jobId={} completeness={} lowConfFields={}",
            state.getJobId(), completeness, lowConfQueue);
        return Map.of("confidenceMap", scores, "lowConfidenceQueue", lowConfQueue);
    }

    private void scoreField(Map<String, Double> scores, List<String> queue, String key, Object value) {
        double score = scoreValue(value);
        scores.put(key, score);
        if (score < LOW_CONFIDENCE_THRESHOLD) queue.add(key);
    }

    private double scoreValue(Object value) {
        if (Objects.isNull(value)) return 0.0;
        String str = value.toString().strip();
        if (str.isEmpty()) return 0.0;
        if (str.length() < 5) return 0.5;  // present but low-quality (very short)
        return 1.0;
    }

    private double computeCompleteness(Map<String, Double> scores) {
        long nonNull = CRITICAL_FIELDS.stream()
            .filter(f -> scores.getOrDefault(f, 0.0) > 0.0)
            .count();
        return (double) nonNull / CRITICAL_FIELDS.size();
    }
}
```

**Implementation Plan:**

1. Create `ScoreConfidenceNode` as a `@Component` implementing `NodeAction<ExtractionState>`.
2. Define `CRITICAL_FIELDS` as a `static final List<String>` — these 7 fields drive `doc_completeness_score`.
3. `scoreValue(Object)`: null → 0.0; empty string → 0.0; length < 5 → 0.5 (low quality signal); otherwise → 1.0.
4. Score every field of `RfpEntities` by reflective access or explicit `scoreField()` calls. Explicit calls preferred
   for testability.
5. `computeCompleteness()`: count critical fields with score > 0.0, divide by 7. Round to 4 decimal places.
6. Return `Map.of("confidenceMap", scores, "lowConfidenceQueue", lowConfQueue)`. LangGraph4J merges these into state.
7. `RepairLoopNode` (stub): return `Map.of()` unchanged, log `"repair.stub jobId={} — skipped (Sprint 7)"`.
8. `RunRulePackNode` (stub): return `Map.of()` unchanged, log `"rule-pack.stub jobId={} — skipped (Sprint 8)"`.

**Test Plan:**

- `shouldScoreFieldAs1WhenValueIsNonNullAndSufficientlyLong()` — "Ministry of ICT" → 1.0
- `shouldScoreFieldAs0WhenValueIsNull()` — null → 0.0 and field in lowConfidenceQueue
- `shouldScoreFieldAs0_5WhenValueIsVeryShort()` — "N/A" (3 chars) → 0.5
- `shouldComputeCompletenessAs5Over7WhenFiveCriticalFieldsNonNull()` — verify 0.7142... result
- `shouldPopulateLowConfidenceQueueWhenAnyFieldBelow0_6()` — queue contains correct field names

**Observability:**

-

`log.info("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=INFO confidence.score jobId={} completeness={:.2f} lowConfFields={}",...)`
on every invocation

- `analysis_results.doc_completeness_score` stores completeness score in PostgreSQL

**Story Points:** 5

---

### Epic 4.5 — Frontend Entity Display

#### Story 4.5.1 — EntityTable React component with confidence badges

**Acceptance Criteria (Gherkin):**

```gherkin
Given ResultPage loads with a completed extraction job
When the EntityTable component is rendered
Then 7 tabs are visible: General, Submission, Financial, ICT, Staffing, Support, Evaluation
And clicking each tab shows the fields for that category
And each field row shows: label, value, source clause ID (linked), confidence badge

Given a field with confidence 1.0
When the confidence badge is rendered
Then the badge has CSS class "bg-green-500" and text "HIGH"

Given a field with confidence 0.5
When the confidence badge is rendered
Then the badge has CSS class "bg-yellow-400" and text "MED"

Given a field with confidence 0.0 or field is null
When the confidence badge is rendered
Then the badge has CSS class "bg-red-500" and text "LOW"

Given ResultPage layout
When EntityTable is rendered
Then it appears in the right panel, tabbed alongside SectionTree on the left
```

**Interfaces / Contracts:**

```typescript
// rfp-frontend/src/components/EntityTable.tsx
import React, {useState} from 'react';

type ConfidenceBadgeProps = {
    score: number;
};

type EntityField = {
    key: string;
    label: string;
    value: string | null;
    sourceClauseId?: string;
    confidence: number;
};

type EntityCategory = {
    id: string;
    label: string;
    fields: EntityField[];
};

type EntityTableProps = {
    entities: Record<string, unknown>;
    confidenceMap: Record<string, number>;
    onClauseClick: (clauseId: string) => void;
};

export const EntityTable: React.FC<EntityTableProps> = ({
                                                            entities, confidenceMap, onClauseClick
                                                        }) => {
    const [activeTab, setActiveTab] = useState<string>('general');
    // ...
};

const ConfidenceBadge: React.FC<ConfidenceBadgeProps> = ({score}) => {
    if (score >= 0.8) return <span className = "bg-green-500 text-white px-2 py-0.5 rounded text-xs font-bold" > HIGH < /span>;
    if (score >= 0.5) return <span className = "bg-yellow-400 text-gray-900 px-2 py-0.5 rounded text-xs font-bold" > MED < /span>;
    return <span className = "bg-red-500 text-white px-2 py-0.5 rounded text-xs font-bold" > LOW < /span>;
};
```

**Implementation Plan:**

1. Create `EntityTable.tsx` in `rfp-frontend/src/components/`. Use React `useState` for active tab. Tailwind v4 for
   styling.
2. Define a `CATEGORIES` constant mapping tab IDs to lists of `EntityField` keys with human-readable labels. This is a
   static lookup table co-located in the component file.
3. Tab bar: render 7 tab buttons using `CATEGORIES.map()`. Active tab highlighted with `border-b-2 border-blue-500`.
4. Field rows: render `<table>` inside the active tab pane with columns: Label | Value | Source | Confidence.
5. `ConfidenceBadge`: standalone inner component. Score `>= 0.8` → green HIGH, `>= 0.5` → yellow MED, `< 0.5` → red LOW.
6. Source clause link: if `sourceClauseId` present, render
   `<button className="text-blue-500 underline text-xs" onClick={() => onClauseClick(sourceClauseId)}>`. The
   `onClauseClick` prop scrolls to the clause in `SectionTree`.
7. Update `ResultPage.tsx` to split into left (SectionTree) and right (EntityTable) panels using
   `<div className="grid grid-cols-2 gap-4">`. Add `confidenceMap` and `entities` to the data fetched from
   `GET /api/v1/rfp/result/{jobId}`.
8. Add REST endpoint `GET /api/v1/rfp/result/{jobId}` in `rfp-service` that returns
   `{entities: RfpEntities, confidenceMap: Map<String,Double>, sections: List<Section>}`.

**Test Plan (Vitest + React Testing Library):**

- `shouldRenderSevenTabsWhenEntityTableMounted()` — query all tab buttons, assert 7 present
- `shouldShowCorrectFieldsWhenTabClicked()` — click "Financial" tab, assert `technical_financial_split` label visible
- `shouldRenderGreenBadgeWhenConfidenceAbove0_8()` — render badge with `score=1.0`, assert `bg-green-500` class
- `shouldRenderRedBadgeWhenConfidenceIsZero()` — render badge with `score=0.0`, assert `bg-red-500` class

**Observability:** Client-side only. No server-side observability needed for the component itself.

**Story Points:** 5

---

## 4) PR Plan

| PR# | Title                                                                                   | Files Changed                                                                                                                                                                                                                                                                                      | Merge Order | Dependencies |
|-----|-----------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------|--------------|
| 1   | feat: ExtractionState + RepairLogEntry + RulePackResults + Table domain models          | `rfp-core/.../model/ExtractionState.java`, `RepairLogEntry.java`, `RulePackResults.java`, `Table.java`                                                                                                                                                                                             | 1           | None         |
| 2   | feat: DocumentChunk model + DocumentChunkingService                                     | `rfp-core/.../model/DocumentChunk.java`, `rfp-service/.../adapter/extraction/DocumentChunkingService.java`                                                                                                                                                                                         | 2           | PR #1        |
| 3   | feat: BaseEntityExtractor template method framework                                     | `rfp-service/.../adapter/entity/BaseEntityExtractor.java`                                                                                                                                                                                                                                          | 3           | PR #2        |
| 4   | feat: GeneralEntityExtractor + entity-general-v1.md prompt                              | `GeneralEntityExtractor.java`, `prompts/entity-general-v1.md`                                                                                                                                                                                                                                      | 4           | PR #3        |
| 5   | feat: Remaining 6 entity extractors + prompts                                           | `SubmissionEntityExtractor.java`, `FinancialEntityExtractor.java`, `IctEntityExtractor.java`, `StaffingEntityExtractor.java`, `SupportEntityExtractor.java`, `EvaluationEntityExtractor.java`, 6 prompt files                                                                                      | 5           | PR #4        |
| 6   | feat: EntityExtractor orchestrator + RfpEntitiesMapper + ExtractEntitiesNode            | `EntityExtractor.java`, `RfpEntitiesMapper.java`, `agent/node/ExtractEntitiesNode.java`                                                                                                                                                                                                            | 6           | PR #5        |
| 7   | feat: LangGraph4J ExtractionGraph skeleton + all node stubs                             | `ExtractionGraph.java`, `ConfidenceRouter.java`, `ValidateNode.java`, `ClassifyPagesNode.java`, `ExtractTextNode.java`, `SegmentSectionsNode.java`, `ExtractTablesNode.java` (stub), `RepairLoopNode.java` (stub), `RunRulePackNode.java` (stub), `FinalizeNode.java`, `RfpDocumentAssembler.java` | 7           | PR #6        |
| 8   | feat: ScoreConfidenceNode + ConfidenceRouter                                            | `ScoreConfidenceNode.java`, `ConfidenceRouter.java` (updated)                                                                                                                                                                                                                                      | 8           | PR #7        |
| 9   | feat: ExtractionOrchestrationService + AsyncConfig + wire to RfpSubmissionService       | `ExtractionOrchestrationService.java`, `AsyncConfig.java`, updated `RfpSubmissionService.java`                                                                                                                                                                                                     | 9           | PR #8        |
| 10  | feat: EntityTable.tsx + ResultPage two-panel layout + GET /api/v1/rfp/result/{jobId} endpoint | `EntityTable.tsx`, updated `ResultPage.tsx`, new REST controller method                                                                                                                                                                                                                            | 10          | PR #9        |

---

## 5) Validation & Demo Script

```bash
# ── 1. Build and unit test ──────────────────────────────────────────────────
cd rfp-extractor
mvn clean test -pl rfp-core,rfp-service
# Expected: BUILD SUCCESS, ~80 new tests pass

# ── 2. Confirm graph compiles at startup ────────────────────────────────────
mvn spring-boot:run -pl rfp-service &
sleep 8
curl -s http://localhost:8080/actuator/health | jq '.status'
# Expected: "UP"
# Log should contain: "graph=ExtractionGraph compiled nodes=10"

# ── 3. Submit a test RFP PDF and trace the full graph execution ─────────────
JOB_ID=$(curl -s -X POST http://localhost:8080/api/submit \
  -F "file=@/tmp/test-rfp.pdf" | jq -r '.jobId')
echo "Job ID: $JOB_ID"

# Poll until COMPLETED
for i in $(seq 1 30); do
  STATUS=$(curl -s http://localhost:8080/api/status/$JOB_ID | jq -r '.status')
  echo "[$i] Status: $STATUS"
  [ "$STATUS" = "COMPLETED" ] && break
  sleep 5
done

# ── 4. Inspect extracted entities ───────────────────────────────────────────
curl -s http://localhost:8080/api/v1/rfp/result/$JOB_ID | jq '.entities'
# Expected: JSON object with keys: client_name, submission_deadline, etc.

curl -s http://localhost:8080/api/v1/rfp/result/$JOB_ID | jq '.confidenceMap'
# Expected: JSON object with scores between 0.0 and 1.0
# Expected: doc_completeness_score key present

curl -s http://localhost:8080/api/v1/rfp/result/$JOB_ID | jq '.confidenceMap.doc_completeness_score'
# Expected: float between 0.0 and 1.0

# ── 5. Verify low confidence queue routing ─────────────────────────────────
# Submit an RFP with intentionally missing fields (blank PDF)
JOB_ID_2=$(curl -s -X POST http://localhost:8080/api/submit \
  -F "file=@/tmp/blank.pdf" | jq -r '.jobId')
sleep 15
curl -s http://localhost:8080/api/status/$JOB_ID_2 | jq '.'
# Expected: status COMPLETED (repair stub passes through)
# Log should contain: "repair.stub jobId=... — skipped (Sprint 7)"

# ── 6. Verify PostgreSQL job state ───────────────────────────────────────────
psql -U rfp -d rfpdb -c "SELECT status FROM analysis_jobs WHERE id = '$JOB_ID';"
# Expected: "COMPLETED"
psql -U rfp -d rfpdb -c "SELECT doc_completeness_score FROM analysis_results WHERE analysis_job_id = '$JOB_ID';"
# Expected: completeness score as a float string

# ── 7. Frontend smoke test ──────────────────────────────────────────────────
cd rfp-frontend
npm run dev &
sleep 3
# Open browser to http://localhost:5173
# Navigate to result page for $JOB_ID
# Verify: EntityTable renders with 7 tabs
# Verify: "General" tab shows client_name, submission_deadline
# Verify: Confidence badges appear (green/yellow/red)

# ── 8. Verify Resilience4j metrics ─────────────────────────────────────────
curl -s http://localhost:8080/actuator/metrics/rfp.extraction.completed | jq '.measurements[0].value'
# Expected: >= 1 (at least one completed extraction)

curl -s http://localhost:8080/actuator/metrics/rfp.entity.extraction.duration | jq '.'
# Expected: timer metrics present for all 7 extractor tags
```

---

## 6) Exit Criteria

- [ ] `mvn clean test -pl rfp-core,rfp-service` passes with no failures.
- [ ] `ExtractionGraph.build()` compiles without exception at application startup (verified in INFO log).
- [ ] All 10 graph nodes are registered and the linear edge chain START→...→FINALIZE→END is defined.
- [ ] Conditional edge from `SCORE_CONFIDENCE` routes to `REPAIR_LOOP` when any field confidence < 0.6, routes to
  `RUN_RULE_PACK` otherwise.
- [ ] `ExtractionOrchestrationService.runExtraction()` is called asynchronously from `RfpSubmissionService.submit()`.
- [ ] Job status transitions: PENDING → IN_PROGRESS → COMPLETED (happy path) or FAILED (error path).
- [ ] All 7 entity extractors extend `BaseEntityExtractor`, constructor injection only, under 250 lines each.
- [ ] All 7 prompt files are present in `rfp-service/src/main/resources/prompts/` with valid frontmatter.
- [ ] `ScoreConfidenceNode` produces `confidenceMap` with entry for every entity field and `doc_completeness_score`.
- [ ] `lowConfidenceQueue` contains field IDs for all fields with score < 0.6.
- [ ] `RepairLoopNode` and `RunRulePackNode` are stubs that pass through without modifying state.
- [ ] `ExtractTablesNode` is a stub returning empty `tables` list.
- [ ] `FinalizeNode` assembles `RfpDocument` and saves it via `DocumentStoragePort`.
- [ ] `EntityTable` React component renders 7 tabs with correct field groupings.
- [ ] Confidence badges render green (HIGH ≥ 0.8), yellow (MED ≥ 0.5), red (LOW < 0.5).
- [ ] `ResultPage` shows left panel (SectionTree) and right panel (EntityTable) side by side.
- [ ] `GET /api/v1/rfp/result/{jobId}` returns `{entities, confidenceMap, sections}`.
- [ ] No service class exceeds 250 lines. No method exceeds 20 lines. No constructor has more than 3 parameters
  injected (use method injection or config classes where needed).
- [ ] Lombok annotations (`@Value`, `@Builder`, `@Slf4j`, `@RequiredArgsConstructor`) used on all domain and DTO
  classes.
- [ ] `mvn clean package -pl rfp-core,rfp-service -DskipTests` produces a runnable JAR.

---

## 7) Notes & Assumptions

**LangGraph4J API — POC REQUIRED BEFORE SPRINT 4 CODING STARTS:**

> **CRITICAL:** Before implementing the 10-node `StateGraph`, run a 1-day proof-of-concept:
> 1. Add `org.bsc.langgraph4j:langgraph4j-core:1.8.4` to a throwaway Maven project.
> 2. Wire a single node (`addNode("test", state -> Map.of("status", "ok"))`).
> 3. Confirm: `StateGraph<ExtractionState>`, `addNode(name, NodeAction)`, `addEdge()`, and `compile()` all work as
     > documented without runtime errors.
> 4. If the API differs (e.g., uses `AgentStateFactory`, `HashMap`-based state, or different class names), document the
     > actual API and update all node stories accordingly BEFORE writing any node code.
> 5. **Fallback plan:** If LangGraph4J 1.8.4 does not compile or the API is incompatible, replace the graph with a
     > `ThreadPoolTaskExecutor` + explicit step enum state machine. Document this as the adopted approach in this Notes
     > section. The individual node classes remain unchanged — only `ExtractionGraph.build()` changes.
>
> POC verdict must be recorded here before Sprint 4 day-1 standup.

- Using `org.bsc.langgraph4j:langgraph4j-core:1.8.4`. The `StateGraph` API uses `addNode(name, NodeAction)` where
  `NodeAction.apply(state)` returns `Map<String,Object>` of state field deltas. LangGraph4J merges deltas into the next
  state using field-name matching. If the LangGraph4J 0.6.x API differs (e.g., uses `AgentStateFactory`), adapt the node
  return type accordingly.
- `ExtractionState` uses `@Value @Builder(toBuilder=true)` — LangGraph4J must be configured with a `StateFactory` that
  deserializes JSON into `ExtractionState`. If LangGraph4J requires a `HashMap`-based state, wrap `ExtractionState` in a
  thin `AgentState` subclass and convert at node boundaries.

**Scope management — if Sprint 4 runs behind:**

Sprint 4 contains a minimum of 4 weeks of engineering work (10 graph nodes + 7 extractors + 6 prompt files + confidence
scoring + React EntityTable). If the sprint falls behind after the first week, use **Option B** (recommended):

- **Option B (keep 2 weeks):** Implement the 3 core extractors (`GeneralEntityExtractor`, `SubmissionEntityExtractor`,
  `FinancialEntityExtractor`) plus the graph skeleton. The remaining 4 extractors (`IctEntityExtractor`,
  `StaffingEntityExtractor`, `SupportEntityExtractor`, `EvaluationEntityExtractor`) become **stubs** that return empty
  `RfpEntities` with confidence 0.0. Implement the stubs fully in Sprint 5 (after table extraction is unblocked).
- **Option A (if more time available):** Split into Sprint 4a (graph wiring + 3 core extractors) and Sprint 4b
  (remaining 4 extractors + confidence scoring). Requires re-numbering Sprints 5–12.

**Immutability strategy:**

- All node `apply()` methods return `Map<String,Object>` deltas. The framework creates a new `ExtractionState` per node
  transition. Never mutate `state` in-place inside a node.

**Token estimation:**

- `estimateTokens(String text)` uses `ceil(length / 4.0)` — a conservative approximation. Gemini's actual tokenizer may
  differ. Sprint 7 can replace with a proper tokenizer if chunking proves imprecise.

**Prompt file loading:**

- `ClassPathResource("prompts/entity-general-v1.md").getInputStream()` reads from `src/main/resources/prompts/`. Prompt
  files are UTF-8. Template substitution uses simple `String.replace()` on `{{contextHeader}}` and `{{chunkText}}` — no
  template engine needed for this sprint.

**`@Async` executor:**

- Declared as a `@Bean` named `extractionExecutor` in `AsyncConfig.java`. Spring Boot's default task executor is
  separate and not used for extraction. `@EnableAsync` on `AsyncConfig` itself (not main class) to limit scan scope.

**LangChain4J dependency:**

- `dev.langchain4j:langchain4j` (version managed by root POM property `${langchain4j.version}` = `1.11.0`) in
  `rfp-service/pom.xml`. Do NOT pin an explicit version here — it conflicts with the root BOM. Only
  `RecursiveCharacterTextSplitter` is used this sprint. `DocumentLoader` not used yet (Sprint 6 adds scanned page
  handling).

**`RfpEntities` domain model:**

- Assumed to have getter methods for every field extracted by the 7 sub-extractors. If any field is missing a getter (
  e.g., added in a later sprint), `ScoreConfidenceNode` should use a reflective accessor or a `Map`-based adapter to
  avoid a compilation error.

**Max params rule:**

- `BaseEntityExtractor` constructor has exactly 3 params: `LlmAdapter`, `DocumentChunkingService`, `ObjectMapper`. Each
  sub-extractor calls `super(...)` — Spring injects all 3 via the sub-extractor's constructor. No `@Autowired` field
  injection.

**Deferred:**

- Multi-language prompt variants (Bangla RFP support) — Sprint 10.
- Prompt version management (A/B testing prompt v1 vs v2) — Sprint 7.
- `manualReviewRequired` field population — Sprint 7 (repair loop decides which fields need human review after repair
  exhaustion).
- Table data feeding into entity extraction — Sprint 5's table data will be available to entity extractors in Sprint 7
  via an updated `ExtractionState`.
