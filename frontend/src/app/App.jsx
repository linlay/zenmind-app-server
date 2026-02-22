import { Navigate, Route, Routes, useNavigate } from 'react-router-dom';
import { LoginPage } from '../features/auth/LoginPage';
import { request } from '../shared/api/apiClient';
import { useAuthSession } from '../shared/hooks/useAuthSession';
import { LoadingOverlay } from '../shared/ui/LoadingOverlay';
import { ToastViewport } from '../shared/ui/ToastViewport';
import { toast } from '../shared/ui/toast';
import { AppLayout } from './layout/AppLayout';

export default function App() {
  const navigate = useNavigate();
  const { loading, session, setSession } = useAuthSession();

  const logout = async () => {
    try {
      await request('/admin/api/session/logout', { method: 'POST' });
      setSession(null);
      navigate('/login');
      toast.success('Signed out');
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Sign out failed');
    }
  };

  if (loading) {
    return (
      <>
        <LoadingOverlay show label="Checking session..." />
        <ToastViewport />
      </>
    );
  }

  return (
    <>
      <Routes>
        <Route
          path="/login"
          element={session ? <Navigate to="/users" replace /> : <LoginPage onLogin={setSession} />}
        />
        <Route
          path="/*"
          element={session ? <AppLayout session={session} onLogout={logout} /> : <Navigate to="/login" replace />}
        />
      </Routes>
      <ToastViewport />
    </>
  );
}
