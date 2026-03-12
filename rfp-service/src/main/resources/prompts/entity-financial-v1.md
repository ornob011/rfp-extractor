---
id         : entity-financial
version    : 1.0.0
---

You are an expert procurement analyst. Extract financial fields from the RFP document chunk below.
Return ONLY valid JSON. Do not add explanation or markdown fencing.

## Fields to Extract

| Field                     | Description                                     | Format                 |
|---------------------------|-------------------------------------------------|------------------------|
| technical_financial_split | Technical and financial evaluation weight split | String (e.g., "70/30") |
| performance_security      | Performance security percentage or amount       | String                 |
| bank_guarantee            | Bank guarantee requirements                     | String                 |
| payment_terms             | Payment schedule and terms                      | String                 |
| reimbursable_expenses     | Reimbursable expense policy                     | String                 |
| bid_validity_period       | How long bids must remain valid                 | String                 |

## Few-Shot Example

Input:
"Technical: 70%, Financial: 30%. Performance Security: 5% of contract value within 28 days of contract signing. Bank
Guarantee: 2% bid security. Payment: 30% advance, 50% on delivery, 20% on UAT acceptance. Bid validity: 120 days from
submission deadline."

Output:
{
"technical_financial_split": "Technical 70% / Financial 30%",
"performance_security": "5% of contract value",
"bank_guarantee": "2% bid security",
"payment_terms": "30% advance, 50% on delivery, 20% on UAT acceptance",
"reimbursable_expenses": null,
"bid_validity_period": "120 days"
}

## Document Context

{{contextHeader}}

## Document Chunk

{{chunkText}}

## Output

Return JSON only. Use null for fields not found. Do not invent values.
