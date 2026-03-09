# Plan: Agentic Govt RFP Extraction + Quality Gate

**Source:** 001_research.md (59 issues resolved) <br>
**Methodology:** Agile — 2-week sprints, each delivering production-grade, runnable software <br>
**LLM Default:** OpenRouter (Gemini), configurable via `application.properties` to Ollama or any OpenAI-compatible
API <br>
**Bangla Scope:** Explicitly out of scope until Sprint 13+. Graceful rejection for legacy-encoded docs. <br>
**Rule Authorship:** Claude as procurement expert proxy on user's provided checklist + additional rules derived from PPR
2008 / CPTU standards <br>

---

## Context

DSI's AI pitch advantage: self-hosted + air-gapped for GOB data security, GOB-specific rule pack running on structured
JSON (not raw text), and adaptive extraction handling real-world PDF chaos. Every sprint delivers working vertical
slices — no code written in Sprint N is removed in Sprint N+1. Each sprint adds capability to the same production-grade
codebase.

---

## Locked Architecture Decisions

| Concern              | Decision                                                                                                                                                                                                                                                                                                            |
|----------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Backend              | Spring Boot 3.5.11, Java 21, Maven multi-module                                                                                                                                                                                                                                                                     |
| LLM abstraction      | Spring AI (primary LLM client) — supports OpenRouter, Ollama, OpenAI via config                                                                                                                                                                                                                                     |
| LLM — extraction     | OpenRouter → `google/gemini-2.0-flash-001` (fast, cheap per-chunk extraction)                                                                                                                                                                                                                                       |
| LLM — judgment       | OpenRouter → `google/gemini-2.5-pro-preview-06-05` (semantic rule checks, clarification generation)                                                                                                                                                                                                                 |
| Doc processing       | LangChain4J (text splitters, doc loaders, embeddings)                                                                                                                                                                                                                                                               |
| Agent orchestration  | LangGraph4J (state machine graph, conditional edges, persistence)                                                                                                                                                                                                                                                   |
| OCR                  | Python FastAPI sidecar (easyOCR + Tesseract) — Java calls it via REST                                                                                                                                                                                                                                               |
| Artifact generation  | Apache POI (XLSX + DOCX) + Freemarker templates                                                                                                                                                                                                                                                                     |
| Frontend             | React 18, Tailwind v4, Vite                                                                                                                                                                                                                                                                                         |
| Build                | Maven (parent POM + modules)                                                                                                                                                                                                                                                                                        |
| State store          | PostgreSQL (Spring Data JPA) for job state and extraction checkpoints                                                                                                                                                                                                                                               |
| Table grid format    | Cell-object array: `[{row, col, value, rowspan, colspan}]`                                                                                                                                                                                                                                                          |
| Clause ID scheme     | Deterministic composite: `{procurement_ref}:{section_num}:{clause_num}` — NEVER LLM-assigned                                                                                                                                                                                                                        |
| Context chunking     | Section-level, max 4000 tokens/call. Doc summary header prepended each chunk                                                                                                                                                                                                                                        |
| Repair loop bound    | Max 3 retries/item, 20 total iterations hard stop. Repair routing is DETERMINISTIC (not LLM)                                                                                                                                                                                                                        |
| Architecture pattern | Hexagonal (Ports & Adapters) — `domain/`, `application/`, `adapter/`, `config/`                                                                                                                                                                                                                                     |
| Service line limit   | No service class > 250 lines. Decompose into focused sub-services if needed                                                                                                                                                                                                                                         |
| Database             | PostgreSQL (via Spring Data JPA + Hibernate). For vector similarity search: pgvector extension (`spring-ai-pgvector-store`)                                                                                                                                                                                         |
| Lombok               | Use Lombok on all domain models and DTOs: `@Data`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j`, `@Value`, `@Getter`/`@Setter` as appropriate. Never write boilerplate getters, setters, constructors, `equals`, `hashCode`, or `toString` by hand.                                                             |
| Library-first rule   | **Always use an existing library/package for any capability that already exists.** Never write custom code for: text splitting, PDF parsing, JSON Schema validation, JMESPath evaluation, JWT handling, encryption, HTTP clients, metrics, scheduling, etc. Justify in a code comment if a library cannot be found. |

---

## Issue Resolution Matrix

Every issue from `001_research.md` §15 is resolved in a specific sprint. "Resolved" means the sprint's Definition of
Done includes verifying the fix.

| Issue ID | Description (brief)                 | Resolved In                                                 |
|----------|-------------------------------------|-------------------------------------------------------------|
| A-01     | Tika/PDFBox redundancy              | Sprint 2                                                    |
| A-02     | No input validation                 | Sprint 2                                                    |
| A-03     | Mixed pages unhandled               | Sprint 6                                                    |
| A-04     | image_detect() undefined            | Sprint 2                                                    |
| A-05     | Section segmenter heuristics fail   | Sprint 3                                                    |
| A-06     | Clause ID instability               | Sprint 3                                                    |
| T-01     | No column de-interleaving           | Sprint 6                                                    |
| T-02     | Multi-page table fragmentation      | Sprint 5                                                    |
| T-03     | Merged cells corrupt grid           | Sprint 5                                                    |
| T-04     | Scanned tables have no path         | Sprint 6                                                    |
| T-05     | Stream mode requires tuning         | Sprint 5                                                    |
| T-06     | Bangla OCR engine undefined         | Sprint 6                                                    |
| T-07     | Legacy Bangla encoding corruption   | Sprint 11                                                   |
| T-08     | OCR confidence not propagated       | Sprint 6                                                    |
| T-09     | Rule pack DSL undefined             | Sprint 8                                                    |
| D-01     | Clause-to-section link missing      | Sprint 3                                                    |
| D-02     | Clause page_range missing           | Sprint 3                                                    |
| D-03     | Cross-clause refs not modeled       | Sprint 3                                                    |
| D-04     | Tables not linked to sections       | Sprint 5                                                    |
| D-05     | Tags field undefined                | Sprint 4                                                    |
| D-06     | Entity provenance missing           | Sprint 4                                                    |
| D-07     | Confidence structure ambiguous      | Sprint 3                                                    |
| D-08     | Grid format undefined               | Sprint 5                                                    |
| R-01     | No rule DSL or schema               | Sprint 8                                                    |
| R-02     | Deterministic/LLM boundary          | Sprint 8                                                    |
| R-03     | No domain expert for rules          | Pre-Sprint (user provided checklist)                        |
| R-04     | Single pack can't cover all types   | Sprint 9                                                    |
| AG-01    | "Agent" decisions are deterministic | Sprint 4 (LangGraph4J deterministic repair)                 |
| AG-02    | Repair loop no termination          | Sprint 7                                                    |
| AG-03    | Context window management absent    | Sprint 4                                                    |
| AG-04    | LLM non-determinism in IDs/tags     | Sprint 3 (deterministic ClauseIdAssigner)                   |
| O-01     | Clarification question generation   | Sprint 10                                                   |
| O-02     | DOCX/XLSX stack undefined           | Sprint 1 (POI + Freemarker locked)                          |
| O-03     | PDF page refs path-dependent        | Sprint 10 (clause_id + page number as evidence)             |
| O-04     | Audit log is JSON not readable      | Sprint 10 (HTML audit report)                               |
| B-01     | Legacy Bangla silent corruption     | Sprint 11 (BanglaEncodingDetector rejects)                  |
| B-02     | No Bangla NER                       | Sprint 13+ (deferred; graceful rejection)                   |
| B-03     | No Bangla heading patterns          | Sprint 13+ (deferred)                                       |
| B-04     | LLM degraded on Bangla              | Sprint 13+ (deferred)                                       |
| S-01     | Spring AI no agentic loop           | Sprint 4 (LangGraph4J)                                      |
| S-02     | Long-running tools block sync       | Sprint 2 (async job infrastructure)                         |
| S-03     | No state persistence                | Sprint 2 (PostgreSQL-backed ExtractionState checkpoints)    |
| S-04     | Local LLM hardware undefined        | Deployment Tiers (see docs/003)                             |
| OP-01    | 10-40 min processing time           | Accepted; async background job; Sprint 2                    |
| OP-02    | No concurrency model                | Sprint 2 (ThreadPoolTaskExecutor, queue=20)                 |
| OP-03    | Model updates change behavior       | Deferred to wishlist (unit-test-only baseline; no Actuator) |
| OP-04    | No monitoring/observability         | Deferred to wishlist (unit-test-only baseline; no Actuator) |
| DIFF-01  | Off-the-shelf tools overlap         | Architectural (self-hosted + GOB rules + Bangla)            |
| DIFF-02  | Unique value buried                 | 003_implementation-index.md executive summary               |
| MVP-01   | MVP is actually a full v1.0         | Accepted; scope is correct for a serious pitch              |
| MVP-02   | Artifacts shouldn't be in MVP       | Accepted; deferred to Sprint 10                             |
| MVP-03   | Repair loop shouldn't be in MVP     | Sprint 7 (after extraction is proven in Sprints 3-6)        |
| SEC-01   | No RBAC/audit/encryption            | Sprint 11                                                   |
| SEC-02   | Prompt injection via doc content    | Sprint 11 (PromptInjectionFilter)                           |
| SEC-03   | Self-hosted not leading pitch       | 003_implementation-index.md executive summary               |
| TEST-01  | No ground truth dataset             | Wishlist (`wishlist/001_wishlist.md`; non-blocking)         |
| TEST-02  | No evaluation metrics               | Sprint 3 (fixture assertions + schema + deadline > 90%)     |
| TEST-03  | Rule pack has no test suite         | Sprint 8 (64 parameterized tests) + Sprint 9 (88 more)      |
| TEST-04  | Repair loop hard to test            | Sprint 7 (deliberately degraded test docs)                  |

**Bangla issues B-02, B-03, B-04 are intentionally deferred to Sprint 13+.** The system gracefully rejects
legacy-encoded Bangla documents with a structured error (Sprint 11 `BanglaEncodingDetector`). Unicode Bangla is accepted
but extraction quality is not guaranteed until Sprint 13+. This is an explicit, documented scope boundary — not an
omission.

---

## Enum Policy (Contract Discipline)

Use enums for closed vocabularies that appear in APIs, persistence models, or rule outputs. Keep `string` only for
free-text or open-ended extracted content.

- Enums required for bounded values: `JobStatus`, `PageClassification`, `RuleSeverity`, `RuleStatus`,
  `ArtifactFileType`, `RiskImpact`, `UserRole`, provider identifiers.
- Keep as `string`: clause text, descriptions, recommendations, notes, URLs, and other natural-language output.
- New contracts must not introduce `string` for a value set that is known and finite at design time.

---

## Engineering Standards (Non-Negotiable)

These standards apply to every file, every sprint, every developer. Code that violates them must be refactored before
the sprint is closed.

---

### 1. Clean Code Rules

| Rule                     | Requirement                                                                                                                                                                                                                                                                                   |
|--------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Service / class size** | No class > 250 lines. If it grows beyond that, split it into focused collaborators.                                                                                                                                                                                                           |
| **Method size**          | No method > 20 lines. Extract sub-steps into private methods with descriptive names.                                                                                                                                                                                                          |
| **Method parameters**    | No method with > 3 parameters. Group into a request/config object beyond that. For methods/constructors/calls with 2+ args, place each argument on a separate line.                                                                                                                           |
| **Naming**               | Names must state intent. `extractSubmissionDeadline()` not `process()`. No abbreviations except universally known ones (`id`, `url`, `pdf`).                                                                                                                                                  |
| **Comments**             | Comments explain *why*, not *what*. Self-documenting code is preferred. No commented-out dead code.                                                                                                                                                                                           |
| **Magic values**         | No inline magic strings or numbers. Use named constants or `application.properties` keys.                                                                                                                                                                                                     |
| **No God classes**       | A class does one thing. `RfpService.java` that does extraction, rules, artifacts, and auth is forbidden.                                                                                                                                                                                      |
| **No utility dumps**     | No `Utils.java` / `Helper.java` catch-alls. Group utilities into specific, named classes: `ClauseIdNormalizer`, `BanglaNumericParser`.                                                                                                                                                        |
| **Return early**         | Use guard clauses at the top of methods. Avoid deep nesting (`if → if → if`). Max nesting depth: 2.                                                                                                                                                                                           |
| **No null returns**      | Return `Optional<T>` for values that may be absent. Never return `null` from a public method.                                                                                                                                                                                                 |
| **Null checks**          | Use library null/blank checks. Java examples must use `Objects.isNull` / `Objects.nonNull` and `StringUtils.hasText`; do not use raw `== null`, `!= null`, or `.isBlank()` chains in business logic examples.                                                                                 |
| **Exceptions**           | Throw specific exceptions (`DocumentEncryptedException`, `OcrUnavailableException`). Do not use `catch (Exception e)` in sample code. Route errors through a global exception handler (for Spring: `@RestControllerAdvice` + typed `@ExceptionHandler` methods) that returns `ProblemDetail`. |
| **No SneakyThrows**      | `@SneakyThrows` is forbidden. Use explicit `throws` declarations and let typed exceptions propagate to the exception handler.                                                                                                                                                                 |
| **Logging**              | Full project logging must use structured, industry-grade pattern with stable keys: `event`, `component`, `status`, `jobId`, `durationMs`, `errorCode`, `traceId`, `spanId`. Avoid free-form log-only messages in examples.                                                                    |

---

### 1.1 Required Example Pattern (Full Project)

All examples in this project must follow the patterns below.

**Java structured logging example:**

```java
log.info(
    "event=extraction.complete component=ExtractionPipelineService status=SUCCESS jobId={} durationMs={} errorCode={} traceId={} spanId={}",
    jobId,
    durationMs,
    "NONE",
    MDC.get("traceId"),
    MDC.

get("spanId")
);
```

**Java null-check pattern example:**

```java
if(Objects.isNull(rawResponse) ||!StringUtils.

hasText(rawResponse)){
    return Optional.

empty();
}
    if(Objects.

nonNull(metadata) &&Objects.

nonNull(metadata.getProcurementRef())){
    // continue
    }
