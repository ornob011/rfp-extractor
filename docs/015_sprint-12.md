# Sprint 12 — Operational Hardening

## 0) Sprint Intent

- Instrument the system with 7 custom Micrometer metrics (queue depth gauge, job counters, processing timer, confidence
  distribution, LLM token counter, rule finding counter, OCR page counter) exposed via Prometheus-compatible
  `/actuator/prometheus`.
- Add three custom Spring Actuator health indicators (database, OCR sidecar, LLM provider) so that `GET /actuator/health`
  reflects the true system state.
- Implement job queue backpressure: reject new submissions with HTTP 503 + `Retry-After` header when the async executor
  queue is full, and add a `GlobalExceptionHandler` covering all expected error conditions.
- Pin LLM model versions in every job result and on startup logs; add Docker Compose resource limits and a complete
  `.env.example`; produce `docs/sla.md` and `docs/security-pitch.md`.
- Extend `AdminPage.tsx` with a metrics dashboard showing queue depth, job counters, system health indicators, and
  recent processing durations — polled every 30 seconds.

**Non-goals:**

- Grafana dashboard provisioning (Prometheus scrape is sufficient for this sprint).
- Full alerting rules (out of scope).
- Full Bangla support (Sprint 13).
- Database-backed user management (Sprint 11+ enhancement, not required).

---

## 1) Entry Criteria

- Sprint 11 is merged and green on CI (`mvn clean verify` passes).
- All 10 LangGraph4J nodes operational end-to-end.
  <!-- FIX [I]: Sprint 4 defines 10 nodes: ValidateNode, ClassifyPagesNode, ExtractTextNode,
       SegmentSectionsNode, ExtractTablesNode, ExtractEntitiesNode, ScoreConfidenceNode,
       RepairLoopNode, RunRulePackNode, FinalizeNode. The previous count of 9 was wrong. -->
- `FinalizeNode` updates `ExtractionJob.status` to `COMPLETED` and calls `ArtifactApplicationService.generateAll()`.
- `LlmAdapter` is the single gateway for all LLM calls with Resilience4j wrapping.
- `OcrSidecarClient` calls the Python FastAPI sidecar.
- `ThreadPoolTaskExecutor` (`AsyncConfig`) queues async extraction jobs.
- `spring-boot-starter-actuator` and `micrometer-registry-prometheus` on classpath (Sprint 1 POM).
- `management.endpoints.web.exposure.include=health,prometheus,metrics` in `application.properties` (may already be
  partial — verify).
- Docker Compose functional with all services.
- JUnit 5 + Mockito + AssertJ on classpath.

---

## 2) Deliverables

| #    | Deliverable                        | Type                  | Location                                           |
|------|------------------------------------|-----------------------|----------------------------------------------------|
| D-01 | `RfpMetrics`                       | Spring component      | `adapter/RfpMetrics.java`                          |
| D-02 | `DatabaseHealthIndicator`             | Health indicator      | `adapter/health/DatabaseHealthIndicator.java`         |
| D-03 | `OcrSidecarHealthIndicator`        | Health indicator      | `adapter/health/OcrSidecarHealthIndicator.java`    |
| D-04 | `LlmProviderHealthIndicator`       | Health indicator      | `adapter/health/LlmProviderHealthIndicator.java`   |
| D-05 | `JobQueueGuard`                    | Application component | `application/service/JobQueueGuard.java`           |
| D-06 | `QueueFullException`               | Runtime exception     | `application/service/QueueFullException.java`      |
| D-07 | `GlobalExceptionHandler` (updated) | REST advice           | `adapter/api/GlobalExceptionHandler.java`          |
| D-08 | `LlmAdapter` (updated)             | Existing component    | `adapter/llm/LlmAdapter.java`                      |
| D-09 | `FinalizeNode` (updated)           | Existing node         | `agent/node/FinalizeNode.java`                     |
| D-10 | `RfpSubmissionService` (updated)   | Existing service      | `application/service/RfpSubmissionService.java`    |
| D-11 | `RulePackRunner` (updated)         | Existing component    | `adapter/rulepack/RulePackRunner.java`             |
| D-12 | `ExtractTextNode` (updated)        | Existing node         | `agent/node/ExtractTextNode.java`                  |
| D-13 | `docs/sla.md`                      | Markdown document     | `docs/sla.md`                                      |
| D-14 | `docs/security-pitch.md`           | Markdown document     | `docs/security-pitch.md`                           |
| D-15 | `docs/deployment.md` (updated)     | Markdown document     | `docs/deployment.md`                               |
| D-16 | `docker-compose.yml` (updated)     | Docker config         | `docker-compose.yml`                               |
| D-17 | `.env.example`                     | Environment template  | `.env.example`                                     |
| D-18 | `AdminPage.tsx` (updated)          | React page            | `rfp-frontend/src/pages/AdminPage.tsx`             |
| D-19 | `MetricsDashboard.tsx`             | React component       | `rfp-frontend/src/components/MetricsDashboard.tsx` |
| D-20 | `SystemHealth.tsx`                 | React component       | `rfp-frontend/src/components/SystemHealth.tsx`     |

