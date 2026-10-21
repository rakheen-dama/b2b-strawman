/**
 * Pure helpers for the Clients table (Phase 87 615D, arch §12.6.4). `GET /api/clients` has no sort
 * or search param and is not paginated, so the server component sorts (R-1) and searches (D-7)
 * the full `ClientListRow[]` with these functions.
 *
 * No directive: used by the server page, the table, and the Save-view dialog's column list.
 */
import type { ClientListRow, ClientType, LifecycleStatus } from "@/lib/types";
import { formatCurrency } from "@/lib/format";
import type { ViewSelection } from "@/lib/constants/system-views";

/** Table column ids = sort keys = saved-view `columns` values (§12.6.4 order). */
export const CLIENT_COLUMNS = [
  { id: "name", label: "Client" },
  { id: "lifecycleStatus", label: "Lifecycle" },
  { id: "ficaStatus", label: "FICA" },
  { id: "openMatterCount", label: "Open matters" },
  { id: "outstanding", label: "Outstanding fee notes" },
  { id: "trustBalance", label: "Trust balance" },
  // The id stays "completeness" so existing `?sortBy=completeness` links keep sorting.
  { id: "completeness", label: "Completeness" },
  { id: "createdAt", label: "Created" },
  { id: "contact", label: "Contact" },
] as const;

export type ClientColumnId = (typeof CLIENT_COLUMNS)[number]["id"];
export type SortDir = "asc" | "desc";
export interface ClientSort {
  key: ClientColumnId;
  dir: SortDir;
}

/** Prefix of a custom-field column id in a saved view's `columns` (`cf:<slug>`). */
export const CUSTOM_FIELD_COLUMN_PREFIX = "cf:";

/** Contact (email + phone) is display-only. */
export const UNSORTABLE_CLIENT_COLUMNS: ReadonlySet<ClientColumnId> = new Set(["contact"]);

const COLUMN_IDS = new Set<string>(CLIENT_COLUMNS.map((c) => c.id));

export function isClientColumnId(value: string | null | undefined): value is ClientColumnId {
  return typeof value === "string" && COLUMN_IDS.has(value);
}

export function clientColumnLabel(id: ClientColumnId): string {
  return CLIENT_COLUMNS.find((c) => c.id === id)?.label ?? id;
}

export const CLIENT_TYPE_LABEL: Record<ClientType, string> = {
  INDIVIDUAL: "Individual",
  COMPANY: "Company",
  TRUST: "Trust",
};

/** Lifecycle order used for sorting (the order a client moves through). */
export const LIFECYCLE_ORDER: readonly LifecycleStatus[] = [
  "PROSPECT",
  "ONBOARDING",
  "ACTIVE",
  "DORMANT",
  "OFFBOARDING",
  "OFFBOARDED",
  "ANONYMIZED",
];

const FICA_ORDER = ["NOT_STARTED", "IN_PROGRESS", "DONE"] as const;

export function isLifecycleStatus(value: string | null | undefined): value is LifecycleStatus {
  return typeof value === "string" && (LIFECYCLE_ORDER as readonly string[]).includes(value);
}

/** D-5: `?sortBy=&sortDir=` (names kept so `sortBy=completeness` survives); default name asc. */
export function parseClientSort(
  rawSortBy: string | null | undefined,
  rawSortDir: string | null | undefined
): ClientSort {
  if (isClientColumnId(rawSortBy) && !UNSORTABLE_CLIENT_COLUMNS.has(rawSortBy)) {
    return { key: rawSortBy, dir: rawSortDir === "desc" ? "desc" : "asc" };
  }
  return { key: "name", dir: "asc" };
}

type SortValue = string | number | null;

function rank(order: readonly string[], value: string | null): number | null {
  const i = value === null ? -1 : order.indexOf(value);
  return i === -1 ? null : i;
}

function sortValue(row: ClientListRow, key: ClientColumnId): SortValue {
  switch (key) {
    case "name":
      return row.name.trim() !== "" ? row.name : null;
    case "lifecycleStatus":
      return rank(LIFECYCLE_ORDER, row.lifecycleStatus);
    case "ficaStatus":
      return rank(FICA_ORDER, row.ficaStatus);
    case "openMatterCount":
      return row.openMatterCount;
    // D-6: redaction is caller-wide (every total is null or none is), so sorting on the total
    // reveals nothing; the count orders redacted callers.
    case "outstanding":
      return row.outstandingFeeNoteTotal ?? row.outstandingFeeNoteCount;
    case "trustBalance":
      return row.trustBalance;
    case "completeness":
      return row.completenessPercent;
    // Instants compare as epoch millis; unparseable → null (sorts last).
    case "createdAt": {
      const ms = row.createdAt ? Date.parse(row.createdAt) : NaN;
      return Number.isNaN(ms) ? null : ms;
    }
    case "contact":
      return null;
  }
}