```

**Method argument formatting example:**

```java
return ResponseEntity.status(status)
    .

header("Retry-After","30")
    .

body(
    ProblemDetail.forStatusAndDetail(
    status,
    detail
    )
    );
```

---

### 2. Architecture Pattern — Hexagonal (Ports & Adapters)

```
┌─────────────────────────────────────────────────────────┐
│                      domain/                            │
│  Pure Java. No Spring annotations. No I/O.              │
│  model/   → RfpDocument, Section, Clause, Table,        │
│             ExtractionState, RuleFinding, …             │
│  port/    → ExtractionPort, RulePackPort,               │
│             JobStatePort, ArtifactPort (interfaces)     │
└──────────────────────┬──────────────────────────────────┘
                       │ depends on
┌──────────────────────▼──────────────────────────────────┐
│                   application/service/                  │
│  Orchestrates use cases. Calls ports. < 250 lines each. │
│  RfpSubmissionService, RfpJobService,                   │
│  RulePackApplicationService, ArtifactApplicationService │
└──────────────────────┬──────────────────────────────────┘
                       │ implements ports
┌──────────────────────▼──────────────────────────────────┐
│                      adapter/                           │
│  All framework/infra code lives here. Spring beans.     │
│  api/          → REST controllers + DTOs                │
│  persistence/  → JPA repositories, JPA repositories and persistence adapters       │
│  llm/          → LlmAdapter (Spring AI wrapper)         │
│  extraction/   → PdfDocumentLoader, PageClassifier, …   │
│  ocr/          → OcrSidecarClient                       │
│  table/        → TableExtractor, TableMerger            │
│  entity/       → EntityExtractor sub-extractors         │
│  rulepack/     → RulePackRunner, RulePackLoader         │
│  artifact/     → XLSX/DOCX/HTML generators              │
│  security/     → JWT filter, audit trail aspect         │
└─────────────────────────────────────────────────────────┘
```

**Rules:**

- `domain/` has zero dependencies on Spring, LangChain4J, PDFBox, or any framework.
- `application/service/` depends only on `domain/port/` interfaces — never on concrete adapters.
- `adapter/` classes implement `domain/port/` interfaces and are injected via Spring DI.
- DTOs (request/response objects) live in `adapter/api/` and are never passed into the domain layer.
- Mappers (`SectionMapper`, `ClauseMapper`) convert between domain models and DTOs at the adapter boundary.

---

### 3. Design Patterns — When to Use Which

| Pattern                      | Use Case in This Project                                      | Example                                                                                                                        |
|------------------------------|---------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------|
| **Strategy**                 | Multiple interchangeable algorithms for the same task         | `HeadingStrategy` implementations (bookmark, regex, font-size, caps). `TableExtractorStrategy` (lattice vs. stream).           |
| **Chain of Responsibility**  | Try strategies in priority order; first success wins          | `SectionSegmenter` applies heading strategies in sequence until one produces results                                           |
| **Factory / Factory Method** | Create objects without exposing instantiation logic           | `LlmProviderFactory` creates the correct `ChatClient` based on `app.llm.provider`                                              |
| **Builder**                  | Construct complex objects step by step                        | `RfpDocument.Builder`, `ExtractionState.Builder`                                                                               |
| **Adapter**                  | Wrap external library/API behind an internal interface        | `LlmAdapter` wraps Spring AI. `OcrSidecarClient` wraps the Python REST API.                                                    |
| **Decorator**                | Add cross-cutting behaviour without modifying the original    | `AuditingJobStateRepository` decorates `JpaJobStateRepository` to log all state changes                                        |
| **Template Method**          | Define the skeleton of an algorithm; subclasses fill in steps | `BaseEntityExtractor` defines extract → chunk → call LLM → parse → validate flow; sub-extractors override field-specific steps |
| **Observer / Event**         | Decouple components that react to state changes               | Spring `ApplicationEvent` for `JobCompletedEvent` → triggers artifact generation, notification                                 |
| **Null Object**              | Avoid null checks on optional collaborators                   | `NoOpRepairStrategy` returned when no repair is applicable; caller never checks for null                                       |
| **Repository**               | Isolate data access behind a domain-facing interface          | `JobStateRepository` (port) implemented by `JpaJobStateRepository` (adapter)                                                   |
| **Command**                  | Encapsulate a request as an object                            | `RepairCommand` wraps the repair action (component ID + strategy + attempt) stored in the repair log                           |
| **Specification**            | Encapsulate business rules as combinable predicates           | `RuleConditionSpecification` evaluates JMESPath or LLM judgment for a rule — composable                                        |

**When NOT to use a pattern:** Do not apply a pattern because it "seems right." Every pattern must be justified by a
concrete need visible today, not a hypothetical future requirement.

---

### 4. Spring Boot Specific Standards

- `@Service` classes: application services only. Never put extraction logic, LLM calls, or file I/O directly in a
  `@Service`. Delegate to adapter components.
- `@Component` classes: adapter implementations (extractors, clients, generators). One responsibility per component.
- `@RestController`: thin layer only. No business logic. Validate input, call service, return DTO. Max 30 lines per
  handler method.
- `@Configuration`: one config class per concern (`LlmProviderConfig`, `AsyncConfig`, `SecurityConfig`,
  `DatabasePersistenceConfig`).
  Not one giant `AppConfig`.
- `@Transactional`: applied at the service layer, never at the controller or repository implementation.
- `@Async`: only on the job executor entry point. Not scattered across the codebase.
- No field injection (`@Autowired` on fields). Use constructor injection everywhere — it makes dependencies explicit and
  enables unit testing without Spring context.

---

### 5. Testing Standards

Backend unit testing only. No integration, E2E, contract, or frontend test requirements.

| Concern               | Rule                                                                                              |
|-----------------------|---------------------------------------------------------------------------------------------------|
| **Framework**         | JUnit 5 + Mockito                                                                                 |
| **Coverage target**   | Every public method in `application/service/` and `adapter/` has at least one unit test           |
| **Isolation**         | All dependencies mocked via `@Mock` / `@InjectMocks`. No Spring context loaded in tests.          |
| **Naming**            | Test class: `{ClassName}Test.java`. Test method: `should{Behaviour}When{Condition}()`             |
| **Assertions**        | Use AssertJ (`assertThat(...)`) — never bare JUnit `assertEquals`                                 |
| **No sleep**          | No `Thread.sleep()` in tests. Use `Awaitility` if testing async behaviour.                        |
| **No external calls** | Tests never hit real LLM APIs, OCR sidecar, or Postgres. Mock or stub everything at the boundary. |

---

### 6. Frontend Standards (React + Tailwind v4 + shadcn/ui)

#### 6.1 Tech Stack

| Concern             | Library                         |
|---------------------|---------------------------------|
| Framework           | React 19                        |
| Build               | Vite 7                          |
| Routing             | React Router DOM 7              |
| Server state        | @tanstack/react-query 5         |
| HTTP client         | axios                           |
| Typing              | TypeScript (strict)             |
| Styling             | Tailwind CSS v4                 |
| Component library   | shadcn/ui (Radix UI primitives) |
| Icons               | lucide-react                    |
| Toast notifications | sonner (via shadcn/ui)          |

#### 6.2 Folder Structure

```
src/
  api/              HTTP clients (rfpClient.ts, authClient.ts)
  components/       Reusable components (SectionTree, EntityTable, etc.)
  components/ui/    shadcn/ui generated components — DO NOT edit manually
  hooks/            Custom React hooks
  layouts/          AppLayout (sidebar + topnav), AuthLayout (centered card)
  lib/              Utilities (cn() helper, formatters)
  pages/            Route-level components only
  types/            TypeScript interfaces matching backend JSON
