import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { UploadPage } from './pages/UploadPage';
import { JobStatusPage } from './pages/JobStatusPage';
import { ResultPage } from './pages/ResultPage';

export function App() {
    return (
        <BrowserRouter>
            <Routes>
                <Route path="/" element={<UploadPage />} />
                <Route path="/job/:jobId" element={<JobStatusPage />} />
                <Route path="/result/:jobId" element={<ResultPage />} />
            </Routes>
        </BrowserRouter>
    );
}

export default App;