---

## 3) Work Breakdown

### Epic 12.1 — Custom Micrometer Metrics

#### Story 12.1.1 — RfpMetrics Component

**Acceptance Criteria (Gherkin):**

```gherkin
Given a job is submitted and queued
When GET /actuator/prometheus is called
Then rfp_jobs_queued gauge reflects the current queue depth

Given a job completes with status COMPLETED
When FinalizeNode calls RfpMetrics.recordJobCompleted()
Then rfp_jobs_completed_total counter is incremented with tag status=COMPLETED

Given an LLM extraction call succeeds
When LlmAdapter records token usage
Then rfp_llm_tokens_used_total is incremented with correct provider and model tags
```

**Interfaces / Contracts:**

```java

@Component
@Slf4j
@RequiredArgsConstructor
public class RfpMetrics {

    // Injected: MeterRegistry meterRegistry, ThreadPoolTaskExecutor taskExecutor

    /** Call once at bean initialisation — registers gauge bound to live queue size. */
    @PostConstruct
    public void registerGauge();

    /** Called by FinalizeNode after job status update. */
    public void recordJobCompleted(UUID jobId, JobStatus status, RfpType rfpType);

    /** Called by FinalizeNode with total processing duration. */
    public void recordProcessingDuration(UUID jobId, Duration duration, RfpType rfpType);

    /** Called by FinalizeNode with doc_completeness_score. */
    public void recordConfidenceScore(double score);

    /** Called by LlmAdapter after each LLM response. */
    public void recordLlmTokens(String provider, String model, String purpose, int tokenCount);

    /** Called by RulePackRunner for each RuleFinding. */
    public void recordRuleFinding(String ruleId, RuleSeverity severity, RuleStatus status);

    /** Called by ExtractTextNode for each page processed. */
    public void recordOcrPage(PageClassification classification);
}
```

**Implementation Plan:**

1. `registerGauge()`:
   `Gauge.builder("rfp.jobs.queued", taskExecutor, e -> e.getQueue().size()).register(meterRegistry)`.
2. `recordJobCompleted()`:
   `Counter.builder("rfp.jobs.completed").tag("status", status.name()).tag("rfpType", rfpType.name()).register(meterRegistry).increment()`.
3. `recordProcessingDuration()`:
   `Timer.builder("rfp.processing.duration").tag("rfpType", rfpType.name()).register(meterRegistry).record(duration)`.
4. `recordConfidenceScore()`:
   `DistributionSummary.builder("rfp.confidence.score").register(meterRegistry).record(score)`.
5. `recordLlmTokens()`:
   `Counter.builder("rfp.llm.tokens.used").tag("provider", provider).tag("model", model).tag("purpose", purpose).register(meterRegistry).increment(tokenCount)`.
6. `recordRuleFinding()`:
   `Counter.builder("rfp.rule_pack.findings").tag("severity", severity.name()).tag("ruleId", ruleId).tag("status", status.name()).register(meterRegistry).increment()`.
7. `recordOcrPage()`:
   `Counter.builder("rfp.ocr.pages.processed").tag("classification", classification.name()).register(meterRegistry).increment()`.
8. Each `Counter`/`Timer`/`DistributionSummary` is retrieved or created via `builder(...).register(meterRegistry)` —
   Micrometer returns the same meter on repeated registration.

**Test Plan:**

- `shouldIncrementJobCompletedCounterWhenRecordJobCompletedCalled()` — use `SimpleMeterRegistry` in test.
- `shouldRecordTimerWhenProcessingDurationRecorded()`.
- `shouldIncrementLlmTokenCounterWithCorrectTags()`.
- `shouldRegisterGaugeAtStartup()` — verify gauge registered in registry.

