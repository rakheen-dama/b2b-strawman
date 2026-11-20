import type {
  FlagSubject,
  FlagSubjectKind,
  IntegrityFlag,
  IntegrityFlagCode,
  IntegritySeverity,
} from "@/lib/types";

/**
 * The trust integrity check's words for the screen (Phase 92 649C, §16.3.9). No raw flag code,
 * severity or subject kind is ever printed: every one has a label here. The maps are exhaustive
 * `Record`s, so a code added to the union without a label fails typecheck.
 */
export const INTEGRITY_FLAG_LABELS: Record<IntegrityFlagCode, string> = {
  BANK_DIFFERENCE: "Bank and cashbook differ",
  BOOKS_OUT_OF_BALANCE: "Cashbook and trust creditors differ",
  INTER_ACCOUNT_NOT_ZERO: "Section 86(3) placements do not net to zero",
  UNIDENTIFIED_ITEMS_MISMATCH: "Unidentified deposits do not agree with their ledger",
  INVESTMENT_BALANCE_MISMATCH: "An investment does not agree with its ledger",
  UNRESOLVED_STATEMENT_LINES: "Statement lines not yet resolved",
  FUND_LEDGER_IN_DEBIT: "Fund interest and charges ledger in debit",
  CLIENT_LEDGER_IN_DEBIT: "Client ledger in debit",
  CASHBOOK_IN_DEBIT: "Trust banking account cashbook in debit",
  UNIDENTIFIED_OVER_ONE_YEAR: "Unidentified deposit held over a year",
  CHARGES_TO_REFUND: "Bank charges to refund from the business account",
  BACKDATED_AFTER_LOCK: "Entered after a monthly list was locked",
};

export const INTEGRITY_SEVERITY_LABELS: Record<IntegritySeverity, string> = {
  BLOCKING: "Blocking",
  WARNING: "Warning",
};

/** Shown for a subject the backend sent without a name. */
export const FLAG_SUBJECT_KIND_LABELS: Record<FlagSubjectKind, string> = {
  CLIENT: "Client",
  CREDITOR_ACCOUNT: "Trust creditor",
  UNIDENTIFIED_DEPOSIT: "Unidentified deposit",
  INVESTMENT: "Investment",
  BANK_STATEMENT: "Bank statement",
  TRUST_ACCOUNT: "Trust account",
  MONTHLY_LIST: "Monthly list",
};

/**
 * A flag's label. A code this build does not know yet (a later slice adds one) reads as a generic
 * label rather than the raw code.
 */
export function integrityFlagLabel(code: string): string {
  return INTEGRITY_FLAG_LABELS[code as IntegrityFlagCode] ?? "Trust books check";
}

export function integritySeverityLabel(severity: string): string {
  return INTEGRITY_SEVERITY_LABELS[severity as IntegritySeverity] ?? "Warning";
}

export function isBlockingFlag(flag: Pick<IntegrityFlag, "severity">): boolean {
  return flag.severity === "BLOCKING";
}

/** True when any flag blocks completing a reconciliation (gate G1–G4). */
export function hasBlockingFlag(flags: readonly Pick<IntegrityFlag, "severity">[]): boolean {
  return flags.some(isBlockingFlag);
}

/** Where a firm user goes to act on a subject; null when there is no page for it. */
export function integritySubjectHref(slug: string, subject: FlagSubject): string | null {
  const base = `/org/${slug}/trust-accounting`;
  switch (subject.kind) {
    case "CLIENT":
      return subject.id ? `${base}/client-ledgers/${encodeURIComponent(subject.id)}` : null;
    case "CREDITOR_ACCOUNT":
    case "UNIDENTIFIED_DEPOSIT":
      return `${base}/other-creditors`;
    case "INVESTMENT":
      return `${base}/investments`;
    case "BANK_STATEMENT":
      return `${base}/reconciliation`;
    case "MONTHLY_LIST":
      return subject.id
        ? `${base}/reconciliation/lists/${encodeURIComponent(subject.id)}`
        : `${base}/reconciliation`;
    default:
      return null;
  }
}

/** A flag that names no subject still links to the page where it is acted on. */
const FLAG_HREF_SUFFIX: Partial<Record<IntegrityFlagCode, { path: string; label: string }>> = {
  BANK_DIFFERENCE: { path: "reconciliation", label: "Reconciliation" },
  CHARGES_TO_REFUND: { path: "other-creditors", label: "Other trust creditors" },
  FUND_LEDGER_IN_DEBIT: { path: "other-creditors", label: "Other trust creditors" },
};

export function integrityFlagLink(
  slug: string,
  code: string
): { href: string; label: string } | null {
  const target = FLAG_HREF_SUFFIX[code as IntegrityFlagCode];
  return target
    ? { href: `/org/${slug}/trust-accounting/${target.path}`, label: target.label }
    : null;
}
