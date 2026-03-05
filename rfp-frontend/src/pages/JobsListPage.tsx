import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { listJobs } from '@/api/rfpClient';
import { StatusBadge } from '@/components/StatusBadge';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { Alert, AlertDescription } from '@/components/ui/alert';

export function JobsListPage() {
    const { data: jobs, isLoading, error } = useQuery({
        queryKey: ['jobs'],
        queryFn: listJobs,
        refetchInterval: 5000,
    });

    const sortedJobs = jobs
        ? [...jobs].sort((a, b) => new Date(b.submittedAt).getTime() - new Date(a.submittedAt).getTime())
        : [];

    return (
        <div className="max-w-4xl space-y-6">
            <div className="flex justify-between items-center">
                <h1 className="text-2xl font-bold text-foreground">RFP Jobs</h1>
                <Button asChild>
                    <Link to="/upload">Upload New</Link>
                </Button>
            </div>

            {isLoading && (
                <div className="space-y-3">
                    <Skeleton className="h-16 w-full" />
                    <Skeleton className="h-16 w-full" />
                    <Skeleton className="h-16 w-full" />
                </div>
            )}

            {error && (
                <Alert variant="destructive">
                    <AlertDescription>Failed to load jobs</AlertDescription>
                </Alert>
            )}

            {sortedJobs.length === 0 && !isLoading && (
                <p className="text-muted-foreground">
                    No jobs yet. Upload an RFP document to get started.
                </p>
            )}

            <div className="space-y-3">
                {sortedJobs.map((job) => (
                    <Link
                        key={job.jobId}
                        to={`/job/${job.jobId}`}
                        className="block rounded-lg border bg-card p-4 hover:bg-accent/50 transition-colors"
                    >
                        <div className="flex justify-between items-center">
                            <div>
                                <p className="font-medium text-card-foreground">
                                    {job.originalFilename ?? job.jobId}
                                </p>
                                <p className="text-sm text-muted-foreground">
                                    {new Date(job.submittedAt).toLocaleString()}
                                </p>
                            </div>
                            <StatusBadge status={job.status} />
                        </div>
                    </Link>
                ))}
            </div>
        </div>
    );
}
