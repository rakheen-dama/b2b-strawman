/**
 * Shared JWT utilities used by both Keycloak and Mock auth providers.
 */

/**
 * Decode the payload (claims) from a JWT without verifying the signature.
 * Used client-side / server-side to extract org claims from access tokens.
 */
export function decodeJwtPayload(token: string): Record<string, unknown> {
  const parts = token.split(".");
  if (parts.length !== 3) {
    throw new Error("Invalid JWT format — expected 3 parts");
  }
  // Base64url decode: replace URL-safe chars, add padding
  const base64 = parts[1].replace(/-/g, "+").replace(/_/g, "/");
  return JSON.parse(atob(base64));
}