**Observability:** The metrics themselves ARE the observability output. No additional logs needed from `RfpMetrics`.
**Story Points:** 8

---

#### Story 12.1.2 — Wire Metrics into Existing Components

**Acceptance Criteria (Gherkin):**

```gherkin
Given a completed extraction run
When GET /actuator/prometheus is called
Then rfp_processing_duration_seconds_count is at least 1
And rfp_confidence_score_count is at least 1
```

**Implementation Plan:**

1. `FinalizeNode.apply()`: at end, call `rfpMetrics.recordJobCompleted(...)`,
   `rfpMetrics.recordProcessingDuration(...)` (duration = `Instant.now() - state.getSubmittedAt()`),
   `rfpMetrics.recordConfidenceScore(state.getDocCompletenessScore())`.
2. `LlmAdapter.extractStructured()` and `judgeSnippet()`: after each successful LLM call, extract token counts from
   Spring AI response metadata (if available) and call `rfpMetrics.recordLlmTokens(provider, model, purpose, count)`. If
   metadata unavailable: estimate from prompt length ÷ 4 and log a WARNING.
3. `RulePackRunner.run()`: for each `RuleFinding`, call
   `rfpMetrics.recordRuleFinding(finding.getRuleId(), finding.getSeverity(), finding.getStatus())`.
4. `ExtractTextNode.apply()`: for each page processed, call `rfpMetrics.recordOcrPage(pageClassification)`.
5. Each of these components gets `RfpMetrics` injected via constructor.

**Test Plan:**

- `shouldCallRecordJobCompletedInFinalizeNode()` — mock `RfpMetrics`, verify called.
- `shouldCallRecordRuleFindingForEachFinding()` — mock `RfpMetrics` in `RulePackRunner` test.

**Story Points:** 3

---

### Epic 12.2 — Custom Health Indicators

#### Story 12.2.1 — Database Health Indicator

**Acceptance Criteria (Gherkin):**

```gherkin
Given PostgreSQL is running and accessible
When GET /actuator/health is called
Then the response contains "database": {"status": "UP"}

Given database is down (connection refused)
When GET /actuator/health is called
Then "database": {"status": "DOWN"} is in the response
```

**Interfaces / Contracts:**

```java

@Component("database")
@RequiredArgsConstructor
public class DatabaseHealthIndicator implements HealthIndicator {

    @Override
    public Health health();
}
```

**Implementation Plan:**

1. Inject `DataSource` or `JdbcTemplate`.
2. `health()`: execute lightweight query `SELECT 1` and measure latency.
3. On any `DataAccessException` (or connection exception): return `Health.down(ex).build()`.
4. Wrap call with a 2-second query timeout.

**Test Plan:**

- `shouldReturnUpWhenDatabaseResponds()` — mock `JdbcTemplate`, return normally.
- `shouldReturnDownWhenDatabaseThrows()` — mock `JdbcTemplate` throwing `DataAccessException`.

**Story Points:** 2

---

#### Story 12.2.2 — OCR Sidecar Health Indicator

**Acceptance Criteria (Gherkin):**

```gherkin
Given the Python OCR sidecar is running and GET /health returns 200
When GET /actuator/health is called
Then "ocrSidecar": {"status": "UP"} is in the response
```

**Interfaces / Contracts:**

```java

@Component("ocrSidecar")
@RequiredArgsConstructor
public class OcrSidecarHealthIndicator implements HealthIndicator {

    @Override
    public Health health();
}
```

**Implementation Plan:**

1. Inject `RestClient` and `@Value("${app.ocr.sidecar.url}") String sidecarUrl`.
2. `health()`: `GET {sidecarUrl}/health` with 5-second connect timeout. On 200:
   `Health.up().withDetail("url", sidecarUrl).build()`.
3. On non-200 or connection error: `Health.down().withDetail("error", message).build()`.

**Test Plan:**

- `shouldReturnUpWhenSidecarRespondsWithOk()` — mock `RestClient`.
- `shouldReturnDownWhenSidecarNotReachable()`.

**Story Points:** 2

---

#### Story 12.2.3 — LLM Provider Health Indicator

**Acceptance Criteria (Gherkin):**

