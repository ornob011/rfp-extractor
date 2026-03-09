# RFP Extractor - Claude Session Bootstrap

## Purpose

`CLAUDE.md` is the project-level session bootstrap (system-prompt equivalent) for Claude Code.

- Sprint docs define what to build.
- `CLAUDE.md` defines how to build and where to navigate.
- Do not read all sprint docs by default before coding.

Primary references:

- `docs/003_implementation-index.md` for sprint map and cross-sprint contracts.
- `docs/004_sprint-1.md` to `docs/014_sprint-11.md` for sprint implementation detail.
- `docs/002_plan.md` for architecture and engineering standards.

## Project in One Paragraph

Air-gapped document intelligence platform that converts Government of Bangladesh RFP/ToR PDFs into
structured `RfpDocument` JSON, then runs domain rule packs to produce Bid Clarity outputs.
Deployment is self-hosted and data must remain inside the controlled network.

## Session Boot Workflow (Mandatory)

When a new task arrives, follow this order:

1. If task is "Implement Sprint N":
    - Read `docs/003_implementation-index.md`.
    - Read only `docs/00X_sprint-N.md`.
    - Start implementation from that sprint's acceptance criteria and stubs.
2. If task is architecture/policy clarification:
    - Read `docs/002_plan.md` relevant section and then `docs/003_implementation-index.md`.
3. If task is bugfix/refactor:
    - Read affected code first, then only the sprint doc tied to that module area.
4. Ask questions only when requirements are ambiguous or contradictory.

## Task-to-Doc Routing

| Task Intent                    | First Doc                          | Second Doc                         |
|--------------------------------|------------------------------------|------------------------------------|
| Implement sprint deliverables  | `docs/003_implementation-index.md` | `docs/00X_sprint-N.md`             |
| Validate cross-sprint contract | `docs/003_implementation-index.md` | `CLAUDE.md`                        |
| Architecture decision          | `docs/002_plan.md`                 | `docs/003_implementation-index.md` |
| Enum/persistence convention    | `CLAUDE.md`                        | sprint doc being implemented       |
| Prompt handling                | `CLAUDE.md`                        | sprint doc + `/prompts/` files     |

## Module and Placement Map

```
rfp-core/     domain model, ports, enums, exceptions (no Spring beans)
rfp-service/  Spring Boot app: adapters, application services, config
```

Canonical package root: `com.dsi.rfp`

```
rfp-core:    .domain.model.*        entities, value objects, enums
             .domain.port.in.*      use-case input ports
             .domain.port.out.*     driven ports (repo, LLM, OCR, storage)
rfp-service: .adapter.rest.*        controllers, DTOs, mappers
             .adapter.persistence.* JPA entities, Spring Data repositories
             .adapter.llm.*         Spring AI ChatClient wrappers
             .adapter.ocr.*         RestClient integration to Python sidecar
             .application.service.* use-case implementations
             .config.*              Spring configuration beans
```

## Technology Stack

| Concern    | Choice                                                                                           |
|------------|--------------------------------------------------------------------------------------------------|
| Language   | Java 21, Spring Boot 3.5.11, Maven multi-module                                                  |
| Agent      | LangGraph4J `StateGraph`                                                                         |
| LLM        | Spring AI `ChatClient` via OpenRouter or Ollama                                                  |
| Database   | PostgreSQL + Spring Data JPA + Hibernate DDL auto; no Redis                                      |
| Resilience | Resilience4j retries, circuit breaker, rate limiter, timeout                                     |
| Frontend   | React 19, TypeScript strict, Tailwind v4, Vite 7, shadcn/ui, lucide-react, sonner, React Query 5 |
| OCR        | Python FastAPI sidecar (`easyOCR` + Tesseract) via Spring `RestClient`                           |
| Artifacts  | Apache POI + Freemarker                                                                          |

## Non-Negotiable Engineering Defaults

- Constructor injection only; `@Autowired` field injection is forbidden.
- Prefer Lombok for boilerplate reduction where appropriate.
- Max 250 lines per class, max 20 lines per method.
- Library-first development; do not hand-roll standard capabilities.
- No checked exceptions in domain/app layers; use typed runtime exceptions.
- `@Transactional` only in application service layer, never in controllers.
- No silent fallbacks for core dependencies (LLM/OCR/storage).
- Use design patterns when they provide clear value; avoid over-engineering for simple cases.
- Put arguments in separate lines in method signature.
- Never catch exception, use exception handler
- No `if/else` blocks or ternary operators. Guard clauses (single `if` with early
  return/throw) are permitted. Prefer `switch` expressions (Java 21), functional
  composition, or polymorphism.
- Use String.format() for string concatenation in Java (except for log statements); template literals in TypeScript;
  f-strings in Python.
- Use proper spacing and line breaks for readability.

## Prompt Governance (Hard Rule, Merge-Blocking)

All prompts must be file-based resources. Hardcoding prompt text in source code is forbidden.

