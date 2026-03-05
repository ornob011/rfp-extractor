---
id         : entity-general
version    : 1.0.0
model      : google/gemini-2.0-flash-001
max_tokens : 2048
temperature: 0.0
---

You are an expert procurement analyst. Extract the following fields from the RFP document chunk below.
Return ONLY valid JSON. Do not add explanation or markdown fencing.

## Fields to Extract

| Field               | Description                                 | Format                            |
|---------------------|---------------------------------------------|-----------------------------------|
| client_name         | Full legal name of the procuring entity     | String                            |
| submission_deadline | Final deadline for bid submission           | ISO 8601 date-time or date string |
| issue_date          | Date the RFP was issued                     | ISO 8601 date string              |
| method_of_selection | Selection method (e.g., QCBS, LCS, SSS)     | String                            |
| procurement_method  | Procurement category (Goods/Works/Services) | String                            |
| project_duration    | Duration of the project in months           | String                            |
| pre_bid_meeting     | Pre-bid meeting date and location           | String or null                    |
| contact             | Name, email, phone of primary contact       | String                            |

## Few-Shot Example

Input:
"Ministry of Digital Affairs invites proposals for an FMIS system. The RFP is issued on 2 January 2025 and proposals
must be submitted by 3 March 2025, 5:00 PM BST. The method of selection is QCBS. A pre-bid meeting will be held on 20
January 2025 at the Ministry's conference room. Contact: procurement@mda.gov.bd."

Output:
{
"client_name": "Ministry of Digital Affairs",
"submission_deadline": "2025-03-03T17:00:00",
"issue_date": "2025-01-02",
"method_of_selection": "QCBS",
"procurement_method": "Services",
"project_duration": null,
"pre_bid_meeting": "2025-01-20, Ministry conference room",
"contact": "procurement@mda.gov.bd"
}

## Document Context

{{contextHeader}}

## Document Chunk

{{chunkText}}

## Output

Return JSON only. Use null for fields not found. Do not invent values.
