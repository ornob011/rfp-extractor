# Ground Truth Dataset

## Directory Structure
- testdata/pdfs/         — raw PDF files (gitignored)
- testdata/ground-truth/ — JSON annotations (committed)

## Sourcing PDFs
PDFs must be obtained from:
1. CPTU (Central Procurement Technical Unit) Bangladesh: https://cptu.gov.bd/
2. Client-provided RFP archives.
3. IMED (Implementation Monitoring and Evaluation Division): https://imed.gov.bd/

Store PDFs as testdata/pdfs/{doc-id}.pdf (e.g., cptu-2024-ict-001.pdf).
PDFs are gitignored — annotators must share via Google Drive or S3.

## Annotation Format
See sample-annotation-template.json for the schema.

## Target
- 15 PDF documents total
- 5 fully annotated (sections + entities)
- 10 partially annotated (sections only)
