import { Badge } from '@/components/ui/badge';
import type { JobStatus } from '@/types/rfp';

interface StatusBadgeProps {
    status: JobStatus;
}

export function StatusBadge({ status }: StatusBadgeProps) {
    switch (status) {
        case 'COMPLETED':
            return (
                <Badge variant="outline" className="text-green-700 border-green-300">
                    COMPLETED
                </Badge>
            );
        case 'RUNNING':
            return <Badge variant="secondary">RUNNING</Badge>;
        case 'QUEUED':
            return (
                <Badge variant="outline" className="text-yellow-700 border-yellow-300">
                    QUEUED
                </Badge>
            );
        case 'FAILED':
            return <Badge variant="destructive">FAILED</Badge>;
        case 'PARTIAL':
            return (
                <Badge variant="outline" className="text-orange-700 border-orange-300">
                    PARTIAL
                </Badge>
            );
    }
}
