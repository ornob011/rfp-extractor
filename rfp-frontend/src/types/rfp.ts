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
    jobId: string;
    status: JobStatus;
    progress: number;
    submittedAt: string;
    completedAt?: string;
    errorMessage?: string;
}

export interface RfpResultResponse {
    jobId: string;
    docMeta: DocMeta;
    sections: Section[];
}
