# Implementation Index — Agentic Govt RFP Extraction + Quality Gate

**Project:** DSI RFP Extractor
**Version:** 1.0
**Date:** 2026-03-01
**Sprint Cadence:** 2-week sprints
**Total Sprints:** 12 (+ separate deferred backlog track)

---

## Executive Summary

DSI is building a self-hosted, air-gapped document intelligence system that converts messy Government of Bangladesh (
GOB) RFP/ToR PDFs into a verified, structured `RfpDocument` JSON, then runs a domain-specific rule pack to produce a Bid
Clarity Pack (DOCX + XLSX artifacts).

The system's competitive advantages over off-the-shelf tools (AWS Textract, Azure Document Intelligence, etc.) are:

1. **Air-gapped / self-hosted** — GOB contract documents never leave DSI's network. No commercial SaaS tool (AWS
   Textract, Azure Document Intelligence, Google Document AI) can match this compliance guarantee for government
   procurement work.
2. **152 GOB-specific rules on structured JSON** — 64 ICT + 33 Works + 33 Consultancy + 22 Goods rules, derived from PPR
   2008 / CPTU standards. Rules run on extracted JSON (not raw text): deterministic, fast, auditable. No off-the-shelf
   tool ships this domain knowledge.
3. **Adaptive extraction pipeline** — three-class page classification (DIGITAL / SCANNED / MIXED), six heading-strategy
   section segmentation, table extraction with merged-cell support, scanned-table LLM reconstruction. Handles real-world
   GOB PDF chaos, not just clean digital documents.
4. **LLM-augmented quality gate with page-level evidence** — bounded, deterministic repair loop (no LLM re-planning).
   Every extracted fact carries a clause ID and source page number. Audit trail is human-readable HTML, not a raw JSON
   file.

---

## Key Architectural Decisions

| Concern          | Decision                                                                                                                           |
|------------------|------------------------------------------------------------------------------------------------------------------------------------|
| Backend          | Spring Boot 3.5.11, Java 21, Maven multi-module (`rfp-core` + `rfp-service`)                                                       |
| LLM (extraction) | OpenRouter → `google/gemini-2.0-flash-001` (fast, cheap per-chunk extraction)                                                      |
| LLM (judgment)   | OpenRouter → `google/gemini-2.5-pro-preview-06-05` (complex semantic checks)                                                       |
| LLM config       | `app.llm.provider=openrouter\|ollama` in `application.properties` — switchable without code change                                 |
| LLM framework    | Spring AI `ChatClient` (abstraction) + LangChain4J (text splitters, doc loaders)                                                   |
| Agent graph      | LangGraph4J `StateGraph` — VALIDATE→CLASSIFY→EXTRACT→SEGMENT→TABLES→ENTITIES→SCORE→REPAIR→RULES→FINALIZE                           |
| OCR              | Python FastAPI sidecar (easyOCR + Tesseract) — Java calls via Spring `RestClient`                                                  |
| Database         | PostgreSQL (Spring Data JPA + Hibernate). pgvector for vector similarity if needed.                                                |
| Job state        | PostgreSQL via JPA (`AnalysisJobEntity`, `AgentExecutionEntity`, `AgentStepEntity`) — permanent, queryable, no TTL. Redis removed. |
| Reliability      | Resilience4j: retry (exp backoff + jitter), circuit breaker, rate limiter, timeouts on all LLM and OCR calls                       |
| Artifacts        | Apache POI (XLSX + DOCX) + Freemarker templates                                                                                    |
| Frontend         | React 19, TypeScript strict, Tailwind v4, Vite 7, React Router 7, React Query 5, shadcn/ui (Radix UI), lucide-react, sonner        |
| Architecture     | Hexagonal (Ports & Adapters): `domain/` ← `application/service/` ← `adapter/`                                                      |
| Code quality     | Max 250 lines/service, max 20 lines/method, constructor injection, Lombok everywhere, library-first                                |
| Prompts          | `/prompts/{name}-v{version}.md` — versioned, model-annotated, snapshot-tested                                                      |

---

## Canonical Enums

Use these enum contracts consistently across sprints and APIs. Do not reintroduce free-form `String` fields for these
closed vocabularies.

