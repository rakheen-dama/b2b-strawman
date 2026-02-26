"use client";

import { useEffect, useState } from "react";
import { getPaymentStatus } from "@/lib/api-client";

interface UsePaymentStatusReturn {
  status: string | null;
  paidAt: string | null;
  isPolling: boolean;
  isTimeout: boolean;
}

export function usePaymentStatus(invoiceId: string): UsePaymentStatusReturn {
  const [status, setStatus] = useState<string | null>(null);
  const [paidAt, setPaidAt] = useState<string | null>(null);
  const [isPolling, setIsPolling] = useState(true);
  const [isTimeout, setIsTimeout] = useState(false);

  useEffect(() => {
    let cancelled = false;
    const timer = { id: undefined as ReturnType<typeof setInterval> | undefined };
    const startTime = Date.now();
    const POLL_INTERVAL = 3000; // 3 seconds
    const MAX_DURATION = 30000; // 30 seconds

    function stopPolling() {
      if (timer.id !== undefined) {
        clearInterval(timer.id);
        timer.id = undefined;
      }
    }

    async function poll() {
      if (cancelled) return;

      if (Date.now() - startTime >= MAX_DURATION) {
        setIsPolling(false);
        // Only timeout if we haven't already confirmed PAID
        setStatus((currentStatus) => {
          if (currentStatus !== "PAID") {
            setIsTimeout(true);
          }
          return currentStatus;
        });
        stopPolling();
        return;
      }

      try {
        const data = await getPaymentStatus(invoiceId);
        if (cancelled) return;

        setStatus(data.status);
        setPaidAt(data.paidAt);

        if (data.status === "PAID") {
          setIsPolling(false);
          setIsTimeout(false);
          stopPolling();
        }
      } catch {
        // Silently continue polling on error
      }
    }

    // Initial poll immediately, then start interval
    poll();
    timer.id = setInterval(poll, POLL_INTERVAL);

    return () => {
      cancelled = true;
      stopPolling();
    };
  }, [invoiceId]);

  return { status, paidAt, isPolling, isTimeout };
}
