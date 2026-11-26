/**
 * The href of page `pageIndex` (zero-based) of a server-paged list (ADR-395, architecture §18.2.5):
 * every non-empty param of `query` is kept in its order and `param` is set — replaced in place when
 * present, appended otherwise (`URLSearchParams.set`). This is the transactions page's `pageUrl`
 * (`trust-accounting/transactions/page.tsx`); other-creditors pages two lists with it (`page` and
 * `itemsPage`). Pure: no `server-only`, safe in a client wrapper building DataTable's
 * `pagination.hrefFor`.
 */
export function buildPageHref(
  basePath: string,
  query: Readonly<Record<string, string>>,
  pageIndex: number,
  param = "page"
): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value) params.set(key, value);
  }
  params.set(param, String(pageIndex));
  return `${basePath}?${params.toString()}`;
}