- Prompt files live under `/prompts/` with versioned names: `{name}-v{n}.md`.
- This rule applies to all prompt types:
    - system prompts,
    - developer/instruction prompts,
    - user-template prompts,
    - repair prompts,
    - evaluator/judge prompts,
    - question-generation prompts.
- Load prompts via classpath/resource loader; do not build long prompt strings inline.
- Prompt version must be traceable in code/config/logging and test fixtures when used.

Forbidden patterns:

- Multi-line string literals containing full prompts in Java/TypeScript/Python.
- Service-level string concatenation that embeds policy instructions directly.
- Hidden emergency prompt fallbacks in code paths.

If a temporary fallback is introduced for debugging, it must not be merged.

## Database and JPA Contract

- PostgreSQL is the only persistence store.
- All JPA entities live in `rfp-service/adapter/persistence/entity/`.
- All entities extend `BaseEntity` (UUID PK + audit timestamps).
- Always use `@Enumerated(EnumType.STRING)`; `ORDINAL` is forbidden.
- JSONB fields use `@Column(columnDefinition = "JSONB")`.
- Hibernate profile policy: `ddl-auto=update` in dev, `validate` in production.

Baseline Sprint 1 tables (do not redefine with incompatible contract changes):

| Entity class           | Table               |
|------------------------|---------------------|
| `UserEntity`           | `users`             |
| `DocumentEntity`       | `documents`         |
| `AnalysisJobEntity`    | `analysis_jobs`     |
| `AnalysisResultEntity` | `analysis_results`  |
| `AgentExecutionEntity` | `agent_executions`  |
| `AgentStepEntity`      | `agent_steps`       |
| `UserAuditEntity`      | `user_audit_events` |

## Enum and Serialization Contract

- Closed vocabularies must be enums in `rfp-core/domain/model/`.
- Do not replace enum contracts with free-form strings.
- Wire-format mapping is allowed via enum serializers (`@JsonValue`, `@JsonCreator`), but internal model types remain
  enums.

Canonical enums include:
`LlmProvider`, `HealthStatus`, `SidecarReachability`, `AnalysisStatus`, `ExecutionStatus`,
`TerminationReason`, `AgentStepType`, `StepOutcome`, `PageClassification`, `PageExtractionMethod`,
`TableType`, `TableProvenance`, `RepairStrategy`, `RepairComponentType`, `ConfidenceSource`,
`RepairOutcome`, `UserRole`.

## Reliability Contract (Cross-Sprint)

```
LLM calls: 30 s timeout | 3 retries (exponential backoff with jitter) | circuit breaker 50%/10 | 60 rpm
           Every response validated against JSON Schema before use
           On failure -> throw LlmUnavailableException (never null, never silent)
OCR calls: 60 s/page | 2 retries
           On failure -> throw OcrUnavailableException (confidence 0.0)
All calls: log provider, model, token in/out, latency, success/failure
```

Operational rule: failure must be explicit and observable; do not degrade silently.

## Sprint Navigation

Read [docs/003_implementation-index.md](./docs/003_implementation-index.md) first,
then the sprint file:

| Sprint | File                    | Theme                                   |
|--------|-------------------------|-----------------------------------------|
| 1      | `docs/004_sprint-1.md`  | Foundation, LLM infra, full DB schema   |
| 2      | `docs/005_sprint-2.md`  | Document ingestion, page classification |
| 3      | `docs/006_sprint-3.md`  | Section segmentation, clause IDs        |
| 4      | `docs/007_sprint-4.md`  | LangGraph4J agent, entity extraction    |
| 5      | `docs/008_sprint-5.md`  | Table extraction                        |
| 6      | `docs/009_sprint-6.md`  | OCR and mixed pages                     |
| 7      | `docs/010_sprint-7.md`  | Repair loop                             |
| 8      | `docs/011_sprint-8.md`  | Rule pack engine and ICT rules          |
| 9      | `docs/012_sprint-9.md`  | Additional rule packs and hot reload    |
| 10     | `docs/013_sprint-10.md` | Bid Clarity Pack artifacts              |
| 11     | `docs/014_sprint-11.md` | Security and RBAC                       |

## Definition of Done (Per Sprint)

- Sprint tasks implemented and backend `mvn test` passes with zero failures.
- Every public method in `application/service/` and `adapter/` has at least one JUnit 5 test.
- No class over 250 lines and no method over 20 lines.
- No `@Autowired` field injection.
- New config keys documented in docs.
- Prompt changes include file versioning, loader integration, and tests.

Frontend test coverage is optional unless a sprint explicitly requires it.

## What This File Must Not Contain

- No long sprint implementation checklists.
- No large code snippets copied from sprint docs.
- No competing architecture variants that conflict with sprint docs.
- No speculative future roadmap items presented as active requirements.

## Consistency Checklist for Future Edits

When editing this file, verify:

- Package root remains consistent with sprint docs and generated stubs.
- No Redis references are reintroduced.
- Prompt rules still enforce file-based versioned prompts only.
- Enum examples and names
  match [docs/003_implementation-index.md](./docs/003_implementation-index.md).
- Sprint links point to existing files only.
