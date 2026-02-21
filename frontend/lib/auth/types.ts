/**
 * Platform-owned auth types.
 * These interfaces decouple domain code from any specific auth provider (Clerk, Auth0, etc.).
 * All server-side auth functions return these types — never provider-specific types.
 */

/** Replaces auth() destructure from Clerk. Contains org + user identity. */
export interface AuthContext {
  orgId: string;
  orgSlug: string;
  orgRole: string;
  userId: string;
}

/** Replaces currentUser() / useUser() from Clerk. User profile info. */
export interface AuthUser {
  firstName: string | null;
  lastName: string | null;
  email: string;
  imageUrl: string | null;
}

/** Replaces useOrganization().memberships from Clerk. Single org member. */
export interface OrgMemberInfo {
  id: string;
  role: string;
  email: string;
  name: string;
}
