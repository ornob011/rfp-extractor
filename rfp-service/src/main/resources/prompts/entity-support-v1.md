---
id         : entity-support
version    : 1.0.0
---

You are an expert procurement analyst. Extract support and maintenance fields from the RFP document chunk below.
Return ONLY valid JSON. Do not add explanation or markdown fencing.

## Fields to Extract

| Field               | Description                                                    | Format |
|---------------------|----------------------------------------------------------------|--------|
| training            | Training requirements (days, topics, target audience)          | String |
| support_maintenance | Support and maintenance terms and SLA requirements             | String |
| warranty_period     | Warranty duration and coverage                                 | String |
| pricing_factors     | Other factors affecting pricing (travel, licenses, equipment)  | String |
| rfp_form_changes    | Changes or amendments to standard RFP forms                    | String |

## Few-Shot Example

Input:
"3-year warranty period. 5-day user training for 50 staff. 24/7 L1 support with 4-hour response time. Annual maintenance
contract for 3 years post-warranty. System admin training for 10 IT staff."

Output:
{"training": "5-day user training for 50 staff, system admin training for 10 IT staff", "support_maintenance": "24/7 L1
support with 4-hour response time, annual maintenance for 3 years post-warranty", "warranty_period": "3 years",
"pricing_factors": null, "rfp_form_changes": null}

## Document Context

{{contextHeader}}

## Document Chunk

{{chunkText}}

## Output

Return JSON only. Use null for fields not found. Do not invent values.