| Enum                   | Values                                                                                                                      | Primary Scope                       |
|------------------------|-----------------------------------------------------------------------------------------------------------------------------|-------------------------------------|
| `LlmProvider`          | `OPENROUTER`, `OLLAMA`                                                                                                      | Provider selection + health payload |
| `HealthStatus`         | `UP`, `DEGRADED`                                                                                                            | `GET /api/v1/health`                |
| `SidecarReachability`  | `REACHABLE`, `UNREACHABLE`                                                                                                  | `GET /api/v1/health`                |
| `JobStatus`            | `QUEUED`, `RUNNING`, `COMPLETED`, `FAILED`, `PARTIAL`                                                                       | Job lifecycle                       |
| `PageClassification`   | `DIGITAL`, `SCANNED`, `MIXED`                                                                                               | Page-level routing/output           |
| `PageExtractionMethod` | `TEXT_LAYER`, `OCR`, `TEXT_PLUS_OCR`, `OCR_LLM_RECONSTRUCT`, `OCR_FAILED`                                                   | Page-detail reporting               |
| `TableType`            | `DELIVERABLES`, `EVALUATION`, `PAYMENT`, `STAFFING`, `SCHEDULE`, `OTHER`                                                    | Table semantics                     |
| `TableProvenance`      | `DIGITAL`, `SCANNED`, `MIXED`                                                                                               | Table origin metadata               |
| `RepairStrategy`       | `RETRY_SECTION_SEGMENTATION`, `SWITCH_TABLE_MODE`, `RETRY_SCANNED_TABLE_OCR_AT_HIGHER_DPI`, `WIDEN_ENTITY_CONTEXT`, `NO_OP` | Repair routing                      |
| `RepairComponentType`  | `SECTION`, `TABLE`, `ENTITY`                                                                                                | Repair candidate typing             |
| `ConfidenceSource`     | `HEADING_STYLE`, `BOOKMARK`, `LATTICE`, `STREAM`, `OCR_LLM_RECONSTRUCT`, `LLM`, `UNKNOWN`                                   | Repair scoring source               |
| `RepairOutcome`        | `IMPROVED`, `NOT_IMPROVED`, `MAX_RETRIES`                                                                                   | Status repair events                |
| `UserRole`             | `ANALYST`, `ADMIN`, `AUDITOR`                                                                                               | Security/RBAC                       |

Serialization note:

- API payloads may expose lowercase or specific wire values (for example `openrouter`, `reachable`) via enum
  serializers (`@JsonValue`). Keep enum types in code, and map wire-format at the serialization boundary.

---

## Critical Risks and Mitigations

| # | Risk                                                                                                                  | Likelihood | Impact | Mitigation                                                                                                                                                                 |
|---|-----------------------------------------------------------------------------------------------------------------------|------------|--------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1 | **OpenRouter rate limits / outages** — Gemini via OpenRouter has per-minute token limits and occasional 503s          | High       | High   | Resilience4j circuit breaker + retry with jitter. Ollama fallback path always working. Implement in Sprint 1 before any extraction code.                                   |
| 2 | **LLM non-determinism corrupts clause IDs** — LLM re-running on same doc produces different section/clause boundaries | Medium     | High   | Clause IDs are assigned by `ClauseIdAssigner` (deterministic composite key), NEVER by the LLM. LLM only assigns tags and extracts entity values.                           |
| 3 | **PDFBox fails on corrupt / XFA / encrypted PDFs** — GOB RFPs arrive in the wild                                      | High       | Medium | `DocumentValidationService` runs before any processing. Password-protected, XFA, corrupt, and oversized files rejected with structured error response, not silent failure. |
| 4 | **Table extraction quality on scanned tables** — Camelot/Tabula cannot process image pages                            | High       | Medium | Scanned table path: OCR → LLM reconstruction (Sprint 6). Confidence tagged `ocr_llm_reconstruct`. Repair loop can retry at higher DPI.                                     |
| 5 | **Repair loop runaway** — low-confidence items never improve and loop endlessly                                       | Low        | High   | Hard stop: max 3 retries per component, 20 total iterations per document. Repair routing is a static decision table (no LLM), so it always terminates.                     |

---

## Project-Wide Definition of Done

A sprint is **Done** when ALL of the following are true:

- [ ] All tasks in the sprint's Work Breakdown are implemented
- [ ] Every public method in `application/service/` and `adapter/` has at least one JUnit 5 unit test (Mockito mocked
  dependencies)
- [ ] `mvn test` passes on the main branch with zero failures
- [ ] Any demo script remains non-blocking and informational only
- [ ] No class > 250 lines, no method > 20 lines
- [ ] No `@Autowired` field injection — constructor injection everywhere
- [ ] All new configuration keys documented in `docs/configuration.md`
- [ ] Sprint file updated with actual outcomes in `§7 Notes`

---

## Sprint Overview Table

