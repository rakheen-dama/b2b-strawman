"use client";

import { useEffect, useState } from "react";

/**
 * Returns the current time in ms, or 0 during SSR / pre-mount. Client-only —
 * avoids hydration mismatch when used as a "now" reference for relative-time
 * rendering (e.g. expiry countdowns in `proposal-table`).
 *
 * <p>Originally introduced in PR #1231 with the OBS-704 issue ID, but the
 * canonical OBS-704 mismatch turned out to be a Radix `aria-controls` issue
 * unrelated to timestamps (see {@code create-proposal-dialog.tsx} and the v3
 * cleanup that removed the mount-gate workaround). This hook still guards a
 * legitimate but separate hydration class — `Date.now()` returns different
 * values on the server vs the client, and any component that renders a
 * relative-time string from a "now" anchor will mismatch on hydration unless
 * the anchor is captured client-only. Returning 0 pre-mount lets consumers
 * render a safe fallback (e.g. "—") on SSR + first commit, then switch to a
 * live value after `useEffect` runs.
 */
export function useNowMs(): number {
  const [now, setNow] = useState(0);
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- SSR hydration: client-only timestamp captured once after mount to avoid SSR/client mismatch on relative-time renders.
    setNow(Date.now());
  }, []);
  return now;
}