function compareValues(a: string | number, b: string | number): number {
  if (typeof a === "number" && typeof b === "number") return a - b;
  return String(a).localeCompare(String(b), undefined, { sensitivity: "base", numeric: true });
}

function compareTieBreak(a: ClientListRow, b: ClientListRow): number {
  const byName = a.name.localeCompare(b.name, undefined, { sensitivity: "base" });
  if (byName !== 0) return byName;
  return a.id < b.id ? -1 : a.id > b.id ? 1 : 0;
}

/**
 * R-1 comparator. Nulls and blanks sort LAST in both directions; ties fall back to name asc, then
 * id, so the order is deterministic. Returns a new array.
 */
export function sortClientRows(
  rows: readonly ClientListRow[],
  key: ClientColumnId,
  dir: SortDir
): ClientListRow[] {
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

/** D-7: case-insensitive substring match on name, email and phone. Blank → unchanged. */
export function searchClientRows(
  rows: readonly ClientListRow[],
  query: string | null | undefined
): ClientListRow[] {
  const q = query?.trim().toLowerCase();
  if (!q) return [...rows];
  return rows.filter((r) =>
    [r.name, r.email, r.phone].some((v) => v != null && v.toLowerCase().includes(q))
  );
}

/** Default column order; the Trust balance column only when it is visible (F-8). */
export function defaultClientColumns(trustVisible: boolean): string[] {
  return CLIENT_COLUMNS.map((c) => c.id).filter((id) => trustVisible || id !== "trustBalance");
}

/** Pre-615D CUSTOMER saved-view column ids → current ids (`null` = dropped). */
const LEGACY_COLUMN_MAP: Readonly<Record<string, ClientColumnId | null>> = {
  email: "contact",
  phone: "contact",
  status: null,
};

/**
 * Maps pre-615D CUSTOMER column ids to current ones (`email` / `phone` → `contact`, `status`
 * dropped) and removes duplicates, keeping the view's order. Other ids pass through unchanged.
 * Shared by the table (`resolveClientColumns`) and the Edit view dialog, so both read a legacy
 * view the same way.
 */
export function migrateLegacyClientColumns(columns: readonly string[]): string[] {
  const migrated: string[] = [];
  for (const raw of columns) {
    const col = raw in LEGACY_COLUMN_MAP ? LEGACY_COLUMN_MAP[raw] : raw;
    if (col !== null && !migrated.includes(col)) migrated.push(col);
  }
  return migrated;
}

/**
 * Saved-view `columns` → rendered column ids (ADR-055: column config is a frontend concern).
 * Legacy CUSTOMER ids map across (`email` / `phone` → Contact, `status` dropped). Keeps known ids
 * and `cf:<slug>` columns whose field exists, in the view's order, without duplicates. Drops the
 * Trust balance column when it is hidden. The Client column is always present (inserted first).
 * Nothing valid → the default order.
 */
export function resolveClientColumns(
  columns: readonly string[] | null | undefined,
  opts: { trustVisible: boolean; customFieldSlugs: readonly string[] }
): string[] {
  const fallback = defaultClientColumns(opts.trustVisible);
  if (!columns || columns.length === 0) return fallback;
  const slugs = new Set(opts.customFieldSlugs);
  const resolved: string[] = [];
  for (const col of migrateLegacyClientColumns(columns)) {
    if (isClientColumnId(col)) {
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
  if (!resolved.includes("name")) resolved.unshift("name");
  return resolved;
}

/**
 * D-6: "R 18 400,00 · 2". Total null (redacted) → the count only; nothing outstanding → "—".
 */
export function formatOutstanding(total: number | null, count: number, currency: string): string {
  if (count === 0 && (total == null || total === 0)) return "—";
  if (total == null) return String(count);
  return `${formatCurrency(total, currency)} · ${count}`;
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
 * other param (view, lifecycleStatus, showIncomplete, search, tags) is kept.
 */
export function buildClientSortHref(
  slug: string,
  current: Readonly<Record<string, string>>,
  key: ClientColumnId,
  active: ClientSort
): string {
  const params = new URLSearchParams(current);
  const dir: SortDir = active.key === key && active.dir === "asc" ? "desc" : "asc";
  params.set("sortBy", key);
  params.set("sortDir", dir);
  return `/org/${slug}/clients?${params.toString()}`;
}

/** Keys a saved view's `filters` may carry that the list endpoint applies on the view path. */
const FORWARDED_VIEW_FILTER_KEYS = ["ficaStatus", "hasTrustBalance", "showIncomplete"] as const;

const FICA_FILTER_VALUES = new Set(["OUTSTANDING", "DONE"]);

function isTrue(value: unknown): boolean {
  return value === true || value === "true";
}

/** URL params that refine the selected view. */
export interface ClientListUrlParams {
  lifecycleStatus?: string | null;
  showIncomplete?: string | null;
  tags?: string | null;
}

/**
 * The selected view + URL refinements → `GET /api/clients` params.
 * - System view: its params ("All clients" has none).
 * - Saved view: `view=<uuid>` plus any `ficaStatus` / `hasTrustBalance` / `showIncomplete` from its
 *   filters (F-4: the backend applies these on the view path; ADR-055 execution ignores them).
 * - URL `lifecycleStatus` (D-2, system path only — the view path ignores it): a valid status
 *   replaces the view's; `ALL` removes it. `ALL` itself is never sent (the backend 400s on it).
 * - URL `showIncomplete=true` applies on both paths. URL `tags` applies to system views only (F-3).
 */
export function buildClientListParams(
  selection: ViewSelection,
  url: ClientListUrlParams
): Record<string, string> {
  const params: Record<string, string> = {};
  if (selection.kind === "system") {
    Object.assign(params, selection.view.params);
  } else {
    params.view = selection.view.id;
    const filters = selection.view.filters ?? {};
    for (const key of FORWARDED_VIEW_FILTER_KEYS) {
      const value = filters[key];
      if (key === "ficaStatus") {
        const fica = typeof value === "string" ? value.trim().toUpperCase() : "";
        if (FICA_FILTER_VALUES.has(fica)) params.ficaStatus = fica;
      } else if (isTrue(value)) {
        params[key] = "true";
      }
    }
  }

  if (selection.kind === "system") {
    const lifecycle = url.lifecycleStatus?.trim().toUpperCase();
    if (lifecycle === "ALL") delete params.lifecycleStatus;
    else if (isLifecycleStatus(lifecycle)) params.lifecycleStatus = lifecycle;
  }

  if (url.showIncomplete === "true") params.showIncomplete = "true";

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

/**
 * Whether the selected saved view itself stores `showIncomplete: true`. The backend applies it on
 * the view path, and a URL param cannot lift it, so the toolbar shows it as a fixed indicator
 * rather than a toggle. False on system views (none carry it).
 */
export function savedViewShowIncomplete(selection: ViewSelection): boolean {
  return selection.kind === "saved" && isTrue(selection.view.filters?.showIncomplete);
}

/**
 * A saved view's stored `lifecycleStatus` (a view saved from the "Active" system view carries
 * `{ lifecycleStatus: "ACTIVE" }`, F-4). Neither ADR-055 view execution nor the list endpoint's
 * view path applies it, so the page filters the rows with it. Null when absent or invalid, or on
 * a system view (whose lifecycle is a list param).
 */
export function savedViewLifecycle(selection: ViewSelection): LifecycleStatus | null {
  if (selection.kind !== "saved") return null;
  const value = selection.view.filters?.lifecycleStatus;
  const status = typeof value === "string" ? value.trim().toUpperCase() : null;
  return isLifecycleStatus(status) ? status : null;
}

/**
 * A saved view's stored lifecycle (`savedViewLifecycle`) applied to the fetched rows. The list
 * endpoint's view path ignores it, so the page filters here. Other selections pass through
 * unchanged. Returns a new array.
 */
export function applySavedViewLifecycle(
  rows: readonly ClientListRow[],
  selection: ViewSelection
): ClientListRow[] {
  // TODO(backend): saved-view path ignores lifecycleStatus (CustomerService.selectBase / ViewFilterService) — apply it server-side, then drop this filter.
  const lifecycle = savedViewLifecycle(selection);
  return lifecycle ? rows.filter((r) => r.lifecycleStatus === lifecycle) : [...rows];
}