| Sprint | Prefix | Theme                                            | Primary Deliverables                                                                                                                                                                                                                                                  | Entry Criteria | Exit Criteria                                                                                                                            | Demo                                                                   |
|--------|--------|--------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------|------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------|
| 1      | `004`  | **Foundation & LLM Infrastructure** ✅            | Maven skeleton, LLM provider abstraction (OpenRouter/Ollama), Resilience4j reliability layer, Python OCR sidecar skeleton, React skeleton, Docker Compose; **complete PostgreSQL DB schema** (all entities, enums, repositories — Redis removed)                      | Empty repo     | `GET /api/v1/health` returns provider info; LLM call succeeds via `LlmAdapter`; all 7 JPA tables created by Hibernate DDL auto           | Call LLM via endpoint, see structured response; verify tables in psql  |
| 2      | `005`  | **Document Ingestion & Page Classification** ✅   | File upload API, input validation (encrypted/XFA/corrupt), PDFBox unified loader, 3-class page classifier, async job infrastructure, `AnalysisJobRepository` job state (PostgreSQL)                                                                                   | Sprint 1 done  | Upload a real PDF, get job ID, poll status, see per-page classification                                                                  | Upload RFP, show job status with DIGITAL/SCANNED/MIXED per page        |
| 3      | `006`  | **Section Segmentation & Clause IDs** ✅          | 6-strategy `SectionSegmenter` (Bookmark, HeadingStyle, Numbered, Bangla, FontSize, AllCaps), TOC detector, deterministic `ClauseIdAssigner`, JSON Schema validator, fixture-based evaluator                                                                           | Sprint 2 done  | Section tree extracted with stable IDs; fixture-based section checks and schema tests pass                                               | Show section hierarchy tree for a real RFP in React UI                 |
| 4      | `007`  | **LangGraph4J Agent + Full Entity Extraction** ✅ | Full `ExtractionGraph` (10 nodes + conditional FLAG_MANUAL_REVIEW branch), all 45 entity fields extracted, context chunking, structured LLM output, JSON Schema conformance                                                                                           | Sprint 3 done  | Full RFP JSON produced, schema-valid, all 45 fields attempted; deadline accuracy > 90%                                                   | Submit RFP, receive populated JSON with confidence scores              |
| 5      | `008`  | **Table Extraction**                             | Lattice + stream table extractors, merged-cell support, multi-page merge, table-section linker                                                                                                                                                                        | Sprint 4 done  | Tables extracted with correct cell-object format on deterministic fixtures                                                               | Show table with merged cells rendered in React                         |
| 6      | `009`  | **OCR & Mixed Pages**                            | Python FastAPI OCR (full easyOCR), Java OCR client, mixed-page merger, scanned table LLM reconstruction, multi-column de-interleaving                                                                                                                                 | Sprint 5 done  | Scanned pages extracted; OCR confidence propagated; mixed pages handled                                                                  | Process a fully scanned RFP; show extracted text + confidence per page |
| 7      | `010`  | **Repair Loop**                                  | Repair node in LangGraph4J (deterministic strategy table), bounded iterations, LLM section fallback, repair audit trail                                                                                                                                               | Sprint 6 done  | Low-confidence items retried; hard stop at 20 iterations; audit log shows retry events                                                   | Inject artificially degraded doc; show repair attempts in UI           |
| 8      | `011`  | **Rule Pack Engine & ICT Rules**                 | `RulePackRunner` (JMESPath + LLM judgment), `RfpTypeClassifier`, `bd-govt-ict-v1.yaml` (64 rules), rule test suite                                                                                                                                                    | Sprint 7 done  | All 64 ICT rules run; FATAL rules fire on docs missing mandatory fields                                                                  | Show PASS/FAIL rule results colour-coded by severity                   |
| 9      | `012`  | **Additional Rule Packs**                        | Works, consultancy, goods rule packs; admin API for rule pack management                                                                                                                                                                                              | Sprint 8 done  | Works/consultancy/goods rules run on respective doc types; hot-reload verified                                                           | Edit a rule YAML, reload without restart, see new result               |
| 10     | `013`  | **Bid Clarity Pack Artifacts**                   | Clarification Questions DOCX, Ambiguity Register XLSX, Compliance Checklist XLSX, Risk Log XLSX, HTML audit report, artifact download API                                                                                                                             | Sprint 9 done  | All 5 artifacts generated and downloadable; clause references correct                                                                    | Download all 5 artifacts; open DOCX, verify page references            |
| 11     | `014`  | **Security & RBAC**                              | Spring Security layer on existing `UserEntity` (Sprint 1): JWT auth, `JpaUserDetailsService`, BCrypt, RBAC (ANALYST/ADMIN/AUDITOR), per-document ownership, AES-256 encryption at rest, data retention (PostgreSQL), prompt injection filter, legacy Bangla rejection | Sprint 10 done | Signup/login/refresh/logout flow works; ANALYST cannot access another user's doc; encryption verified; rejection error for legacy Bangla | Demo role-based access and signup/login flow                           |

---

## File Links