```

- `components/ui/` is the shadcn/ui output directory. Files here are owned by the shadcn CLI. Do not modify them
  directly — copy and rename if customization is needed.
- `layouts/` contains layout wrappers. Every route uses either `AppLayout` or `AuthLayout`.

#### 6.3 Layout System

**AppLayout** (all authenticated pages):

- Fixed left sidebar (240 px on desktop) + top navigation bar (56 px tall) + scrollable content area.
- Sidebar contains: application logo / wordmark, primary navigation links (Jobs, Upload, Admin), user info at bottom
  (username, role badge), logout button.
- Top nav contains: page title or breadcrumb, secondary actions (e.g., "Upload New" button).
- Content area: scrollable, padded `p-6`, `max-w-7xl mx-auto` for wide pages, `max-w-4xl mx-auto` for detail pages.
- Mobile: sidebar collapses to an off-canvas `Sheet` (shadcn/ui) triggered by a hamburger button in the top nav.

**AuthLayout** (login, signup, error pages):

- Full-screen centered card: `min-h-screen bg-muted flex items-center justify-center`.
- Card: `w-full max-w-sm` using shadcn `Card`, `CardHeader`, `CardContent`.

#### 6.4 Required shadcn/ui Components

**Navigation & Layout:**
`Sheet`, `Tabs` / `TabsList` / `TabsTrigger` / `TabsContent`, `Collapsible` / `CollapsibleTrigger` /
`CollapsibleContent`

**Data Display:**
`Table` / `TableHeader` / `TableRow` / `TableHead` / `TableBody` / `TableCell`, `Badge`, `Card` / `CardHeader` /
`CardContent` / `CardFooter`, `Progress`

**Forms & Inputs:**
`Button` (variants: `default`, `secondary`, `outline`, `destructive`, `ghost`), `Input`, `Label`

**Feedback:**
`Sonner` (toast), `Alert` / `AlertDescription`, `Skeleton`

**Overlays:**
`Dialog` / `DialogContent` / `DialogHeader` / `DialogTitle`

#### 6.5 Responsive Behavior

| Breakpoint           | Sidebar                      | Tables                   |
|----------------------|------------------------------|--------------------------|
| Mobile (< 640 px)    | Hidden; hamburger → Sheet    | `overflow-x-auto` scroll |
| Tablet (640–1024 px) | Icon-only strip (64 px wide) | Normal                   |
| Desktop (> 1024 px)  | Full with labels (240 px)    | Normal                   |

- `AppLayout` owns all responsive behavior. Pages never implement their own responsive wrappers.
- Use Tailwind responsive prefixes (`sm:`, `md:`, `lg:`) for breakpoint-specific overrides.

#### 6.6 Theming and Color System

shadcn/ui uses CSS custom properties defined in `src/index.css`. Never hard-code color hex values in component logic.
Map semantic meanings to shadcn primitives:

**Confidence badges** (shared `ConfidenceBadge` component, reused across sprints):

| Level        | Markup                                                                               |
|--------------|--------------------------------------------------------------------------------------|
| HIGH (≥ 0.8) | `<Badge variant="outline" className="text-green-700 border-green-300">HIGH</Badge>`  |
| MED (≥ 0.5)  | `<Badge variant="outline" className="text-yellow-700 border-yellow-300">MED</Badge>` |
| LOW (< 0.5)  | `<Badge variant="destructive">LOW</Badge>`                                           |

**Job status badges:**

| Status    | Badge                                                                     |
|-----------|---------------------------------------------------------------------------|
| COMPLETED | `<Badge variant="outline" className="text-green-700 border-green-300">`   |
| RUNNING   | `<Badge variant="secondary">`                                             |
| QUEUED    | `<Badge variant="outline" className="text-yellow-700 border-yellow-300">` |
| FAILED    | `<Badge variant="destructive">`                                           |
| PARTIAL   | `<Badge variant="outline" className="text-orange-700 border-orange-300">` |

#### 6.7 Error Pages

| Page                   | Route / Trigger    | Content                                                      |
|------------------------|--------------------|--------------------------------------------------------------|
| `NotFoundPage.tsx`     | `<Route path="*">` | "404" heading, message, link to `/jobs`. Uses `AuthLayout`.  |
| `ServerErrorPage.tsx`  | Error boundary     | "Something went wrong", retry button. Uses `AuthLayout`.     |
| `AccessDeniedPage.tsx` | Redirect on 403    | "No permission" message, link to `/jobs`. Uses `AuthLayout`. |

**Inline boundary states** (replace scattered ad-hoc patterns):

| State   | Component                                | Replaces                       |
|---------|------------------------------------------|--------------------------------|
| Loading | shadcn `Skeleton`                        | `<p>Loading…</p>`              |
| Empty   | `Card` with icon + heading + description | blank page                     |
| Error   | `<Alert variant="destructive">`          | `<p className="text-red-500">` |

#### 6.8 Code Rules

- **Component size**: No component file > 150 lines. Extract sub-components aggressively.
- **State management**: React Query (`@tanstack/react-query`) for all server state (polling, caching, mutations). No
  `useEffect` for data fetching.
- **API layer**: All HTTP calls go through `src/api/rfpClient.ts` or `src/api/authClient.ts`. Components never call
  `axios` or `fetch` directly.
- **Typing**: TypeScript strict mode. No `any`. All API response types match backend JSON schema.
- **Styling**: No inline `style={{}}` objects except where CSS grid `gridColumn` / `gridRow` is required for
  `TableViewer.tsx` merged cells — this is the only permitted exception.
- **No raw Tailwind color classes** (`bg-green-500`, `text-red-600`, etc.) outside of `components/ui/` or the semantic
  overrides documented in §6.6. Use shadcn component variants and the className overrides listed above.
- **Tab bars**: All tab navigation uses shadcn `Tabs`. Manual `border-b-2` tab implementations are forbidden from
  Sprint 3 onward.
- **Toast notifications**: All toasts use `sonner`. Manual `useState(toast)` pattern is forbidden.
- **Collapsible panels**: All collapsible panels use shadcn `Collapsible`. Manual `useState(open)` toggle is permitted
  only for tree-node expand/collapse (SectionTree leaf-level UI).
- **Error handling**: Every async operation has an `isError` state displayed via `<Alert variant="destructive">`.
  No silent failures.

#### 6.9 Accessibility

All interactive UI must meet WCAG 2.1 AA baseline:

- **Labels**: Every form input (`Input`, `Select`, `Textarea`) must have an associated `<Label>` with matching
  `htmlFor` / `id`. Placeholder text is not a substitute for a label.
- **Focus states**: All interactive elements (buttons, links, inputs, tabs, tree nodes) must have a visible focus ring.
  shadcn/ui components include `focus-visible:ring-2 focus-visible:ring-ring` by default — do not remove it. Custom
  interactive elements must add equivalent Tailwind focus-visible utilities.
- **Keyboard navigation**:
    - `Dialog`: must trap focus while open. Close on `Escape`. shadcn `Dialog` handles this via Radix UI — do not
      reimplement.
    - `Tabs`: arrow keys move between triggers. shadcn `Tabs` handles this via Radix — do not override.
    - `Collapsible`: `Enter` / `Space` toggles open/closed on the trigger element.
    - `Sheet` (mobile sidebar): must trap focus while open. Close on `Escape`.
    - `SectionTree`: tree nodes must be focusable (`tabIndex={0}`). `Enter` / `Space` toggles expand/collapse.
- **ARIA attributes**: Use semantic HTML (`<nav>`, `<main>`, `<aside>`, `<header>`) for landmarks in `AppLayout`.
  Sidebar uses `<aside aria-label="Main navigation">`. Content area uses `<main>`. Top nav uses `<header>`.
  shadcn components provide correct ARIA roles automatically — do not override them.
- **Color contrast**: Text on colored badges must meet 4.5:1 contrast ratio. The badge color mappings in §6.6 are
  pre-validated for AA compliance. Do not introduce new color combinations without checking contrast.
- **Screen reader text**: Icon-only buttons (e.g., hamburger menu, close button) must include `aria-label` or
  visually hidden text via `<span className="sr-only">`.

#### 6.10 Frontend Library-First Mapping

| Capability         | Use                          | Never write custom                           |
|--------------------|------------------------------|----------------------------------------------|
| UI primitives      | shadcn/ui (`components/ui/`) | raw Tailwind buttons, inputs, tables, badges |
| Tab navigation     | shadcn `Tabs`                | manual `border-b-2` tab bars                 |
| Data tables        | shadcn `Table`               | raw `<table>` with Tailwind padding          |
| Status badges      | shadcn `Badge`               | raw `<span className="bg-*-100">`            |
| Collapsible panels | shadcn `Collapsible`         | manual useState toggle + overflow-hidden     |
| Progress bars      | shadcn `Progress`            | `bg-gray-200 rounded-full h-1.5` divs        |
| Toast              | `sonner`                     | useState-based toast divs                    |
| Form inputs        | shadcn `Input` + `Label`     | raw `<input className="...">`                |
| Icons              | `lucide-react`               | inline SVG or emoji characters               |
| Loading states     | shadcn `Skeleton`            | `<p>Loading…</p>`                            |
| Inline errors      | shadcn `Alert`               | `<p className="text-red-500">`               |
| Modals             | shadcn `Dialog`              | manual z-index overlays                      |

---

### 7. Python (FastAPI OCR Sidecar) Standards

- **Typed**: all functions use Python type hints. `mypy` or `pyright` clean.
- **One responsibility per module**: `main.py` (FastAPI routes only), `ocr_service.py` (OCR logic),
  `layout_detector.py` (layout detection), `image_utils.py` (PDF-to-image).
- **Pydantic models** for all request/response bodies — never raw dicts.
- **No global mutable state**: easyOCR reader initialized once at startup via FastAPI lifespan, not at import time.
- **Error responses**: HTTP 422 for bad input, 503 for OCR engine unavailable, 200 with `confidence: 0.0` for degraded
  results.

---

## Library-First Development Principle

> **If a library does it, use the library. Never reinvent.**

This applies across the entire codebase without exception. Before writing any utility, helper, or infrastructure code,
verify no library already provides it.

### Mandatory Library Mappings

| Capability                                                                                 | Library to Use                                                                                     | Never Write Custom                  |
|--------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------|-------------------------------------|
| PDF text extraction                                                                        | `pdfbox` (Apache PDFBox 3.x)                                                                       | custom PDF parser                   |
| DOCX extraction                                                                            | LangChain4J `ApachePdfBoxDocumentParser` or Apache POI                                             | custom XML reader                   |
| Text chunking / splitting                                                                  | LangChain4J `DocumentSplitter` / `RecursiveCharacterTextSplitter`                                  | custom chunking logic               |
| JSON Schema validation                                                                     | `networknt:json-schema-validator`                                                                  | custom schema checker               |
| JMESPath evaluation                                                                        | `io.burt:jmespath-java`                                                                            | custom expression engine            |
| YAML parsing                                                                               | Jackson `YAMLFactory` (`jackson-dataformat-yaml`)                                                  | custom YAML reader                  |
| LLM calls                                                                                  | Spring AI `ChatClient` (wrapped in `LlmAdapter`)                                                   | raw HTTP to LLM APIs                |
| Agent state graph                                                                          | LangGraph4J `StateGraph`                                                                           | custom state machine                |
| Embeddings                                                                                 | LangChain4J `EmbeddingModel`                                                                       | custom embedding logic              |
| Vector store                                                                               | `spring-ai-pgvector-store` (pgvector)                                                              | custom similarity search            |
| HTTP client (Java→Python)                                                                  | Spring `RestClient` (built-in since Spring 6)                                                      | `HttpURLConnection` / raw sockets   |
| JWT auth                                                                                   | `spring-boot-starter-oauth2-resource-server` + `nimbus-jose-jwt`                                   | custom JWT parser                   |
| Password hashing                                                                           | Spring Security `BCryptPasswordEncoder`                                                            | custom hash function                |
| AES encryption                                                                             | Java `javax.crypto` (standard library)                                                             | custom crypto                       |
| XLSX/DOCX generation                                                                       | Apache POI (`poi-ooxml`)                                                                           | custom XML writer                   |
| HTML templating (audit report)                                                             | Freemarker                                                                                         | string concatenation                |
| DOCX templating                                                                            | Freemarker + Apache POI XWPF                                                                       | custom template engine              |
| Scheduled jobs                                                                             | Spring `@Scheduled`                                                                                | custom thread loops                 |
| Async execution                                                                            | Spring `@Async` + `ThreadPoolTaskExecutor`                                                         | manual `Thread` / `Executor` wiring |
| Metrics                                                                                    | Deferred to wishlist (not in current baseline)                                                     | mandatory metrics pipeline          |
| Health checks                                                                              | Spring Web controller (`GET /api/v1/health`)                                                       | Actuator dependency in baseline     |
| DB access                                                                                  | Spring Data JPA + Hibernate                                                                        | raw JDBC for standard queries       |
| Connection pooling                                                                         | HikariCP (auto-configured by Spring Boot)                                                          | manual connection management        |
| File watching (rule hot-reload)                                                            | Java `WatchService` (standard library)                                                             | polling loops                       |
| UUID generation                                                                            | `java.util.UUID.randomUUID()`                                                                      | custom ID generators                |
| Boilerplate (getters, setters, builders, constructors, toString, equals/hashCode, logging) | Lombok (`@Data`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j`, `@Value`, `@Getter`, `@Setter`) | hand-written boilerplate            |
| String similarity                                                                          | Apache Commons Text `LevenshteinDistance`                                                          | custom edit distance                |
| Image rendering from PDF                                                                   | PDFBox `PDFRenderer`                                                                               | custom PDF rasterizer               |
| OCR (Python side)                                                                          | easyOCR + Tesseract via `pytesseract`                                                              | custom OCR implementation           |
| PDF-to-image (Python side)                                                                 | `pdf2image`                                                                                        | custom rasterization                |

