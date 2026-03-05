import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { submitRfp } from '@/api/rfpClient';
import { Button } from '@/components/ui/button';
import { Alert, AlertDescription } from '@/components/ui/alert';

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
        <div className="max-w-md mx-auto">
            <form
                onSubmit={(e) => { void handleSubmit(e); }}
                className="rounded-lg border bg-card p-8 space-y-6"
            >
                <div className="flex justify-between items-center">
                    <h1 className="text-2xl font-bold text-card-foreground">
                        Upload RFP Document
                    </h1>
                    <Link to="/jobs" className="text-sm text-primary hover:underline">
                        View Jobs
                    </Link>
                </div>

                <input
                    type="file"
                    accept=".pdf,.docx"
                    onChange={handleFileChange}
                    className="block w-full text-sm text-muted-foreground"
                    data-testid="file-input"
                />

                {error && (
                    <Alert variant="destructive">
                        <AlertDescription>{error}</AlertDescription>
                    </Alert>
                )}

                <Button
                    type="submit"
                    disabled={!file || uploading}
                    className="w-full"
                    data-testid="submit-button"
                >
                    {uploading ? 'Uploading...' : 'Extract RFP Data'}
                </Button>
            </form>
        </div>
    );
}
