import type { TableExtractionResult } from '@/types/table';

export type JobStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'PARTIAL';

export interface DocMeta {
    title: string | null;
    procurementRef: string | null;
    issueDate: string | null;
    rfpType: string | null;
    sourceLanguage: string | null;
    extractionModel: string | null;
    extractionTimestamp: string | null;
}

export interface Section {
    id: string;
    title: string;
    level: number;
    pageStart: number;
    pageEnd: number;
    children: Section[];
    confidence: {
        score: number;
        method: string;
    };
}

export interface JobStatusResponse {
    jobId: number;
    status: JobStatus;
    progress: number;
    submittedAt: string;
    completedAt?: string;
    errorMessage?: string;
    originalFilename?: string;
    pageCount?: number;
}

export interface EntityField {
    label: string;
    key: string;
    category: EntityCategory;
}

export type EntityCategory =
    | 'general'
    | 'submission'
    | 'financial'
    | 'ict'
    | 'staffing'
    | 'support'
    | 'evaluation';

export const ENTITY_FIELDS: EntityField[] = [
    { label: 'Client Name', key: 'clientName', category: 'general' },
    { label: 'Submission Deadline', key: 'submissionDeadline', category: 'general' },
    { label: 'Issue Date', key: 'issueDate', category: 'general' },
    { label: 'Procurement Reference', key: 'procurementReference', category: 'general' },
    { label: 'Project Title', key: 'projectTitle', category: 'general' },
    { label: 'Funding Source', key: 'fundingSource', category: 'general' },
    { label: 'Procurement Method', key: 'procurementMethod', category: 'general' },
    { label: 'Currency', key: 'currency', category: 'general' },

    { label: 'Guidelines Summary', key: 'guidelinesSummary', category: 'submission' },
    { label: 'Number of Copies', key: 'numberOfCopies', category: 'submission' },
    { label: 'Submission Address', key: 'submissionAddress', category: 'submission' },
    { label: 'Pre-bid Meeting Date', key: 'preBidMeetingDate', category: 'submission' },

    { label: 'Technical/Financial Split', key: 'technicalFinancialSplit', category: 'financial' },
    { label: 'Marking Criteria', key: 'markingCriteria', category: 'financial' },
    { label: 'Estimated Budget', key: 'estimatedBudget', category: 'financial' },
    { label: 'Payment Terms', key: 'paymentTerms', category: 'financial' },
    { label: 'Financial Scoring', key: 'financialScoring', category: 'financial' },
    { label: 'Bid Validity', key: 'bidValidity', category: 'financial' },

    { label: 'Total Users', key: 'totalUsers', category: 'ict' },
    { label: 'Database', key: 'database', category: 'ict' },
    { label: 'Hosting', key: 'hosting', category: 'ict' },
    { label: 'Operating System', key: 'operatingSystem', category: 'ict' },
    { label: 'Integration', key: 'integration', category: 'ict' },
    { label: 'Bandwidth', key: 'bandwidth', category: 'ict' },
    { label: 'Data Center', key: 'dataCenter', category: 'ict' },
    { label: 'Backup Requirement', key: 'backupRequirement', category: 'ict' },
    { label: 'Disaster Recovery', key: 'disasterRecovery', category: 'ict' },
    { label: 'Security Requirements', key: 'securityRequirements', category: 'ict' },
    { label: 'Accessibility', key: 'accessibility', category: 'ict' },
    { label: 'Mobile Support', key: 'mobileSupport', category: 'ict' },
    { label: 'Cloud Required', key: 'cloudRequired', category: 'ict' },
    { label: 'Open Source Required', key: 'openSourceRequired', category: 'ict' },
    { label: 'Implementation Timeline', key: 'implementationTimeline', category: 'ict' },
    { label: 'Pilot Phase', key: 'pilotPhase', category: 'ict' },
    { label: 'SLA Requirements', key: 'slaRequirements', category: 'ict' },

    { label: 'Staff Months', key: 'staffMonths', category: 'staffing' },
    { label: 'Key Personnel', key: 'keyPersonnel', category: 'staffing' },
    { label: 'Local Staffing', key: 'localStaffing', category: 'staffing' },

    { label: 'Training', key: 'training', category: 'support' },
    { label: 'Support & Maintenance', key: 'supportMaintenance', category: 'support' },
    { label: 'Warranty Period', key: 'warrantyPeriod', category: 'support' },

    { label: 'Criteria', key: 'criteria', category: 'evaluation' },
    { label: 'Eligibility Summary', key: 'eligibilitySummary', category: 'evaluation' },
    { label: 'Scope Summary', key: 'scopeSummary', category: 'evaluation' },
];

export const ENTITY_CATEGORY_LABELS: Record<EntityCategory, string> = {
    general: 'General',
    submission: 'Submission',
    financial: 'Financial',
    ict: 'ICT',
    staffing: 'Staffing',
    support: 'Support',
    evaluation: 'Evaluation',
};

export interface BadgeThresholds {
    high: number;
    medium: number;
}

export interface RfpResultResponse {
    jobId: number;
    docMeta?: DocMeta;
    sections: Section[];
    entities?: Record<string, unknown>;
    confidenceMap?: Record<string, number>;
    tables?: TableExtractionResult[];
    badgeThresholds?: BadgeThresholds;
}
