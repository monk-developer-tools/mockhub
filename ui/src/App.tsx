import { createBrowserRouter, Navigate, RouterProvider } from 'react-router-dom';
import { Layout } from './components/Layout';
import { Loading } from './components/Loading';
import { ToastProvider } from './components/Toasts';
import { DashboardPage } from './pages/DashboardPage';
import { ExamplesPage } from './pages/ExamplesPage';
import { LoginPage } from './pages/LoginPage';
import { ImportExportTab } from './pages/project/ImportExportTab';
import { OverviewTab } from './pages/project/OverviewTab';
import { ProjectPage } from './pages/project/ProjectPage';
import { ProjectSettingsTab } from './pages/project/ProjectSettingsTab';
import { RequestsTab } from './pages/project/RequestsTab';
import { ScenariosTab } from './pages/project/ScenariosTab';
import { StubsTab } from './pages/project/StubsTab';
import { AiSettingsPage } from './pages/AiSettingsPage';
import { BackupPage } from './pages/BackupPage';
import { OrgPage } from './pages/OrgPage';
import { AssistantTab } from './pages/project/AssistantTab';
import { ServerPage } from './pages/ServerPage';
import { SsoPage } from './pages/SsoPage';
import { UsersPage } from './pages/UsersPage';
import { AppProvider, useApp } from './state/AppContext';

const router = createBrowserRouter(
  [
    {
      path: '/',
      element: <Layout />,
      children: [
        { index: true, element: <DashboardPage /> },
        { path: 'examples', element: <ExamplesPage /> },
        { path: 'server', element: <ServerPage /> },
        { path: 'org', element: <OrgPage /> },
        { path: 'admin/users', element: <UsersPage /> },
        { path: 'admin/sso', element: <SsoPage /> },
        { path: 'admin/ai', element: <AiSettingsPage /> },
        { path: 'admin/backup', element: <BackupPage /> },
        {
          path: 'projects/:projectId',
          element: <ProjectPage />,
          children: [
            { index: true, element: <Navigate to="overview" replace /> },
            { path: 'overview', element: <OverviewTab /> },
            { path: 'stubs', element: <StubsTab /> },
            { path: 'stubs/:stubId', element: <StubsTab /> },
            { path: 'assistant', element: <AssistantTab /> },
            { path: 'requests', element: <RequestsTab /> },
            { path: 'scenarios', element: <ScenariosTab /> },
            { path: 'import-export', element: <ImportExportTab /> },
            { path: 'settings', element: <ProjectSettingsTab /> },
          ],
        },
        { path: '*', element: <Navigate to="/" replace /> },
      ],
    },
  ],
  { basename: '/__admin/mockhub/ui', future: { v7_relativeSplatPath: true } },
);

function Gate() {
  const { needsLogin, session } = useApp();
  if (needsLogin) return <LoginPage />;
  if (!session) return <Loading label="Connecting to MockHub…" />;
  return <RouterProvider router={router} future={{ v7_startTransition: true }} />;
}

export function App() {
  return (
    <ToastProvider>
      <AppProvider>
        <Gate />
      </AppProvider>
    </ToastProvider>
  );
}
