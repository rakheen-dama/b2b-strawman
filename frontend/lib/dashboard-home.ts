// Legal-native Home (Phase 87 614B, arch §12.6.3 / §12.11.2): which widgets a caller sees, in which
// rows, and therefore which reads the page fans out to. Pure — no directive, no I/O — so the
// server-only fan-out (lib/dashboard-home-fetch.ts) and the page share one plan.

/**
 * Audiences are capability bundles, not roles. The built-in `member` role carries VIEW_TRUST, so
 * a single capability never promotes a fee earner: only owner, admin or FINANCIAL_VISIBILITY does.
 */
export type HomeAudience = "feeEarner" | "firm" | "owner";

/** The subset of `GET /api/me/capabilities` the plan needs. */
export interface HomeCaps {
  capabilities: string[];
  isAdmin: boolean;
  isOwner: boolean;
}

export type HomeWidget =
  | "diary"
  | "tasks"
  | "unbilledWip"
  | "debtors"
  | "trustPosition"
  | "ficaOutstanding"
  | "matterHealth"
  | "activity"
  | "sensitive";

export interface HomePlan {
  audience: HomeAudience;
  /** `me` = the caller's own diary (feeEarnerId); `firm` = everyone's (admin/owner only). */
  diaryScope: "me" | "firm";
  tasks: boolean;
  unbilledWip: boolean;
  /** The "Unbilled work" link — `/invoices/unbilled` 404s without INVOICING. */
  unbilledWipLink: boolean;
  debtors: boolean;
  trustPosition: boolean;
  ficaOutstanding: boolean;
  matterHealth: boolean;
  activity: boolean;
  sensitive: boolean;
  /** Rows top-down, already filtered to the widgets this plan renders. */
  rows: HomeWidget[][];
}

/** A widget's read: loaded, absent (gate failed or 403/404 — the widget drops out), or failed. */
export type WidgetResult<T> = { state: "ok"; data: T } | { state: "absent" } | { state: "error" };

const TRUST_MODULE = "trust_accounting";

/** §12.6.3 row layout per audience, before per-widget gates. */
const AUDIENCE_ROWS: Record<HomeAudience, HomeWidget[][]> = {
  feeEarner: [["diary"], ["tasks", "unbilledWip"], ["matterHealth", "activity"]],
  firm: [
    ["unbilledWip", "debtors", "trustPosition"],
    ["diary", "ficaOutstanding"],
    ["activity", "sensitive"],
  ],
  owner: [
    ["diary", "unbilledWip"],
    ["debtors", "trustPosition"],
    ["ficaOutstanding", "matterHealth", "activity", "sensitive"],
  ],
};

/** Admin and owner hold every capability (server-page idiom, e.g. the reconciliation page). */
export function hasCapability(caps: HomeCaps, capability: string): boolean {
  return caps.isAdmin || caps.isOwner || caps.capabilities.includes(capability);
}

export function resolveHomeAudience(caps: HomeCaps): HomeAudience {
  if (caps.isOwner) return "owner";
  if (caps.isAdmin || caps.capabilities.includes("FINANCIAL_VISIBILITY")) return "firm";
  return "feeEarner";
}

export function buildHomePlan(caps: HomeCaps, enabledModules: readonly string[]): HomePlan {
  const audience = resolveHomeAudience(caps);
  const firmView = audience !== "feeEarner";
  const has = (capability: string) => hasCapability(caps, capability);

  const gates: Record<HomeWidget, boolean> = {
    diary: true,
    tasks: audience === "feeEarner",
    unbilledWip: true,
    debtors: firmView && has("INVOICING"),
    trustPosition: firmView && has("VIEW_TRUST") && enabledModules.includes(TRUST_MODULE),
    ficaOutstanding: firmView && has("CUSTOMER_MANAGEMENT"),
    matterHealth: audience !== "firm",
    activity: true,
    sensitive: firmView && has("TEAM_OVERSIGHT"),
  };

  return {
    audience,
    diaryScope: caps.isAdmin || caps.isOwner ? "firm" : "me",
    tasks: gates.tasks,
    unbilledWip: gates.unbilledWip,
    unbilledWipLink: has("INVOICING"),
    debtors: gates.debtors,
    trustPosition: gates.trustPosition,
    ficaOutstanding: gates.ficaOutstanding,
    matterHealth: gates.matterHealth,
    activity: gates.activity,
    sensitive: gates.sensitive,
    rows: dropEmptyRows(AUDIENCE_ROWS[audience].map((row) => row.filter((w) => gates[w]))),
  };
}

/**
 * Widgets whose read came back `absent` (gate failed, or 403/404). Generic over the fan-out's
 * result, which is keyed by widget name: any `WidgetResult` field drops out when absent, so a new
 * gated widget can't be forgotten here. Non-WidgetResult fields (diary, activity) never match.
 */
export function absentWidgets(data: Partial<Record<HomeWidget, unknown>>): Set<HomeWidget> {
  return new Set(
    (Object.entries(data) as Array<[HomeWidget, unknown]>)
      .filter(([, result]) => isAbsent(result))
      .map(([widget]) => widget)
  );
}

function isAbsent(result: unknown): boolean {
  return (
    typeof result === "object" && result !== null && "state" in result && result.state === "absent"
  );
}

/**
 * Removes widgets that turned out absent after the fan-out (e.g. trust 404 = no account) — a
 * widget is present or absent, never greyed — and the rows they leave empty.
 */
export function withoutAbsent(
  rows: HomeWidget[][],
  absent: ReadonlySet<HomeWidget>
): HomeWidget[][] {
  return dropEmptyRows(rows.map((row) => row.filter((w) => !absent.has(w))));
}

function dropEmptyRows(rows: HomeWidget[][]): HomeWidget[][] {
  return rows.filter((row) => row.length > 0);
}

/**
 * 12-column span per present-widget count. Literal class strings so Tailwind sees them. Four-up
 * rows wrap 2×2 at every breakpoint: the app content area is width-capped, so a quarter cell
 * (~230–290px) can't hold the Matter health / FICA / activity tables without truncating them.
 */
const ROW_SPAN: Record<number, string> = {
  1: "lg:col-span-12",
  2: "lg:col-span-6",
  3: "lg:col-span-4",
  4: "lg:col-span-6",
};

export function rowSpanClass(count: number): string {
  return ROW_SPAN[count] ?? "lg:col-span-12";
}
