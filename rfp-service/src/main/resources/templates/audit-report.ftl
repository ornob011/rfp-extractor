<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>Audit Report — ${title!"Untitled RFP"}</title>
  <style>
    * {
      margin: 0;
      padding: 0;
      box-sizing: border-box;
    }

    body {
      font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
      color: #1a1a1a;
      padding: 24px;
      background: #f8f9fa;
    }

    h1 {
      font-size: 1.5rem;
      margin-bottom: 4px;
    }

    h2 {
      font-size: 1.15rem;
      margin: 24px 0 12px 0;
      border-bottom: 2px solid #dee2e6;
      padding-bottom: 4px;
    }

    .meta {
      color: #6c757d;
      font-size: 0.85rem;
      margin-bottom: 20px;
    }

    .stats-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
      gap: 12px;
      margin-bottom: 24px;
    }

    .stat-card {
      background: #fff;
      border: 1px solid #dee2e6;
      border-radius: 6px;
      padding: 12px;
      text-align: center;
    }

    .stat-card .value {
      font-size: 1.5rem;
      font-weight: 700;
    }

    .stat-card .label {
      font-size: 0.75rem;
      color: #6c757d;
      text-transform: uppercase;
    }

    table {
      width: 100%;
      border-collapse: collapse;
      margin-bottom: 20px;
      background: #fff;
    }

    th {
      background: #343a40;
      color: #fff;
      padding: 8px 10px;
      text-align: left;
      font-size: 0.8rem;
      text-transform: uppercase;
    }

    td {
      padding: 6px 10px;
      border-bottom: 1px solid #e9ecef;
      font-size: 0.85rem;
    }

    tr:hover {
      background: #f1f3f5;
    }

    .conf-bar {
      height: 8px;
      border-radius: 4px;
    }

    .conf-high {
      background: #28a745;
    }

    .conf-mid {
      background: #fd7e14;
    }

    .conf-low {
      background: #dc3545;
    }

    .severity-fatal {
      color: #dc3545;
      font-weight: 700;
    }

    .severity-high {
      color: #fd7e14;
      font-weight: 700;
    }

    .severity-medium {
      color: #ffc107;
      font-weight: 600;
    }

    .severity-low {
      color: #17a2b8;
    }

    .severity-info {
      color: #6c757d;
    }

    .status-pass {
      color: #28a745;
    }

    .status-fail {
      color: #dc3545;
      font-weight: 700;
    }

    .status-skipped {
      color: #6c757d;
    }

    footer {
      margin-top: 32px;
      text-align: center;
      color: #adb5bd;
      font-size: 0.75rem;
    }
  </style>
</head>
<body>
<h1>Audit Report</h1>
<div class="meta">
  <strong>Project:</strong> ${title!"N/A"} &nbsp;|&nbsp;
  <strong>Reference:</strong> ${procurementRef!"N/A"} &nbsp;|&nbsp;
  <strong>Generated:</strong> ${generatedAt}
</div>

<h2>Summary</h2>
<div class="stats-grid">
  <div class="stat-card">
    <div class="value">${stats.totalPages}</div>
    <div class="label">Total Pages</div>
  </div>
  <div class="stat-card">
    <div class="value">${stats.digitalPages}</div>
    <div class="label">Digital Pages</div>
  </div>
  <div class="stat-card">
    <div class="value">${stats.scannedPages}</div>
    <div class="label">Scanned Pages</div>
  </div>
  <div class="stat-card">
    <div class="value">${stats.mixedPages}</div>
    <div class="label">Mixed Pages</div>
  </div>
  <div class="stat-card">
    <div class="value">${stats.tablesFound}</div>
    <div class="label">Tables Found</div>
  </div>
  <div class="stat-card">
    <div class="value">${stats.entitiesExtracted}</div>
    <div class="label">Entities Extracted</div>
  </div>
  <div class="stat-card">
    <div class="value severity-fatal">${stats.fatalFail}</div>
    <div class="label">Fatal Failures</div>
  </div>
  <div class="stat-card">
    <div class="value severity-high">${stats.highFail}</div>
    <div class="label">High Failures</div>
  </div>
</div>

<h2>Page-by-Page Breakdown</h2>
<table>
  <thead>
  <tr>
    <th>#</th>
    <th>Classification</th>
    <th>Extraction Method</th>
    <th>Confidence</th>
    <th>Retries</th>
  </tr>
  </thead>
  <tbody>
  <#list pageSummaries as p>
    <tr>
      <td>${p.pageNumber}</td>
      <td>${p.classification}</td>
      <td>${p.method}</td>
      <td>
        <div class="conf-bar ${p.confClass}" style="width:${(p.confidence * 100)?string["0"]}%"></div>
        ${(p.confidence * 100)?string["0.0"]}%
      </td>
      <td>${p.retries}</td>
    </tr>
  </#list>
  </tbody>
</table>

<h2>Entity Extraction Results</h2>
<table>
  <thead>
  <tr>
    <th>Entity</th>
    <th>Value</th>
    <th>Confidence</th>
  </tr>
  </thead>
  <tbody>
  <#list entitySummary as e>
    <tr>
      <td>${e.name}</td>
      <td>${e.value}</td>
      <td>
        <div class="conf-bar ${e.confClass}" style="width:${(e.confidence * 100)?string["0"]}%"></div>
        ${(e.confidence * 100)?string["0.0"]}%
      </td>
    </tr>
  </#list>
  </tbody>
</table>

<#if repairLog?has_content>
  <h2>Repair Log</h2>
  <table>
    <thead>
    <tr>
      <th>Component</th>
      <th>Strategy</th>
      <th>Attempt</th>
      <th>Before</th>
      <th>After</th>
      <th>Outcome</th>
    </tr>
    </thead>
    <tbody>
    <#list repairLog as r>
      <tr>
        <td>${r.componentId}</td>
        <td>${r.strategy}</td>
        <td>${r.attempt}</td>
        <td>${(r.before * 100)?string["0.0"]}%</td>
        <td>${(r.after * 100)?string["0.0"]}%</td>
        <td>${r.outcome}</td>
      </tr>
    </#list>
    </tbody>
  </table>
</#if>

<#if ruleFindings?has_content>
  <h2>Rule Findings</h2>
  <table>
    <thead>
    <tr>
      <th>Rule ID</th>
      <th>Severity</th>
      <th>Status</th>
      <th>Message</th>
    </tr>
    </thead>
    <tbody>
    <#list ruleFindings as f>
      <tr>
        <td>${f.ruleId}</td>
        <td class="severity-${f.severity?lower_case}">${f.severity}</td>
        <td class="status-${f.status?lower_case}">${f.status}</td>
        <td>${f.message}</td>
      </tr>
    </#list>
    </tbody>
  </table>
</#if>

<footer>Generated by RFP Extractor &mdash; DSI Bid Clarity Pack</footer>
</body>
</html>
