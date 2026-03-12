import { useEffect, useState } from 'react';
import { Card, CardContent } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { fetchArtifactBlobUrl } from '@/api/rfpClient';

export function AuditReportViewer({ jobId }: { jobId: string }) {
    const [blobUrl, setBlobUrl] = useState<string | null>(null);

    useEffect(() => {
        let revoke: string | null = null;

        fetchArtifactBlobUrl(jobId, 'audit-report.html').then((url) => {
            revoke = url;
            setBlobUrl(url);
        });

        return () => {
            if (revoke) {
                URL.revokeObjectURL(revoke);
            }
        };
    }, [jobId]);

    if (!blobUrl) {
        return <Skeleton className="h-[600px] w-full" />;
    }

    return (
        <Card>
            <CardContent className="p-0">
                <iframe
                    src={blobUrl}
                    className="w-full h-[600px] border-0 rounded-md"
                    title="Audit Report"
                />
            </CardContent>
        </Card>
    );
}
