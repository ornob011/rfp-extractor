---
id         : entity-ict
version    : 1.0.0
model      : google/gemini-2.0-flash-001
max_tokens : 2048
temperature: 0.0
---

You are an expert procurement analyst. Extract ICT-specific fields from the RFP document chunk below.
Return ONLY valid JSON. Do not add explanation or markdown fencing.

## Fields to Extract

| Field                           | Description                                                 | Format               |
|---------------------------------|-------------------------------------------------------------|----------------------|
| total_users                     | Total expected system users                                 | String               |
| concurrent_users                | Expected concurrent users                                   | String               |
| programming_language_preference | Preferred programming languages                             | String               |
| system_language                 | UI/system language requirements                             | String               |
| architecture                    | System architecture pattern (microservices, monolith, etc.) | String               |
| tech_stack                      | Required technology stack components                        | String               |
| database                        | Database requirements                                       | String               |
| hosting                         | Hosting requirements (cloud, on-premise, hybrid)            | String               |
| data_migration_required         | Whether data migration from existing systems is needed      | boolean (true/false) |
| data_migration                  | Data migration scope, source systems, formats, and plan     | String               |
| legacy_system                   | Existing legacy systems to integrate with                   | String               |
| hardware_requirements           | Hardware provisioning requirements                          | String               |
| integrations                    | Required third-party integrations                           | String               |
| mobile_app_required             | Whether mobile application is required                      | boolean (true/false) |
| ui_mock_required                | Whether UI mockups must be submitted                        | boolean (true/false) |
| presentation_required           | Whether a presentation/demo is required                     | boolean (true/false) |
| gantt_chart_required            | Whether a Gantt chart must be submitted                     | boolean (true/false) |
| e_governance_compliance         | E-governance or regulatory compliance requirements          | String               |

## Few-Shot Example

Input:
"The system shall support 500 total users with 100 concurrent users. The application must be developed using Java or
Python with a PostgreSQL database. Cloud hosting on government-approved infrastructure is mandatory. Data migration from
the existing Oracle-based system is required. Mobile app for field inspectors is required. UI mockups must be submitted
with the technical proposal."

Output:
{
"total_users": "500",
"concurrent_users": "100",
"programming_language_preference": "Java or Python",
"system_language": null,
"architecture": null,
"tech_stack": null,
"database": "PostgreSQL",
"hosting": "Cloud (government-approved infrastructure)",
"data_migration_required": true,
"data_migration": "Data migration from existing Oracle-based system required",
"legacy_system": "Existing Oracle-based system",
"hardware_requirements": null,
"integrations": null,
"mobile_app_required": true,
"ui_mock_required": true,
"presentation_required": false,
"gantt_chart_required": false,
"e_governance_compliance": null
}

## Document Context

{{contextHeader}}

## Document Chunk

{{chunkText}}

## Output

Return JSON only. Use null for fields not found. Do not invent values.
