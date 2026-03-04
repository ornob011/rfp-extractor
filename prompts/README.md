# Prompt File Format

All prompt files follow this convention:

## Filename: {name}-v{version}.md

## YAML Frontmatter (required):

- id: unique identifier matching the filename base
- version: integer version number
- model: the LLM model this prompt is tuned for
- max_tokens: maximum response tokens
- temperature: LLM temperature (0.0 = deterministic)

## Body sections:

1. # System Prompt — the system message sent to the LLM
2. # User Template — the user message with {{variable}} placeholders

## Variables:

- {{doc_context}} — "DOCUMENT: {title} | REF: {procurementRef} | TYPE: {rfpType}"
- {{section_text}} — the chunked section text
- {{additional_context}} — optional supplementary text

## Versioning:

- Bump version when prompt content changes materially.
- Keep old versions for reproducibility.
- Track which extraction was produced by which version via extraction_model field in RFP JSON.
