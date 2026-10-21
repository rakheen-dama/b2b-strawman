/**
 * One Diary — wire types and pure helpers (Phase 87 613B; ADR-339; arch §12.2.6, §12.6.2).
 * No "use server" / "use client": imported by page.tsx (server), diary-actions.ts and client views.
 * Pure helpers live here because a "use server" file may export only async functions.
 */

export type DiaryKind = "TASK_DUE" | "MATTER_DUE" | "COURT_DATE" | "PRESCRIPTION";

/** Display order = backend within-day rank (court dates first — immovable; tasks last). */
export const DIARY_KINDS: readonly DiaryKind[] = [
  "COURT_DATE",
  "PRESCRIPTION",
  "MATTER_DUE",
  "TASK_DUE",
];

/** Chip labels (plural, sentence case). */
export const DIARY_KIND_LABELS: Record<DiaryKind, string> = {
  COURT_DATE: "Court dates",
  PRESCRIPTION: "Prescriptions",
  MATTER_DUE: "Matter due dates",
  TASK_DUE: "Tasks",
};

/**
 * GET /api/diary row (DiaryEventResponse.java). The backend is @JsonInclude(NON_NULL): nullable
 * fields are OMITTED from the JSON, never sent as null — always read them with `?? null`.
 */
export interface DiaryEventResponse {
  id: string;
  kind: DiaryKind;
  /** yyyy-MM-dd */
  date: string;
  /** "HH:mm:ss" — COURT_DATE with a scheduled time only. */
  time?: string | null;
  title: string;
  subtype?: string | null;
  status: string;
  matterId: string;
  matterName: string;
  matterReference?: string | null;
  clientId?: string | null;
  clientName?: string | null;
  feeEarnerId?: string | null;
  feeEarnerName?: string | null;
  overdue: boolean;
  /** Relative to /org/{slug}, e.g. "/matters/{id}?tab=court-dates&courtDateId={id}". */
  href: string;
}

export interface DiaryResult {
  events: DiaryEventResponse[];
  /** Parsed X-Diary-Types — the kinds the server agreed to serve (may be empty). */
  typesServed: DiaryKind[];
}

export interface DiaryQueryParams {
  from: string;
  to: string;
  types?: DiaryKind[];
  feeEarnerId?: string;
  matterId?: string;
  includeOverdue?: boolean;
}

/** 613B ships Week + List; Day and Month arrive in 613C. */
export type DiaryView = "week" | "list";

export interface DiaryMemberOption {
  id: string;
  name: string;
  email: string;
}

export interface DiaryMatterOption {
  id: string;
  name: string;
  referenceNumber: string | null;
}

export interface DiaryFilters {
  kinds: DiaryKind[];
  feeEarnerId: string | null;
  matterId: string | null;
}

/** §12.6.2: fee earner is filtered client-side unless the window exceeds 31 days. */
export const FEE_EARNER_CLIENT_FILTER_MAX_DAYS = 31;

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

/** Strict yyyy-MM-dd (UTC round-trip rejects 2026-02-31) — same idiom as invoices/unbilled/page.tsx. */
export function isValidIsoDate(value: string | null | undefined): value is string {
  if (!value || !ISO_DATE.test(value)) return false;
  const [y, m, d] = value.split("-").map(Number);
  const date = new Date(Date.UTC(y, m - 1, d));
  return date.getUTCFullYear() === y && date.getUTCMonth() === m - 1 && date.getUTCDate() === d;
}

/** Local-calendar yyyy-MM-dd. Never use toISOString() (UTC shift). */
export function toIsoDate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

/** Local Date at midnight for a yyyy-MM-dd string. Never `new Date("yyyy-MM-dd")` (parses as UTC). */
export function parseIsoDate(iso: string): Date {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(y, m - 1, d);
}

export function addDays(iso: string, days: number): string {
  const d = parseIsoDate(iso);
  d.setDate(d.getDate() + days);
  return toIsoDate(d);
}

/** Monday of the ISO week containing `iso` (Mon–Sun weeks). */
export function mondayOf(iso: string): string {
  const d = parseIsoDate(iso);
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7));
  return toIsoDate(d);
}

/** Whole days from `from` to `to` (same arithmetic as the backend's ChronoUnit.DAYS.between). */
export function daysBetween(from: string, to: string): number {
  const [fy, fm, fd] = from.split("-").map(Number);
  const [ty, tm, td] = to.split("-").map(Number);
  return Math.round((Date.UTC(ty, tm - 1, td) - Date.UTC(fy, fm - 1, fd)) / 86_400_000);
}

