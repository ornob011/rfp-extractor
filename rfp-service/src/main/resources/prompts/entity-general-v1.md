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

| Field                 | Description                                                           | Format         |
|-----------------------|-----------------------------------------------------------------------|----------------|
| rfp_title             | Full title of the RFP/ToR document                                    | String         |
| procurement_reference | RFP identification/reference number                                   | String         |
| client_name           | Full legal name of the procuring entity, with designation and contact | String         |
| submission_deadline   | Final deadline for bid submission including time                      | String         |
| issue_date            | Date the RFP was issued                                               | String         |
| method_of_selection   | Selection method (e.g., QCBS, LCS, SSS)                               | String         |
| procurement_method    | Procurement category (Goods/Works/Services)                           | String         |
| project_duration      | Duration of the project (development + maintenance breakdown)         | String         |
| pre_bid_meeting       | Pre-bid meeting date, time and location                               | String or null |
| contact               | Contact person name, designation, address, telephone, email           | String         |
| other_information     | Additional documents or declarations required with the proposal       | String or null |

## Few-Shot Example

Input:
"Design, Development and Implementation of FMIS. Reference: 36.01.0000.100.01.001.2025.100. Ministry of Digital Affairs
invites proposals. The RFP is issued on 2 January 2025 and proposals must be submitted by 3 March 2025, 5:00 PM BST.
The method of selection is QCBS. Project duration: 12 months development + 24 months maintenance = 36 months.
A pre-bid meeting will be held on 20 January 2025, 11:00 AM at the Ministry's conference room.
Contact: Mr. Ahmed, Deputy Director, ICT Division, Tel: 02-1234567, Email: procurement@mda.gov.bd.
Other documents required: (a) Declaration that no debar has been declared in any government institution."

Output:
{
"rfp_title": "Design, Development and Implementation of FMIS",
"procurement_reference": "36.01.0000.100.01.001.2025.100",
"client_name": "Ministry of Digital Affairs",
"submission_deadline": "3 March 2025, 5:00 PM BST",
"issue_date": "2 January 2025",
"method_of_selection": "QCBS",
"procurement_method": "Services",
"project_duration": "12 months development + 24 months maintenance = 36 months",
"pre_bid_meeting": "20 January 2025, 11:00 AM, Ministry conference room",
"contact": "Mr. Ahmed, Deputy Director, ICT Division, Tel: 02-1234567, Email: procurement@mda.gov.bd",
"other_information": "Declaration that no debar has been declared in any government institution"
}

## Document Context

{{contextHeader}}

## Document Chunk

{{chunkText}}

## Output

Return JSON only. Use null for fields not found. Do not invent values.
