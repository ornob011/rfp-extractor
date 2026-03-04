import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { listJobs } from '../api/rfpClient';
import type { JobStatus } from '../types/rfp';

const STATUS_COLORS: Record<JobStatus, string> = {
    QUEUED: 'bg-yellow-100 text-yellow-800',
    RUNNING: 'bg-blue-100 text-blue-800',
    COMPLETED: 'bg-green-100 text-green-800',
    FAILED: 'bg-red-100 text-red-800',
    PARTIAL: 'bg-orange-100 text-orange-800',
};

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
        <div className="min-h-screen bg-gray-50 p-8">
            <div className="max-w-4xl mx-auto">
                <div className="flex justify-between items-center mb-6">
                    <h1 className="text-2xl font-bold text-gray-800">RFP Jobs</h1>
                    <Link
                        to="/upload"
                        className="bg-blue-600 text-white py-2 px-4 rounded-lg hover:bg-blue-700"
                    >
                        Upload New
                    </Link>
                </div>

                {isLoading && <p className="text-gray-500">Loading...</p>}
                {error && <p className="text-red-500">Failed to load jobs</p>}

                {sortedJobs.length === 0 && !isLoading && (
                    <p className="text-gray-500">No jobs yet. Upload an RFP document to get started.</p>
                )}

                <div className="space-y-3">
                    {sortedJobs.map((job) => (
                        <Link
                            key={job.jobId}
                            to={`/job/${job.jobId}`}
                            className="block bg-white p-4 rounded-lg shadow-sm hover:shadow-md transition-shadow"
                        >
                            <div className="flex justify-between items-center">
                                <div>
                                    <p className="font-medium text-gray-800">
                                        {job.originalFilename ?? job.jobId}
                                    </p>
                                    <p className="text-sm text-gray-500">
                                        {new Date(job.submittedAt).toLocaleString()}
                                    </p>
                                </div>
                                <span className={`px-3 py-1 rounded-full text-xs font-medium ${STATUS_COLORS[job.status]}`}>
                                    {job.status}
                                </span>
                            </div>
                        </Link>
                    ))}
                </div>
            </div>
        </div>
    );
}
