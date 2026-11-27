/**
 * A page's awaited `searchParams` as the `query` a server-paged list keeps (ADR-395, DA-8): every
 * non-empty string param, in the URL's order; repeated (`string[]`) and empty params are dropped.
 * The pager then sets `page` (`buildPageHref`). Pure: safe on the server and in a client wrapper.
 */
export function searchParamsToQuery(
  search: Readonly<Record<string, string | string[] | undefined>>
): Record<string, string> {
  return Object.fromEntries(
    Object.entries(search).filter(
      (entry): entry is [string, string] => typeof entry[1] === "string" && entry[1] !== ""
    )
  );
}