```gherkin
Given OpenRouter is accessible and the API key is valid
When GET /actuator/health is called
Then "llmProvider": {"status": "UP", "provider": "openrouter"} is present

Given the LLM provider returns an error
Then "llmProvider": {"status": "DOWN"} is present
```

**Interfaces / Contracts:**

```java

@Component("llmProvider")
@RequiredArgsConstructor
public class LlmProviderHealthIndicator implements HealthIndicator {

    @Override
    public Health health();
}
```

**Implementation Plan:**

1. Inject `LlmAdapter` and `@Value("${app.llm.provider}") String provider`.
2. `health()`: call `LlmAdapter.extractStructured("Reply with: OK", "{\"type\":\"string\"}")` with a very short prompt.
   Timeout: 5 seconds (shorter than normal 30s).
3. On success: `Health.up().withDetail("provider", provider).withDetail("model", extractionModel).build()`.
4. On `LlmUnavailableException` or timeout: `Health.down().withDetail("error", ex.getMessage()).build()`.
5. Note: this makes a real LLM call — cache result for 60 seconds to avoid hitting rate limits on every health check
   poll.

**Test Plan:**

- `shouldReturnUpWhenLlmAdapterResponds()` — mock `LlmAdapter`.
- `shouldReturnDownWhenLlmAdapterThrows()`.
- `shouldUseCachedResultWithin60Seconds()` — mock LlmAdapter called only once for two health checks within 60s.

**Story Points:** 3

---

### Epic 12.3 — Job Queue Backpressure

#### Story 12.3.1 — Queue Guard & Global Exception Handler

**Acceptance Criteria (Gherkin):**

```gherkin
Given the job queue has 20 jobs already queued
When a new submission request arrives
Then HTTP 503 is returned with body {"error":"SERVICE_UNAVAILABLE","retryAfterSeconds":30}
And Retry-After: 30 header is set

Given an AccessDeniedException is thrown in a controller
When GlobalExceptionHandler processes it
Then HTTP 403 with {"error":"FORBIDDEN","message":"Access denied"} is returned
```

**Interfaces / Contracts:**

```java
public class QueueFullException extends RuntimeException {
    public QueueFullException(int currentSize, int maxSize);
}

@Component
@RequiredArgsConstructor
public class JobQueueGuard {
    // @Value("${app.job.queue-max:20}") int maxQueueSize
    // Inject ThreadPoolTaskExecutor

    public void checkQueueCapacity();
}

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(QueueFullException.class)
    public ResponseEntity<ErrorResponse> handleQueueFull(QueueFullException ex);

    @ExceptionHandler(DocumentValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationError(DocumentValidationException ex);

    @ExceptionHandler(LlmUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleLlmUnavailable(LlmUnavailableException ex);

    @ExceptionHandler(OcrUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleOcrUnavailable(OcrUnavailableException ex);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex);

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex);
}

public record ErrorResponse(String error, String message) {
}
```

**Implementation Plan:**

1. `JobQueueGuard.checkQueueCapacity()`: if `executor.getQueue().size() >= maxQueueSize` → throw
   `QueueFullException(current, max)`.
2. `RfpSubmissionService.submitDocument()`: call `jobQueueGuard.checkQueueCapacity()` before creating the job.
3. `GlobalExceptionHandler.handleQueueFull()`: return
   `ResponseEntity.status(503).header("Retry-After","30").body(new ErrorResponse("SERVICE_UNAVAILABLE","Queue is full. Retry in 30 seconds."))`.
4. All other handlers: map to appropriate HTTP status and `ErrorResponse` body. Log at ERROR for 5xx, WARN for 4xx.
5. `handleGeneric()`: log with full stack trace at ERROR, return 500
   `{"error":"INTERNAL_SERVER_ERROR","message":"An unexpected error occurred"}`. Never expose exception message to
   client for generic exceptions.

**Test Plan:**

- `shouldReturn503WithRetryAfterWhenQueueFull()` — mock `ThreadPoolTaskExecutor.getQueue().size()` = 20.
- `shouldReturn400WhenDocumentValidationFails()` — mock `DocumentValidationException`.
- `shouldReturn503WhenLlmUnavailable()` — mock `LlmUnavailableException`.
- `shouldReturn403WhenAccessDenied()`.
- `shouldReturn500WithGenericMessageForUnhandledException()`.

**Observability:** ERROR log with stack trace for 5xx; WARN log for 4xx.
**Story Points:** 5

---

### Epic 12.4 — Model Version Management

