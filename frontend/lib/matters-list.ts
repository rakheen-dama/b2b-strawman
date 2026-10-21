/**
 * Pure helpers for the Matters table (Phase 87 615C, arch §12.6.4). The backend list endpoint
 * has no sort or search param and is not paginated, so the server component sorts (R-1) and
 * searches (F-2) the full `MatterListRow[]` with these functions.
 *
 * No directive: used by the server page, the table, and the Save-view dialog's column list.
 */
import type { MatterListRow, MatterStatus } from "@/lib/types";
import { formatCurrency, formatDuration } from "@/lib/format";
import { resolveRelativeDate, type ViewSelection } from "@/lib/constants/system-views";

/** Table column ids = sort keys = saved-view `columns` values (§12.6.4 default order). */
export const MATTER_COLUMNS = [
  { id: "referenceNumber", label: "Reference" },
  { id: "name", label: "Matter" },
  { id: "clientName", label: "Client" },
  { id: "workType", label: "Work type" },
  { id: "feeEarnerName", label: "Fee earner" },
  { id: "status", label: "Status" },
  { id: "createdAt", label: "Opened" },
  { id: "unbilled", label: "Unbilled" },
  { id: "trustBalance", label: "Trust" },
  { id: "nextDiaryDate", label: "Next in diary" },
] as const;

export type MatterColumnId = (typeof MATTER_COLUMNS)[number]["id"];
export type SortDir = "asc" | "desc";
export interface MatterSort {
  key: MatterColumnId;
  dir: SortDir;
}

/** Prefix of a custom-field column id in a saved view's `columns` (`cf:<slug>`). */
export const CUSTOM_FIELD_COLUMN_PREFIX = "cf:";

const COLUMN_IDS = new Set<string>(MATTER_COLUMNS.map((c) => c.id));

export function isMatterColumnId(value: string | null | undefined): value is MatterColumnId {
  return typeof value === "string" && COLUMN_IDS.has(value);
}

export function matterColumnLabel(id: MatterColumnId): string {
  return MATTER_COLUMNS.find((c) => c.id === id)?.label ?? id;
}

/** R-1: default `nextDiaryDate` asc (nulls last); Dormant defaults to `createdAt` asc. */
export function parseMatterSort(
  rawSort: string | null | undefined,
  rawDir: string | null | undefined,
  isDormant: boolean
): MatterSort {
  if (isMatterColumnId(rawSort)) {
    return { key: rawSort, dir: rawDir === "desc" ? "desc" : "asc" };
  }
  return { key: isDormant ? "createdAt" : "nextDiaryDate", dir: "asc" };
}

type SortValue = string | number | null;

function sortValue(row: MatterListRow, key: MatterColumnId): SortValue {
  switch (key) {
    // Sort by minutes, never by value: value is redacted for some callers, and sorting on it
    // would leak the redacted order.
    case "unbilled":
      return row.unbilledMinutes;
    case "trustBalance":
      return row.trustBalance;
    // ISO dates/instants compare as epoch millis: string order breaks when fractional-second
    // precision varies ("…00.5Z" vs "…00.123456Z"). Unparseable → null (sorts last).
    case "createdAt":
    case "nextDiaryDate": {
      const v = row[key];
      const ms = v ? Date.parse(v) : NaN;
      return Number.isNaN(ms) ? null : ms;
    }
    default: {
      const v = row[key];
      return typeof v === "string" && v.trim() !== "" ? v : null;
    }
  }
}

function compareValues(a: string | number, b: string | number): number {
  if (typeof a === "number" && typeof b === "number") return a - b;
  return String(a).localeCompare(String(b), undefined, { sensitivity: "base", numeric: true });
}

function compareTieBreak(a: MatterListRow, b: MatterListRow): number {
  const byName = a.name.localeCompare(b.name, undefined, { sensitivity: "base" });
  if (byName !== 0) return byName;
  return a.id < b.id ? -1 : a.id > b.id ? 1 : 0;
}

/**
 * R-1 comparator. Nulls and blanks sort LAST in both directions; ties fall back to name asc, then
 * id, so the order is deterministic. Returns a new array.
 */
export function sortMatterRows(
  rows: readonly MatterListRow[],
  key: MatterColumnId,
  dir: SortDir
): MatterListRow[] {
  const sign = dir === "desc" ? -1 : 1;
  return [...rows].sort((a, b) => {
    const va = sortValue(a, key);
    const vb = sortValue(b, key);
    if (va === null && vb === null) return compareTieBreak(a, b);
    if (va === null) return 1;
    if (vb === null) return -1;
    const cmp = compareValues(va, vb);
    return cmp !== 0 ? cmp * sign : compareTieBreak(a, b);
  });
}

/** F-2: case-insensitive substring match on name, reference and client. Blank → unchanged. */
export function searchMatterRows(
  rows: readonly MatterListRow[],
  query: string | null | undefined
): MatterListRow[] {
  const q = query?.trim().toLowerCase();
  if (!q) return [...rows];
  return rows.filter((r) =>
    [r.name, r.referenceNumber, r.clientName].some((v) => v != null && v.toLowerCase().includes(q))
  );
}

/** Default column order; the Trust column only when it is visible (F-8). */
export function defaultMatterColumns(trustVisible: boolean): string[] {
  return MATTER_COLUMNS.map((c) => c.id).filter((id) => trustVisible || id !== "trustBalance");
}

