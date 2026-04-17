"use client";

import { useOrgProfile } from "@/lib/org-profile";

export type ProfileId =
  | "consulting-za"
  | "legal-za"
  | "accounting-za"
  | "consulting-generic"
  | null;

const KNOWN_PROFILE_IDS = new Set<Exclude<ProfileId, null>>([
  "consulting-za",
  "legal-za",
  "accounting-za",
  "consulting-generic",
]);

/**
 * Returns the active vertical profile ID from OrgProfileProvider, narrowed
 * to the known profile union. Returns null if no profile is set, or if the
 * backend returns an unknown profile string (data drift / future additions).
 *
 * Must be used inside an OrgProfileProvider; throws if used outside.
 */
export function useProfile(): ProfileId {
  const { verticalProfile } = useOrgProfile();
  if (!verticalProfile) return null;
  return KNOWN_PROFILE_IDS.has(verticalProfile as Exclude<ProfileId, null>)
    ? (verticalProfile as Exclude<ProfileId, null>)
    : null;
}