#### Story 12.4.1 — Model ID in Job Results & Startup Logging

**Acceptance Criteria (Gherkin):**

```gherkin
Given a job that completed using google/gemini-2.0-flash-001
When GET /api/v1/rfp/result/{jobId} is called
Then doc_meta.extraction_model = "google/gemini-2.0-flash-001"

On application startup
When the LLM provider is OpenRouter
Then the startup log contains "LLM provider: openrouter, extraction model: google/gemini-2.0-flash-001, judgment model: google/gemini-2.5-pro-preview-06-05"
```

**Implementation Plan:**

1. `LlmProviderConfig.java`: add `@PostConstruct` method that logs:
   `"LLM provider: {}, extraction model: {}, judgment model: {}"`.
2. `ExtractionGraph.java` initial state builder: set `state.extractionModel = extractionModelId` (injected from
   `@Value("${app.llm.openrouter.model}")`).
3. `FinalizeNode.apply()`: copy `state.extractionModel` into `rfpDocument.getDocMeta().setExtractionModel(...)`.
4. For Ollama path (`app.llm.provider=ollama`): if `app.llm.ollama.model.digest` is set, call Ollama REST API
   `GET {base-url}/api/show` with model name in body, extract `digest` field. If mismatch: log WARNING
   `"Ollama model digest mismatch: expected={}, actual={}"`. On Ollama API failure: log WARNING (do not abort startup).

**Test Plan:**

- `shouldLogProviderAndModelAtStartup()` — capture log output (use `@Slf4j` test appender or check `@PostConstruct` is
  called).
- `shouldSetExtractionModelInDocMeta()` — verify `RfpDocument.docMeta.extractionModel` is set in `FinalizeNode` test.

**Observability:** INFO log on startup with provider/model info.
**Story Points:** 3

---

### Epic 12.5 — Docker Compose Production Config & Docs

#### Story 12.5.1 — Docker Compose Resource Limits

**Implementation Plan:**
Update `docker-compose.yml` with `deploy.resources.limits` for each service:

```yaml
services:
    rfp-service   :
        deploy     :
            resources:
                limits:
                    cpus  : '2.0'
                    memory: 2g
        environment:
            - OPENROUTER_API_KEY=${OPENROUTER_API_KEY}
            - STORAGE_ENCRYPTION_KEY=${STORAGE_ENCRYPTION_KEY}
        env_file   : .env
        healthcheck:
            test    : [ "CMD", "curl", "-f", "http://localhost:8080/api/v1/health" ]
            interval: 30s
            timeout : 10s
            retries : 3
        restart    : unless-stopped

    rfp-python-ocr:
        deploy :
            resources:
                limits:
                    cpus  : '2.0'
                    memory: 4g
        restart: unless-stopped

    redis         :
        deploy :
            resources:
                limits:
                    cpus  : '0.5'
                    memory: 512m
        restart: unless-stopped

    postgres      :
        deploy :
            resources:
                limits:
                    cpus  : '1.0'
                    memory: 1g
        restart: unless-stopped

    rfp-frontend  :
        deploy :
            resources:
                limits:
                    cpus  : '0.25'
                    memory: 256m
        restart: unless-stopped
```

Also add `.env.example` to project root:

```
OPENROUTER_API_KEY=your-openrouter-api-key-here
STORAGE_ENCRYPTION_KEY=64-hex-chars-32-byte-key-here
POSTGRES_PASSWORD=changeme-in-production
REDIS_PASSWORD=changeme-in-production
JWT_PRIVATE_KEY_PATH=/secrets/jwt-private.pem
JWT_PUBLIC_KEY_PATH=/secrets/jwt-public.pem
```

**Story Points:** 3

---

#### Story 12.5.2 — SLA & Security Pitch Documentation

**`docs/sla.md` content:**

- Processing time estimates (based on Sprint 6 OCR benchmarks):
    - 50-page digital PDF: ~1–2 min
    - 200-page digital PDF: ~4–8 min
    - 200-page fully scanned PDF: ~12–25 min
    - 200-page mixed PDF (50% scanned): ~8–16 min
- LLM API constraints: OpenRouter rate limit 60 calls/min (configurable). Estimate ~3–5 LLM calls per 10-page section.
- Concurrent job capacity: queue depth 20 (configurable `app.job.queue-max`). Async pool: 2 core, 4 max threads.
- Job state retention is managed by PostgreSQL data-retention policy (`app.retention.days`).
- Data retention: 90 days (`app.retention.days`), hard-delete 7 days after soft-delete.
- Artifact storage: no auto-cleanup — grow monotonically until data retention kicks in.

