import { useCallback, useEffect, useState } from 'react';
import { describeError } from './client';

interface ApiState<T> {
  data: T | null;
  loading: boolean;
  error: string | null;
  reload: () => void;
  /** Substitui o resultado sem refazer o request (ex.: resposta de outra chamada). */
  setData: (value: T) => void;
}

/** Executa um request e expõe loading/erro amigável para a UI. */
export function useApi<T>(fetcher: () => Promise<T>, deps: unknown[] = []): ApiState<T> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tick, setTick] = useState(0);

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const run = useCallback(fetcher, deps);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);

    run()
      .then((result) => {
        if (active) setData(result);
      })
      .catch((err) => {
        if (active) setError(describeError(err));
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [run, tick]);

  return { data, loading, error, reload: () => setTick((t) => t + 1), setData };
}
