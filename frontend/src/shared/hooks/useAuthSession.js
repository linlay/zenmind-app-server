import { useCallback, useEffect, useState } from 'react';
import { request } from '../api/apiClient';

export function useAuthSession() {
  const [loading, setLoading] = useState(true);
  const [session, setSession] = useState(null);

  const refresh = useCallback(async () => {
    try {
      const me = await request('/admin/api/session/me');
      setSession(me);
    } catch {
      setSession(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  return {
    loading,
    session,
    setSession,
    refresh
  };
}