**`docs/security-pitch.md` content (director-level):**

Section 1 — **Regulatory Compliance First, Not a Feature:**

- GOB contract PDFs contain sensitive tendering information: budgets, evaluation weights, bidder shortlists.
- Cloud SaaS tools (AWS Textract, Azure Document Intelligence, Google Document AI) send documents to foreign servers — a
  direct violation of GOB data sovereignty expectations under ICT Act 2006.
- This system is fully self-hosted. Documents never leave DSI's network. Ollama path requires zero internet
  connectivity.

Section 2 — **GOB-Specific Intelligence: 130+ Rules No SaaS Knows:**

- Runs 64 ICT + 33 Works + 33 Consultancy + 22 Goods procurement rules derived from PPR 2008 / CPTU standards.
- Rules run on structured JSON extracted from the document — not on raw text — making them fast, reliable, and
  auditable.
- No SaaS tool ships a bd-govt-rfp-v1 rule pack. This is DSI's intellectual property.

Section 3 — **Practical Value: 4-6 Hours Saved Per Bid:**

- Manual RFP review by a senior consultant: ~4-6 hours per document.
- System review: ~15 minutes (digital PDF).
- Output: Clarification Questions DOCX, Ambiguity Register, Compliance Checklist, Risk Log — ready to submit to client.
- The system flags what's missing, what's vague, and what contradicts itself — so consultants start from a position of
  informed analysis.

Section 4 — **Pitch to Director:**

- Lead message: "We eliminate 4-6 hours of manual RFP review per bid, cut missed compliance issues, and keep GOB
  contract data on our servers."
- Do NOT lead with "agentic AI" — that's a buzzword.
- DO say: "purpose-built for GOB procurement, self-hosted, and produces standardised bid analysis outputs."
- Bangla support (Sprint 13) is the next competitive moat — no other tool reads GOB Bangla procurement documents.

**`docs/deployment.md` additions:**

- Prerequisites checklist.
- Step-by-step: clone repo, copy `.env.example` to `.env`, fill values, generate JWT keys, `docker-compose up -d`.
- Verify: `curl http://localhost:8080/api/v1/health`.
- All `app.*` configuration properties with defaults and descriptions.
- Backup procedure: backup `data/` directory (contains encrypted PDFs and artifacts).
- Key rotation: replace `STORAGE_ENCRYPTION_KEY` (re-encrypt all stored files — procedure documented).

**Story Points:** 5

---

### Epic 12.6 — AdminPage Metrics Dashboard

#### Story 12.6.1 — MetricsDashboard React Component

**Acceptance Criteria (Gherkin):**

```gherkin
Given metrics are available at GET /actuator/metrics/rfp.jobs.queued
When MetricsDashboard renders
Then the current queue depth is displayed as a number

When the component mounts
Then data is fetched every 30 seconds automatically via React Query
```

**Interfaces / Contracts:**

```typescript
// hooks/useMetrics.ts
export function useMetrics() {
    // Uses React Query to poll /actuator/metrics/* every 30s
    // Returns { queueDepth, completedJobs, failedJobs, avgProcessingTime, isLoading, isError }
}

// components/MetricsDashboard.tsx
interface MetricsDashboardProps {
    className?: string;
}
```

**Implementation Plan:**

1. `useMetrics()` hook: calls `GET /actuator/metrics/rfp.jobs.queued`, `GET /actuator/metrics/rfp.jobs.completed`, etc.
   Uses `useQuery` with `refetchInterval: 30000`.
2. `MetricsDashboard.tsx`:
    - 4-card row: Queue Depth (gauge value), Completed Jobs (counter), Failed Jobs (counter), Avg Processing Time (timer
      mean in minutes).
    - Each card: label, value, colour indicator (green if healthy, orange if degraded).
    - Queue Depth card: orange if > 10, red if > 18.
3. `SystemHealth.tsx`:
    - Calls `GET /actuator/health` every 30s.
    - Renders a status badge per indicator: Database, OCR Sidecar, LLM Provider.
    - Badge: green dot = UP, red dot = DOWN, grey dot = UNKNOWN.