### When a Library Gap Exists

If no library covers the need (rare): write the minimum code, add a `// NOTE: no library available for X because Y`
comment, and create a GitHub issue to revisit if a library emerges.

---

## LLM Provider Configuration Design

`application.properties` drives provider selection via a single property:

```properties
# Switch between: openrouter | ollama | openai
app.llm.provider=openrouter
# OpenRouter (default)
app.llm.openrouter.base-url=https://openrouter.ai/api/v1
app.llm.openrouter.api-key=${OPENROUTER_API_KEY}
app.llm.openrouter.model=google/gemini-2.0-flash-001
app.llm.openrouter.model.judge=google/gemini-2.5-pro-preview-06-05
# Ollama (self-hosted fallback)
app.llm.ollama.base-url=http://localhost:11434
app.llm.ollama.model=llama3.1:8b
app.llm.ollama.model.judge=llama3.1:70b
# Shared
app.llm.temperature=0.0
app.llm.max-tokens=4096
app.llm.chunk-size-tokens=3500
```

A `LlmProviderConfig` Spring `@Configuration` class reads `app.llm.provider` and registers the correct Spring AI
`ChatClient` and LangChain4J `ChatLanguageModel` beans. All services depend on the interface, not a concrete provider.

### Deployment Mode: OpenRouter vs. Ollama

| Mode                        | Provider          | Data leaves network?                 | Use for                                                |
|-----------------------------|-------------------|--------------------------------------|--------------------------------------------------------|
| Development / pilot         | OpenRouter        | Yes (to OpenRouter → upstream model) | Local dev, demo, evaluation                            |
| GOB production (air-gapped) | Ollama on-premise | **No**                               | Any client with data sovereignty / air-gap requirement |
| GOB production (OpenRouter) | OpenRouter        | Yes                                  | **Not permitted** — refutes air-gap claim              |

**Rule:** `app.llm.provider=openrouter` is for **development and evaluation only**. Any GOB client
who cites data sovereignty or air-gap requirements **must** be deployed with `app.llm.provider=ollama`
running on DSI's own GPU hardware. The architecture is one config flag away — no code changes needed.

The `doc_meta.extraction_model` field in the output JSON records which provider and model was used,
satisfying audit trail requirements regardless of deployment mode.

---

## Project Structure

```
rfp-extractor/
├── pom.xml                          ← Parent POM
├── rfp-core/                        ← Domain + shared models (no Spring)
│   └── src/main/java/com/dsi/rfp/
│       ├── domain/model/            ← RfpDocument, Section, Clause, Table, Entity*
│       └── domain/port/             ← Extraction/RulePack/Artifact port interfaces
├── rfp-service/                     ← Spring Boot application
│   └── src/main/java/com/dsi/rfp/
│       ├── RfpApplication.java
│       ├── config/                  ← LlmProviderConfig, AsyncConfig, DatabasePersistenceConfig, SecurityConfig
│       ├── adapter/api/             ← REST controllers + DTOs
│       ├── adapter/persistence/     ← PostgreSQL job store, file storage adapter
│       ├── adapter/llm/             ← LLM adapter (wraps Spring AI + provider config)
│       ├── adapter/extraction/      ← PdfDocumentLoader, PageClassifier, SectionSegmenter,
│       │                               ClauseIdAssigner, ColumnDetector
│       ├── adapter/ocr/             ← OCR sidecar HTTP client
│       ├── adapter/table/           ← TableExtractor, TableMerger
│       ├── adapter/entity/          ← EntityExtractor (all 45 fields)
│       ├── adapter/rulepack/        ← RulePackLoader, RulePackRunner, RfpTypeClassifier
│       ├── adapter/artifact/        ← XLSX generators, DOCX generator, AuditReportGenerator
│       ├── adapter/security/        ← JWT filter, audit trail logger
│       ├── agent/                   ← LangGraph4J graph definition, nodes, edges
│       └── application/service/     ← Application services < 250 lines each
├── rfp-frontend/                    ← React 18 + Tailwind v4 + Vite
│   ├── package.json
│   └── src/
│       ├── pages/                   ← UploadPage, JobStatusPage, ResultPage, AdminPage
│       ├── components/              ← SectionTree, EntityTable, RulePackResults, ArtifactDownload
│       └── api/                     ← API client (axios)
├── rfp-python-sidecar/                  ← Python FastAPI OCR sidecar
│   ├── main.py                      ← FastAPI app
│   ├── ocr_service.py               ← easyOCR + Tesseract orchestration
│   └── requirements.txt
├── schema/
│   ├── rfp-schema-v1.json           ← RFP JSON Schema (canonical)
│   └── rule-pack-schema-v1.json          ← Rule YAML validation schema
├── rules/
│   ├── bd-govt-ict-v1.yaml          ← 64 ICT rules (55 structural JMESPath + 9 semantic LLM)
│   ├── bd-govt-works-v1.yaml        ← 33 Works contract rules (30 structural + 3 semantic)
│   ├── bd-govt-consultancy-v1.yaml  ← 33 Consultancy/ToR rules (30 structural + 3 semantic)
│   └── bd-govt-goods-v1.yaml        ← 22 Goods procurement rules (20 structural + 2 semantic)
├── testdata/ground-truth/           ← Annotated GOB RFP JSON files
└── docker-compose.yml               ← All services
```

---

## RFP JSON Schema — Full Entity Model

The schema captures ALL 45 fields from the user's checklist, organized into typed sub-objects:

```json
{
    "schema_version": "1.0",
    "doc_meta": {
        "title": "",
        "procurement_ref": "",
        "issue_date": "",
        "rfp_type": "ict|works|consultancy|goods|unknown",
        "source_language": "en|bn|mixed|unknown",
        "extraction_model": "",
        "extraction_timestamp": ""
    },
    "sections": [
        {
            "id": "",
            "title": "",
            "level": 1,
            "page_start": 0,
            "page_end": 0,
            "children": [],
            "confidence": {
                "score": 0.0,
                "method": "bookmark|heading_style|regex|font_size|caps|llm"
            }
        }
    ],
    "clauses": [
        {
            "id": "",
            "section_id": "",
            "page_start": 0,
            "page_end": 0,
            "text": "",
            "text_language": "en|bn|mixed",
            "tags": [],
            "references": [],
            "confidence": {
                "score": 0.0,
                "method": "text_layer|ocr|text+ocr",
                "ocr_confidence": null
            }
        }
    ],
    "tables": [
        {
            "id": "",
            "section_id": "",
            "clause_id": "",
            "page_start": 0,
            "page_end": 0,
            "caption": "",
            "type": "deliverables|evaluation|payment|staffing|schedule|other",
            "headers": [],
            "rows": [],
            "grid": [
                {
                    "row": 0,
                    "col": 0,
                    "value": "",
                    "rowspan": 1,
                    "colspan": 1
                }
            ],
            "confidence": {
                "score": 0.0,
                "method": "lattice|stream|ocr_llm_reconstruct"
            }
        }
    ],
    "entities": {
        "general": {
            "client_name": {
                "value": "",
                "clause_id": "",
                "page": 0,
                "confidence": 0.0
            },
            "submission_deadline": {
                "value": "",
                "clause_id": "",
                "page": 0,
                "confidence": 0.0
            },
            "issue_date": {
                "value": "",
                "clause_id": "",
                "page": 0,
                "confidence": 0.0
            },
            "method_of_selection": {
                "value": "",
                "clause_id": "",
                "page": 0,
                "confidence": 0.0
            },
            "procurement_method": {
                "value": "",
                "clause_id": "",
                "page": 0,
                "confidence": 0.0
            },
            "project_duration": {
                "value": "",
                "clause_id": "",
                "page": 0,
                "confidence": 0.0
            },
            "pre_bid_meeting": {
                "date": "",
                "venue": "",
                "clause_id": "",
                "page": 0,
                "confidence": 0.0
            },
            "contact": {
                "name": "",
                "email": "",
                "phone": "",
                "address": "",
                "clause_id": "",
                "page": 0,
                "confidence": 0.0
            }
        },
        "submission": {
            "guidelines_summary": {
                "value": "",
                "clause_id": "",
                "confidence": 0.0
            },
            "number_of_copies": {
                "value": 0,
                "clause_id": "",
                "confidence": 0.0
            },
            "soft_submission_required": {
                "value": false,
                "email": "",
                "clause_id": "",
                "confidence": 0.0
            },
            "submission_address": {
                "value": "",
                "clause_id": "",
                "confidence": 0.0
            }
        },
        "financial": {
            "technical_financial_split": {
                "technical_weight": 0,
                "financial_weight": 0,
                "clause_id": "",
                "confidence": 0.0
            },
            "performance_security": {
                "percentage": 0,
                "type": "",
                "clause_id": "",
                "confidence": 0.0
            },
            "bank_guarantee": {
                "required": false,
                "details": "",
                "clause_id": "",
                "confidence": 0.0
            },
            "payment_terms": {
                "value": "",
                "clause_id": "",
                "confidence": 0.0
            },
            "reimbursable_expenses": {
                "value": "",
                "clause_id": "",
                "confidence": 0.0
            },
            "bid_validity_period": {
                "value": "",
                "clause_id": "",
                "confidence": 0.0
            }
        },
        "ict": {
            "total_users": {
                "value": 0,
                "clause_id": "",
                "confidence": 0.0
            },
            "concurrent_users": {
                "value": 0,
                "clause_id": "",
                "confidence": 0.0
            },
            "programming_language_preference": {
                "value": "",
                "clause_id": "",
                "confidence": 0.0
            },
            "system_language": {
                "value": "en|bn|both",
                "clause_id": "",
                "confidence": 0.0
            },
            "architecture": {
                "value": "",
                "clause_id": "",
                "confidence": 0.0
            },
            "tech_stack": {
                "value": "",
                "clause_id": "",
                "confidence": 0.0
            },
            "database": {
                "value": "",
                "clause_id": "",
                "confidence": 0.0
            },
            "hosting": {
                "type": "cloud|on-premise|hybrid",
                "details": "",
                "clause_id": "",
                "confidence": 0.0
            },
            "data_migration_required": {
                "value": false,
                "details": "",
                "clause_id": "",
                "confidence": 0.0
            },
            "legacy_system": {
                "value": "",
                "clause_id": "",
                "confidence": 0.0
            },
            "hardware_requirements": {
                "value": "",
                "clause_id": "",
                "confidence": 0.0
            },
            "integrations": [
                {
                    "system": "",
                    "type": "",
                    "clause_id": "",
                    "confidence": 0.0
                }
            ],
            "mobile_app_required": {
                "value": false,
                "platforms": [],
                "clause_id": "",
                "confidence": 0.0
            },
            "ui_mock_required": {
                "value": false,
                "clause_id": "",
                "confidence": 0.0
            },
            "presentation_required": {
                "value": false,
                "clause_id": "",
                "confidence": 0.0
            },
            "gantt_chart_required": {
                "value": false,
                "clause_id": "",
                "confidence": 0.0
            },
            "e_governance_compliance": {
                "required": false,
                "framework": "",
                "clause_id": "",
                "confidence": 0.0
            }
        },
        "staffing": {
            "staff_months": {
                "value": 0,
                "clause_id": "",
                "confidence": 0.0
            },
            "onsite_resource_requirements": {
                "value": "",
                "clause_id": "",
                "confidence": 0.0
            },
            "marking_criteria": {
                "value": "",
                "clause_id": "",
                "confidence": 0.0
            }
        },
        "support": {
            "training": {
                "value": "",
                "duration": "",
                "clause_id": "",
                "confidence": 0.0
            },
            "support_maintenance": {
                "value": "",
                "period": "",
                "sla": "",
                "clause_id": "",
                "confidence": 0.0
            },
            "warranty_period": {
                "value": "",
                "clause_id": "",
                "confidence": 0.0
            }
        },
        "evaluation": {
            "criteria": [
                {
                    "name": "",
                    "weight": 0,
                    "clause_id": "",
                    "confidence": 0.0
                }
            ],
            "eligibility_summary": {
                "value": "",
                "clause_id": "",
                "confidence": 0.0
            },
            "scope_summary": {
                "value": "",
                "clause_id": "",
                "confidence": 0.0
            }
        },
        "pricing_factors": [
            {
                "factor": "",
                "clause_id": "",
                "confidence": 0.0
            }
        ],
        "rfp_amendments": [
            {
                "description": "",
                "date": "",
                "clause_id": "",
                "confidence": 0.0
            }
        ],
        "other_info": {
            "value": "",
            "clause_id": "",
            "confidence": 0.0
        }
    },
    "rule_pack_results": {
        "pack_id": "",
        "pack_version": "",
        "run_timestamp": "",
        "summary": {
            "fatal": 0,
            "high": 0,
            "medium": 0,
            "low": 0,
            "info": 0
        },
        "findings": [
            {
                "rule_id": "",
                "severity": "",
                "status": "PASS|FAIL|SKIPPED",
                "message": "",
                "evidence": ""
            }
        ]
    },
    "extraction_state": {
        "job_id": "",
        "doc_completeness_score": 0.0,
        "missing_fields": [],
        "manual_review_required": [],
        "page_summary": [
            {
                "page": 0,
                "classification": "DIGITAL|SCANNED|MIXED",
                "method": "",
                "confidence": 0.0,
                "retries": 0
            }
        ]
    }
}
```

