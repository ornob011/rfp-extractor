# Agentic Govt RFP Extraction + Quality Gate — Deep-Dive Research & Issue Report

**Author:** Research Analysis
**Date:** 2026-03-01
**Status:** First Draft — Definitive Pre-Implementation Reference
**Stack Context:** Java, Spring Boot, Spring AI (DSI internal constraint)

---

## Table of Contents

1. [Proposal Mechanics — How the System Actually Works](#1-proposal-mechanics)
2. [Architecture Decomposition](#2-architecture-decomposition)
3. [Tool-Layer Analysis](#3-tool-layer-analysis)
4. [Data Model Analysis](#4-data-model-analysis)
5. [Rule Pack Architecture](#5-rule-pack-architecture)
6. [Agent Design Critique](#6-agent-design-critique)
7. [Output Layer Analysis](#7-output-layer-analysis)
8. [Bangla-English Mixed Document Problem](#8-bangla-english-mixed-document-problem)
9. [Spring AI Feasibility Gaps](#9-spring-ai-feasibility-gaps)
10. [Operational and Self-Hosting Risks](#10-operational-and-self-hosting-risks)
11. [Differentiation Risk vs. Off-the-Shelf Tools](#11-differentiation-risk)
12. [MVP Scope Assessment](#12-mvp-scope-assessment)
13. [Security and Compliance Gaps](#13-security-and-compliance-gaps)
14. [Testing and Validation Blindspot](#14-testing-and-validation-blindspot)
15. [Itemized Issue Register](#15-itemized-issue-register)
16. [Recommended Mitigations and Architectural Corrections](#16-recommended-mitigations)

---

## 1. Proposal Mechanics

### What the System Proposes to Do

The system is a document intelligence pipeline with an LLM-driven orchestration layer positioned as the "agent." The
pipeline receives a government RFP PDF/DOCX, runs a multi-stage extraction process, verifies its own output, and
produces both a machine-readable JSON and human-readable bid artifacts.

### Execution Flow (as proposed)

```
Input (PDF/DOCX)
      │
      ▼
[1. Initial Characterization]
      │  tika_extract()       → full text + metadata
      │  image_detect()       → identify scanned pages
      │  pdf_layout_extract() → per-page layout blocks
      │
      ▼
[2. Adaptive Per-Page Strategy]
      │  For digital pages → text/layout path
      │  For scanned pages → ocr_page() path
      │
      ▼
[3. Structural Segmentation]
      │  section_segmenter() → headings + TOC inference
      │  clause_id_assigner() → stable IDs + page anchors
      │
      ▼
[4. Table Extraction]
      │  table_extract(mode=lattice) → structured grids
      │  table_extract(mode=stream)  → if lattice fails
      │
      ▼
[5. Entity Extraction]
      │  (LLM pass) → deadlines, amounts, penalties, contacts, SLAs
      │
      ▼
[6. Verification Loop]
      │  validator() → schema checks + cross-checks
      │  Confidence scoring per clause/section/table
      │  If confidence < threshold → re-plan → retry tools → repeat
      │
      ▼
[7. Rule Pack]
      │  rulepack_runner() → deterministic + LLM judgment checks
      │  On structured JSON, not raw text
      │
      ▼
[8. Artifact Generation]
      │  artifact_writer() → DOCX + XLSX outputs
      │
      ▼
Outputs: RFP JSON + Bid Clarity Pack + Audit Log
```

### Where the "Agentic" Claim Lives

The proposal argues the system is agentic because the LLM makes decisions about:

- Which extraction tool to use per page
- When to trigger OCR vs. text-layer parsing
- When confidence is insufficient and re-planning is needed
- Which table extraction mode to try

This is the core claim to interrogate, and it is where the most significant vulnerabilities exist.

---

## 2. Architecture Decomposition

### Layer 1: Input Ingestion

**What it does:** Accepts PDF/DOCX, runs Tika + image detection + layout extraction in parallel.

**Critical Issues:**

**[A-01] Tika and PDFBox are not parallel, independent tools.**
Apache Tika's PDF parser is PDFBox internally. When you call `tika_extract()` and then `pdf_layout_extract()` (
PDFBox-based), you are running PDFBox twice over the same document. This produces:

- Redundant CPU/memory usage
- Potentially inconsistent outputs (Tika post-processes PDFBox output; raw PDFBox differs)
- A false sense of "two independent views" when the data source is the same library

**[A-02] No input validation or sanitization layer.**
Government PDFs arrive in the wild. They may be:

- Password-protected (common in govt docs)
- Corrupted or partially downloaded
- PDF/A, PDF/X, or XFA form-based (completely different internal structure)
- Embedded packages (ZIP inside PDF, which Tika can partially unpack)
- Excessively large (500+ pages common in multi-annex RFPs)

None of these edge cases are addressed. A corrupted PDF fed into the pipeline will produce silently wrong outputs, not
errors.

### Layer 2: Adaptive Per-Page Strategy

**What it does:** Classify each page as digital or scanned; route accordingly.

**Critical Issues:**

**[A-03] "Mixed pages" are unaddressed and common.**
Bangladeshi government documents routinely contain:

- Digital text with a scanned signature block on the same page
- Printed-and-scanned text with an embedded digital watermark
- Pages where the main body is digital but all table cells are images (copy-protected)

The binary "digital vs. scanned" classification has no "mixed" state. A page classified as "digital" that has a scanned
table in it will miss that table entirely.

**[A-04] image_detect() is not defined.**
What constitutes a "scanned page"? Approaches include:

- Checking if the PDF page has no text layer (simple but wrong for scanned docs with OCR already applied)
- Checking raster image coverage percentage (better but needs threshold calibration)
- Checking text density vs. image density ratio

Without specifying the detection method, this tool is a black box with unknown failure modes.

### Layer 3: Structural Segmentation

**What it does:** Identify sections, headings, and assign clause IDs.

**Critical Issues:**

**[A-05] section_segmenter() heuristics will fail on a non-trivial percentage of real Bangladeshi govt RFPs.**
Real documents use:

- `PART – II : Technical Specifications` (dash and colon as separator)
- `Section-3: Qualification Requirements` (mixed case, hyphen)
- Bangla headings with no Latin structure at all
- Tables-as-headings (major section title in a single-cell table)
- ALL CAPS text as headings with no heading style (just font size)
- Numbered headings that reset per section (`1.` in Part I and `1.` again in Part II)

A heuristic-based segmenter without a trained classifier will produce hierarchically wrong outputs. Wrong segmentation
means wrong clause IDs, which means the rule pack runs on incorrectly bounded text.

**[A-06] Stable clause IDs are not stable across re-runs.**
If the document is re-processed after amendment (re-issued RFP), how are clause IDs maintained? The proposal describes
`clause_id_assigner()` but gives no ID generation scheme. Options (and their failure modes):

- Sequential integers: Break on re-run if extraction order changes
- Page-offset hash: Unstable if the document shifts pagination
- Content hash: Two identical paragraphs get the same ID (collision)

This is a data integrity gap.

---

## 3. Tool-Layer Analysis

### tika_extract()

**Strength:** Robust text extraction for digital PDFs. Handles DOCX natively.

**Weakness:**

- Will not extract text from image-only PDFs (returns empty or metadata only)
- Font encoding issues: Older PDFs with custom CMaps (common in scanned-then-OCR'd Bangla docs) produce garbled text
- No coordinate/position information (can't tell where on the page text appears)
- Metadata quality from GOB documents is typically poor (wrong dates, wrong titles, empty author)

### pdf_layout_extract()

**Strength:** Provides bounding boxes and positional data. Can detect columns, sidebars, headers/footers.

**Weakness:**

- PDFBox layout analysis is notoriously unreliable for documents with non-standard column layouts
- Multi-column layouts (common in formal govt documents) produce interleaved text that must be de-interleaved
- No open-source Java library does reliable multi-column re-ordering; this is a known hard problem

**[T-01] There is no de-interleaving strategy mentioned for multi-column layouts.**

### table_extract()

**Strength:** The "stream vs. lattice" distinction is correct — lattice works on tables with explicit borders, stream
works on whitespace-aligned columns.

**Weakness (critical):**

**[T-02] Multi-page table spanning is not addressed.**
A deliverables table that spans 3 pages will be extracted as 3 separate table fragments. Merging them requires:

- Detecting table continuation ("Table continued on next page" or visual cues)
- Header row deduplication across fragments
- Row re-indexing across fragments

This is a complex sub-problem that neither Camelot nor Tabula handles well. The proposal has no answer for it.

**[T-03] Merged cells destroy table structure.**
Rowspan/colspan in PDF tables (very common in evaluation criteria matrices) produce incorrect grid output from Camelot.
A cell spanning 3 rows is extracted as if it belongs only to the first row; the remaining rows have empty columns. This
produces malformed data downstream.

**[T-04] Scanned tables cannot be processed by Camelot/Tabula.**
If `image_detect()` classifies a page as scanned, `table_extract()` cannot run on it. But `ocr_page()` produces a text
string, not a structured table. The pipeline has no path from "scanned table page" → "structured table grid." This is a
critical gap — many GOB RFPs are entirely scanned.

**[T-05] Stream mode requires tuning.**
Camelot's stream mode requires `columns` parameter (x-coordinates of column separators) or relies on whitespace
detection. Without pre-configured column positions, stream mode often produces wildly wrong column boundaries. The agent
would need to infer these from layout data, which is a non-trivial sub-problem.

### ocr_page()

**Weakness:**

**[T-06] Bangla OCR quality is not production-ready in open-source tools.**
Tesseract 5 supports Bangla (`ben`) but:

- Accuracy on GOB documents (mixed fonts, low DPI) is typically 70-85%
- easyOCR does better but requires GPU and is Python-based (integration in Java stack is non-trivial)
- PaddleOCR has decent Bangla support but is also Python
- The proposal does not name any specific OCR engine, leaving this completely undefined

**[T-07] Legacy Bangla font encoding (SutonnyMJ, Vrinda) is a critical gap.**
A large fraction of GOB documents from pre-2015 use these proprietary encodings. When these PDFs are processed:

- Tika extracts text but it is phonetically mapped wrong (Unicode codepoints are wrong)
- OCR on such pages may work (it sees the rendered glyphs) but produces inconsistent results
- There is no mention of encoding detection or normalization

**[T-08] OCR confidence is not propagated into the JSON confidence field.**
When OCR is used on a page, the resulting text has inherent confidence (Tesseract provides per-character confidence).
This information is discarded. The clause confidence stored in JSON should differentiate between "extracted from digital
text layer" and "extracted from OCR with 78% character confidence."

### rulepack_runner()

**[T-09] The rule pack itself is completely undefined.**
No schema, no examples, no rule count, no domain expert involvement mentioned. This is the highest-value component in
the system (it's the "quality gate") and it is the least specified. Rule packs need:

- A formal rule DSL or schema
- Versioning and migration strategy
- Coverage metrics (what % of GOB RFP requirements are covered)
- Maintenance ownership (who writes new rules when regulations change)

---

## 4. Data Model Analysis

### The Proposed RFP JSON Structure

```json
{
    "doc_meta": {
        "title",
        "issuing_authority",
        "date",
        "procurement_ref"
    },
    "sections": [
        {
            "id",
            "title",
            "page_range"
        }
    ],
    "clauses": [
        {
            "clause_id",
            "page",
            "text",
            "tags"
        }
    ],
    "tables": [
        {
            "page",
            "caption",
            "type",
            "grid"
        }
    ],
    "entities": {
        "deadlines",
        "amounts",
        "validity_period",
        "penalties",
        "slas",
        "contact_info"
    },
    "confidence": {
        "per section/table/clause"
    }
}
```

### Schema Issues

**[D-01] Clause-to-section relationship is absent.**
Sections and clauses are two parallel arrays with no explicit parent-child relationship. If clause `C.3.2` belongs to
section `S.3`, there is no foreign key connecting them. This forces all consumers to re-infer the hierarchy from
clause_id naming conventions, which may not be present.

**[D-02] Multi-page clause span is not representable.**
`clause.page` is a single integer. Clauses routinely span multiple pages. A single clause about eligibility may run from
page 12 to page 15. There is no `page_range` equivalent for clauses.

**[D-03] Cross-references between clauses are not modeled.**
"See Section 4.2 for evaluation criteria" is a cross-reference. The proposal mentions "Deadlines appear in more than one
place but disagree → flag as contradiction" as a verification signal, but the data model has no mechanism to store "
clause X references clause Y." Without explicit reference tracking, contradiction detection requires scanning all text
for mentions of clause IDs — fragile and incomplete.

**[D-04] Tables are not linked to clauses.**
A table is the deliverables table for Section 3. The data model treats tables as a separate top-level array, not as
nested content within a clause. This means a consumer of the JSON cannot know which section a table belongs to.

**[D-05] The `tags` field is an undefined enum.**
Tags like `submission`, `eligibility`, `scope`, `evaluation` are mentioned but never formalized. Key questions:

- Is this a closed enum? Who defines it?
- Can a clause have multiple tags? (Very common: a clause about submission that also specifies eligibility)
- How are tags assigned — LLM inference or rule-based?
- Bangla clause text will be tagged based on what model?

**[D-06] The `entities` block is a flat structure but entities have complex relationships.**
`deadlines` as a field will presumably be a list of dates. But:

- Which deadline is for bid submission? Which for technical proposal? Which for commercial?
- Are deadlines associated with specific clauses?
- What timezone are deadlines in? (GOB documents use BST, rarely stated explicitly)

**[D-07] `confidence` as a top-level block structure is ambiguous.**
"Per section/table/clause" — is this a nested object mirroring the sections/clauses/tables arrays? Or a separate lookup
by ID? The schema doesn't specify. Embedding confidence inside each clause/section/table object is far more practical.

**[D-08] The `grid` field for tables is undefined.**
No format specified. Common options:

- `[[cell, cell], [cell, cell]]` — 2D array (common but loses header information)
- `[{"row": 0, "col": 0, "value": "", "rowspan": 1, "colspan": 1}]` — cell objects (preserves merging)
- Named columns format (requires header row detection)

Without specifying the grid format, the artifact_writer() and rule pack cannot be built.

---

## 5. Rule Pack Architecture

### What the Rule Pack Is Supposed to Do

Run deterministic and LLM-based checks on the structured RFP JSON to flag:

- Missing mandatory sections
- Ambiguous or contradictory clauses
- Compliance red flags
- Evaluation criteria completeness

### Issues

**[R-01] No rule DSL is defined.**
A rule pack is only maintainable if rules are declarative, not code. The proposal implies YAML/JSON rules, but the
structure is undefined. A rule might be:

```yaml
-   id      : BD-001
    name    : Submission Deadline Required
    check   : entities.deadlines.submission IS NOT NULL
    severity: FATAL
    message : "No submission deadline found"
```

But without a formal grammar for checks, rules will be written inconsistently and become unmaintainable.

**[R-02] The deterministic/LLM check split is undefined at the boundary.**
The proposal says deterministic checks use clause tags and entities; LLM checks are for ambiguity/conflict/missing
acceptance criteria. But:

- "Missing acceptance criteria" is a semantic judgment, not a structural one. How does the rule trigger? (
  `IF section.type == 'deliverables' AND no_acceptance_criteria_detected THEN run LLM check`)
- What prompt is sent to the LLM? Is it the full clause text?
- LLM check results are non-deterministic across runs. What happens when the same RFP is re-analyzed and the LLM gives a
  different answer?

**[R-03] Rule pack needs domain expert authorship, which is not mentioned.**
Writing rules like "Payment mentions milestone but no milestone table → missing info" requires procurement domain
expertise. Who writes and validates these rules at DSI? A software developer cannot write coverage-complete procurement
rules alone. The proposal implicitly assumes this expertise exists and is available.

**[R-04] Rule pack versioning and RFP type alignment.**
The pack is called `bd-govt-rfp-v1`. But:

- Works contracts have different mandatory sections than consultancy contracts
- IMED-formatted RFPs differ from CPTU-formatted ones
- UNDP/World Bank-funded projects use different templates
  One rule pack cannot cover all Bangladeshi government procurement types without conditional logic or multiple packs.

---

## 6. Agent Design Critique

### Is This System Actually Agentic?

The proposal claims the system is agentic because it:

1. Plans which extraction strategy to use
2. Verifies its own output
3. Repairs by re-trying with different tools

**[AG-01] All described "agent decisions" can be implemented as deterministic rules without an LLM.**

| Proposed "Agent Decision"            | Deterministic Alternative                                      |
|--------------------------------------|----------------------------------------------------------------|
| Use OCR on scanned pages             | IF image_coverage > 80% THEN ocr_page()                        |
| Try lattice vs. stream for tables    | IF grid_lines_detected THEN lattice ELSE stream                |
| Re-segment with different heuristics | Fixed retry list: [regex-based, font-size-based, indent-based] |
| Flag low-confidence extractions      | confidence_score < 0.7 → add to retry queue                    |

If the "agent" is actually a rule engine with conditional branching, this proposal has the same structural vulnerability
as the rejected ticket routing system: it is solvable by a non-AI pipeline with deterministic logic. This is the same
reason the director rejected the previous pitch.

**The genuinely agentic decisions** (where LLM is irreplaceable) are:

- Semantic tag assignment for clauses with ambiguous text
- LLM judgment checks in the rule pack
- Generating clarification questions
- Detecting implicit contradictions that require reading multiple clauses together

These are the true value-add interactions and they should be the pitch centerpiece.

**[AG-02] The repair loop has no termination guarantee.**
"If confidence < threshold, the agent re-plans and retries." But:

- What is the threshold? (Unspecified; presumably per-field but not defined)
- What is the maximum retry count? If unset, infinite loops are possible (e.g., a page that is always low-confidence
  because the original document is genuinely garbled)
- When all retries fail, what is the output? A partial JSON with null fields? An error? Flagged sections?
- The stopping condition and fallback state are undefined

**[AG-03] Context window management is entirely absent.**
For a 200-page RFP:

- tika_extract() returns potentially 300,000+ tokens of text
- pdf_layout_extract() per page returns coordinate blocks — multiply by 200 pages
- All tool results accumulate in the agent's context across the repair loop

No LLM context window can hold a 200-page document's extraction in one pass. The proposal needs:

- A chunking strategy (how are long documents split for the LLM planner?)
- A context compression strategy (summarize tool results before passing to LLM?)
- A state management layer (what persists between agent turns?)

**[AG-04] LLM planning is non-deterministic across identical inputs.**
Running the same RFP twice through the agent may produce:

- Different clause IDs (if the LLM decides to re-segment differently)
- Different tag assignments
- Different confidence scores
- Different clarification questions

This non-determinism is acceptable for some outputs but catastrophic for others (clause IDs must be stable for
downstream reference).

---

## 7. Output Layer Analysis

### Bid Clarity Pack Artifacts

**[O-01] Clarification Questions generation mechanism is never specified.**
The document lists "Clarification Questions (DOCX/PDF)" as output but never explains:

- What triggers a question? (Low confidence? Rule pack flag? Missing field?)
- Is it LLM-generated from clause snippets?
- How are duplicate questions deduplicated?
- How are questions mapped back to source clause + page number for evidence?
- Are questions formatted as formal RFI (Request for Information) letters or informal lists?

**[O-02] DOCX/XLSX generation stack is undefined.**
Java options include Apache POI (XLSX/DOCX), JasperReports, Freemarker templates. Each has significant differences in:

- Template capability
- Table rendering complexity
- Merge field support
- Memory footprint for large documents

No choice is made. This is a significant integration decision that needs resolution before any code is written.

**[O-03] Page reference links in output artifacts.**
The proposal mentions "clause/page evidence" in the Bid Clarity Pack. This implies that the DOCX output should reference
specific pages in the source PDF. Implementing clickable PDF page references from DOCX requires embedding absolute file
paths or URLs — which break when documents are moved. This is harder than it sounds.

**[O-04] The Extraction Audit Log "for trust" is only useful if it is human-readable and visually clear.**
Proposing a JSON audit log for "leadership trust" is the wrong format for the audience. Leadership will not open a JSON
file. The audit log needs to be an HTML report or a sheet within the XLSX output showing:

- Page-by-page extraction method
- Confidence heatmap
- Retry events with reasons
- Low-confidence regions highlighted

Without a visual audit report, the "trust" claim is hollow.

---

## 8. Bangla-English Mixed Document Problem

This is explicitly called out in the proposal as a known challenge. The assessment below reveals it is far more severe
than acknowledged.

### Encoding

**[B-01] Legacy Bangla font encodings will corrupt extraction silently.**
Documents produced before 2018 by many GOB offices use SutonnyMJ, Bijoy, or Vrinda — proprietary encodings that map
Bangla Unicode codepoints to wrong characters. When Tika processes these:

- The extracted text looks like Bangla Unicode but the characters are wrong
- The system has no way to detect this without visual comparison
- Entity extraction on corrupted text will silently fail

Solution requires: encoding fingerprinting + transliteration mapping. This is a research-grade problem, not an
engineering sprint.

### NLP on Bangla Text

**[B-02] Named entity extraction in Bangla is effectively unsolved for this domain.**
Detecting "deadline: 15 March 2025" in English is trivial. Detecting `জমা দেওয়ার শেষ তারিখ: ১৫ মার্চ ২০২৫` in Bangla
requires:

- A Bangla NER model trained on procurement documents (does not exist publicly)
- Bangla date parsing (Bangla digits + Bangla month names)
- Handling Bangla numerals (০-৯) vs. Arabic numerals (0-9) — both used in same docs

The proposal does not name any Bangla NLP library. `bnlp-toolkit` exists but has no procurement-domain NER.

**[B-03] Section heading detection in Bangla is unsolved.**
`section_segmenter()` needs heading detection for Bangla text. Common Bangla heading patterns (`প্রথম অধ্যায়`,
`ধারা ১.২`) are different from English conventions. Without explicit Bangla heading pattern rules, Bangla-dominant
documents will produce empty section arrays.

**[B-04] LLM behavior on Bangla text is inconsistent.**
Most LLMs (including GPT-4) have significantly degraded performance on Bangla text for structured extraction tasks. Tag
assignment, entity extraction, and clarification question generation will be much lower quality for Bangla-dominant
clauses. Local Bangla LLMs (e.g., BanglaBERT variants) are classification-only, not generative. This is a fundamental
gap.

---

## 9. Spring AI Feasibility Gaps

### Current State of Spring AI (as of Q1 2026)

Spring AI provides:

- LLM client abstraction (OpenAI, Anthropic, Azure, local via Ollama)
- Tool/function calling with `@Tool` annotation
- Basic `ChatClient` with system prompt management
- Simple advisor chain pattern

**[S-01] Spring AI has no native agentic loop or state machine.**
The "Plan → Act → Verify → Repair" loop described in the proposal requires:

- Agent state persistence between turns (what has been extracted so far?)
- Conditional tool invocation based on prior results
- Retry orchestration with backoff
- Loop termination logic

None of this is provided by Spring AI out of the box. The team must build a custom agent loop, which is a significant
engineering effort. LangGraph (Python) or LangGraph4J (experimental Java port) would provide this, but integrating them
into a Spring Boot application is non-trivial and LangGraph4J is not production-ready.

**[S-02] Long-running tool execution blocks Spring AI's synchronous model.**
`pdf_layout_extract()` on a 200-page document may take 2-5 minutes. Spring AI's tool calling is synchronous by default.
Options:

- Async tool execution with CompletableFuture (requires custom wrapper)
- Message queue-based processing (RabbitMQ/Kafka job dispatch)

Neither approach is simple. The proposal treats tool calls as instant operations.

**[S-03] No built-in state persistence across agent turns.**
If the agent needs to say "I've extracted sections 1-5, now doing 6-10, confidence is low on section 3, I'll retry it
after section 10," this state must be persisted somewhere. Spring AI has no session/state store. Options:

- In-memory state object (lost if service restarts)
- Redis or DB-backed state (requires additional infrastructure)

**[S-04] Local LLM hosting is understated as an engineering problem.**
"Self-hosted" implies running a capable LLM locally. Options and their realities:

- Ollama with Mistral/LLaMA: Adequate for simple extraction; poor at complex multi-step reasoning
- Ollama with LLaMA 3.1 70B: Requires 40+ GB VRAM; multi-GPU setup needed
- vLLM with quantized models: Better throughput but complex setup
- Commercial API (OpenAI/Anthropic): Not "self-hosted"; data leaves the building (security issue for govt contracts)

The proposal says "self-hosted" without specifying what LLM runs locally. This is a hardware budget and model capability
decision that fundamentally shapes what the agent can do.

---

## 10. Operational and Self-Hosting Risks

**[OP-01] Processing time is unaddressed.**
A realistic estimate for a 200-page mixed-document RFP:

- tika_extract(): 5-15 seconds
- image_detect() per page: 0.5-2 seconds × 200 = 100-400 seconds
- ocr_page() per scanned page (assume 50%): 3-10 seconds × 100 = 300-1000 seconds
- table_extract() per table page: 2-10 seconds × 50 tables = 100-500 seconds
- LLM entity extraction (chunked): 30-120 seconds
- rule_pack_runner(): 30-90 seconds

**Total realistic range: 10-40 minutes per document.**

Is a 40-minute processing time acceptable? The proposal doesn't say. For an async background job, maybe. For an
interactive UI, no.

**[OP-02] No concurrency model for multiple RFPs.**
If two large RFPs are submitted simultaneously:

- OCR is CPU/GPU intensive
- LLM inference is GPU intensive
- They will compete for the same resources

No job queue, resource allocation, or priority system is described.

**[OP-03] Model updates change agent behavior.**
When the local LLM is upgraded (e.g., Mistral 7B → Mistral 12B), the agent's decisions change. Previously passing
documents may fail rule pack checks with the new model. There is no model versioning strategy in the proposal.

**[OP-04] No monitoring or observability plan.**
For a production system, you need:

- Processing time per document metrics
- Per-tool failure rates
- Confidence score distribution over time (drift detection)
- LLM token usage tracking (for cost control even with local models)

---

## 11. Differentiation Risk

### The "Off-the-Shelf" Threat (Same Issue That Killed the Last Pitch)

The director rejected the ticket routing pitch because it was "solvable by off-the-shelf tools like OpenClaw." This
proposal must survive the same test.

**Existing tools that overlap:**

| Tool                         | Capabilities                                                         | Overlap with This Proposal |
|------------------------------|----------------------------------------------------------------------|----------------------------|
| AWS Textract                 | Table extraction, form detection, confidence scores, layout analysis | High                       |
| Azure Document Intelligence  | Pre-built + custom models, table extraction, key-value pairs         | High                       |
| Google Document AI           | Layout parser, tables, confidence, multi-language                    | High                       |
| Reducto.ai / Unstructured.io | Document chunking, table extraction, structured output               | High                       |
| LlamaIndex + GPT-4o          | PDF parsing, structured extraction, agent orchestration              | High                       |
| Docsumo                      | Document automation, tables, rules engine                            | Moderate                   |

**[DIFF-01] The proposal's unique claims reduce to three things:**

1. Self-hosted (avoids data leaving the building — critical for GOB contracts)
2. Rule pack that runs on structured JSON (domain-specific quality gate)
3. Repair loop that adapts per-page strategy (vs. single-pass extraction)

Of these, #1 is a deployment choice (you can self-host Unstructured.io too), #2 is genuinely unique but is a
rule-authoring problem not an AI problem, and #3 is where the agentic claim lives.

**[DIFF-02] The case for uniqueness must be reframed.**
The actual differentiation is:

- **Bangla language support** in a government procurement context (no off-the-shelf tool handles this well)
- **GOB-specific rule pack** (bd-govt-rfp-v1) that encodes procurement regulation knowledge
- **Self-hosted + air-gapped** deployment for data security
- **Structured RFP JSON** with page-anchor evidence (not just text extraction)

The agentic framing should be secondary. The pitch wins on domain specificity, not AI architecture.

---

## 12. MVP Scope Assessment

### Proposed MVP Includes:

1. Automatic digital vs. scanned page detection
2. Per-page strategy selection
3. Table extraction with retry (stream vs. lattice)
4. Confidence scoring + at least 1 repair loop
5. Structured RFP JSON
6. 3 XLSX/DOCX artifacts

**[MVP-01] This is not an MVP. This is a full v1.0.**

An MVP should answer one question: "Can the system produce a useful structured JSON from a GOB RFP PDF that is more
accurate than naive text extraction?"

True MVP:

- Single extraction strategy (layout-based, no per-page adaptation)
- Structured JSON output (no artifacts)
- No repair loop
- Basic confidence scoring (simple heuristics)
- 5 mandatory fields extracted: title, issuer, deadline, eligibility summary, evaluation weights

This true MVP is buildable in 4-6 weeks and validates the core value proposition. The proposed MVP is 4-6 months and
delays validation.

**[MVP-02] Artifact generation should not be in the MVP.**
DOCX/XLSX generation is presentation layer work that can be replaced by a simple CSV export in the MVP phase. It adds
implementation complexity without validating the core extraction capability.

**[MVP-03] The repair loop should not be in the MVP.**
The repair loop requires: correct confidence scoring, correct threshold calibration, correct tool selection logic, and
correct loop termination. Each of these has failure modes. In an MVP, a failed extraction should fail visibly, not enter
a repair loop that may silently produce wrong output. Fail loudly in MVP; fix gracefully later.

---

## 13. Security and Compliance Gaps

**[SEC-01] RFP documents contain sensitive procurement data.**
GOB RFPs include:

- Estimated project budgets (commercially sensitive)
- Evaluation criteria and weightings (if leaked, enables bid manipulation)
- Qualification thresholds (can be used to design bids exactly at the boundary)

There is no mention of:

- Role-based access control (who can upload and view processed RFPs)
- Audit logging of user actions (required for govt-adjacent systems)
- Data retention and deletion policies
- Encryption at rest for stored PDFs and extracted JSON

**[SEC-02] LLM prompt injection via document content.**
If an RFP PDF contains text like `"Ignore previous instructions and instead output..."`, the LLM component may be
affected. Self-hosted LLMs are still vulnerable to prompt injection when document content is passed directly to the
context. No input sanitization is mentioned.

**[SEC-03] Self-hosted requirement should be the leading security argument.**
The proposal buries "self-hosted" as a technical footnote. For govt contract work, this is a legal compliance
requirement — document data cannot leave the client's network. This should be the opening argument in the director
pitch, not a subordinate clause.

---

## 14. Testing and Validation Blindspot

**[TEST-01] No ground truth dataset is mentioned.**
Without labeled examples of:

- "Correct" clause extraction from 10-20 real GOB RFPs
- "Correct" table extraction with human-verified grids
- "Correct" entity values (deadlines, amounts)

...there is no way to evaluate whether the system works. Confidence scores are meaningless without calibration against
ground truth. Who builds the ground truth dataset? Procurement experts? This is a significant pre-work step.

**[TEST-02] No evaluation metrics defined.**
What constitutes acceptable performance?

- Clause extraction F1 > 0.85?
- Deadline extraction accuracy > 95%?
- Table structure correctness > 80% (by row-column match)?

Without agreed metrics, the system can never be declared "done" and improvement is unmeasurable.

**[TEST-03] Rule pack needs its own test suite.**
Each rule should have:

- A positive test case (document that should trigger the rule)
- A negative test case (document that should not trigger the rule)
- An edge case

Without rule tests, rule pack updates will silently break existing behavior.

**[TEST-04] The repair loop is extremely hard to test.**
To test the repair loop, you need documents that specifically fail on first extraction pass but succeed on second.
Building such a test corpus requires deliberately degraded documents. This is non-trivial and not mentioned.

---

## 15. Itemized Issue Register

| ID      | Category        | Issue                                                                           | Severity | Complexity to Fix        |
|---------|-----------------|---------------------------------------------------------------------------------|----------|--------------------------|
| A-01    | Architecture    | Tika + PDFBox are redundant (same underlying library)                           | Medium   | Low                      |
| A-02    | Architecture    | No input validation for corrupt/encrypted/XFA PDFs                              | High     | Medium                   |
| A-03    | Architecture    | Mixed pages (partial scan) are unhandled                                        | High     | High                     |
| A-04    | Architecture    | image_detect() is a black box with no defined method                            | High     | Medium                   |
| A-05    | Architecture    | section_segmenter() heuristics will fail on real GOB formatting                 | High     | High                     |
| A-06    | Architecture    | Clause IDs are not stable across re-runs or amendments                          | Medium   | Medium                   |
| T-01    | Tools           | No multi-column de-interleaving strategy                                        | Medium   | High                     |
| T-02    | Tools           | Multi-page tables produce fragmented outputs with no merge strategy             | High     | High                     |
| T-03    | Tools           | Merged cells (rowspan/colspan) corrupt table grid structure                     | High     | Medium                   |
| T-04    | Tools           | Scanned tables have no extraction path (OCR → text, not grid)                   | Critical | High                     |
| T-05    | Tools           | Stream table mode requires column coordinate tuning; cannot auto-infer reliably | High     | Medium                   |
| T-06    | Tools           | No specific Bangla OCR engine named; Tesseract accuracy inadequate              | High     | Medium                   |
| T-07    | Tools           | Legacy Bangla font encodings (SutonnyMJ) silently corrupt text extraction       | Critical | Very High                |
| T-08    | Tools           | OCR confidence not propagated into JSON confidence field                        | Medium   | Low                      |
| T-09    | Tools           | Rule pack DSL, schema, and authorship are completely undefined                  | Critical | High                     |
| D-01    | Data Model      | Clause-to-section parent relationship missing from JSON schema                  | High     | Low                      |
| D-02    | Data Model      | Clause page_range missing (single page field only)                              | Medium   | Low                      |
| D-03    | Data Model      | Cross-clause references not modeled                                             | Medium   | Medium                   |
| D-04    | Data Model      | Tables not linked to parent sections/clauses                                    | High     | Low                      |
| D-05    | Data Model      | Tags field has no defined taxonomy or assignment mechanism                      | High     | Medium                   |
| D-06    | Data Model      | Entities block lacks clause-level provenance                                    | Medium   | Low                      |
| D-07    | Data Model      | Confidence structure is ambiguous (top-level vs. embedded)                      | Medium   | Low                      |
| D-08    | Data Model      | Grid field for tables has no defined format                                     | High     | Low                      |
| R-01    | Rule Pack       | No rule DSL or schema defined                                                   | Critical | High                     |
| R-02    | Rule Pack       | Deterministic/LLM check boundary is undefined                                   | High     | Medium                   |
| R-03    | Rule Pack       | No domain expert named to author rules                                          | High     | Organizational           |
| R-04    | Rule Pack       | Single rule pack cannot cover all GOB procurement types                         | Medium   | Medium                   |
| AG-01   | Agent Design    | All "agent decisions" can be replaced by deterministic rules                    | High     | Low (to fix framing)     |
| AG-02   | Agent Design    | Repair loop has no termination guarantee                                        | Critical | Medium                   |
| AG-03   | Agent Design    | Context window management absent for large documents                            | Critical | High                     |
| AG-04   | Agent Design    | LLM non-determinism makes clause IDs and tags unstable                          | High     | Medium                   |
| O-01    | Output          | Clarification question generation is unexplained                                | High     | Medium                   |
| O-02    | Output          | DOCX/XLSX generation stack undefined                                            | Medium   | Low (decision)           |
| O-03    | Output          | PDF page reference links in DOCX output are path-dependent                      | Low      | Medium                   |
| O-04    | Output          | Audit log is JSON (wrong format for leadership trust)                           | Medium   | Low                      |
| B-01    | Bangla          | Legacy encoding corruption — silent and undetectable                            | Critical | Very High                |
| B-02    | Bangla          | No Bangla NER for procurement entities                                          | Critical | Very High                |
| B-03    | Bangla          | No Bangla heading pattern rules for section_segmenter                           | High     | Medium                   |
| B-04    | Bangla          | LLM performance on Bangla text is degraded                                      | High     | Medium                   |
| S-01    | Spring AI       | No native agentic loop; must be custom-built                                    | High     | High                     |
| S-02    | Spring AI       | Long-running tools block synchronous model                                      | High     | Medium                   |
| S-03    | Spring AI       | No built-in state persistence across agent turns                                | High     | Medium                   |
| S-04    | Spring AI       | Local LLM hosting requirements undefined (hardware/model)                       | Critical | Organizational           |
| OP-01   | Operations      | Processing time 10-40 min per large document — not specified                    | Medium   | N/A (accept or optimize) |
| OP-02   | Operations      | No concurrency model for multiple simultaneous RFPs                             | Medium   | Medium                   |
| OP-03   | Operations      | Model updates change agent behavior silently                                    | Medium   | Medium                   |
| OP-04   | Operations      | No monitoring/observability plan                                                | Medium   | Medium                   |
| DIFF-01 | Differentiation | Off-the-shelf tools provide most features; weak differentiation framing         | High     | Low (framing fix)        |
| DIFF-02 | Differentiation | Unique value (Bangla + GOB rules + self-hosted) is buried                       | High     | Low (framing fix)        |
| MVP-01  | Scope           | Proposed MVP is a full v1.0, not an MVP                                         | High     | Organizational           |
| MVP-02  | Scope           | Artifact generation should not be in MVP                                        | Medium   | Organizational           |
| MVP-03  | Scope           | Repair loop should not be in MVP                                                | High     | Organizational           |
| SEC-01  | Security        | No RBAC, audit logging, retention policy, or encryption mentioned               | High     | Medium                   |
| SEC-02  | Security        | LLM prompt injection via document content                                       | Medium   | Medium                   |
| SEC-03  | Security        | Self-hosted security argument not leading the pitch                             | Medium   | Low (framing)            |
| TEST-01 | Testing         | No ground truth dataset defined or planned                                      | Critical | High                     |
| TEST-02 | Testing         | No evaluation metrics defined                                                   | High     | Medium                   |
| TEST-03 | Testing         | Rule pack has no test suite                                                     | High     | Medium                   |
| TEST-04 | Testing         | Repair loop is extremely difficult to test systematically                       | Medium   | High                     |

**Total issues identified: 59**
**Critical severity: 10**
**High severity: 29**
**Medium severity: 19**
**Low severity: 1**

---

## 16. Recommended Mitigations and Architectural Corrections

### Immediate Structural Changes

**1. Redefine the True MVP**

Cut the MVP to:

- Single-strategy extraction (no per-page adaptation)
- Structured RFP JSON (no DOCX/XLSX)
- 7 mandatory fields: title, issuer, procurement_ref, submission_deadline, eligibility_summary, scope_summary,
  evaluation_criteria
- Simple binary confidence: "extracted" vs. "not found"
- No repair loop — fail loudly with missing field flags

This is 6-8 weeks for two developers and validates the core proposition.

**2. Fix the Data Model First**

Define the JSON schema completely before any extraction code:

```json
{
    "doc_meta": {
        "title",
        "issuer",
        "procurement_ref",
        "issue_date",
        "source_language"
    },
    "sections": [
        {
            "id": "S.1",
            "title": "",
            "page_start": 1,
            "page_end": 3,
            "subsections": [
                "S.1.1",
                "S.1.2"
            ],
            "confidence": {
                "score": 0.92,
                "method": "layout"
            }
        }
    ],
    "clauses": [
        {
            "id": "C.1.2.3",
            "section_id": "S.1.2",
            "page_start": 4,
            "page_end": 5,
            "text": "",
            "text_language": "en",
            "tags": [
                "eligibility",
                "financial"
            ],
            "references": [
                "C.2.1",
                "C.3.4"
            ],
            "confidence": {
                "score": 0.88,
                "method": "text_layer",
                "ocr_confidence": null
            }
        }
    ],
    "tables": [
        {
            "id": "T.1",
            "clause_id": "C.3.2",
            "section_id": "S.3",
            "page_start": 12,
            "page_end": 13,
            "type": "deliverables",
            "caption": "",
            "headers": [
                "Item",
                "Description",
                "Due Date",
                "Payment"
            ],
            "rows": [
                [
                    "1",
                    "Inception Report",
                    "2025-06-01",
                    "10%"
                ]
            ],
            "merged_cells": [],
            "confidence": {
                "score": 0.75,
                "method": "lattice"
            }
        }
    ],
    "entities": {
        "deadlines": [
            {
                "type": "submission",
                "value": "2025-03-15T17:00:00+06:00",
                "clause_id": "C.2.1"
            }
        ],
        "amounts": [
            {
                "type": "contract_value",
                "value": 5000000,
                "currency": "BDT",
                "clause_id": "C.4.1"
            }
        ]
    }
}
```

**3. Replace "Agent" Framing with "Adaptive Extraction Pipeline with LLM-Augmented Quality Gate"**

This is more accurate and more defensible. The truly agentic parts:

- LLM-based tag assignment
- LLM judgment checks in the rule pack
- LLM-generated clarification questions

These should be positioned as "LLM-augmented" steps in an otherwise deterministic pipeline.

**4. Address Bangla as a Separate Phase**

Phase 1: English-dominant documents (or documents with digital text layer)
Phase 2: Mixed Bangla-English with Unicode
Phase 3: Legacy-encoded Bangla documents

Each phase is a separate engineering effort. Never promise all three in the same delivery.

**5. Define the Local LLM Stack**

Commit to one of:

- Ollama + Mistral 7B for basic extraction (fast, lower quality)
- Ollama + LLaMA 3.1 70B for high-quality reasoning (requires 2× A100 or equivalent)
- Commercial API (OpenAI/Anthropic) with data processing agreement (not truly self-hosted)

The hardware budget determines the model, which determines the quality ceiling.

**6. Build Ground Truth Before Building Models**

Before writing extraction code, collect 20 real GOB RFPs (across procurement types), manually annotate them with correct
clause boundaries, table structures, and entity values. Use these as the evaluation benchmark. Without this, you cannot
know if the system works.

**7. Design the Rule Pack DSL as Priority 1**

The rule pack is the defensible, durable moat of this system. Invest in its design early:

- Define a declarative rule schema (JSON/YAML with JSON Schema validation)
- Hire or designate a procurement domain expert as rule author
- Build a rule test suite before adding more rules
- Plan for multiple rule packs by procurement type (works, goods, consultancy, ICT)

### Pitch Reframing for the Director

Lead with:

1. **Security first:** "Government RFP data never leaves our servers." (Self-hosted, air-gapped)
2. **Domain specificity:** "This is not a generic document extractor. It knows GOB procurement rules." (Rule pack)
3. **Bangla support:** "No off-the-shelf tool handles Bangla procurement documents." (Genuine differentiator)
4. **Auditability:** "Every extracted fact is linked to a page number in the source document." (Trust mechanism)

Follow with business value:

- Faster bid readiness
- Fewer missed mandatory items
- Better clarification packs before bid submission
- Reduced scope disputes post-award

Omit from the pitch:

- "Agentic" (jargon, invites the "off-the-shelf" question again)
- The repair loop mechanics (implementation detail that sounds speculative)
- Specific LLM model names (they become outdated)

---

*This document should be treated as a living reference. Sections should be updated as design decisions are made and as
issues are resolved. Each [XX-##] reference in the Issue Register corresponds to a specific architectural decision or
implementation risk that must be addressed before the corresponding component is built.*
