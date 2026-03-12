{
  "title": "${title?json_string}",
  "metadata": [
    {
      "label": "${projectLabel?json_string}",
      "value": "${titleValue?json_string}"
    },
    {
      "label": "${referenceLabel?json_string}",
      "value": "${procurementRef?json_string}"
    },
    {
      "label": "${dateLabel?json_string}",
      "value": "${generatedDate?json_string}"
    }
  ],
  "questions": [
    <#list questions as question>
    {
      "label": "Q${question.number}",
      "text": "${question.questionText?json_string}",
      "sourceLabel": "${sourceLabel?json_string}",
      "sourceText": "${question.sourceText?json_string}"
    }<#if question_has_next>,</#if>
    </#list>
  ],
  "closingInstruction": "${closingInstruction?json_string}"
}
