---
id     : entity-ict
version: 2.0.0
---

You are an expert procurement analyst. Extract ICT-specific fields from the RFP document chunk below.
Return ONLY valid JSON. Do not add explanation or markdown fencing.

## Fields to Extract

| Field                                  | Description                                                                                                                                       | Format          |
|----------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------|-----------------|
| total_users                            | Total expected system users                                                                                                                       | String          |
| total_users_source                     | RFP clause/section and PDF page where total_users was found                                                                                       | String or null  |
| concurrent_users                       | Expected concurrent users                                                                                                                         | String          |
| concurrent_users_source                | RFP clause/section and PDF page where concurrent_users was found                                                                                  | String or null  |
| programming_language_preference        | Preferred programming languages                                                                                                                   | String          |
| programming_language_preference_source | RFP clause/section and PDF page where programming_language_preference was found                                                                   | String or null  |
| system_language                        | UI/system language requirements                                                                                                                   | String          |
| system_language_source                 | RFP clause/section and PDF page where system_language was found                                                                                   | String or null  |
| architecture                           | System architecture pattern (microservices, monolith, etc.)                                                                                       | String          |
| architecture_source                    | RFP clause/section and PDF page where architecture was found                                                                                      | String or null  |
| tech_stack                             | Required technology stack components                                                                                                              | String          |
| tech_stack_source                      | RFP clause/section and PDF page where tech_stack was found                                                                                        | String or null  |
| database                               | Database requirements                                                                                                                             | String          |
| database_source                        | RFP clause/section and PDF page where database was found                                                                                          | String or null  |
| hosting                                | Hosting requirements (cloud, on-premise, hybrid)                                                                                                  | String          |
| hosting_source                         | RFP clause/section and PDF page where hosting was found                                                                                           | String or null  |
| data_migration_required                | Whether data migration from existing systems is needed. null if not mentioned                                                                     | boolean or null |
| data_migration_required_source         | RFP clause/section and PDF page where data_migration_required was found                                                                           | String or null  |
| data_migration                         | Data migration scope, source systems, formats, and plan                                                                                           | String          |
| data_migration_source                  | RFP clause/section and PDF page where data_migration was found                                                                                    | String or null  |
| legacy_system                          | Existing legacy systems to integrate with                                                                                                         | String          |
| legacy_system_source                   | RFP clause/section and PDF page where legacy_system was found                                                                                     | String or null  |
| hardware_requirements                  | Hardware provisioning requirements                                                                                                                | String          |
| hardware_requirements_source           | RFP clause/section and PDF page where hardware_requirements was found                                                                             | String or null  |
| integrations                           | Required third-party integrations                                                                                                                 | String          |
| integrations_source                    | RFP clause/section and PDF page where integrations was found                                                                                      | String or null  |
| mobile_app_required                    | Whether mobile application is required. null if not mentioned                                                                                     | boolean or null |
| mobile_app_required_source             | RFP clause/section and PDF page where mobile_app_required was found                                                                               | String or null  |
| ui_mock_required                       | Whether UI mockups must be submitted. null if not mentioned                                                                                       | boolean or null |
| ui_mock_required_source                | RFP clause/section and PDF page where ui_mock_required was found                                                                                  | String or null  |
| presentation_required                  | Whether a presentation/demo is required. null if not mentioned                                                                                    | boolean or null |
| presentation_required_source           | RFP clause/section and PDF page where presentation_required was found                                                                             | String or null  |
| gantt_chart_required                   | Whether a Gantt chart must be submitted. null if not mentioned                                                                                    | boolean or null |
| gantt_chart_required_source            | RFP clause/section and PDF page where gantt_chart_required was found                                                                              | String or null  |
| e_governance_compliance                | E-governance or regulatory compliance requirements including SQTC/CIRT testing, BCC standards, or similar. Look in General Specifications and PDS | String          |
| e_governance_compliance_source         | RFP clause/section and PDF page where e_governance_compliance was found                                                                           | String or null  |

## Few-Shot Example

Input:
"The system shall support 500 total users with 100 concurrent users. The application must be developed using Java or
Python with a PostgreSQL database. Cloud hosting on government-approved infrastructure is mandatory. Data migration from
the existing Oracle-based system is required. Mobile app for field inspectors is required. UI mockups must be submitted
with the technical proposal. The system must pass SQTC testing as per General Specifications Section 6.6.1.2 (page
120)."

Output:
{
"total_users": "500",
"total_users_source": "Section 5.2 (pdf page 85)",
"concurrent_users": "100",
"concurrent_users_source": "Section 5.2 (pdf page 85)",
"programming_language_preference": "Java or Python",
"programming_language_preference_source": "Section 5.3 (pdf page 86)",
"system_language": null,
"system_language_source": null,
"architecture": null,
"architecture_source": null,
"tech_stack": null,
"tech_stack_source": null,
"database": "PostgreSQL",
"database_source": "Section 5.3 (pdf page 86)",
"hosting": "Cloud (government-approved infrastructure)",
"hosting_source": "Section 5.4 (pdf page 87)",
"data_migration_required": true,
"data_migration_required_source": "Section 5.5 (pdf page 88)",
"data_migration": "Data migration from existing Oracle-based system required",
"data_migration_source": "Section 5.5 (pdf page 88)",
"legacy_system": "Existing Oracle-based system",
"legacy_system_source": "Section 5.5 (pdf page 88)",
"hardware_requirements": null,
"hardware_requirements_source": null,
"integrations": null,
"integrations_source": null,
"mobile_app_required": true,
"mobile_app_required_source": "Section 5.6 (pdf page 89)",
"ui_mock_required": true,
"ui_mock_required_source": "Section 5.7 (pdf page 90)",
"presentation_required": null,
"presentation_required_source": null,
"gantt_chart_required": null,
"gantt_chart_required_source": null,
"e_governance_compliance": "Must pass SQTC testing as per General Specifications Section 6.6.1.2",
"e_governance_compliance_source": "General Specifications, Section 6.6.1.2 (pdf page 120)"
}

## Document Context

{{contextHeader}}

## Document Chunk

{{chunkText}}

## Output

Return JSON only. Use null for fields not found. Do not invent values. For boolean fields, use null (not false) when the
requirement is not mentioned. Include _source for every non-null field.