---

## Rule Pack DSL

Each rule is a YAML entry validated against `rule-pack-schema-v1.json`:

```yaml
-   id           : BD-ICT-001
    name         : RFP Title Present
    pack         : bd-govt-ict-v1
    version      : "1.0.0"
    severity     : FATAL           # FATAL | HIGH | MEDIUM | LOW | INFO
    check_type   : structural    # structural (JMESPath) | semantic (LLM)
    condition    : "doc_meta.title != null && doc_meta.title != ''"
    evidence_path: "doc_meta.title"
    message      : "RFP Title is missing from the document"

-   id           : BD-ICT-056
    name         : Scope Sufficiently Specific
    pack         : bd-govt-ict-v1
    version      : "1.0.0"
    severity     : HIGH
    check_type   : semantic
    evidence_path: "entities.evaluation.scope_summary.value"
    llm_prompt   : |
        You are a GOB ICT procurement expert. Evaluate if this scope of work is specific enough
        to price accurately. Reply with JSON: {"finding": true/false, "explanation": "...", "confidence": 0.0-1.0}
        finding=true means there IS a problem (scope is vague).
        Scope text: {{evidence}}
    message      : "Scope of work may be too vague to price accurately"
```

---

## LangGraph4J Agent Graph

```
                    ┌─────────────────────────────────────────────┐
                    │             ExtractionGraph                 │
                    │                                             │
  START ──► [VALIDATE] ──► [CLASSIFY_PAGES] ──► [EXTRACT_TEXT]    │
                                                       │          │
                                               [SEGMENT_SECTIONS] │
                                                       │          │
                                              [EXTRACT_TABLES]    │
                                                       │          │
                                              [EXTRACT_ENTITIES]  │
                                                       │          │
                                              [SCORE_CONFIDENCE]  │
                                                       │          │
                               ┌───── confidence ok? ──┤          │
                               │                       │ low      │
                               │              [REPAIR_LOOP] ◄──┐  │
                               │                       │       │  │
                               │            retries < max? ────┘  │
                               │            (deterministic table) │
                               │                       │ exhausted│
                               ▼                       ▼          │
                         [RUN_RULE_PACK]     [FLAG_MANUAL_REVIEW] │
                               │                       │          │
                               └───────────┬───────────┘          │
                                           ▼                      │
                                    [FINALIZE] ──► END            │
                    └─────────────────────────────────────────────┘
```

Each node is a Spring-managed `@Component` implementing `LangGraph4J NodeAction`. State is typed as `ExtractionState` (
PostgreSQL-backed `StateGraph` checkpoint persistence).

---

## bd-govt-ict-v1 Rule Pack — Complete Rule List

### Structural Rules (JMESPath)

| Rule ID    | Field                                                         | Severity |
|------------|---------------------------------------------------------------|----------|
| BD-ICT-001 | RFP Title present                                             | FATAL    |
| BD-ICT-002 | RFP Identification Number present                             | FATAL    |
| BD-ICT-003 | Issue Date present                                            | HIGH     |
| BD-ICT-004 | Client/Procuring Entity name present                          | FATAL    |
| BD-ICT-005 | Submission Date present                                       | FATAL    |
| BD-ICT-006 | Submission Time specified (not just date)                     | HIGH     |
| BD-ICT-007 | Method of Selection present                                   | HIGH     |
| BD-ICT-008 | Procurement Method present                                    | HIGH     |
| BD-ICT-009 | Project Duration specified                                    | HIGH     |
| BD-ICT-010 | Pre-Bid Meeting details present                               | MEDIUM   |
| BD-ICT-011 | Submission Guidelines section present                         | HIGH     |
| BD-ICT-012 | Number of copies required specified                           | MEDIUM   |
| BD-ICT-013 | Soft/email submission instructions present                    | MEDIUM   |
| BD-ICT-014 | Technical-Financial split ratio specified                     | FATAL    |
| BD-ICT-015 | Technical + Financial weights sum to 100                      | HIGH     |
| BD-ICT-016 | Performance Security requirement specified                    | HIGH     |
| BD-ICT-017 | Client Contact information present                            | HIGH     |
| BD-ICT-018 | Total user count specified                                    | HIGH     |
| BD-ICT-019 | Concurrent user count specified                               | HIGH     |
| BD-ICT-020 | Programming language preference stated                        | MEDIUM   |
| BD-ICT-021 | System language (Bangla/English/Both) specified               | HIGH     |
| BD-ICT-022 | Architecture requirements stated                              | MEDIUM   |
| BD-ICT-023 | Tech stack / platform preference stated                       | MEDIUM   |
| BD-ICT-024 | Database type/preference specified                            | MEDIUM   |
| BD-ICT-025 | Staff months requirement specified                            | HIGH     |
| BD-ICT-026 | Integration requirements listed                               | HIGH     |
| BD-ICT-027 | Hosting type (cloud/on-premise) specified                     | HIGH     |
| BD-ICT-028 | Data migration requirement stated                             | MEDIUM   |
| BD-ICT-029 | Legacy system details provided                                | MEDIUM   |
| BD-ICT-030 | Hardware requirements stated                                  | MEDIUM   |
| BD-ICT-031 | Onsite resource requirements stated                           | MEDIUM   |
| BD-ICT-032 | UI mock requirement stated                                    | MEDIUM   |
| BD-ICT-033 | Presentation requirement stated                               | MEDIUM   |
| BD-ICT-034 | Reimbursable expenses policy stated                           | MEDIUM   |
| BD-ICT-035 | Training requirements specified                               | HIGH     |
| BD-ICT-036 | Support & maintenance terms specified                         | HIGH     |
| BD-ICT-037 | Warranty period specified                                     | HIGH     |
| BD-ICT-038 | Pricing-affecting factors documented                          | HIGH     |
| BD-ICT-039 | Gantt chart requirement stated                                | MEDIUM   |
| BD-ICT-040 | Staffing & marking criteria present                           | FATAL    |
| BD-ICT-041 | Payment terms specified                                       | HIGH     |
| BD-ICT-042 | Bank guarantee terms specified                                | HIGH     |
| BD-ICT-043 | Mobile application requirement stated                         | MEDIUM   |
| BD-ICT-044 | e-Governance framework compliance stated                      | HIGH     |
| BD-ICT-045 | Evaluation criteria section present                           | FATAL    |
| BD-ICT-046 | SLA requirements specified                                    | HIGH     |
| BD-ICT-047 | Source code ownership/escrow clause present                   | HIGH     |
| BD-ICT-048 | Data ownership and privacy clause present                     | HIGH     |
| BD-ICT-049 | Acceptance testing criteria specified                         | HIGH     |
| BD-ICT-050 | Eligibility criteria section present                          | FATAL    |
| BD-ICT-051 | Technical qualification requirements present                  | HIGH     |
| BD-ICT-052 | Staff CV requirements (roles, experience) present             | HIGH     |
| BD-ICT-053 | Bid validity period specified                                 | HIGH     |
| BD-ICT-054 | Submission deadline consistency (deadline < bid validity end) | HIGH     |
| BD-ICT-055 | RFP amendments/changes noted                                  | INFO     |

### Semantic Rules (LLM Judgment)

| Rule ID    | Check                                                                   | Severity |
|------------|-------------------------------------------------------------------------|----------|
| BD-ICT-056 | Scope of work is specific enough to price accurately                    | HIGH     |
| BD-ICT-057 | Acceptance criteria for deliverables are measurable                     | HIGH     |
| BD-ICT-058 | No contradictory technical requirements detected                        | HIGH     |
| BD-ICT-059 | SLA targets are realistic and measurable                                | MEDIUM   |
| BD-ICT-060 | Payment schedule is tied to deliverables (not time-only)                | MEDIUM   |
| BD-ICT-061 | Training scope is clearly bounded (duration, audience, syllabus)        | MEDIUM   |
| BD-ICT-062 | Support & maintenance SLA has quantified response/resolution times      | HIGH     |
| BD-ICT-063 | Integration requirements have sufficient API/protocol detail            | HIGH     |
| BD-ICT-064 | Submission deadline is not unrealistically short (< 21 days from issue) | MEDIUM   |

---

## Sprint Plan (2-Week Sprints)

---

### Sprint 1 — Project Bootstrap & LLM Provider Infrastructure

**Delivers:** A running Spring Boot app + React frontend + Python OCR sidecar (skeleton) with configurable LLM provider.
Hello-world end-to-end.

**Tasks:**

**Project Setup**

- [ ] Create Maven parent POM with modules: `rfp-core`, `rfp-service`, `rfp-frontend` (managed separately via Node)
- [ ] `rfp-core`: Pure Java 21 domain model (no Spring deps). Add `RfpDocument`, `Section`, `Clause`, `Table`,
  `ExtractionState`, all entity sub-models matching schema above
- [ ] `rfp-service`: Spring Boot 3.5.11 starter, import `rfp-core`
- [ ] Add dependencies to `rfp-service/pom.xml`:
    - `spring-boot-starter-web`, `spring-boot-starter-security`
    - `spring-ai-openai-spring-boot-starter` (covers OpenRouter + OpenAI via OpenAI-compatible API)
    - `spring-ai-ollama-spring-boot-starter`
    - `langchain4j-spring-boot-starter`, `langchain4j-open-ai`, `langchain4j-ollama`
    - `langchain4j-document-parser-apache-pdfbox`
    - `langgraph4j-core` (io.github.bsorrentino:langgraph4j-core)
    - `pdfbox` (Apache PDFBox 3.x)
    - `jackson-databind`, `jackson-dataformat-yaml`
    - `jmespath-java` (io.burt:jmespath-java)
    - `networknt:json-schema-validator` (for JSON Schema validation)
    - `poi-ooxml` (Apache POI)
    - `freemarker`

