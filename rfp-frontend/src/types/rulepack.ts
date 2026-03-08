export type RuleSeverity = 'FATAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO';

export type RuleStatus = 'PASS' | 'FAIL' | 'SKIPPED';

export interface RuleFinding {
    ruleId: string;
    severity: RuleSeverity;
    status: RuleStatus;
    message: string;
    evidence?: string;
}

export interface RulePackResults {
    packId: string;
    packVersion: string;
    runTimestamp: string;
    summary: Partial<Record<RuleSeverity, number>>;
    findings: RuleFinding[];
}