| File                                 | Sprint    | Theme                                      |
|--------------------------------------|-----------|--------------------------------------------|
| [004_sprint-1.md](004_sprint-1.md)   | Sprint 1  | Foundation & LLM Infrastructure            |
| [005_sprint-2.md](005_sprint-2.md)   | Sprint 2  | Document Ingestion & Page Classification   |
| [006_sprint-3.md](006_sprint-3.md)   | Sprint 3  | Section Segmentation & Clause IDs          |
| [007_sprint-4.md](007_sprint-4.md)   | Sprint 4  | LangGraph4J Agent + Full Entity Extraction |
| [008_sprint-5.md](008_sprint-5.md)   | Sprint 5  | Table Extraction                           |
| [009_sprint-6.md](009_sprint-6.md)   | Sprint 6  | OCR & Mixed Pages                          |
| [010_sprint-7.md](010_sprint-7.md)   | Sprint 7  | Repair Loop                                |
| [011_sprint-8.md](011_sprint-8.md)   | Sprint 8  | Rule Pack Engine & ICT Rules               |
| [012_sprint-9.md](012_sprint-9.md)   | Sprint 9  | Additional Rule Packs & Hot Reload         |
| [013_sprint-10.md](013_sprint-10.md) | Sprint 10 | Bid Clarity Pack Artifacts                 |
| [014_sprint-11.md](014_sprint-11.md) | Sprint 11 | Security & RBAC                            |

---

## Cross-Sprint Reliability Contract

These must be implemented in **Sprint 1** and respected by every subsequent sprint:

```
LLM calls:
  - Timeout:         30s per call (configurable: app.llm.timeout-seconds)
  - Retry:           3 attempts, exponential backoff starting 1s, jitter ±30%
  - Circuit breaker: opens after 50% failure rate over 10 calls, stays open 30s
  - Rate limit:      60 calls/min (configurable: app.llm.rate-limit-per-minute)
  - JSON validation: every LLM response validated against expected schema before use
  - On failure:      throw LlmUnavailableException — never return null or silently skip

OCR sidecar calls:
  - Timeout:         60s per page
  - Retry:           2 attempts, 2s wait
  - On failure:      throw OcrUnavailableException — mark page confidence 0.0

All external calls:
  - Idempotency:     job ID passed as correlation header on all calls
  - Logging:         every call logs provider, model, tokens (in/out), latency, success/failure
```

---

## Deployment Tiers

The system ships identical code for both modes; provider switches via `app.llm.provider` in `application.properties`.

| Mode                | LLM Provider                            | GPU Requirement                                                                                                                    | Data Boundary                                              | Use Case                  |
|---------------------|-----------------------------------------|------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------|---------------------------|
| **Cloud-connected** | OpenRouter (Gemini 2.0 Flash + 2.5 Pro) | None                                                                                                                               | Data processed under OpenRouter DPA; does not reach Google | Pilot / evaluation        |
| **Air-gapped**      | Ollama on-premise                       | Min: 1× GPU 24 GB VRAM (Llama 3.1 8B, adequate for extraction). Recommended: 2× A100 80 GB (Llama 3.1 70B, high-accuracy judgment) | Data never leaves client premises                          | GOB production deployment |

The default Ollama model (`llama3.1:8b`) handles structured extraction reliably. The judgment model (`llama3.1:70b`) is
used only for the 9 ICT semantic rules and 8 semantic rules across other packs — approximately 11% of all rule
evaluations.

---

## Prompt Management

All LLM prompts live in `/prompts/` with versioned filenames:

```
/prompts/
  entity-general-v1.md
  entity-financial-v1.md
  entity-ict-v1.md
  entity-staffing-v1.md
  entity-support-v1.md
  entity-evaluation-v1.md
  section-segmentation-fallback-v1.md
  scanned-table-reconstruction-v1.md
  rule-judgment-v1.md
  clarification-question-gen-v1.md
  risk-mitigation-suggestion-v1.md
```

Each prompt file header:

```markdown
---
id: entity-general
version: 1.0.0
model: google/gemini-2.0-flash-001
max_tokens: 2048
temperature: 0.0
---
```

Prompt changes require a version bump and a snapshot test update.

---

## Ground Truth Data Requirements (Deferred, Non-Blocking)

Optional benchmark track for real-document accuracy reporting:

| Category             | Count  | Required Characteristics                      |
|----------------------|--------|-----------------------------------------------|
| ICT procurement      | 5      | At least 2 with embedded tables; 1 scanned    |
| Works/construction   | 4      | At least 1 multi-page table; 1 Bangla Unicode |
| Consultancy / ToR    | 3      | At least 1 with TOC; 1 scanned pages          |
| Goods procurement    | 2      | Basic; used for rule pack type detection      |
| Mixed Bangla/English | 1      | Unicode Bangla only (no legacy encoding)      |
| **Total**            | **15** |                                               |

Each annotation file at `testdata/ground-truth/{doc-id}.json` should contain:

- Correct section boundaries (title, page range, level)
- Correct entity values for all fields present in the document
- Correct table structure (first table only, minimum)
- Expected rule pack findings (which rules should FAIL)