**LLM Provider Abstraction**

- [ ] `LlmProviderConfig.java` — reads `app.llm.provider`, registers correct `ChatClient` bean (Spring AI) and
  `ChatLanguageModel` bean (LangChain4J). Two concrete configurations: `OpenRouterLlmConfig` and `OllamaLlmConfig`,
  selected via `@ConditionalOnProperty`
- [ ] `LlmAdapter.java` (adapter layer) — wraps Spring AI `ChatClient`. Methods: `extractStructured(prompt, schema)`,
  `judgeSnippet(prompt, snippet)`. All callers depend on this adapter, never on Spring AI directly
- [ ] Validate: a JUnit unit test that calls `LlmAdapter.extractStructured()` with mocked provider responses and
  verifies
  JSON parsing + exception mapping

**Python OCR Sidecar**

- [ ] `rfp-python-sidecar/main.py` — FastAPI app with health endpoint `GET /health`
- [ ] `rfp-python-sidecar/requirements.txt` — `fastapi`, `uvicorn`, `easyocr`, `pytesseract`, `Pillow`, `pdf2image`
- [ ] `rfp-python-sidecar/ocr_service.py` — skeleton with `extract_page(page_image_bytes, lang)` stub

**Frontend Skeleton**

- [ ] `rfp-frontend/` — Vite + React 18 + Tailwind v4 scaffold
- [ ] `src/pages/UploadPage.tsx` — file input, submit button (wired to nothing yet)
- [ ] `src/api/rfpClient.ts` — axios client pointed at Spring Boot backend

**Infrastructure**

- [ ] `docker-compose.yml` — services: `postgres`, `rfp-service` (Spring Boot), `rfp-python-sidecar` (FastAPI),
  `rfp-frontend` (Nginx)
- [ ] Health check endpoints: `GET /api/v1/health` → 200 + provider info

---

### Sprint 2 — Document Ingestion, Page Classification & Async Jobs

**Delivers:** Upload a PDF → get a job ID → poll for status → see per-page classification result.

**Domain**

- [ ] `ExtractionJob.java` (domain model): `{jobId, status, submittedAt, completedAt, documentId, progress}`
- [ ] `JobStatus` enum: `QUEUED`, `RUNNING`, `COMPLETED`, `FAILED`, `PARTIAL`
- [ ] `PageClassification` enum: `DIGITAL`, `SCANNED`, `MIXED`

**Input Validation** (fixes A-02)

- [ ] `DocumentValidationService.java` — validates before any processing:
    - File size ≤ configured limit (default 100 MB)
    - MIME type: `application/pdf` or DOCX MIME
    - PDF: attempt PDFBox load; catch `InvalidPasswordException` → reject as ENCRYPTED
    - PDF: check for XFA stream in AcroForm → reject as XFA_FORM
    - PDF: page count > 0 → reject CORRUPT if 0
    - Returns: `ValidationResult {valid, errorCode, errorMessage}`

**Unified PDF Loader** (fixes A-01)

- [ ] `PdfDocumentLoader.java` — single PDFBox-based loader. Provides:
    - `loadFullText()` → String
    - `loadPageText(pageNum)` → String
    - `loadPageBoundingBoxes(pageNum)` → `List<TextBlock>`
    - `loadPageImages(pageNum)` → `List<EmbeddedImage>`
    - `loadFontMetadata()` → `Map<String, FontInfo>`
    - DOCX path: delegates to LangChain4J `ApachePdfBoxDocumentParser` only for DOCX
    - Tika is NOT used on PDFs at all

**Page Classifier** (fixes A-04)

- [ ] `PageClassifier.java` — 3-class output using `PdfDocumentLoader` data:
    - Character density = (text char count / page area)
    - Raster coverage = (sum of raster image areas / page area)
    - DIGITAL: char density > 0.001 AND raster coverage < 0.60
    - SCANNED: char density < 0.0001 OR raster coverage > 0.80
    - MIXED: everything else
    - Calibrated thresholds — constants with TODO comment to tune after Sprint 3 test data

**Async Job Infrastructure**

- [ ] `AsyncConfig.java` — `ThreadPoolTaskExecutor`: core=2, max=4, queue=20, thread name prefix `rfp-worker-`
- [ ] `JobStateRepository.java` (port interface) + `JpaJobStateRepository.java` (adapter) — stores `ExtractionJob` in
  PostgreSQL via JPA (durable, queryable)
- [ ] `RfpSubmissionService.java` — accepts upload, validates, stores PDF to disk (encrypted), creates job, dispatches
  to executor
- [ ] `RfpJobService.java` — status queries, result retrieval

**REST API**

- [ ] `RfpController.java`:
    - `POST /api/v1/rfp/submit` → `{jobId, status: QUEUED}`
    - `GET /api/v1/rfp/status/{jobId}` → `{status, progress, createdAt}`
    - `GET /api/v1/rfp/result/{jobId}` → full RFP JSON when COMPLETED
    - `GET /api/v1/rfp/jobs` → list of jobs for current user

**Frontend**

- [ ] `UploadPage.tsx` — file picker, POST to `/api/v1/rfp/submit`, navigate to status page
- [ ] `JobStatusPage.tsx` — polls `/api/v1/rfp/status/{jobId}` every 3s, shows progress bar + current phase

---

### Sprint 3 — Section Segmentation & Clause IDs

**Delivers:** Uploaded PDF produces a structured section tree with stable clause IDs. Frontend shows the tree.

**Section Segmenter** (fixes A-05)

- [ ] `SectionSegmenter.java` — priority-ordered strategy chain (each is a `@Component` implementing `HeadingStrategy`):
    1. `BookmarkHeadingStrategy` — extracts from PDF outline/bookmark tree (most reliable)
    2. `HeadingStyleStrategy` — detects PDF font metadata naming (`Heading1`, `H2`, etc.)
    3. `NumberedHeadingStrategy` — regex: `^\s*(\d+\.)+\s+[A-Z]`, `^(PART|SECTION|SCHEDULE|ANNEX)\s+[IVX\d]`,
       `^(\d+)\.\s+[A-Z]`
    4. `BanglaHeadingStrategy` — Unicode regex: `^ধারা\s+[\d০-৯]`, `^অনুচ্ছেদ\s+[\d০-৯]`, `^অধ্যায়\s+[\d০-৯]` (safe
       regex, no NLP)
    5. `FontSizeHeadingStrategy` — font size > (body font + 2pt) = heading candidate
    6. `AllCapsHeadingStrategy` — ALL-CAPS line, length < 80 chars (last resort)
- [ ] `TocDetector.java` — if a page has > 8 short lines ending in page numbers, extract headings from it and skip
  heuristic strategies
- [ ] Output: `List<Section>` with `{id, title, level, pageStart, pageEnd, children[]}`
- [ ] Log which strategy fired per section (stored in `ExtractionState.pageAuditLog`)

**Clause ID Assigner** (fixes A-06)

- [ ] `ClauseIdAssigner.java`:
    - Primary: parse section number from heading text → `{procurementRef}:{sectionNum}:{clauseNum}`
    - Fallback (unnumbered): `{procurementRef}:S{sectionIdx}:P{paragraphIdx}` + WARNING in audit log
    - `procurementRef` normalized: lowercase, spaces→hyphens, max 20 chars, stripped of special chars
    - Unit test: same document processed twice → identical IDs every time

**JSON Schema Validation**

- [ ] `RfpJsonSchemaValidator.java` — validates output against `schema/rfp-schema-v1.json` before returning result. Hard
  fail if schema validation fails.
- [ ] Load schema at startup, cache it

**Quality Gates (Non-Blocking, No Annotation Dependency)**

- [ ] Build deterministic fixture set under `testdata/fixtures/` for sectioning, entity extraction, and tables
- [ ] Write `SectionSegmenterIntegrationTest.java` against fixture expectations (section count, title, hierarchy)
- [ ] Keep optional `testdata/ground-truth/` support behind a best-effort test profile (`-Pbenchmark`) for Sprint 13+

**Frontend**

- [ ] `ResultPage.tsx` — shows parsed section tree (collapsible, left panel)
- [ ] `SectionTree.tsx` component — renders `Section[]` hierarchically

---

### Sprint 4 — LangGraph4J Agent Graph + Full Entity Extraction

**Delivers:** The full agent graph runs. Upload a PDF, get back a populated RFP JSON with all 45 entity fields
attempted.

**LangGraph4J Graph**

- [ ] `ExtractionGraph.java` — defines the `StateGraph<ExtractionState>`:
    - Nodes: `ValidateNode`, `ClassifyPagesNode`, `ExtractTextNode`, `SegmentSectionsNode`, `ExtractTablesNode` (stub),
      `ExtractEntitiesNode`, `ScoreConfidenceNode`, `RepairLoopNode` (stub), `RunRulePackNode` (stub), `FinalizeNode`
    - Edges: sequential
      START→VALIDATE→CLASSIFY→EXTRACT_TEXT→SEGMENT→EXTRACT_TABLES→EXTRACT_ENTITIES→SCORE→REPAIR→RULE_PACK→FINALIZE→END
    - Conditional edge: SCORE → REPAIR if `anyFieldBelowThreshold(state)`, else → RULE_PACK
    - Each node is `@Component`-injected into the graph builder
- [ ] `ExtractionState.java` — LangGraph4J typed state:
  ```
  jobId, documentPath, pageClassifications[], sections[], tables[], clauses[],
  entities (RfpEntities), confidenceMap{sectionId→score},
  repairLog[{component, attempt, reason, result}],
  lowConfidenceQueue[], manualReviewRequired[], totalRepairIterations
  ```
- [ ] `ExtractionOrchestrationService.java` — compiles and runs the graph; dispatched by async executor

**Entity Extractor** (fixes D-01 through D-08, all entity fields)

- [ ] `EntityExtractor.java` — orchestrates extraction of all 45 fields using section chunks:
    - Each call to LLM processes one section at a time (≤ 3500 tokens)
    - Prepends doc context header: `{title, procurementRef, rfpType, issuer}` to every chunk
    - Uses Spring AI `ChatClient` with `format: json` structured output
    - Retry once on JSON parse failure; mark `confidence: 0.0` on second failure
    - Stores `{value, clauseId, page, method: "llm", confidence}` for each entity
- [ ] Decompose into focused sub-extractors (each < 250 lines):
    - `GeneralEntityExtractor.java` — title, issuer, dates, contact, pre-bid, methods
    - `SubmissionEntityExtractor.java` — guidelines, copies, soft submission, address
    - `FinancialEntityExtractor.java` — split ratio, performance security, bank guarantee, payment terms
    - `IctEntityExtractor.java` — users, language, architecture, tech stack, hosting, mobile, integrations
    - `StaffingEntityExtractor.java` — staff months, onsite, marking criteria
    - `SupportEntityExtractor.java` — training, support/maintenance, warranty
    - `EvaluationEntityExtractor.java` — criteria weights, eligibility summary, scope summary

**Context Window Chunking** (fixes AG-03)

- [ ] `LangChain4JTextSplitter.java` — wraps LangChain4J `DocumentSplitter` with 3500-token chunk size and 200-token
  overlap
- [ ] Splitting is done per section, not across the full document

**LLM Non-Determinism** (fixes AG-04)

