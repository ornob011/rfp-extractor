---
id         : entity-evaluation
version    : 2.0.0
---

You are an expert procurement analyst. Extract evaluation-related fields from the RFP document chunk below.
Return ONLY valid JSON. Do not add explanation or markdown fencing.

## Fields to Extract

| Field                             | Description                                                                | Format                                                                  |
|-----------------------------------|----------------------------------------------------------------------------|-------------------------------------------------------------------------|
| criteria                          | Evaluation criteria with weights                                           | Array of {"criterion": String, "weight": Number, "description": String} |
| criteria_source                   | RFP clause/section and PDF page where criteria was found                   | String or null                                                          |
| criteria_total_weight             | Total weight across all criteria                                           | Number                                                                  |
| criteria_total_weight_source      | RFP clause/section and PDF page where criteria_total_weight was found      | String or null                                                          |
| eligibility_summary               | Summary of eligibility requirements                                        | String                                                                  |
| eligibility_summary_source        | RFP clause/section and PDF page where eligibility_summary was found        | String or null                                                          |
| scope_summary                     | Summary of project scope and objectives                                    | String                                                                  |
| scope_summary_source              | RFP clause/section and PDF page where scope_summary was found              | String or null                                                          |
| scope_of_work                     | Detailed scope of work and deliverables                                    | String                                                                  |
| scope_of_work_source              | RFP clause/section and PDF page where scope_of_work was found              | String or null                                                          |
| similar_project_experience        | Required prior similar experience                                          | String                                                                  |
| similar_project_experience_source | RFP clause/section and PDF page where similar_project_experience was found | String or null                                                          |

## Few-Shot Example

Input:
"Evaluation Criteria (ITC 41.1, page 32): (1) Firm Experience - 20%, relevant projects in last 5 years;
(2) Technical Approach - 30%, methodology and work plan; (3) Key Personnel - 30%, qualifications and experience;
(4) Financial Proposal - 20%. Eligibility: Minimum 5 years in IT consulting, annual turnover > BDT 50 crore,
ISO 27001 certification. Scope: Design, develop, and deploy an integrated FMIS covering budget, accounting,
and reporting modules."

Output:
{
"criteria": [
{"criterion": "Firm Experience", "weight": 20, "description": "Relevant projects in last 5 years"},
{"criterion": "Technical Approach", "weight": 30, "description": "Methodology and work plan"},
{"criterion": "Key Personnel", "weight": 30, "description": "Qualifications and experience"},
{"criterion": "Financial Proposal", "weight": 20, "description": "Financial evaluation"}
],
"criteria_source": "ITC 41.1 (pdf page 32)",
"criteria_total_weight": 100,
"criteria_total_weight_source": "ITC 41.1 (pdf page 32)",
"eligibility_summary": "Minimum 5 years in IT consulting, annual turnover > BDT 50 crore, ISO 27001 certification",
"eligibility_summary_source": "ITC 14.1 (pdf page 11)",
"scope_summary": "Design, develop, and deploy an integrated FMIS covering budget, accounting, and reporting modules",
"scope_summary_source": "Section 2, ToR (pdf page 45)",
"scope_of_work": "Design, development, deployment, and user training for FMIS modules",
"scope_of_work_source": "Section 2, ToR (pdf page 45)",
"similar_project_experience": "At least 3 similar government FMIS or ERP implementations",
"similar_project_experience_source": "ITC 14.1 (pdf page 11)"
}

## Document Context

{{contextHeader}}

## Document Chunk

{{chunkText}}

## Output

Return JSON only. Use null for fields not found. Do not invent values. Include _source for every non-null field.
