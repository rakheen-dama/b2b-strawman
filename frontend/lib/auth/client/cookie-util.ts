/**
 * Shared utility for reading the mock-auth-token cookie.
 * Used by both MockAuthContextProvider and client hooks.
 */
export function getTokenFromDocumentCookie(): string | null {
  if (typeof document === "undefined") return null;
  const match = document.cookie.match(/(?:^|;\s*)mock-auth-token=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}
