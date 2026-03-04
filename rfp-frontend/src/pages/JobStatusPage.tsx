import { useParams } from 'react-router-dom';

export function JobStatusPage() {
    const { jobId } = useParams<{ jobId: string }>();
    return (
        <div className="p-8">
            <h1 className="text-xl font-bold">Job: {jobId}</h1>
            <p className="text-gray-500 mt-2">Status polling — implemented in Sprint 2.</p>
        </div>
    );
}
