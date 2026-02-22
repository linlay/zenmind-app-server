import { useCallback, useState } from 'react';

export function useAsyncAction() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const run = useCallback(async (action, options = {}) => {
    const { onError } = options;
    setLoading(true);
    setError('');

    try {
      return await action();
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Request failed';
      setError(message);
      if (onError) onError(message, err);
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  return {
    loading,
    error,
    setError,
    run
  };
}
