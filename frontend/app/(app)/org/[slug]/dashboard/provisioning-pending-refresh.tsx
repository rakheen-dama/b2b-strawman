"use client";

import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";

const POLL_INTERVAL_MS = 2000;
const MAX_POLLS = 15; // 30 seconds max

export function ProvisioningPendingRefresh() {
  const router = useRouter();
  const pollCount = useRef(0);
  const [timedOut, setTimedOut] = useState(false);

  useEffect(() => {
    const interval = setInterval(() => {
      pollCount.current += 1;
      if (pollCount.current >= MAX_POLLS) {
        clearInterval(interval);
        setTimedOut(true);
        return;
      }
      router.refresh();
    }, POLL_INTERVAL_MS);

    return () => clearInterval(interval);
  }, [router]);

  if (timedOut) {
    return (
      <p className="text-muted-foreground text-sm">
        Taking longer than expected. Please{" "}
        <button
          onClick={() => window.location.reload()}
          className="text-foreground underline underline-offset-4"
        >
          refresh the page
        </button>
        .
      </p>
    );
  }

  return null;
}
