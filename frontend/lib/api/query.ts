/**
 * Internal query-string helper shared by the `./matters.ts` and `./clients.ts` API modules.
 * Not exported from the `@/lib/api` barrel.
 */

export type QueryValue = string | number | boolean | null | undefined;

/** `?a=1&b=2` from defined, non-empty values (insertion order kept); `""` when nothing is set. */
export function toQuery(params?: Record<string, QueryValue>): string {
  if (!params) return "";
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === "") continue;
    search.set(key, String(value));
  }
  const qs = search.toString();
  return qs ? `?${qs}` : "";
}