4. `AdminPage.tsx` updated tabs:
    - "Metrics" tab → `<MetricsDashboard />` + `<SystemHealth />`.
    - "Rule Packs" tab → existing rule pack list (from Sprint 9).
    - "System Health" tab (merged into Metrics tab).
5. Admin-only guard from Sprint 11 remains.

**Test Plan:** Manual browser testing.
**Story Points:** 5

---

## 4) PR Plan

| PR#      | Title                                                                             | Files Changed                                                                                                         | Merge Order | Dependencies |
|----------|-----------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|-------------|--------------|
| PR-12-01 | feat: RfpMetrics component (7 metrics)                                            | `RfpMetrics.java`                                                                                                     | 1st         | None         |
| PR-12-02 | feat: wire metrics into FinalizeNode, LlmAdapter, RulePackRunner, ExtractTextNode | `FinalizeNode.java`, `LlmAdapter.java`, `RulePackRunner.java`, `ExtractTextNode.java`                                 | 2nd         | PR-12-01     |
| PR-12-03 | feat: custom health indicators (Database, OCR, LLM)                                  | `DatabaseHealthIndicator.java`, `OcrSidecarHealthIndicator.java`, `LlmProviderHealthIndicator.java`                      | 2nd         | None         |
| PR-12-04 | feat: job queue backpressure + global exception handler                           | `JobQueueGuard.java`, `QueueFullException.java`, `GlobalExceptionHandler.java`, `RfpSubmissionService.java` (updated) | 3rd         | None         |
| PR-12-05 | feat: model version pinning + startup logging                                     | `LlmProviderConfig.java`, `ExtractionGraph.java`, `FinalizeNode.java`                                                 | 3rd         | PR-12-02     |
| PR-12-06 | chore: Docker Compose resource limits + .env.example                              | `docker-compose.yml`, `.env.example`                                                                                  | 4th         | None         |
| PR-12-07 | docs: sla.md, security-pitch.md, deployment.md                                    | `docs/sla.md`, `docs/security-pitch.md`, `docs/deployment.md`                                                         | 4th         | None         |
| PR-12-08 | feat: AdminPage metrics dashboard frontend                                        | `MetricsDashboard.tsx`, `SystemHealth.tsx`, `AdminPage.tsx`, `hooks/useMetrics.ts`                                    | 5th         | PR-12-03     |
| PR-12-09 | chore: application.properties metrics config                                      | `application.properties`                                                                                              | 1st         | None         |

---

## 5) Validation & Demo Script

```bash
# 1. Build and verify all tests pass
cd rfp-extractor
mvn clean verify

# 2. Start all services with resource limits
docker-compose up -d
sleep 20

# 3. Verify health endpoint shows all indicators
curl -s http://localhost:8080/actuator/health | jq '{
  status: .status,
  database: .components.database.status,
  ocr: .components.ocrSidecar.status,
  llm: .components.llmProvider.status
}'
# Expected: all "UP"

# 4. Check Prometheus metrics endpoint
curl -s http://localhost:8080/actuator/prometheus | grep "rfp_"
# Expected: 7 custom metric families present:
# rfp_jobs_queued, rfp_jobs_completed_total, rfp_processing_duration_seconds,
# rfp_confidence_score, rfp_llm_tokens_used_total, rfp_rule_pack_findings_total,
# rfp_ocr_pages_processed_total

# 5. Submit a document to generate metric data
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"analyst1","password":"analyst123"}' | jq -r .token)

JOB_ID=$(curl -s -F "file=@testdata/sample-ict-rfp.pdf" \
  -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/rfp/submit | jq -r .jobId)

# Wait for completion
while [ "$(curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/rfp/status/$JOB_ID | jq -r .status)" != "COMPLETED" ]; do
  sleep 5; echo -n "."
done
echo ""

# 6. Verify metrics updated after job completion
curl -s http://localhost:8080/actuator/prometheus | \
  grep "rfp_jobs_completed_total"
# Expected: rfp_jobs_completed_total{...status="COMPLETED"...} >= 1

curl -s http://localhost:8080/actuator/prometheus | \
  grep "rfp_processing_duration_seconds_count"
# Expected: count >= 1

# 7. Test queue full 503
# Fill the queue by submitting 21 jobs rapidly
for i in {1..21}; do
  curl -s -F "file=@testdata/sample-ict-rfp.pdf" \
    -H "Authorization: Bearer $TOKEN" \
    http://localhost:8080/api/v1/rfp/submit > /dev/null &
done
wait

# The 21st submit should return 503
curl -v -F "file=@testdata/sample-ict-rfp.pdf" \
  -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/rfp/submit 2>&1 | grep -E "< HTTP|Retry-After"
# Expected: < HTTP/1.1 503, Retry-After: 30

# 8. Check model in result
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/rfp/result/$JOB_ID | \
  jq '.doc_meta.extraction_model'
# Expected: "google/gemini-2.0-flash-001"

# 9. Verify startup log
docker-compose logs rfp-service | grep "LLM provider:"
# Expected: "LLM provider: openrouter, extraction model: google/gemini-2.0-flash-001, judgment model: google/gemini-2.5-pro-preview-06-05"

# 10. Open AdminPage in browser
echo "Open http://localhost:3000/admin"
echo "Login as admin user. Go to Metrics tab."
echo "Verify: Queue Depth, Completed Jobs, System Health badges (DB/OCR/LLM all green)."
```

