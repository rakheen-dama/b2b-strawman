import type { PerformConflictCheckFormData } from "@/lib/schemas/legal";

/** Search params the conflict-check page prefills its form from. */
export interface ConflictCheckSearchParams {
  customerId?: string;
  checkedName?: string;
  checkedIdNumber?: string;
  checkType?: string;
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
 * Form defaults from the page's search params. Callers: the client overflow menu
 * (customerId + checkedName) and the New Matter dialog link-out (+ checkedIdNumber,
 * checkType=NEW_MATTER, Phase 88 G-A6). An unknown checkType is ignored (form default applies).
 */
export function buildConflictCheckFormDefaults(
  searchParams: ConflictCheckSearchParams
): Partial<PerformConflictCheckFormData> {
  const defaults: Partial<PerformConflictCheckFormData> = {};
  if (searchParams.customerId) defaults.customerId = searchParams.customerId;
  if (searchParams.checkedName) defaults.checkedName = searchParams.checkedName;
  if (searchParams.checkedIdNumber) defaults.checkedIdNumber = searchParams.checkedIdNumber;
  if (searchParams.checkType && isCheckType(searchParams.checkType)) {
    defaults.checkType = searchParams.checkType;
  }
  return defaults;
}
