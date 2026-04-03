const DOCS_URL =
  process.env.NEXT_PUBLIC_DOCS_URL ?? "https://docs.heykazi.com";

/**
 * Construct a full URL to a documentation page.
 * @param path - Relative path on the doc site, e.g., "/features/invoicing"
 */
export function docsLink(path: string): string {
  return `${DOCS_URL}${path}`;
}
