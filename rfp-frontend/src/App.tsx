import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { UploadPage } from './pages/UploadPage';
import { JobStatusPage } from './pages/JobStatusPage';
import { JobsListPage } from './pages/JobsListPage';
import { ResultPage } from './pages/ResultPage';

export function App() {
    return (
        <BrowserRouter>
            <Routes>
                <Route path="/" element={<Navigate to="/jobs" replace />} />
                <Route path="/upload" element={<UploadPage />} />
                <Route path="/jobs" element={<JobsListPage />} />
                <Route path="/job/:jobId" element={<JobStatusPage />} />
                <Route path="/result/:jobId" element={<ResultPage />} />
            </Routes>
        </BrowserRouter>
    );
}

export default App;
