import type { PerformConflictCheckFormData } from "@/lib/schemas/legal";

/** Search params the conflict-check page prefills its form from. */
export interface ConflictCheckSearchParams {
  customerId?: string;
  checkedName?: string;
  checkedIdNumber?: string;
  checkType?: string;
}

/** The `customerId` client, resolved server-side by the page (`getClient`). */
export interface ConflictCheckClientDetails {
  name: string;
  idNumber: string | null;
}

const CHECK_TYPES: readonly PerformConflictCheckFormData["checkType"][] = [
  "NEW_CLIENT",
  "NEW_MATTER",
  "PERIODIC_REVIEW",
];

function isCheckType(value: string): value is PerformConflictCheckFormData["checkType"] {
  return (CHECK_TYPES as readonly string[]).includes(value);
}

/**
 * Form defaults from the page's search params, filled in from the resolved `customerId` client.
 * Callers: the client overflow menu (customerId + checkedName) and the New Matter dialog link-out
 * (customerId + checkType=NEW_MATTER, Phase 88 G-A6). The ID number never travels in the URL
 * (POPIA, ruling R-17): it comes from the client's structural `idNumber`. Explicit query values
 * win over the client's. An unknown checkType is ignored (form default applies).
 */
export function buildConflictCheckFormDefaults(
  searchParams: ConflictCheckSearchParams,
  client?: ConflictCheckClientDetails | null
): Partial<PerformConflictCheckFormData> {
  const defaults: Partial<PerformConflictCheckFormData> = {};
  if (searchParams.customerId) defaults.customerId = searchParams.customerId;

  const checkedName = searchParams.checkedName || client?.name.trim();
  if (checkedName) defaults.checkedName = checkedName;

  const checkedIdNumber = searchParams.checkedIdNumber || client?.idNumber?.trim();
  if (checkedIdNumber) defaults.checkedIdNumber = checkedIdNumber;

  if (searchParams.checkType && isCheckType(searchParams.checkType)) {
    defaults.checkType = searchParams.checkType;
  }
  return defaults;
}
