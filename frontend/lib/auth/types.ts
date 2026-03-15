/**
 * Platform-owned auth types.
 * These interfaces decouple domain code from any specific auth provider (Keycloak, etc.).
 * All server-side auth functions return these types — never provider-specific types.
 */

/**
 * Lightweight identity for guards that do NOT require an active org context.
 * Used by platform-admin layout where a user may belong to groups but have
 * no org selected yet.
 */
export interface SessionIdentity {
  userId: string;
  /** Keycloak groups the user belongs to (e.g. ["platform-admins"]) */
  groups: string[];
}

/** Org-scoped auth context. Contains org + user identity. */
export interface AuthContext {
  orgId: string;
  orgSlug: string;
  userId: string;
  /** Keycloak groups the user belongs to (e.g. ["platform-admins"]) */
  groups: string[];
}

/** User profile info. */
export interface AuthUser {
  firstName: string | null;
  lastName: string | null;
  email: string;
  imageUrl: string | null;
}

/** Single org member. */
export interface OrgMemberInfo {
  id: string;
  role: string;
  email: string;
  name: string | null;
}
