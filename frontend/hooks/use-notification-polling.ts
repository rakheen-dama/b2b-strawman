"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { fetchUnreadCount } from "@/lib/actions/notifications";

const POLL_INTERVAL_MS = 30_000;

export function useNotificationPolling() {
  const [unreadCount, setUnreadCount] = useState(0);
  const mountedRef = useRef(true);

  const refetch = useCallback(async () => {
    try {
      const result = await fetchUnreadCount();
      if (mountedRef.current) {
        setUnreadCount(result.count);
      }
    } catch {
      // Silently ignore polling errors — count stays stale
    }
  }, []);

  useEffect(() => {
    mountedRef.current = true;

    // Initial fetch — use a flag so the callback sets state only if still mounted
    let cancelled = false;
    fetchUnreadCount()
      .then((result) => {
        if (!cancelled) {
          setUnreadCount(result.count);
        }
      })
      .catch(() => {
        // Ignore initial fetch errors
      });

    const interval = setInterval(refetch, POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      mountedRef.current = false;
      clearInterval(interval);
    };
  }, [refetch]);

  return { unreadCount, refetch };
}
