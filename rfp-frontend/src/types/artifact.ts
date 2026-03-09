export type ArtifactFileType = 'DOCX' | 'XLSX' | 'HTML';

export interface ArtifactMetadata {
    jobId: number;
    filename: string;
    fileType: ArtifactFileType;
    sizeBytes: number;
    generatedAt: string;
    downloadUrl: string;
}
