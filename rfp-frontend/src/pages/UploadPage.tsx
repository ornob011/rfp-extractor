import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { submitRfp } from '../api/rfpClient';

export function UploadPage() {
    const [file, setFile] = useState<File | null>(null);
    const [uploading, setUploading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const navigate = useNavigate();

    const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        setFile(e.target.files?.[0] ?? null);
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!file) return;
        setUploading(true);
        setError(null);
        try {
            await submitRfp(file);
            void navigate('/jobs');
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Upload failed');
        } finally {
            setUploading(false);
        }
    };

    return (
        <div className="min-h-screen bg-gray-50 flex items-center justify-center">
            <form
                onSubmit={(e) => { void handleSubmit(e); }}
                className="bg-white p-8 rounded-xl shadow-md w-full max-w-md"
            >
                <div className="flex justify-between items-center mb-6">
                    <h1 className="text-2xl font-bold text-gray-800">Upload RFP Document</h1>
                    <Link to="/jobs" className="text-blue-600 hover:underline text-sm">
                        View Jobs
                    </Link>
                </div>
                <input
                    type="file"
                    accept=".pdf,.docx"
                    onChange={handleFileChange}
                    className="mb-4 block w-full text-sm text-gray-500"
                    data-testid="file-input"
                />
                {error && <p className="text-red-500 text-sm mb-4">{error}</p>}
                <button
                    type="submit"
                    disabled={!file || uploading}
                    className="w-full bg-blue-600 text-white py-2 px-4 rounded-lg disabled:opacity-50"
                    data-testid="submit-button"
                >
                    {uploading ? 'Uploading...' : 'Extract RFP Data'}
                </button>
            </form>
        </div>
    );
}