---

## 6) Exit Criteria

- [ ] `mvn clean verify` passes with zero failures.
- [ ] `GET /actuator/prometheus` returns all 7 custom metric families (grep `rfp_`).
- [ ] `GET /actuator/health` shows `database`, `ocrSidecar`, and `llmProvider` indicators with correct statuses.
- [ ] Submitting 21 jobs in rapid succession causes the 21st to return HTTP 503 with `Retry-After: 30` header.
- [ ] `GET /api/v1/rfp/result/{jobId}` includes `doc_meta.extraction_model = "google/gemini-2.0-flash-001"` (or
  configured model).
- [ ] Application startup log contains `"LLM provider: ..."` line with provider and both model IDs.
- [ ] `docker-compose.yml` has `deploy.resources.limits` for all 4 services.
- [ ] `.env.example` exists at project root with all required env vars documented.
- [ ] `docs/sla.md` exists with processing time estimates.
- [ ] `docs/security-pitch.md` exists with 4 sections (compliance, rule pack, practical value, director pitch).
- [ ] `AdminPage.tsx` "Metrics" tab shows live queue depth, job counters, and health badges.
- [ ] All custom metrics increment correctly after a real extraction run.
- [ ] No class exceeds 250 lines. No method exceeds 20 lines. Constructor injection throughout.
- [ ] `RfpMetricsTest` passes with `SimpleMeterRegistry` verifying counter increments.

---

## 7) Notes & Assumptions

- **Prometheus metric naming**: Micrometer converts camelCase metric names to snake_case with dots converted to
  underscores for Prometheus: `rfp.jobs.queued` → `rfp_jobs_queued`. This is automatic. No manual naming conversion
  needed.
- **LLM token counting**: OpenRouter responses include `usage.prompt_tokens` and `usage.completion_tokens` in the
  response metadata. Spring AI surfaces these via `ChatResponse.getMetadata().getUsage()`. If `getUsage()` returns
  null (Ollama path may not provide this), estimate from prompt character count ÷ 4 and log a DEBUG note.
- **LLM health indicator caching**: The `LlmProviderHealthIndicator` makes a real API call. Cache the result for 60
  seconds using `java.util.concurrent.atomic.AtomicReference<CachedHealth>` with a timestamp check. This avoids hitting
  rate limits when `/actuator/health` is polled frequently by load balancers.
- **Queue depth gauge**: Micrometer `Gauge` is backed by a live reference to `taskExecutor.getQueue()::size`. It always
  reflects the current value — no manual increment needed.
- **Docker Compose `deploy` key**: `deploy.resources.limits` is supported in Docker Compose v3.x with Docker Swarm mode.
  For plain `docker-compose up` (non-swarm), use the `mem_limit` and `cpus` top-level keys for v2.x compatibility, or
  upgrade to Compose Specification format which supports `deploy` in non-swarm mode with Docker Engine 23+.
- **`docs/security-pitch.md` audience**: Written for a DSI director who evaluates whether to continue funding the
  project. Avoid technical jargon. Lead with cost savings and compliance risk avoidance. The technical architecture is
  in `docs/deployment.md` for engineers.
- **`app.job.queue-max` default**: Set to 20 in `application.properties`. This matches the `ThreadPoolTaskExecutor`
  queue capacity set in `AsyncConfig`. They must match — document this coupling in `docs/configuration.md`.
