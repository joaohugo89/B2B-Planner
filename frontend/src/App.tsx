import { Navigate, Route, Routes } from 'react-router-dom';
import { AppShell } from './components/AppShell';
import { RequireAuth } from './auth/RequireAuth';
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
        {/* Única tela aberta: mostra os posts já prontos para publicar. */}
        <Route index element={<FeedGridPage />} />

        {/* A bancada de trabalho. Sem conta, o endereço devolve para o Feed Grid. */}
        <Route path="posts" element={<RequireAuth><PostDetailPage /></RequireAuth>} />
        <Route path="copy" element={<RequireAuth><CopyGuidePage /></RequireAuth>} />
        <Route path="templates" element={<RequireAuth><TemplateExportPage /></RequireAuth>} />
        <Route path="ai" element={<RequireAuth><AiStudioPage /></RequireAuth>} />
        <Route path="studio" element={<RequireAuth><PostStudioPage /></RequireAuth>} />
        <Route path="instagram" element={<RequireAuth><InstagramPage /></RequireAuth>} />
        <Route path="instagram/callback" element={<RequireAuth><InstagramPage /></RequireAuth>} />

        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
