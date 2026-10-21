import "server-only";

import { unstable_rethrow } from "next/navigation";
import { ApiError, resolveServerReturnTo } from "@/lib/api/client";
import { redirectToReLogin } from "./expiry";

/**
 * Expired-session funnel for server loaders that otherwise fail soft. Given the errors a loader
 * caught, it first rethrows framework control flow (e.g. a re-login redirect thrown deeper down,
 * which must not be swallowed), then sends a backend 401 — incl. the manual-redirect 3xx mapped to
 * 401 in apiRequest — to the branded re-login for the current path. Returns normally otherwise, so
 * the caller can degrade.
 *
 * Lives apart from `./expiry` because that module is client-safe and this one needs the
 * server-only API client.
 */
export async function rethrowOnExpiredSession(...errors: unknown[]): Promise<void> {
  for (const error of errors) unstable_rethrow(error);
  if (errors.some((error) => error instanceof ApiError && error.status === 401)) {
    redirectToReLogin(await resolveServerReturnTo(), "expired");
  }
}