/** 613B ships Week + List; day/month (613C) and junk fall back to week. */
export function parseDiaryView(value: string | null | undefined): DiaryView {
  return value === "list" ? "list" : "week";
}

/** `?types=` CSV → known kinds in DIARY_KINDS order; unknown tokens dropped; [] when none. */
export function parseDiaryKinds(csv: string | null | undefined): DiaryKind[] {
  const tokens = new Set((csv ?? "").split(",").map((t) => t.trim()));
  return DIARY_KINDS.filter((k) => tokens.has(k));
}

/**
 * X-Diary-Types → served kinds, in DIARY_KINDS order. DiaryController always sets the header;
 * an empty or missing header means no kinds were served. Unknown tokens are ignored.
 */
export function parseTypesHeader(header: string | null | undefined): DiaryKind[] {
  return parseDiaryKinds(header);
}

/** Query string for GET /api/diary (no leading "?"). */
export function buildDiaryQuery(p: DiaryQueryParams): string {
  const qs = new URLSearchParams();
  qs.set("from", p.from);
  qs.set("to", p.to);
  if (p.types && p.types.length > 0) qs.set("types", p.types.join(","));
  if (p.feeEarnerId) qs.set("feeEarnerId", p.feeEarnerId);
  if (p.matterId) qs.set("matterId", p.matterId);
  if (p.includeOverdue === false) qs.set("includeOverdue", "false");
  return qs.toString();
}

/** True when the window is wide enough that fee earner must be a server param (§12.6.2). */
export function shouldUseServerFeeEarnerFilter(from: string, to: string): boolean {
  return daysBetween(from, to) > FEE_EARNER_CLIENT_FILTER_MAX_DAYS;
}

/** Client-side filters over the loaded window (§12.6.2). Preserves server order. */
export function filterDiaryEvents(
  events: DiaryEventResponse[],
  f: DiaryFilters
): DiaryEventResponse[] {
  return events.filter(
    (e) =>
      f.kinds.includes(e.kind) &&
      (f.feeEarnerId === null || (e.feeEarnerId ?? null) === f.feeEarnerId) &&
      (f.matterId === null || e.matterId === f.matterId)
  );
}

/** Overdue band = rows dated before the window (server appends them when includeOverdue). */
export function partitionOverdueBand(
  events: DiaryEventResponse[],
  from: string
): { band: DiaryEventResponse[]; inWindow: DiaryEventResponse[] } {
  const band: DiaryEventResponse[] = [];
  const inWindow: DiaryEventResponse[] = [];
  for (const e of events) (e.date < from ? band : inWindow).push(e);
  return { band, inWindow };
}

/** Groups events by date, keeping server order within and across groups (Map insertion order). */
export function groupByDate(events: DiaryEventResponse[]): Map<string, DiaryEventResponse[]> {
  const groups = new Map<string, DiaryEventResponse[]>();
  for (const e of events) {
    const group = groups.get(e.date);
    if (group) group.push(e);
    else groups.set(e.date, [e]);
  }
  return groups;
}

/** "09:30:00" → "09:30"; missing → null. */
export function formatDiaryTime(time: string | null | undefined): string | null {
  return time ? time.slice(0, 5) : null;
}

/** "PRE_TRIAL" → "Pre-trial", "CASE_MANAGEMENT" → "Case management". */
export function humaniseSubtype(subtype: string | null | undefined): string | null {
  if (!subtype) return null;
  const words = subtype.toLowerCase().split("_");
  const label =
    words[0] === "pre" && words.length > 1 ? `pre-${words.slice(1).join(" ")}` : words.join(" ");
  return label.charAt(0).toUpperCase() + label.slice(1);
}

/**
 * en-GB day label without the locale's weekday comma: "Mon 19 Oct" (short) or
 * "Monday 19 October 2026" (long).
 */
export function formatDiaryDay(iso: string, style: "short" | "long" = "short"): string {
  const d = parseIsoDate(iso);
  const weekday = d.toLocaleDateString("en-GB", { weekday: style });
  const rest = d.toLocaleDateString(
    "en-GB",
    style === "short"
      ? { day: "numeric", month: "short" }
      : { day: "numeric", month: "long", year: "numeric" }
  );
  return `${weekday} ${rest}`;
}
