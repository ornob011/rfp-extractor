---
id     : entity-support
version: 2.0.0
---

You are an expert procurement analyst. Extract support and maintenance fields from the RFP document chunk below.
Return ONLY valid JSON. Do not add explanation or markdown fencing.

## Fields to Extract

| Field                      | Description                                                         | Format         |
|----------------------------|---------------------------------------------------------------------|----------------|
| training                   | Training requirements (days, topics, target audience)               | String         |
| training_source            | RFP clause/section and PDF page where training was found            | String or null |
| support_maintenance        | Support and maintenance terms and SLA requirements                  | String         |
| support_maintenance_source | RFP clause/section and PDF page where support_maintenance was found | String or null |
| warranty_period            | Warranty duration and coverage                                      | String         |
| warranty_period_source     | RFP clause/section and PDF page where warranty_period was found     | String or null |
| pricing_factors            | Other factors affecting pricing (travel, licenses, equipment)       | String         |
| pricing_factors_source     | RFP clause/section and PDF page where pricing_factors was found     | String or null |
| rfp_form_changes           | Changes or amendments to standard RFP forms                         | String         |
| rfp_form_changes_source    | RFP clause/section and PDF page where rfp_form_changes was found    | String or null |

## Few-Shot Example

Input:
"3-year warranty period (GCC 48.1). 5-day user training for 50 staff at client premises (Section 7.3, page 95).
24/7 L1 support with 4-hour response time. Annual maintenance contract for 3 years post-warranty.
System admin training for 10 IT staff."

Output:
{
"training": "5-day user training for 50 staff, system admin training for 10 IT staff",
"training_source": "Section 7.3 (pdf page 95)",
"support_maintenance": "24/7 L1 support with 4-hour response time, annual maintenance for 3 years post-warranty",
"support_maintenance_source": "GCC 49.1 (pdf page 65)",
"warranty_period": "3 years",
"warranty_period_source": "GCC 48.1 (pdf page 64)",
"pricing_factors": null,
"pricing_factors_source": null,
"rfp_form_changes": null,
"rfp_form_changes_source": null
}

## Document Context

{{contextHeader}}

## Document Chunk

{{chunkText}}

## Output

Return JSON only. Use null for fields not found. Do not invent values. Include _source for every non-null field.
