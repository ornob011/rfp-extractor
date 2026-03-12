---
id     : entity-financial
version: 2.0.0
---

You are an expert procurement analyst. Extract financial fields from the RFP document chunk below.
Return ONLY valid JSON. Do not add explanation or markdown fencing.

## Fields to Extract

| Field                            | Description                                                                                                      | Format                 |
|----------------------------------|------------------------------------------------------------------------------------------------------------------|------------------------|
| technical_financial_split        | Technical vs Financial weight ratio (e.g., "Technical 85 : Financial 15"). Look in PDS/ITC 45.1                  | String (e.g., "85/15") |
| technical_financial_split_source | RFP clause/section and PDF page where technical_financial_split was found                                        | String or null         |
| performance_security             | Performance security PERCENTAGE or AMOUNT (not the validity clause or general description). Look in PDS/ITC 54.1 | String                 |
| performance_security_source      | RFP clause/section and PDF page where performance_security was found                                             | String or null         |
| bank_guarantee                   | Bank guarantee requirements                                                                                      | String                 |
| bank_guarantee_source            | RFP clause/section and PDF page where bank_guarantee was found                                                   | String or null         |
| payment_terms                    | Payment schedule and terms                                                                                       | String                 |
| payment_terms_source             | RFP clause/section and PDF page where payment_terms was found                                                    | String or null         |
| reimbursable_expenses            | Reimbursable expense policy                                                                                      | String                 |
| reimbursable_expenses_source     | RFP clause/section and PDF page where reimbursable_expenses was found                                            | String or null         |
| bid_validity_period              | How long bids must remain valid                                                                                  | String                 |
| bid_validity_period_source       | RFP clause/section and PDF page where bid_validity_period was found                                              | String or null         |

## Few-Shot Example

Input:
"ITC 45.1 (PDS): Technical = 85, Financial = 15. ITC 54.1 (PDS): Performance Security: 5% of Contract Price.
Bank Guarantee: 2% bid security. Payment: 30% advance on mobilization, 50% on delivery, 20% on UAT acceptance.
Bid validity: 120 days from submission deadline."

Output:
{
"technical_financial_split": "Technical 85 : Financial 15",
"technical_financial_split_source": "ITC 45.1, PDS (pdf page 33)",
"performance_security": "5% of Contract Price",
"performance_security_source": "ITC 54.1, PDS (pdf page 33)",
"bank_guarantee": "2% bid security",
"bank_guarantee_source": "ITC 28.1 (pdf page 27)",
"payment_terms": "30% advance on mobilization, 50% on delivery, 20% on UAT acceptance",
"payment_terms_source": "GCC 45.1 (pdf page 60)",
"reimbursable_expenses": null,
"reimbursable_expenses_source": null,
"bid_validity_period": "120 days",
"bid_validity_period_source": "ITC 35.1 (pdf page 32)"
}

## Document Context

{{contextHeader}}

## Document Chunk

{{chunkText}}

## Output

Return JSON only. Use null for fields not found. Do not invent values. Include _source for every non-null field.