- [ ] All LLM calls use `temperature: 0` (configured in `LlmAdapter`)
- [ ] Clause IDs: never from LLM. Always from `ClauseIdAssigner`
- [ ] Entity results: stored with job ID. Re-run creates new job ID, preserves old results

**Frontend**

- [ ] `EntityTable.tsx` — grouped display of all entity fields with confidence badges (green/yellow/red)
- [ ] `ResultPage.tsx` — add entity panel (right panel, tabs: General / Financial / ICT / Staffing / Evaluation)

---

### Sprint 5 — Table Extraction

**Delivers:** Tables extracted, linked to sections, with merged cells handled correctly.

**Table Extractor**

- [ ] `TableExtractor.java` (orchestrator, < 250 lines) — determines lattice or stream per table region, calls
  sub-extractors
- [ ] `LatticeTableExtractor.java` (fixes T-03):
    - Use PDFBox to detect horizontal + vertical lines on page
    - Build cell grid from line intersections
    - Detect spanning cells (empty internal intersections) → assign `rowspan`/`colspan`
    - Output: `TableGrid` with cell-object array `[{row, col, value, rowspan, colspan}]`
- [ ] `StreamTableExtractor.java` (fixes T-05):
    - Project text bounding boxes onto X axis
    - Find gaps > configurable threshold → column separators
    - Sort text by (column_index, y_position)
    - Fallback: if lattice finds < 4 lines → switch to stream
- [ ] `TableContinuationDetector.java` (fixes T-02):
    - After per-page extraction: detect table fragments that span pages
    - Signals: table at page-bottom + table at page-top next page + matching headers
    - Merge: deduplicate headers, re-index rows, merge into single `Table` object
- [ ] `TableSectionLinker.java` (fixes D-04):
    - For each table: find the section whose page range contains `table.pageStart`
    - Store `table.sectionId` and `table.clauseId` (nearest preceding clause on same page)
- [ ] Add `ExtractTablesNode.java` to LangGraph4J graph (was stub in Sprint 4)

**Frontend**

- [ ] `TableViewer.tsx` — renders tables from `RfpDocument.tables[]` with merged cell display
- [ ] Integrate into `ResultPage.tsx` tables tab

---

### Sprint 6 — OCR Integration (Python FastAPI Sidecar)

**Delivers:** Scanned pages are OCR'd. Mixed pages handled. Scanned tables reconstructed via LLM. OCR confidence
propagated.

**Python OCR Sidecar (full implementation)**

- [ ] `rfp-python-sidecar/ocr_service.py`:
    - `extract_page(image_bytes: bytes, lang: str = "eng+ben") → OcrResult`
    - Uses easyOCR for primary extraction (better Bangla accuracy)
    - Falls back to Tesseract for Latin-heavy pages (faster)
    - Returns: `{text, word_confidences: [{word, confidence, bbox}], page_confidence, word_count}`
- [ ] `rfp-python-sidecar/main.py`:
    - `POST /ocr/page` — body: `{image_base64, lang}`, returns `OcrResult`
    - `POST /ocr/page-with-layout` — body: `{image_base64}`, returns `{has_table, table_regions, text_regions}`
    - `GET /health`
- [ ] PDF-to-image: `pdf2image` at 300 DPI for scanned pages, 150 DPI for mixed pages

**Java OCR Sidecar Client** (fixes T-06)

- [ ] `OcrSidecarClient.java` (adapter) — Spring `@Component` using `RestClient`:
    - `extractPage(byte[] imageBytes, String lang) → OcrResult`
    - Configurable base URL: `app.sidecar.url=http://rfp-python-sidecar:8000`
    - Timeout: 60s per page (configurable)
    - On failure: throw `OcrUnavailableException` → page marked as `confidence: 0.0, method: "ocr_failed"`

**Scanned Page Extractor** (fixes T-08)

- [ ] `ScannedPageExtractor.java`:
    - Renders PDF page to PNG via `PDFRenderer` (PDFBox)
    - Sends to OCR sidecar
    - Propagates `page_confidence` into clause/section confidence fields
    - Tags extracted items with `extraction_method: "ocr"`

**Mixed-Page Handling** (fixes A-03)

- [ ] `MixedPageExtractor.java`:
    - Runs both `PdfDocumentLoader.loadPageBoundingBoxes()` AND OCR sidecar
    - Uses OCR sidecar's `/ocr/page-with-layout` to find image-dominant regions
    - Merges: text-layer content for digital regions, OCR content for image regions
    - Tags with `extraction_method: "text+ocr"`

**Scanned Table Reconstruction** (fixes T-04)

- [ ] `ScannedTableReconstructor.java`:
    - On SCANNED pages where OCR sidecar detects table layout: sends OCR text to LLM
    - LLM prompt: reconstruct table as `[[cell, cell], [cell, cell]]` 2D array
    - Converts to cell-object format with `rowspan: 1, colspan: 1`
    - Confidence = OCR page confidence × 0.8 (reconstruction penalty)
    - Tags with `extraction_method: "ocr_llm_reconstruct"`

**Multi-Column De-interleaving** (fixes T-01)

- [ ] `ColumnDetector.java`:
    - Projects text bounding boxes onto X axis
    - Finds large gap (> 15% page width) in X distribution → 2-column layout
    - Single-column: sort by y_position. Double-column: sort by (column_idx, y_position)
    - Logs `layout: single_column|double_column|unknown` per page

**Frontend**

- [ ] `ResultPage.tsx` — add page classification badge per page in extraction summary
- [ ] Show `extraction_method` tag on each clause/table card

---

### Sprint 7 — Repair Loop (LangGraph4J Conditional Edges)

**Delivers:** Low-confidence items are retried using deterministic strategies. Loop is bounded and auditable.

**Repair Loop Node** (fixes AG-01, AG-02)

- [ ] `RepairLoopNode.java` — implements `NodeAction<ExtractionState>`:
    - Pops first item from `state.lowConfidenceQueue`
    - Applies deterministic repair strategy (no LLM involved in routing):

| Component | Confidence < Threshold | Repair Action                                           |
|-----------|------------------------|---------------------------------------------------------|
| Section   | < 0.5                  | Try next `HeadingStrategy` in priority chain            |
| Table     | < 0.6                  | Switch lattice↔stream mode                              |
| Table     | < 0.6 (scanned)        | Try `ScannedTableReconstructor`                         |
| Entity    | < 0.5                  | Re-extract from wider context (2 sections instead of 1) |
| OCR Page  | < 0.6                  | Re-render at 300 DPI (up from 150) and re-OCR           |

- Increments `retryCount` for item. At 3 retries: mark `confidence: 0.0, method: "failed"`, move to
  `manualReviewRequired[]`
- Increments `totalRepairIterations`. At 20: set flag `repairExhausted = true`
- Conditional edge: if `lowConfidenceQueue` not empty AND `totalRepairIterations < 20` → back to REPAIR_LOOP. Else →
  RULE_PACK
- [ ] `RepairAuditService.java` — logs each repair attempt:
  `{component, componentId, attempt, strategy, beforeConfidence, afterConfidence, reason}`

**Score Confidence Node**

- [ ] `ScoreConfidenceNode.java`:
    - Per-entity: `1.0` if extracted and non-empty, `0.5` if extracted but low-quality signal, `0.0` if null
    - Per-section: aggregate from extraction method + heading strategy used
    - Per-table: from lattice/stream quality signals
    - `doc_completeness_score` = non-null critical entities / total critical entities
    - Populates `lowConfidenceQueue` with items below threshold (configurable, default 0.6)

**LLM-Assisted Section Fallback** (completes A-05)

- [ ] `LlmSectionSegmentStrategy.java` — fires ONLY if heuristic strategies found < 3 sections in > 20-page document:
    - Sends first 3 pages of text (≤ 3500 tokens) to LLM
    - Prompt: extract section structure, return `[{title, level, approximatePage}]`
    - Merges with heuristic findings; deduplicates by title similarity (Levenshtein < 0.3)

**Frontend**

- [ ] `JobStatusPage.tsx` — show repair events in real-time: "Retrying table on page 12 (attempt 2/3)"
- [ ] `AuditPanel.tsx` — expandable audit log per job showing retry history

---

### Sprint 8 — Rule Pack Infrastructure & ICT Rule Pack

**Delivers:** Extracted JSON is quality-checked. All 64 ICT rules run. Results in the JSON output.

**Rule Pack Infrastructure**

- [ ] `RulePackLoader.java` — scans `rules/` directory, validates each YAML file against `schema/rule-pack-schema-v1.json`,
  caches parsed rules, supports hot-reload (file watcher)
- [ ] `JmesPathEvaluator.java` — wraps `io.burt:jmespath-java`. Evaluates `condition` expression against RFP JSON.
  Returns truthy/falsy.
- [ ] `LlmJudgmentChecker.java` — for `check_type: semantic` rules: extracts `evidence_path` value from JSON, sends to
  `LlmAdapter.judgeSnippet()` with rule's `llm_prompt`. Handles timeout → SKIPPED. Handles parse failure → SKIPPED.
- [ ] `RulePackRunner.java` (< 250 lines) — iterates rules, dispatches to JMESPath or LLM checker, collects
  `RuleFinding[]`
- [ ] `RfpTypeClassifier.java` (fixes R-04) — classifies doc into `works|consultancy|ict|goods|unknown` from section
  titles + scope summary keywords. Selects appropriate rule pack. Falls back to running all packs if `unknown`.
- [ ] `RunRulePackNode.java` — LangGraph4J node that runs `RulePackRunner` and stores results in
  `ExtractionState.rulePackResults`

**ICT Rule Pack**

- [ ] `rules/bd-govt-ict-v1.yaml` — all 64 rules from the rule list above (structural + semantic)
- [ ] `rules/bd-govt-works-v1.yaml` — 30+ rules for works/civil contracts
- [ ] `rules/bd-govt-consultancy-v1.yaml` — 30+ rules for ToR/consultancy

**Rule Pack Tests** (fixes TEST-03)

- [ ] `RulePackRunnerTest.java` — for each structural rule: one test with JSON that PASSES, one with JSON that FAILS
- [ ] `RuleDslValidationTest.java` — asserts all YAML rule files parse without error

**Frontend**

- [ ] `RulePackResults.tsx` — grouped by severity, color-coded (red=FATAL, orange=HIGH, yellow=MEDIUM, blue=INFO).
  Expandable to show evidence clause.

---

### Sprint 9 — Additional Rule Packs + Hot Reload

**Delivers:** Works and consultancy packs complete. Rule packs hot-reload without service restart.

- [ ] Complete `bd-govt-works-v1.yaml` (30+ rules, covers: BoQ, performance bond, site requirements, contractor
  qualification, liquidated damages, retention money, extension of time)
- [ ] Complete `bd-govt-consultancy-v1.yaml` (30+ rules, covers: TOR objectives, reporting requirements, team
  composition, CV requirements, methodology evaluation, inception report, deliverable schedule)
- [ ] `bd-govt-goods-v1.yaml` (20+ rules, covers: specifications, inspection requirements, delivery schedule, warranty,
  incoterms)
- [ ] `RulePackHotReload.java` — `@Scheduled` every 30s, checks file modification timestamps, reloads changed packs
  without restart
- [ ] `GET /api/v1/admin/rule-packs` — lists loaded packs with version, rule count, load timestamp
- [ ] `POST /api/v1/admin/rule-packs/reload` — manual trigger for admin role
- [ ] Rule pack versioning: store `pack_id + pack_version` in every RFP result. Re-analysis with new pack is detectable.

---

### Sprint 10 — Bid Clarity Pack Artifacts

**Delivers:** Full Bid Clarity Pack generated and downloadable from the frontend.

**Clarification Questions DOCX** (fixes O-01)

