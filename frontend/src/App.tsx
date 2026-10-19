import { Navigate, Route, Routes } from 'react-router-dom';
import { AppShell } from './components/AppShell';
import { FeedGridPage } from './pages/FeedGridPage';
import { PostDetailPage } from './pages/PostDetailPage';
import { CopyGuidePage } from './pages/CopyGuidePage';
import { TemplateExportPage } from './pages/TemplateExportPage';
import { AiStudioPage } from './pages/AiStudioPage';
import { PostStudioPage } from './pages/PostStudioPage';
import { InstagramPage } from './pages/InstagramPage';

export default function App() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<FeedGridPage />} />
        <Route path="posts" element={<PostDetailPage />} />
        <Route path="copy" element={<CopyGuidePage />} />
        <Route path="templates" element={<TemplateExportPage />} />
        <Route path="ai" element={<AiStudioPage />} />
        <Route path="studio" element={<PostStudioPage />} />
        <Route path="instagram" element={<InstagramPage />} />
        <Route path="instagram/callback" element={<InstagramPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
