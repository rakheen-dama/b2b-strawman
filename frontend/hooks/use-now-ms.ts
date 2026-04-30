"use client";

import { useEffect, useState } from "react";

/**
 * Returns the current time in ms, or 0 during SSR / pre-mount. Client-only —
 * avoids hydration mismatch when used as a "now" reference for relative-time
 * rendering (e.g. expiry countdowns).
 */
export function useNowMs(): number {
  const [now, setNow] = useState(0);
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- SSR hydration: client-only timestamp captured once after mount to avoid SSR/client mismatch on relative-time renders.
    setNow(Date.now());
  }, []);
  return now;
}