- [ ] `ClarificationQuestionsTrigger.java` — defines explicit trigger rules:
    - Rule pack FAIL (FATAL/HIGH) → "Mandatory Clarification" question
    - Entity confidence < 0.5 on critical fields → "Confirmation" question
    - LLM judgment check `finding: true` → "Ambiguity" question
    - Cross-field contradiction → "Contradiction Resolution" question
- [ ] `ClarificationQuestionsGenerator.java`:
    - For each trigger: calls LLM with clause snippet + question-generation prompt
    - Output: `{questionText, clauseId, page, questionType, priority}`
    - Deduplication: same `clauseId + questionType` → keep highest priority
- [ ] `ClarificationQuestionsDocxWriter.java` — Apache POI XWPF + Freemarker. Format: formal RFI letter with numbered
  questions and source references `[Section X.Y, Page N]`

**Ambiguity & Missing-Info Register XLSX**

- [ ] `AmbiguityRegisterXlsxWriter.java` — Apache POI. Columns:
  `Issue ID | Severity | Category | Description | Source Clause | Page | Recommended Action`. Color-coded rows by
  severity. Sorted by severity descending.

**Compliance & Submission Checklist XLSX**

- [ ] `ComplianceChecklistXlsxWriter.java` — one row per mandatory requirement. Columns:
  `Requirement | Source Clause | Page | Mandatory? | Status`. Status column left blank for bid team.

**Risk & Assumptions Log XLSX**

- [ ] `RiskLogXlsxWriter.java` — columns: `Risk/Assumption | Source | Impact | Mitigation Suggestion | Owner`.
  Mitigation Suggestion = LLM-generated from clause snippet.

**Visual Audit Report HTML** (fixes O-04)

- [ ] `AuditReportGenerator.java` — Freemarker HTML template:
    - Page-by-page table: page, classification, extraction method, confidence (color bar), retry count
    - Summary stats: total pages, scanned, tables found, entities extracted, rule PASS/FAIL counts
    - Confidence heatmap CSS: green (≥0.8), yellow (0.5–0.8), red (<0.5)
    - Repair log section: what was retried, why, outcome
- [ ] Stored at `output/{jobId}/audit-report.html`

**Artifact Storage & Download**

- [ ] `ArtifactStorageService.java` — stores generated files to disk under `output/{jobId}/`
- [ ] `GET /api/v1/rfp/artifacts/{jobId}` — lists available artifacts with download URLs
- [ ] `GET /api/v1/rfp/artifacts/{jobId}/{filename}` — streams file download

**Frontend**

- [ ] `ArtifactDownload.tsx` — card per artifact: icon, filename, size, download button
- [ ] Integrate into `ResultPage.tsx` artifacts tab
- [ ] `AuditReportViewer.tsx` — embeds `audit-report.html` in an iframe

---

### Sprint 11 — Security, RBAC & Data Protection

**Delivers:** Production-ready security. Multi-user with role-based access.

**Spring Security RBAC** (fixes SEC-01)

- [ ] `SecurityConfig.java` — JWT-based authentication (`spring-boot-starter-oauth2-resource-server` or custom JWT
  filter)
- [ ] `UserRole` enum used end-to-end (`ANALYST`, `ADMIN`, `AUDITOR`) — no role `String` contracts in service/domain
  APIs
- [ ] Roles: `ROLE_ANALYST` (upload + view own docs), `ROLE_ADMIN` (view all + manage rule packs), `ROLE_AUDITOR`
  (view only)
- [ ] `@PreAuthorize` annotations on all service methods
- [ ] Document-level ownership: each job record stores `createdByUserId`. ANALYST can only access their own jobs. ADMIN
  and AUDITOR can access all.
- [ ] Public auth flow endpoints: `POST /api/v1/auth/signup`, `POST /api/v1/auth/login`,
  `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout`
- [ ] Refresh-token lifecycle: short-lived access token + persisted, revocable, rotating refresh token

**User Audit Trail** (fixes SEC-01)

- [ ] `UserAuditEvent.java` — `{userId, action, documentId, timestamp, ipAddress, outcome}`
- [ ] `UserAuditService.java` — saves to a dedicated `audit_events` PostgreSQL table
- [ ] AOP `@Aspect` intercepts all controller methods tagged `@Auditable` — logs automatically

**Encryption at Rest** (fixes SEC-01)

- [ ] `EncryptedFileStorageService.java` — wraps file writes with AES-256-GCM. Key from `app.storage.encryption-key` env
  var.
- [ ] Decrypts on read transparently. PDFs and output artifacts are encrypted at rest.

**Prompt Injection Filter** (fixes SEC-02)

- [ ] `PromptInjectionFilter.java` — applied before any text is sent to LLM:
    - Strips: `ignore previous instructions`, `you are now`, `disregard`, `system:`, `<|im_start|>`
    - Wraps content in delimiters: `<document_content>...</document_content>` in all LLM prompts
    - Logs and flags document if injection pattern detected

**Data Retention**

- [ ] `DataRetentionScheduler.java` — `@Scheduled` daily. Soft-deletes jobs + files older than `app.retention.days` (
  default 90). Hard-deletes after 7 more days. Logs to audit trail.

**Legacy Bangla Encoding Detector** (placeholder for Sprint 13)

- [ ] `BanglaEncodingDetector.java` — heuristic: if page has Bangla Unicode chars but many fall in `U+0041`–`U+007F`
  range (wrong encoding), flag as `ENCODING_SUSPECTED_LEGACY`
- [ ] On detection: abort job with error `ENCODING_UNSUPPORTED`, clear user message: "This document appears to use a
  legacy Bangla font encoding (SutonnyMJ/Bijoy). Please convert to Unicode Bangla and resubmit."

**Frontend**

- [ ] `SignupPage.tsx` + `LoginPage.tsx` — public signup and JWT login forms
- [ ] Route guards in React Router — redirect to login if no JWT
- [ ] Axios response interceptor refreshes access token on 401 once, then retries original request
- [ ] `AdminPage.tsx` — rule pack management, user list (ADMIN only)

---

### Sprint 12 — Deferred (Out of Scope in Current Baseline)

Sprint 12 operational hardening is deferred to wishlist (unit-test-only baseline; no Actuator, no mandatory
Prometheus/Micrometer deliverables).

---

### Sprint 13 (Future) — Full Bangla Support

Deferred. Bangla v1.0 gracefully rejects legacy-encoded documents. This sprint implements full support:

- [ ] B-01: Legacy encoding detection + transliteration mapping (SutonnyMJ → Unicode)
- [ ] B-02: Bangla NER for procurement entities (multilingual LLM evaluation: Aya, Qwen2.5, Gemma multilingual)
- [ ] B-03: Full Bangla heading pattern library expansion
- [ ] B-04: Evaluate and configure optimal multilingual model via `app.llm.provider=openrouter`, model =
  `google/gemma-3-27b-it` or similar

---

## Issue Resolution Traceability

| Issue IDs                                  | Sprint                                                      |
|--------------------------------------------|-------------------------------------------------------------|
| A-01 (Tika/PDFBox redundancy)              | Sprint 2                                                    |
| A-02 (no input validation)                 | Sprint 2                                                    |
| A-03 (mixed pages)                         | Sprint 6                                                    |
| A-04 (image_detect undefined)              | Sprint 2                                                    |
| A-05 (section segmenter fragile)           | Sprint 3 + Sprint 7                                         |
| A-06 (unstable clause IDs)                 | Sprint 3                                                    |
| T-01 (multi-column)                        | Sprint 6                                                    |
| T-02 (multi-page tables)                   | Sprint 5                                                    |
| T-03 (merged cells)                        | Sprint 5                                                    |
| T-04 (scanned tables)                      | Sprint 6                                                    |
| T-05 (stream mode tuning)                  | Sprint 5                                                    |
| T-06 (OCR engine)                          | Sprint 6                                                    |
| T-07 (legacy Bangla encoding)              | Sprint 11 (placeholder) + Sprint 13                         |
| T-08 (OCR confidence propagation)          | Sprint 6                                                    |
| T-09 (rule pack undefined)                 | Sprint 8                                                    |
| D-01 through D-08 (schema issues)          | Sprint 1 (schema defined)                                   |
| R-01 (rule DSL)                            | Sprint 1 (DSL defined) + Sprint 8 (implemented)             |
| R-02 (deterministic/LLM boundary)          | Sprint 8                                                    |
| R-03 (rule authorship)                     | Sprint 8 + Sprint 9                                         |
| R-04 (single rule pack for all types)      | Sprint 8 + Sprint 9                                         |
| AG-01 (agentic framing)                    | Sprint 4 (graph defined correctly)                          |
| AG-02 (repair loop termination)            | Sprint 7                                                    |
| AG-03 (context window)                     | Sprint 4                                                    |
| AG-04 (LLM non-determinism)                | Sprint 4                                                    |
| O-01 (clarification questions)             | Sprint 10                                                   |
| O-02 (artifact stack)                      | Sprint 1 (decision locked)                                  |
| O-03 (page references)                     | Sprint 10                                                   |
| O-04 (audit log format)                    | Sprint 10                                                   |
| B-01 through B-04                          | Sprint 13 (deferred)                                        |
| S-01 (Spring AI + LangGraph4J loop)        | Sprint 4                                                    |
| S-02 (async execution)                     | Sprint 2                                                    |
| S-03 (state persistence)                   | Sprint 2 + Sprint 4                                         |
| S-04 (LLM provider config)                 | Sprint 1                                                    |
| OP-01 (processing time SLA)                | Deferred to wishlist (unit-test-only baseline; no Actuator) |
| OP-02 (concurrency model)                  | Deferred to wishlist (unit-test-only baseline; no Actuator) |
| OP-03 (model version pinning)              | Deferred to wishlist (unit-test-only baseline; no Actuator) |
| OP-04 (monitoring)                         | Deferred to wishlist (unit-test-only baseline; no Actuator) |
| DIFF-01, DIFF-02 (differentiation framing) | Deferred to wishlist (unit-test-only baseline; no Actuator) |
| MVP-01, MVP-02, MVP-03 (scope)             | Resolved by sprint model                                    |
| SEC-01 (RBAC + encryption)                 | Sprint 11                                                   |
| SEC-02 (prompt injection)                  | Sprint 11                                                   |
| SEC-03 (security pitch)                    | Deferred to wishlist (unit-test-only baseline; no Actuator) |
| TEST-01 (ground truth)                     | Wishlist (`wishlist/001_wishlist.md`; non-blocking)         |
| TEST-02 (metrics definition)               | Sprint 3 (unit-level confidence and schema checks)          |
| TEST-03 (rule pack tests)                  | Sprint 8                                                    |
| TEST-04 (repair loop testability)          | Sprint 7 (deterministic routing makes it testable)          |

---

## Verification Plan (End-to-End, per Sprint)

Each sprint must pass before merging to `main`:

1. `mvn test` — unit tests pass
2. Static contract checks pass (schema files, prompt versions, rule DSL validation)
3. Upload a known fixture RFP via `POST /api/v1/rfp/submit`
4. Poll `GET /api/v1/rfp/status/{jobId}` until COMPLETED
5. `GET /api/v1/rfp/result/{jobId}` — validate response against `rfp-schema-v1.json`
6. Validate against deterministic fixture assertions (schema validity, required-field coverage, rule outcomes, and
   section/table structural checks) — no annotation dependency
7. **Sprint 8+**: verify rule pack fires FATAL on a document known to be missing a mandatory section
8. **Sprint 10+**: download artifacts; open DOCX/XLSX, verify clause references are correct page numbers
9. **Sprint 11+**: attempt access as wrong role → verify 403 returned
10. **Sprint 12+**: N/A (operational hardening deferred in current baseline)
