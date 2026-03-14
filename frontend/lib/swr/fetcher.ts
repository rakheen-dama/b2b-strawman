/**
 * SWR fetcher utilities for client-side data caching.
 *
 * This project uses server actions (not direct API calls) for client-side
 * data fetching. SWR wraps these server actions to provide caching,
 * deduplication, revalidation, and polling.
 *
 * Usage:
 *   const { data, error, isLoading } = useSWR(
 *     open ? "org-members" : null,   // null key = paused (don't fetch)
 *     () => fetchOrgMembers()
 *   );
 *
 * For polling:
 *   const { data } = useSWR("unread-count", () => fetchUnreadCount(), {
 *     refreshInterval: 30_000,
 *   });
 */

import type { SWRConfiguration } from "swr";

/**
 * Default SWR options for the project.
 *
 * - revalidateOnFocus: true — refetch when user returns to tab
 * - dedupingInterval: 2000 — deduplicate identical requests within 2s
 * - errorRetryCount: 3 — retry failed requests up to 3 times
 */
export const defaultSWROptions: SWRConfiguration = {
  revalidateOnFocus: true,
  dedupingInterval: 2000,
  errorRetryCount: 3,
};

/**
 * Creates a stable SWR key for conditional fetching in dialogs/sheets.
 * Returns null when the condition is false, pausing the fetch.
 *
 * Example:
 *   useSWR(conditionalKey(open, "customers"), () => fetchCustomers())
 */
export function conditionalKey(
  condition: boolean,
  key: string
): string | null {
  return condition ? key : null;
}
