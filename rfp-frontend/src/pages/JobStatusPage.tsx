import { useParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { getJobStatus } from '../api/rfpClient';
import type { JobStatus } from '../types/rfp';

const STATUS_COLORS: Record<JobStatus, string> = {
    QUEUED: 'bg-yellow-100 text-yellow-800',
    RUNNING: 'bg-blue-100 text-blue-800',
    COMPLETED: 'bg-green-100 text-green-800',
    FAILED: 'bg-red-100 text-red-800',
    PARTIAL: 'bg-orange-100 text-orange-800',
};

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
            <div className="min-h-screen bg-gray-50 flex items-center justify-center">
                <p className="text-gray-500">Loading...</p>
            </div>
        );
    }

    if (error || !job) {
        return (
            <div className="min-h-screen bg-gray-50 flex items-center justify-center">
                <div className="bg-white p-8 rounded-xl shadow-md">
                    <p className="text-red-500">Job not found</p>
                    <Link to="/jobs" className="text-blue-600 hover:underline mt-4 block">
                        Back to Jobs
                    </Link>
                </div>
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-gray-50 flex items-center justify-center">
            <div className="bg-white p-8 rounded-xl shadow-md w-full max-w-lg">
                <div className="flex justify-between items-center mb-6">
                    <h1 className="text-2xl font-bold text-gray-800">Job Status</h1>
                    <Link to="/jobs" className="text-blue-600 hover:underline text-sm">
                        All Jobs
                    </Link>
                </div>

                <div className="space-y-4">
                    <div className="flex justify-between">
                        <span className="text-gray-500">Job ID</span>
                        <span className="text-sm font-mono">{job.jobId}</span>
                    </div>

                    {job.originalFilename && (
                        <div className="flex justify-between">
                            <span className="text-gray-500">File</span>
                            <span className="text-sm">{job.originalFilename}</span>
                        </div>
                    )}

                    <div className="flex justify-between items-center">
                        <span className="text-gray-500">Status</span>
                        <span className={`px-3 py-1 rounded-full text-xs font-medium ${STATUS_COLORS[job.status]}`}>
                            {job.status}
                        </span>
                    </div>

                    <div>
                        <div className="flex justify-between mb-1">
                            <span className="text-gray-500">Progress</span>
                            <span className="text-sm">{job.progress}%</span>
                        </div>
                        <div className="w-full bg-gray-200 rounded-full h-2">
                            <div
                                className="bg-blue-600 h-2 rounded-full transition-all duration-300"
                                style={{ width: `${job.progress}%` }}
                            />
                        </div>
                    </div>

                    {job.pageCount !== undefined && job.pageCount > 0 && (
                        <div className="flex justify-between">
                            <span className="text-gray-500">Pages</span>
                            <span className="text-sm">{job.pageCount}</span>
                        </div>
                    )}

                    <div className="flex justify-between">
                        <span className="text-gray-500">Submitted</span>
                        <span className="text-sm">{new Date(job.submittedAt).toLocaleString()}</span>
                    </div>

                    {job.completedAt && (
                        <div className="flex justify-between">
                            <span className="text-gray-500">Completed</span>
                            <span className="text-sm">{new Date(job.completedAt).toLocaleString()}</span>
                        </div>
                    )}

                    {job.errorMessage && (
                        <div className="bg-red-50 p-3 rounded-lg">
                            <p className="text-red-700 text-sm">{job.errorMessage}</p>
                        </div>
                    )}

                    {job.status === 'COMPLETED' && (
                        <Link
                            to={`/result/${job.jobId}`}
                            className="block w-full text-center bg-green-600 text-white py-2 px-4 rounded-lg hover:bg-green-700"
                        >
                            View Results
                        </Link>
                    )}
                </div>
            </div>
        </div>
    );
}
