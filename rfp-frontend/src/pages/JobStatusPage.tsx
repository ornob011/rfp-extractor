import { useParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { getJobStatus } from '@/api/rfpClient';
import { StatusBadge } from '@/components/StatusBadge';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { Alert, AlertDescription } from '@/components/ui/alert';
import type { JobStatus } from '@/types/rfp';

function isTerminal(status: JobStatus): boolean {
    return status === 'COMPLETED' || status === 'FAILED';
}

export function JobStatusPage() {
    const { jobId } = useParams<{ jobId: string }>();

    const { data: job, isLoading, error } = useQuery({
        queryKey: ['jobStatus', jobId],
        queryFn: () => getJobStatus(jobId!),
        refetchInterval: (query) => {
            const status = query.state.data?.status;
            if (status && isTerminal(status)) return false;
            return 3000;
        },
        enabled: !!jobId,
    });

    if (isLoading) {
        return (
            <div className="max-w-lg mx-auto space-y-4">
                <Skeleton className="h-8 w-32" />
                <Skeleton className="h-64 w-full" />
            </div>
        );
    }

    if (error || !job) {
        return (
            <div className="max-w-lg mx-auto space-y-4">
                <Alert variant="destructive">
                    <AlertDescription>Job not found</AlertDescription>
                </Alert>
                <Link to="/jobs" className="text-sm text-primary hover:underline">
                    Back to Jobs
                </Link>
            </div>
        );
    }

    return (
        <div className="max-w-lg mx-auto">
            <div className="rounded-lg border bg-card p-6 space-y-4">
                <div className="flex justify-between items-center">
                    <h1 className="text-2xl font-bold text-card-foreground">Job Status</h1>
                    <Link to="/jobs" className="text-sm text-primary hover:underline">
                        All Jobs
                    </Link>
                </div>

                <div className="space-y-4">
                    <div className="flex justify-between">
                        <span className="text-muted-foreground">Job ID</span>
                        <span className="text-sm font-mono">{job.jobId}</span>
                    </div>

                    {job.originalFilename && (
                        <div className="flex justify-between">
                            <span className="text-muted-foreground">File</span>
                            <span className="text-sm">{job.originalFilename}</span>
                        </div>
                    )}

                    <div className="flex justify-between items-center">
                        <span className="text-muted-foreground">Status</span>
                        <StatusBadge status={job.status} />
                    </div>

                    <div>
                        <div className="flex justify-between mb-1">
                            <span className="text-muted-foreground">Progress</span>
                            <span className="text-sm">{job.progress}%</span>
                        </div>
                        <div className="w-full bg-secondary rounded-full h-2">
                            <div
                                className="bg-primary h-2 rounded-full transition-all duration-300"
                                style={{ width: `${job.progress}%` }}
                            />
                        </div>
                    </div>

                    {job.pageCount !== undefined && job.pageCount > 0 && (
                        <div className="flex justify-between">
                            <span className="text-muted-foreground">Pages</span>
                            <span className="text-sm">{job.pageCount}</span>
                        </div>
                    )}

                    <div className="flex justify-between">
                        <span className="text-muted-foreground">Submitted</span>
                        <span className="text-sm">{new Date(job.submittedAt).toLocaleString()}</span>
                    </div>

                    {job.completedAt && (
                        <div className="flex justify-between">
                            <span className="text-muted-foreground">Completed</span>
                            <span className="text-sm">{new Date(job.completedAt).toLocaleString()}</span>
                        </div>
                    )}

                    {job.errorMessage && (
                        <Alert variant="destructive">
                            <AlertDescription>{job.errorMessage}</AlertDescription>
                        </Alert>
                    )}

                    {job.status === 'COMPLETED' && (
                        <Button asChild className="w-full">
                            <Link to={`/result/${job.jobId}`}>View Results</Link>
                        </Button>
                    )}
                </div>
            </div>
        </div>
    );
}
