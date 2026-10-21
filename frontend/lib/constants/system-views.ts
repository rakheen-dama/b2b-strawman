/**
 * Code-defined system views (Phase 87 615C, arch §12.3.4). They are not DB rows: this phase adds
 * no migration, and a seeded row would be per-tenant mutable data. Each one translates to list
 * query params. `entityType` is the storage-plane string — "PROJECT" / "CUSTOMER", never
 * "MATTER" / "CLIENT" (ADR-342).
 *
 * No directive: imported by server pages and client view selectors alike.
 */
import type { SavedViewResponse } from "@/lib/types";

export const SYSTEM_VIEW_PREFIX = "system:";

export interface SystemView {
  /** Never a UUID, which keeps it distinct from SavedViewResponse.id. */
  id: `system:${string}`;
  /** Storage-plane string, unchanged (ADR-342). */
  entityType: "PROJECT" | "CUSTOMER";
  name: string;
  /** Appended to the list endpoint. `inactiveSince` may be a relative token ("-90d"). */
  params: Readonly<Record<string, string>>;
  system: true;
}

export const MATTER_SYSTEM_VIEWS: readonly SystemView[] = [
  {
    id: "system:my-matters",
    entityType: "PROJECT",
    name: "My matters",
    params: { mine: "true", status: "ACTIVE" },
    system: true,
  },
  {
    id: "system:open-matters",
    entityType: "PROJECT",
    name: "Open matters",
    params: { status: "ACTIVE" },
    system: true,
  },
  {
    id: "system:awaiting-fee-note",
    entityType: "PROJECT",
    name: "Awaiting fee note",
    params: { status: "ACTIVE", hasUnbilled: "true" },
    system: true,
  },
  {
    id: "system:dormant",
    entityType: "PROJECT",
    name: "Dormant",
    params: { status: "ACTIVE", inactiveSince: "-90d" },
    system: true,
  },
];

/**
 * Defined and tested now (R-2), wired into the clients page in 615D. Params match the 615B
 * `GET /api/clients` contract.
 */
export const CLIENT_SYSTEM_VIEWS: readonly SystemView[] = [
  {
    id: "system:active",
    entityType: "CUSTOMER",
    name: "Active",
    params: { lifecycleStatus: "ACTIVE" },
    system: true,
  },
  {
    id: "system:fica-outstanding",
    entityType: "CUSTOMER",
    name: "FICA outstanding",
    params: { ficaStatus: "OUTSTANDING" },
    system: true,
  },
  {
    id: "system:with-trust-balance",
    entityType: "CUSTOMER",
    name: "With trust balance",
    params: { hasTrustBalance: "true" },
    system: true,
  },
];

export const DEFAULT_MATTER_VIEW_ID = "system:open-matters";
/**
 * Not wired: 615D ruling D-1(B) defaults the Clients page to "All clients"
 * (`ALL_CLIENTS_VIEW_ID` in lib/clients-list.ts). Kept for its 615C test pins.
 */
export const DEFAULT_CLIENT_VIEW_ID = "system:active";
export const DORMANT_VIEW_ID = "system:dormant";

export function isSystemViewId(id: string | null | undefined): id is `system:${string}` {
  return typeof id === "string" && id.startsWith(SYSTEM_VIEW_PREFIX);
}

export function findSystemView(
  views: readonly SystemView[],
  id: string | null | undefined
): SystemView | null {
  return views.find((v) => v.id === id) ?? null;
}

/** A selector entry: a code-defined system view or a saved view. */
export type ViewOption = SystemView | (SavedViewResponse & { system?: false });

/** System views first (fixed order), then saved views in API order (never re-sorted). */
export function mergeViews(
  systemViews: readonly SystemView[],
  savedViews: readonly SavedViewResponse[]
): ViewOption[] {
  return [...systemViews, ...savedViews];
}

/** The view a list page renders. */
export type ViewSelection =
  | { kind: "system"; view: SystemView }
  | { kind: "saved"; view: SavedViewResponse };

/**
 * The `view` URL param → the selected view. A known system id or saved-view id selects that view.
 * No param, or an unknown/garbage id, falls back to the default system view. The backend would
 * 400 on a non-UUID `view`, so a `system:*` id never reaches it.
 */
export function resolveViewSelection(
  systemViews: readonly SystemView[],
  savedViews: readonly SavedViewResponse[],
  rawViewId: string | null | undefined,
  defaultViewId: string
): ViewSelection {
  const system = findSystemView(systemViews, rawViewId);
  if (system) return { kind: "system", view: system };
  const saved = rawViewId ? savedViews.find((v) => v.id === rawViewId) : undefined;
  if (saved) return { kind: "saved", view: saved };
  const fallback = findSystemView(systemViews, defaultViewId) ?? systemViews[0];
  return { kind: "system", view: fallback };
}

/**
 * The id the selector highlights: the URL view when it names a known entry, else the default.
 */
export function selectedViewId(
  systemViews: readonly SystemView[],
  savedViews: readonly SavedViewResponse[],
  rawViewId: string | null | undefined,
  defaultViewId: string
): string {
  return resolveViewSelection(systemViews, savedViews, rawViewId, defaultViewId).view.id;
}

const RELATIVE_DAYS = /^-(\d{1,4})d$/;
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

/**
 * R-3: "-90d" → the ISO date 90 days before `today` (local calendar). A valid yyyy-MM-dd passes
 * through. Anything else → null, so the caller drops the param instead of sending a value the
 * backend rejects with 400. Pure.
 */
export function resolveRelativeDate(token: string | null | undefined, today: Date): string | null {
  if (!token) return null;
  const rel = RELATIVE_DAYS.exec(token);
  if (rel) {
    const d = new Date(today.getFullYear(), today.getMonth(), today.getDate() - Number(rel[1]));
    return toLocalIsoDate(d);
  }
  if (ISO_DATE.test(token)) {
    const [y, m, dd] = token.split("-").map(Number);
    const d = new Date(y, m - 1, dd);
    return d.getFullYear() === y && d.getMonth() === m - 1 && d.getDate() === dd ? token : null;
  }
  return null;
}

/** Local-calendar yyyy-MM-dd. Never toISOString() (UTC shift). */
function toLocalIsoDate(d: Date): string {
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  return `${d.getFullYear()}-${mm}-${dd}`;
}

/**
 * F-4: the saved-view `filters` that "Save view" from a system view is pre-filled with. `status`
 * becomes a list (the StatusFilterHandler shape). Other params ride along as raw strings ("-90d"
 * stays relative): ViewFilterService stores but does not execute them, so the list page forwards
 * them as list params next to `view=<uuid>`.
 */
export function systemViewToFilters(view: SystemView): Record<string, unknown> {
  const { status, ...rest } = view.params;
  return { ...(status ? { status: [status] } : {}), ...rest };
}
