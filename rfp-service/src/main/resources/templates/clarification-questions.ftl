REQUEST FOR INFORMATION (RFI)
Project: ${title}
Reference: ${procurementRef}
Date: ${generatedDate}

<#list questions as question>
  Q${question?index + 1}: ${question.questionText}
  Source: [Section ${question.clauseId!"N/A"}, Page ${question.page}]

</#list>
Please respond by [date] to the Procuring Entity.
