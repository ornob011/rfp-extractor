import { useQuery } from '@tanstack/react-query';
import { listArtifacts, artifactDownloadUrl } from '@/api/rfpClient';
import { Card, CardContent, CardFooter } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { FileText, Sheet, FileCode, Download } from 'lucide-react';
import type { ArtifactFileType, ArtifactMetadata } from '@/types/artifact';

const FILE_TYPE_ICONS: Record<ArtifactFileType, typeof FileText> = {
    DOCX: FileText,
    XLSX: Sheet,
    HTML: FileCode,
};
const SKELETON_COUNT = 3;

function formatSize(bytes: number): string {
    if (bytes < 1024) {
        return `${bytes} B`;
    }
    if (bytes < 1024 * 1024) {
        return `${(bytes / 1024).toFixed(1)} KB`;
    }
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function ArtifactCard({ artifact, jobId }: { artifact: ArtifactMetadata; jobId: string }) {
    const Icon = FILE_TYPE_ICONS[artifact.fileType];
    const url = artifactDownloadUrl(jobId, artifact.filename);

    return (
        <Card>
            <CardContent className="flex items-center gap-3 pt-4">
                <Icon className="h-8 w-8 text-muted-foreground shrink-0" />
                <div className="min-w-0">
                    <p className="text-sm font-medium truncate">{artifact.filename}</p>
                    <p className="text-xs text-muted-foreground">
                        {artifact.fileType} &middot; {formatSize(artifact.sizeBytes)}
                    </p>
                </div>
            </CardContent>
            <CardFooter>
                <Button asChild variant="outline" size="sm">
                    <a href={url} download>
                        <Download className="mr-1.5 h-3.5 w-3.5" />
                        Download
                    </a>
                </Button>
            </CardFooter>
        </Card>
    );
}

export function ArtifactDownload({ jobId }: { jobId: string }) {
    const { data: artifacts, isLoading, error } = useQuery({
        queryKey: ['artifacts', jobId],
        queryFn: () => listArtifacts(jobId),
        enabled: !!jobId,
    });

    if (isLoading) {
        return (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                {Array.from({ length: SKELETON_COUNT }).map((_, i) => (
                    <Skeleton key={i} className="h-24 w-full" />
                ))}
            </div>
        );
    }

    if (error) {
        return (
            <Alert variant="destructive">
                <AlertDescription>
                    Failed to load artifacts.
                </AlertDescription>
            </Alert>
        );
    }

    if (!artifacts || artifacts.length === 0) {
        return (
            <p className="text-muted-foreground text-sm italic">
                No artifacts generated yet.
            </p>
        );
    }

    return (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {artifacts.map((artifact) => (
                <ArtifactCard
                    key={artifact.filename}
                    artifact={artifact}
                    jobId={jobId}
                />
            ))}
        </div>
    );
}
