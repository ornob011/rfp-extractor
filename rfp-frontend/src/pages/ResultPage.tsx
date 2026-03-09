import { useParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { getRfpResult } from '@/api/rfpClient';
import { SectionTree } from '@/components/SectionTree';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Skeleton } from '@/components/ui/skeleton';
import { Alert, AlertDescription } from '@/components/ui/alert';

export function ResultPage() {
    const { jobId } = useParams<{ jobId: string }>();

    const { data: result, isLoading, error } = useQuery({
        queryKey: ['rfpResult', jobId],
        queryFn: () => getRfpResult(jobId!),
        enabled: !!jobId,
    });

    if (isLoading) {
        return (
            <div className="space-y-4 max-w-5xl">
                <Skeleton className="h-8 w-48" />
                <Skeleton className="h-10 w-full" />
                <Skeleton className="h-64 w-full" />
            </div>
        );
    }

    if (error) {
        return (
            <div className="max-w-5xl space-y-4">
                <Alert variant="destructive">
                    <AlertDescription>
                        {error instanceof Error ? error.message : 'Failed to load results'}
                    </AlertDescription>
                </Alert>
                <Link to="/jobs" className="text-sm text-primary hover:underline">
                    Back to Jobs
                </Link>
            </div>
        );
    }

    if (!result) {
        return <p className="text-muted-foreground">No result data found.</p>;
    }

    return (
        <div className="max-w-5xl space-y-6">
            <div className="flex items-center justify-between">
                <h1 className="text-xl font-bold text-foreground">
                    Result: Job #{result.jobId}
                </h1>
                <Link to="/jobs" className="text-sm text-primary hover:underline">
                    Back to Jobs
                </Link>
            </div>

            <Tabs defaultValue="sections">
                <TabsList>
                    <TabsTrigger value="sections">Sections</TabsTrigger>
                    <TabsTrigger value="entities">Entities</TabsTrigger>
                    <TabsTrigger value="tables">Tables</TabsTrigger>
                </TabsList>

                <TabsContent value="sections" className="rounded-lg border bg-card p-4">
                    <SectionTree sections={result.sections} />
                </TabsContent>

                <TabsContent value="entities" className="rounded-lg border bg-card p-4">
                    <p className="text-muted-foreground text-sm italic">
                        Entity extraction available in Sprint 4.
                    </p>
                </TabsContent>

                <TabsContent value="tables" className="rounded-lg border bg-card p-4">
                    <p className="text-muted-foreground text-sm italic">
                        Table extraction available in Sprint 5.
                    </p>
                </TabsContent>
            </Tabs>
        </div>
    );
}
