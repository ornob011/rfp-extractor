import { Card, CardContent } from '@/components/ui/card';
import { artifactDownloadUrl } from '@/api/rfpClient';

export function AuditReportViewer({ jobId }: { jobId: string }) {
    const url = artifactDownloadUrl(jobId, 'audit-report.html');

    return (
        <Card>
            <CardContent className="p-0">
                <iframe
                    src={url}
                    className="w-full h-[600px] border-0 rounded-md"
                    title="Audit Report"
                />
            </CardContent>
        </Card>
    );
}
