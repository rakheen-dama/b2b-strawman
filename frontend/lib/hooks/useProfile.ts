"use client";

import { useOrgProfile } from "@/lib/org-profile";

export type ProfileId =
  | "consulting-za"
  | "legal-za"
  | "accounting-za"
  | "consulting-generic"
  | null;

/**
 * Returns the active vertical profile ID from OrgProfileProvider, narrowed
 * to the known profile union. Returns null if no profile is set.
 *
 * Must be used inside an OrgProfileProvider; throws if used outside.
 */
export function useProfile(): ProfileId {
  const { verticalProfile } = useOrgProfile();
  return verticalProfile as ProfileId;
}
