import axios from 'axios';
import type { JobStatus, JobStatusResponse, RfpResultResponse } from '../types/rfp';
import type { RulePackSummary, ReloadResult } from '../types/rulepack';
import type { ArtifactMetadata } from '../types/artifact';

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

export const rfpClient = axios.create({
    baseURL: BASE_URL,
    timeout: 30_000,
    headers: { 'Content-Type': 'application/json' },
});

export async function submitRfp(file: File): Promise<{ jobId: number; status: JobStatus }> {
    const formData = new FormData();
    formData.append('file', file);
    const response = await rfpClient.post<{ jobId: number; status: JobStatus }>(
        '/api/v1/rfp/submit',
        formData,
        { headers: { 'Content-Type': 'multipart/form-data' } },
    );
    return response.data;
}

export async function getJobStatus(jobId: string): Promise<JobStatusResponse> {
    const response = await rfpClient.get<JobStatusResponse>(`/api/v1/rfp/status/${jobId}`);
    return response.data;
}

export async function listJobs(): Promise<JobStatusResponse[]> {
    const response = await rfpClient.get<JobStatusResponse[]>('/api/v1/rfp/jobs');
    return response.data;
}

export async function getRfpResult(jobId: string): Promise<RfpResultResponse> {
    const response = await rfpClient.get<RfpResultResponse>(`/api/v1/rfp/result/${jobId}`);
    return response.data;
}

export async function listRulePacks(): Promise<RulePackSummary[]> {
    const response = await rfpClient.get<RulePackSummary[]>('/api/v1/admin/rule-packs');
    return response.data;
}

export async function reloadRulePacks(): Promise<ReloadResult> {
    const response = await rfpClient.post<ReloadResult>('/api/v1/admin/rule-packs/reload');
    return response.data;
}

export async function listArtifacts(jobId: string): Promise<ArtifactMetadata[]> {
    const response = await rfpClient.get<ArtifactMetadata[]>(
        `/api/v1/rfp/artifacts/${jobId}`,
    );
    return response.data;
}

export function artifactDownloadUrl(jobId: string, filename: string): string {
    return `${BASE_URL}/api/v1/rfp/artifacts/${jobId}/${filename}`;
}
