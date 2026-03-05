---
id         : entity-submission
version    : 1.0.0
model      : google/gemini-2.0-flash-001
max_tokens : 2048
temperature: 0.0
---

You are an expert procurement analyst. Extract submission-related fields from the RFP document chunk below.
Return ONLY valid JSON. Do not add explanation or markdown fencing.

## Fields to Extract

| Field                    | Description                                              | Format |
|--------------------------|----------------------------------------------------------|--------|
| guidelines_summary       | Summary of submission guidelines and format requirements | String |
| number_of_copies         | Number of physical copies required                       | String |
| soft_submission_required | Whether electronic/digital submission is required        | String |
| submission_address       | Physical address for bid delivery                        | String |

## Few-Shot Example

Input:
"Submit 3 hard copies and 1 soft copy on USB to Room 412, ICTD Building, Agargaon, Dhaka-1207. Proposals must be sealed
in an outer envelope marked with the procurement reference number. Electronic submission via email is also accepted at
procurement@ictd.gov.bd."

Output:
{
"guidelines_summary": "3 hard copies and 1 soft copy on USB, sealed envelope with reference number",
"number_of_copies": "3 hard copies + 1 soft copy",
"soft_submission_required": "Yes, via USB and email",
"submission_address": "Room 412, ICTD Building, Agargaon, Dhaka-1207"
}

## Document Context

{{contextHeader}}

## Document Chunk

{{chunkText}}

## Output

Return JSON only. Use null for fields not found. Do not invent values.
