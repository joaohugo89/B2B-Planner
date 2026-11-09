import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { AppShell } from './components/AppShell';
import { RequireAuth } from './auth/RequireAuth';
import { LoadingState } from './components/primitives';
import { FeedGridPage } from './pages/FeedGridPage';

// Só o Feed Grid entra no bundle inicial. O resto — editor de canvas, estúdio de IA,
// Instagram — é baixado quando a aba é aberta: quem só olha o feed não paga por eles.
const PostDetailPage = lazy(() => import('./pages/PostDetailPage').then((m) => ({ default: m.PostDetailPage })));
const CopyGuidePage = lazy(() => import('./pages/CopyGuidePage').then((m) => ({ default: m.CopyGuidePage })));
const TemplateExportPage = lazy(() => import('./pages/TemplateExportPage').then((m) => ({ default: m.TemplateExportPage })));
const AiStudioPage = lazy(() => import('./pages/AiStudioPage').then((m) => ({ default: m.AiStudioPage })));
const PostStudioPage = lazy(() => import('./pages/PostStudioPage').then((m) => ({ default: m.PostStudioPage })));
const VideoStudioPage = lazy(() => import('./pages/VideoStudioPage').then((m) => ({ default: m.VideoStudioPage })));
const InstagramPage = lazy(() => import('./pages/InstagramPage').then((m) => ({ default: m.InstagramPage })));

export default function App() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        {/* Única tela aberta: mostra os posts já prontos para publicar. */}
        <Route index element={<FeedGridPage />} />

        {/* A bancada de trabalho. Sem conta, o endereço devolve para o Feed Grid. */}
        <Route path="posts" element={<Protegida><PostDetailPage /></Protegida>} />
        <Route path="copy" element={<Protegida><CopyGuidePage /></Protegida>} />
        <Route path="templates" element={<Protegida><TemplateExportPage /></Protegida>} />
        <Route path="ai" element={<Protegida><AiStudioPage /></Protegida>} />
        <Route path="studio" element={<Protegida><PostStudioPage /></Protegida>} />
        <Route path="video" element={<Protegida><VideoStudioPage /></Protegida>} />
        <Route path="instagram" element={<Protegida><InstagramPage /></Protegida>} />
        <Route path="instagram/callback" element={<Protegida><InstagramPage /></Protegida>} />

        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}

/** Exige conta e espera o código da tela chegar. */
function Protegida({ children }: { children: React.ReactNode }) {
  return (
    <RequireAuth>
      <Suspense fallback={<LoadingState label="Carregando tela…" />}>{children}</Suspense>
    </RequireAuth>
  );
}
