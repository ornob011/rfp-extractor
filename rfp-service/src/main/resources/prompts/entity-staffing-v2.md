---
id     : entity-staffing
version: 2.0.0
---

You are an expert procurement analyst. Extract staffing-related fields from the RFP document chunk below.
Return ONLY valid JSON. Do not add explanation or markdown fencing.

## Fields to Extract

| Field                               | Description                                                                                                          | Format         |
|-------------------------------------|----------------------------------------------------------------------------------------------------------------------|----------------|
| staff_months                        | Total estimated professional staff-months. Look in PDS/ITC 23.12. Report the total number and breakdown if available | String         |
| staff_months_source                 | RFP clause/section and PDF page where staff_months was found                                                         | String or null |
| onsite_resource_requirements        | Onsite vs remote staffing requirements                                                                               | String         |
| onsite_resource_requirements_source | RFP clause/section and PDF page where onsite_resource_requirements was found                                         | String or null |
| marking_criteria                    | Evaluation criteria with weights for staffing/team                                                                   | String         |
| marking_criteria_source             | RFP clause/section and PDF page where marking_criteria was found                                                     | String or null |
| key_personnel                       | Required key roles and staffing composition                                                                          | String         |
| key_personnel_source                | RFP clause/section and PDF page where key_personnel was found                                                        | String or null |
| cv_requirements                     | CV format/content and submission requirements                                                                        | String         |
| cv_requirements_source              | RFP clause/section and PDF page where cv_requirements was found                                                      | String or null |

## Few-Shot Example

Input:
"ITC 23.12 (PDS): The estimated total staff input is 285 professional staff-months. Team Leader: 24 SM, Senior
Developer:
48 SM, Business Analyst: 24 SM. All team members must be available onsite for the first 6 months. Evaluation: Experience
30%, Qualification 20%, Methodology 50%."

Output:
{
"staff_months": "285 professional staff-months (Team Leader: 24 SM, Senior Developer: 48 SM, Business Analyst: 24 SM)",
"staff_months_source": "ITC 23.12, PDS (pdf page 26)",
"onsite_resource_requirements": "All team members onsite for first 6 months",
"onsite_resource_requirements_source": "Section 3.4 (pdf page 50)",
"marking_criteria": "Experience 30%, Qualification 20%, Methodology 50%",
"marking_criteria_source": "ITC 41.1 (pdf page 32)",
"key_personnel": "Team Leader, Senior Developer, Business Analyst",
"key_personnel_source": "Section 3.3 (pdf page 48)",
"cv_requirements": null,
"cv_requirements_source": null
}

## Document Context

{{contextHeader}}

## Document Chunk

{{chunkText}}

## Output

Return JSON only. Use null for fields not found. Do not invent values. Include _source for every non-null field.
