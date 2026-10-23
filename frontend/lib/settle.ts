import "server-only";

import { ApiError } from "@/lib/api/client";
import { rethrowOnExpiredSession } from "@/lib/auth/expiry-server";
import type { WidgetResult } from "@/lib/dashboard-home";

// One gated server read → WidgetResult. Shared by the Home fan-out (lib/dashboard-home-fetch.ts)
// and the Fee notes summary tiles (invoice-crud-actions.ts), so "forbidden" means the same thing
// on both pages.

const ABSENT = { state: "absent" } as const;

/**
 * One gated read. Not in the plan → absent without a request. A 403/404 → absent too (module off,
 * capability missing, no trust account): the widget drops out rather than greying. An expired
 * session (401) goes to the branded re-login. Anything else → a quiet error line in the card.
 * Never handleApiError here: it notFound()s on a 404 and would take the whole page down.
 */
export async function settle<T>(
  enabled: boolean,
  label: string,
  read: () => Promise<T>
): Promise<WidgetResult<T>> {
  if (!enabled) return ABSENT;
  try {
    return { state: "ok", data: await read() };
  } catch (error) {
    await rethrowOnExpiredSession(error);
    if (error instanceof ApiError && (error.status === 403 || error.status === 404)) {
      return ABSENT;
    }
    console.error(`Failed to load ${label}:`, error);
    return { state: "error" };
  }
}
