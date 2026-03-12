---
id     : entity-general
version: 2.0.0
---

You are an expert procurement analyst. Extract the following fields from the RFP document chunk below.
Return ONLY valid JSON. Do not add explanation or markdown fencing.

## Fields to Extract

| Field                        | Description                                                                                                                                                                                                         | Format         |
|------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------|
| rfp_title                    | Full title of the RFP/ToR document                                                                                                                                                                                  | String         |
| rfp_title_source             | RFP clause/section and PDF page where rfp_title was found                                                                                                                                                           | String or null |
| procurement_reference        | RFP identification/reference number                                                                                                                                                                                 | String         |
| procurement_reference_source | RFP clause/section and PDF page where procurement_reference was found                                                                                                                                               | String or null |
| client_name                  | Full legal name of the procuring entity, with designation and contact                                                                                                                                               | String         |
| client_name_source           | RFP clause/section and PDF page where client_name was found                                                                                                                                                         | String or null |
| submission_deadline          | Final submission date AND time. Look in PDS/ITC 33.3(d) and ITC 34.1. Must include both date and time if available                                                                                                  | String         |
| submission_deadline_source   | RFP clause/section and PDF page where submission_deadline was found                                                                                                                                                 | String or null |
| issue_date                   | Date the RFP was issued                                                                                                                                                                                             | String         |
| issue_date_source            | RFP clause/section and PDF page where issue_date was found                                                                                                                                                          | String or null |
| method_of_selection          | Selection method (e.g., QCBS, LCS, SSS)                                                                                                                                                                             | String         |
| method_of_selection_source   | RFP clause/section and PDF page where method_of_selection was found                                                                                                                                                 | String or null |
| procurement_method           | Procurement category (Goods/Works/Services)                                                                                                                                                                         | String         |
| procurement_method_source    | RFP clause/section and PDF page where procurement_method was found                                                                                                                                                  | String or null |
| project_duration             | Total project duration including development AND maintenance/support periods. Must include breakdown (e.g., "15 months development + 24 months maintenance = 39 months"). Do not report only the development phase. | String         |
| project_duration_source      | RFP clause/section and PDF page where project_duration was found                                                                                                                                                    | String or null |
| pre_bid_meeting              | Pre-bid meeting date, time and location                                                                                                                                                                             | String or null |
| pre_bid_meeting_source       | RFP clause/section and PDF page where pre_bid_meeting was found                                                                                                                                                     | String or null |
| contact                      | Contact person name, designation, address, telephone, email                                                                                                                                                         | String         |
| contact_source               | RFP clause/section and PDF page where contact was found                                                                                                                                                             | String or null |
| other_information            | Additional documents or declarations required with the proposal                                                                                                                                                     | String or null |
| other_information_source     | RFP clause/section and PDF page where other_information was found                                                                                                                                                   | String or null |

## Few-Shot Example

Input:
"Design, Development and Implementation of FMIS. Reference: 36.01.0000.100.01.001.2025.100. Ministry of Digital Affairs
invites proposals. The RFP is issued on 2 January 2025 and proposals must be submitted by 3 March 2025, 5:00 PM BST
(ITC 33.3(d), page 31). The method of selection is QCBS. Project duration: 12 months development + 24 months
maintenance = 36 months. A pre-bid meeting will be held on 20 January 2025, 11:00 AM at the Ministry's conference room.
Contact: Mr. Ahmed, Deputy Director, ICT Division, Tel: 02-1234567, Email: procurement@mda.gov.bd.
Other documents required: (a) Declaration that no debar has been declared in any government institution."

Output:
{
"rfp_title": "Design, Development and Implementation of FMIS",
"rfp_title_source": "Cover page (pdf page 1)",
"procurement_reference": "36.01.0000.100.01.001.2025.100",
"procurement_reference_source": "Cover page (pdf page 1)",
"client_name": "Ministry of Digital Affairs",
"client_name_source": "Cover page (pdf page 1)",
"submission_deadline": "3 March 2025, 5:00 PM BST",
"submission_deadline_source": "ITC 33.3(d) (pdf page 31)",
"issue_date": "2 January 2025",
"issue_date_source": "Cover page (pdf page 1)",
"method_of_selection": "QCBS",
"method_of_selection_source": "ITC 7.2 (pdf page 5)",
"procurement_method": "Services",
"procurement_method_source": "ITC 7.1 (pdf page 5)",
"project_duration": "12 months development + 24 months maintenance = 36 months",
"project_duration_source": "Section 2, ToR (pdf page 45)",
"pre_bid_meeting": "20 January 2025, 11:00 AM, Ministry conference room",
"pre_bid_meeting_source": "ITC 15.1 (pdf page 12)",
"contact": "Mr. Ahmed, Deputy Director, ICT Division, Tel: 02-1234567, Email: procurement@mda.gov.bd",
"contact_source": "ITC 12.1 (pdf page 10)",
"other_information": "Declaration that no debar has been declared in any government institution",
"other_information_source": "ITC 31.2 (pdf page 28)"
}

## Document Context

{{contextHeader}}

## Document Chunk

{{chunkText}}

## Output

Return JSON only. Use null for fields not found. Do not invent values. Include _source for every non-null field.
