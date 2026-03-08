import type { RfpType } from '@/types/rfpType';

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
    rfpType: RfpType;
    runTimestamp: string;
    summary: Partial<Record<RuleSeverity, number>>;
    findings: RuleFinding[];
}

export interface RulePackSummary {
    packId: string;
    version: string;
    rfpType: RfpType;
    ruleCount: number;
    lastLoadedAt: string;
}

export interface ReloadResult {
    reloadedPacks: string[];
    timestamp: string;
}
