import { useState } from 'react';
import type { ReactElement } from 'react';
import { Badge } from '@/components/ui/badge';
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from '@/components/ui/table';
import type {
    RuleFinding,
    RulePackResults as RulePackResultsType,
    RuleSeverity,
    RuleStatus,
} from '@/types/rulepack';

const SEVERITY_ORDER: RuleSeverity[] = ['FATAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];

const SEVERITY_STYLES: Record<RuleSeverity, string> = {
    FATAL: 'border-red-200 bg-red-50 text-red-800',
    HIGH: 'border-orange-200 bg-orange-50 text-orange-800',
    MEDIUM: 'border-yellow-200 bg-yellow-50 text-yellow-800',
    LOW: 'border-blue-200 bg-blue-50 text-blue-800',
    INFO: 'border-gray-200 bg-gray-50 text-gray-700',
};

const STATUS_BADGES: Record<RuleStatus, ReactElement> = {
    FAIL: <Badge variant="destructive">FAIL</Badge>,
    PASS: (
        <Badge variant="outline" className="border-green-300 text-green-700">
            PASS
        </Badge>
    ),
    SKIPPED: <Badge variant="secondary">SKIPPED</Badge>,
};

interface RulePackResultsProps {
    results: RulePackResultsType;
}

export function RulePackResultsPanel({ results }: RulePackResultsProps) {
    const grouped = groupBySeverity(results.findings);
    const severityGroups = SEVERITY_ORDER.map((severity) => ({
        severity,
        findings: grouped[severity],
    })).filter((group) => group.findings.length > 0);

    return (
        <div className="space-y-4">
            <SummaryBar summary={results.summary} />

            <p className="text-xs text-muted-foreground">
                Pack: {results.packId} v{results.packVersion}
            </p>

            {severityGroups.map((group) => (
                <SeverityGroup
                    key={group.severity}
                    severity={group.severity}
                    findings={group.findings}
                />
            ))}
        </div>
    );
}

function SummaryBar({ summary }: { summary: Partial<Record<RuleSeverity, number>> }) {
    const summaryEntries = SEVERITY_ORDER.map((severity) => ({
        severity,
        count: summary[severity] ?? 0,
    })).filter((entry) => entry.count > 0);

    const summaryViews = [
        (
            <div className="flex flex-wrap gap-2">
                {summaryEntries.map((entry) => (
                    <div
                        key={entry.severity}
                        className={`rounded-md border px-3 py-1.5 text-sm font-medium ${SEVERITY_STYLES[entry.severity]}`}
                    >
                        {entry.severity}: {entry.count} failed
                    </div>
                ))}
            </div>
        ),
        (
            <div className="rounded-md border border-green-200 bg-green-50 p-3 text-sm text-green-800">
                All rules passed.
            </div>
        ),
    ] as const;

    return summaryViews[Number(summaryEntries.length === 0)];
}

function SeverityGroup({
    severity,
    findings,
}: {
    severity: RuleSeverity;
    findings: RuleFinding[];
}) {
    return (
        <div className={`rounded-lg border p-3 ${SEVERITY_STYLES[severity]}`}>
            <h3 className="mb-2 text-sm font-semibold">
                {severity} ({findings.length})
            </h3>
            <Table>
                <TableHeader>
                    <TableRow>
                        <TableHead className="w-28">Rule ID</TableHead>
                        <TableHead>Message</TableHead>
                        <TableHead className="w-20">Status</TableHead>
                        <TableHead>Evidence</TableHead>
                    </TableRow>
                </TableHeader>
                <TableBody>
                    {findings.map((finding) => (
                        <FindingRow key={finding.ruleId} finding={finding} />
                    ))}
                </TableBody>
            </Table>
        </div>
    );
}

function FindingRow({ finding }: { finding: RuleFinding }) {
    const [expanded, setExpanded] = useState(false);
    const evidence = finding.evidence ?? '';
    const evidenceView = evidenceState(
        evidence,
        expanded
    );
    const toggleLabel = ['more', 'less'][Number(expanded)];

    return (
        <TableRow>
            <TableCell className="font-mono text-xs">{finding.ruleId}</TableCell>
            <TableCell className="text-xs">{finding.message}</TableCell>
            <TableCell>{STATUS_BADGES[finding.status]}</TableCell>
            <TableCell className="text-xs">
                {evidenceView.text}
                {evidenceView.toggleable && (
                    <button
                        onClick={() => setExpanded((value) => !value)}
                        className="ml-1 text-primary hover:underline"
                    >
                        {toggleLabel}
                    </button>
                )}
            </TableCell>
        </TableRow>
    );
}

function groupBySeverity(findings: RuleFinding[]): Record<RuleSeverity, RuleFinding[]> {
    return findings.reduce<Record<RuleSeverity, RuleFinding[]>>(
        (groups, finding) => ({
            ...groups,
            [finding.severity]: [...groups[finding.severity], finding],
        }),
        {
            FATAL: [],
            HIGH: [],
            MEDIUM: [],
            LOW: [],
            INFO: [],
        }
    );
}

function evidenceState(
    evidence: string,
    expanded: boolean
) {
    const limit = 80;

    const states = [
        {
            text: evidence,
            toggleable: evidence.length > limit,
        },
        {
            text: `${evidence.slice(0, limit)}...`,
            toggleable: true,
        },
    ] as const;

    return states[Number(evidence.length > limit && !expanded)];
}
