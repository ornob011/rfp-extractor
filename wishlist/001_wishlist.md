# Wishlist — Benchmark Dataset Wishlist (Non-Blocking)

## 0) Wishlist Intent

- Build an optional real-document benchmark dataset and reporting harness.
- Keep benchmark work fully decoupled from Sprint 1-12 delivery gates.
- Improve long-term extraction quality visibility without blocking implementation progress.

---

## 1) Scope

- Ground-truth PDF collection workflow (ICT, works, consultancy, goods, mixed Bangla/English).
- Annotation schema and templates for sections, entities, tables, and expected rule findings.
- Benchmark runner profile (`-Pbenchmark`) that executes only when dataset is present.
- Scorecard output (section quality, entity coverage, table structure match, rule precision/recall).

**Out of scope:**

- Any release gate for core delivery sprints.
- Mandatory dependency for CI merge checks.

---

## 2) Deliverables

1. `testdata/ground-truth/README.md` finalized with annotation workflow and ownership.
2. `testdata/ground-truth/sample-annotation-template.json` aligned with current schema.
3. `GroundTruthLoader` benchmark profile wiring and dataset presence checks.
4. `BenchmarkReportGenerator` output (JSON + markdown summary).
5. Documentation updates that clearly mark benchmark runs as optional.

---

## 3) Acceptance Criteria

1. Running `mvn verify` (default profile) passes without any ground-truth dataset.
2. Running `mvn verify -Pbenchmark` with dataset present executes benchmark suite and publishes a scorecard.
3. Missing benchmark dataset under `-Pbenchmark` fails fast with a clear setup error.
4. No sprint before 13 references benchmark data as a merge blocker.

---

## 4) Risks and Mitigations

- Risk: Annotation effort stalls due to unavailable SMEs.
  Mitigation: Keep benchmark profile optional and schedule in parallel to feature work.
- Risk: Schema drift breaks old annotations.
  Mitigation: Version annotation schema and provide migration script/checker.

---

## 5) Notes

- This sprint is a wishlist/hardening track.
- Production scope and release readiness remain defined by Sprints 1-12.
