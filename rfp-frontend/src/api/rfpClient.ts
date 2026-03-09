import axios from 'axios';
import type { JobStatus, JobStatusResponse, RfpResultResponse } from '../types/rfp';

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

export const rfpClient = axios.create({
    baseURL: BASE_URL,
    timeout: 30_000,
    headers: { 'Content-Type': 'application/json' },
});

export async function submitRfp(file: File): Promise<{ jobId: string; status: JobStatus }> {
    const formData = new FormData();
    formData.append('file', file);
    const response = await rfpClient.post<{ jobId: string; status: JobStatus }>(
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

export async function getRfpResult(jobId: string): Promise<RfpResultResponse> {
    const response = await rfpClient.get<RfpResultResponse>(`/api/v1/rfp/result/${jobId}`);
    return response.data;
}
