import { useCallback, useEffect, useRef, useState } from 'react';

/** Runs an async loader and tracks data / error / loading. Call reload() to refetch. */
export function useLoad(loader, deps = []) {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const seq = useRef(0);

  const reload = useCallback(() => {
    const id = ++seq.current;
    setLoading(true);
    return loader()
      .then((d) => { if (id === seq.current) { setData(d); setError(null); } })
      .catch((e) => { if (id === seq.current) setError(e); })
      .finally(() => { if (id === seq.current) setLoading(false); });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  useEffect(() => { reload(); }, [reload]);
  return { data, error, loading, reload, setData };
}
