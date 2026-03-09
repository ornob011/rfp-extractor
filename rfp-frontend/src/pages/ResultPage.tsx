import { useParams } from 'react-router-dom';

export function ResultPage() {
    const { jobId } = useParams<{ jobId: string }>();
    return (
        <div className="p-8">
            <h1 className="text-xl font-bold">Result: {jobId}</h1>
            <p className="text-gray-500 mt-2">Results view — implemented in Sprint 3+.</p>
        </div>
    );
}
