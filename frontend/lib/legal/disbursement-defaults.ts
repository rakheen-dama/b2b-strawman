import type { DisbursementCategory, VatTreatment } from "@/lib/api/legal-disbursements";

/**
 * Default VAT treatment for a given disbursement category.
 * These defaults mirror the backend service logic (phase 67, epic 486).
 *
 * Category defaults:
 *   - Sheriff, deeds office, court fees → ZERO_RATED_PASS_THROUGH
 *     (statutory fees with no VAT charged)
 *   - Counsel, advocate, search, expert witness, travel, other → STANDARD_15
 *     (commercial services subject to standard VAT)
 *
 * The user can always override in the form; this function only seeds the
 * initial value whenever the user hasn't manually chosen a treatment yet.
 */
export function defaultVatTreatmentForCategory(category: DisbursementCategory): VatTreatment {
  switch (category) {
    case "SHERIFF_FEES":
    case "DEEDS_OFFICE_FEES":
    case "COURT_FEES":
      return "ZERO_RATED_PASS_THROUGH";
    case "COUNSEL_FEES":
    case "ADVOCATE_FEES":
    case "SEARCH_FEES":
    case "EXPERT_WITNESS":
    case "TRAVEL":
    case "OTHER":
    default:
      return "STANDARD_15";
  }
}

export const DISBURSEMENT_CATEGORY_OPTIONS: {
  value: DisbursementCategory;
  label: string;
}[] = [
  { value: "SHERIFF_FEES", label: "Sheriff Fees" },
  { value: "COUNSEL_FEES", label: "Counsel Fees" },
  { value: "SEARCH_FEES", label: "Search Fees" },
  { value: "DEEDS_OFFICE_FEES", label: "Deeds Office Fees" },
  { value: "COURT_FEES", label: "Court Fees" },
  { value: "ADVOCATE_FEES", label: "Advocate Fees" },
  { value: "EXPERT_WITNESS", label: "Expert Witness" },
  { value: "TRAVEL", label: "Travel" },
  { value: "OTHER", label: "Other" },
];

export const VAT_TREATMENT_OPTIONS: { value: VatTreatment; label: string }[] = [
  { value: "STANDARD_15", label: "Standard (15%)" },
  { value: "ZERO_RATED_PASS_THROUGH", label: "Zero-rated pass-through" },
  { value: "EXEMPT", label: "Exempt" },
];

export const APPROVAL_STATUS_OPTIONS: {
  value: "DRAFT" | "PENDING_APPROVAL" | "APPROVED" | "REJECTED";
  label: string;
}[] = [
  { value: "DRAFT", label: "Draft" },
  { value: "PENDING_APPROVAL", label: "Pending Approval" },
  { value: "APPROVED", label: "Approved" },
  { value: "REJECTED", label: "Rejected" },
];

export const BILLING_STATUS_OPTIONS: {
  value: "UNBILLED" | "BILLED" | "WRITTEN_OFF";
  label: string;
}[] = [
  { value: "UNBILLED", label: "Unbilled" },
  { value: "BILLED", label: "Billed" },
  { value: "WRITTEN_OFF", label: "Written Off" },
];
