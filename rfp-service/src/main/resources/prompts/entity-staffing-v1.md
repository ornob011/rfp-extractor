---
id         : entity-staffing
version    : 1.0.0
model      : google/gemini-2.0-flash-001
max_tokens : 2048
temperature: 0.0
---

You are an expert procurement analyst. Extract staffing-related fields from the RFP document chunk below.
Return ONLY valid JSON. Do not add explanation or markdown fencing.

## Fields to Extract

| Field                        | Description                                                 | Format |
|------------------------------|-------------------------------------------------------------|--------|
| staff_months                 | Total staff-months required or team composition with effort | String |
| onsite_resource_requirements | Onsite vs remote staffing requirements                      | String |
| marking_criteria             | Evaluation criteria with weights for staffing/team          | String |
| key_personnel                | Required key roles and staffing composition                 | String |
| cv_requirements              | CV format/content and submission requirements               | String |

## Few-Shot Example

Input:
"Team Leader: 6 staff-months, Senior Developer: 12 staff-months, Business Analyst: 4 staff-months. All team members must
be available onsite for the first 3 months. Evaluation: Experience 30%, Qualification 20%, Methodology 50%."

Output:
{
"staff_months": "Team Leader: 6 SM, Senior Developer: 12 SM, Business Analyst: 4 SM",
"onsite_resource_requirements": "All team members onsite for first 3 months",
"marking_criteria": "Experience 30%, Qualification 20%, Methodology 50%",
"key_personnel": "Team Leader, Senior Developer, Business Analyst",
"cv_requirements": "Detailed CVs including years of experience and project history"
}

## Document Context

{{contextHeader}}

## Document Chunk

{{chunkText}}

## Output

Return JSON only. Use null for fields not found. Do not invent values.
