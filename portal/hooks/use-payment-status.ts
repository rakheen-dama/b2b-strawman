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
    const startTime = Date.now();
    const POLL_INTERVAL = 3000; // 3 seconds
    const MAX_DURATION = 30000; // 30 seconds

    async function poll() {
      if (cancelled) return;

      if (Date.now() - startTime >= MAX_DURATION) {
        setIsPolling(false);
        setIsTimeout(true);
        clearInterval(intervalId);
        return;
      }

      try {
        const data = await getPaymentStatus(invoiceId);
        if (cancelled) return;

        setStatus(data.status);
        setPaidAt(data.paidAt);

        if (data.status === "PAID") {
          setIsPolling(false);
          clearInterval(intervalId);
        }
      } catch {
        // Silently continue polling on error
      }
    }

    // Initial poll immediately
    poll();
    const intervalId = setInterval(poll, POLL_INTERVAL);

    return () => {
      cancelled = true;
      clearInterval(intervalId);
    };
  }, [invoiceId]);

  return { status, paidAt, isPolling, isTimeout };
}
