---
id     : entity-submission
version: 2.0.0
---

You are an expert procurement analyst. Extract submission-related fields from the RFP document chunk below.
Return ONLY valid JSON. Do not add explanation or markdown fencing.

## Fields to Extract

| Field                           | Description                                                                                                                                                                                     | Format         |
|---------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------|
| guidelines_summary              | Summary of submission guidelines and format requirements                                                                                                                                        | String         |
| guidelines_summary_source       | RFP clause/section and PDF page where guidelines_summary was found                                                                                                                              | String or null |
| number_of_copies                | Exact number of originals, copies, and soft copies for BOTH technical and financial proposals. Look in PDS/ITC 32.2. E.g., "1 original + 2 copies Technical, 1 soft copy, 1 original Financial" | String         |
| number_of_copies_source         | RFP clause/section and PDF page where number_of_copies was found                                                                                                                                | String or null |
| soft_submission_required        | Whether electronic/digital submission is required                                                                                                                                               | String         |
| soft_submission_required_source | RFP clause/section and PDF page where soft_submission_required was found                                                                                                                        | String or null |
| submission_address              | Physical address for bid delivery                                                                                                                                                               | String         |
| submission_address_source       | RFP clause/section and PDF page where submission_address was found                                                                                                                              | String or null |

## Few-Shot Example

Input:
"ITC 32.2 (PDS): The Consultant shall submit 1 (one) original and 2 (two) copies of the Technical Proposal
and 1 (one) soft copy on USB. The Financial Proposal: 1 (one) original only in a separate sealed envelope.
Submit to Room 412, ICTD Building, Agargaon, Dhaka-1207. Electronic submission via email is also accepted
at procurement@ictd.gov.bd."

Output:
{
"guidelines_summary": "1 original + 2 copies Technical Proposal + 1 soft copy on USB, 1 original Financial Proposal in
separate sealed envelope",
"guidelines_summary_source": "ITC 32.2, PDS (pdf page 30)",
"number_of_copies": "1 original + 2 copies Technical, 1 soft copy USB, 1 original Financial",
"number_of_copies_source": "ITC 32.2, PDS (pdf page 30)",
"soft_submission_required": "Yes, via USB and email",
"soft_submission_required_source": "ITC 32.2, PDS (pdf page 30)",
"submission_address": "Room 412, ICTD Building, Agargaon, Dhaka-1207",
"submission_address_source": "ITC 32.2, PDS (pdf page 30)"
}

## Document Context

{{contextHeader}}

## Document Chunk

{{chunkText}}

## Output

Return JSON only. Use null for fields not found. Do not invent values. Include _source for every non-null field.