/**
 * Saved-view `columns` → rendered column ids (ADR-055: column config is a frontend concern).
 * Keeps known ids and `cf:<slug>` columns whose field exists, in the view's order. Drops legacy
 * ids ("description", "updatedAt") and the Trust column when it is hidden. The Matter column is
 * always present (inserted after Reference, else first). Nothing valid → the default order.
 */
export function resolveMatterColumns(
  columns: readonly string[] | null | undefined,
  opts: { trustVisible: boolean; customFieldSlugs: readonly string[] }
): string[] {
  const fallback = defaultMatterColumns(opts.trustVisible);
  if (!columns || columns.length === 0) return fallback;
  const slugs = new Set(opts.customFieldSlugs);
  const resolved: string[] = [];
  for (const col of columns) {
    if (resolved.includes(col)) continue;
    if (isMatterColumnId(col)) {
      if (col === "trustBalance" && !opts.trustVisible) continue;
      resolved.push(col);
    } else if (
      col.startsWith(CUSTOM_FIELD_COLUMN_PREFIX) &&
      slugs.has(col.slice(CUSTOM_FIELD_COLUMN_PREFIX.length))
    ) {
      resolved.push(col);
    }
  }
  if (resolved.length === 0) return fallback;
  if (!resolved.includes("name")) {
    const refIndex = resolved.indexOf("referenceNumber");
    resolved.splice(refIndex === -1 ? 0 : refIndex + 1, 0, "name");
  }
  return resolved;
}

/** "21h · R 42 000,00"; value null (redacted) → hours only; nothing unbilled → "—". */
export function formatUnbilled(minutes: number, value: number | null, currency: string): string {
  const parts: string[] = [];
  if (minutes > 0) parts.push(formatDuration(minutes));
  if (value != null && value > 0) parts.push(formatCurrency(value, currency));
  return parts.length > 0 ? parts.join(" · ") : "—";
}

/** "PERSONAL_INJURY" → "Personal injury"; null/blank → "—". */
export function humaniseWorkType(workType: string | null | undefined): string {
  const trimmed = workType?.trim();
  if (!trimmed) return "—";
  const label = trimmed.toLowerCase().replace(/_+/g, " ");
  return label.charAt(0).toUpperCase() + label.slice(1);
}

/** A custom-field cell value; empty → "—". */
export function formatCustomFieldCell(value: unknown): string {
  if (value === null || value === undefined || value === "") return "—";
  if (typeof value === "boolean") return value ? "Yes" : "No";
  if (Array.isArray(value)) return value.length > 0 ? value.map(String).join(", ") : "—";
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
}

/**
 * Sortable header link: the active column toggles asc ↔ desc; another column starts asc. Every
 * other param (view, status, search, tags) is kept.
 */
export function buildSortHref(
  slug: string,
  current: Readonly<Record<string, string>>,
  key: MatterColumnId,
  active: MatterSort
): string {
  const params = new URLSearchParams(current);
  const dir: SortDir = active.key === key && active.dir === "asc" ? "desc" : "asc";
  params.set("sort", key);
  params.set("dir", dir);
  return `/org/${slug}/matters?${params.toString()}`;
}

const VALID_STATUS_PARAMS = new Set<string>([
  "ACTIVE",
  "COMPLETED",
  "ARCHIVED",
  "CLOSED",
  "ALL",
] satisfies (MatterStatus | "ALL")[]);

/** Keys a saved view's `filters` may carry that the list endpoint applies on the view path (F-4). */
const FORWARDED_VIEW_FILTER_KEYS = ["mine", "hasUnbilled", "inactiveSince"] as const;

function isTrue(value: unknown): boolean {
  return value === true || value === "true";
}

/** URL params that override the selected view. */
export interface MatterListUrlParams {
  status?: string | null;
  tags?: string | null;
}

/**
 * The selected view + URL overrides → `GET /api/matters` params.
 * - System view: its params, with `inactiveSince` resolved to an ISO date (R-3; dropped if invalid).
 * - Saved view: `view=<uuid>` plus any `mine` / `hasUnbilled` / `inactiveSince` from its filters
 *   (F-4: the backend applies these three on the view path; ADR-055 execution ignores them).
 * - URL `status` (a valid status or ALL) overrides the view's status, so `?status=CLOSED` lists
 *   closed matters. URL `tags` applies to system views only (ignored on the view path, F-3).
 */
export function buildMatterListParams(
  selection: ViewSelection,
  url: MatterListUrlParams,
  today: Date
): Record<string, string> {
  const params: Record<string, string> = {};
  if (selection.kind === "system") {
    Object.assign(params, selection.view.params);
  } else {
    params.view = selection.view.id;
    const filters = selection.view.filters ?? {};
    for (const key of FORWARDED_VIEW_FILTER_KEYS) {
      const value = filters[key];
      if (key === "inactiveSince") {
        if (typeof value === "string") params.inactiveSince = value;
      } else if (isTrue(value)) {
        params[key] = "true";
      }
    }
  }

  if (params.inactiveSince !== undefined) {
    const resolved = resolveRelativeDate(params.inactiveSince, today);
    if (resolved) params.inactiveSince = resolved;
    else delete params.inactiveSince;
  }

  const status = url.status?.trim().toUpperCase();
  if (status && VALID_STATUS_PARAMS.has(status)) params.status = status;

  if (selection.kind === "system" && url.tags) {
    const tags = url.tags
      .split(",")
      .map((t) => t.trim())
      .filter(Boolean)
      .join(",");
    if (tags) params.tags = tags;
  }
  return params;
}
