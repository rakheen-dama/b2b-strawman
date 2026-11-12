import "server-only";

import { createElement, type ReactNode } from "react";
import { AlertTriangle } from "lucide-react";
import type { HeroTile } from "@/components/shared/detail-hero";
import { formatCurrency, formatLocalDate } from "@/lib/format";
import { formatChartValue } from "@/lib/chart-format";
import {
  getMatterBudget,
  getMatterUnbilledSummary,
  getMatterUpcomingDeadlines,
} from "@/lib/api/matters";
import { isModuleEnabledServer } from "@/lib/api/settings";
import { fetchOutstandingSummaryAction } from "@/app/(app)/org/[slug]/invoices/invoice-crud-actions";
import { fetchMatterTrustBalance } from "@/app/(app)/org/[slug]/trust-accounting/actions";
import { hasCapability, type HomeCaps, type WidgetResult } from "@/lib/dashboard-home";
import { settle } from "@/lib/settle";
import type {
  BudgetStatusResponse,
  MatterTrustBalance,
  MatterUpcomingDeadline,
  OutstandingSummary,
  UnbilledTimeSummary,
} from "@/lib/types";

// Matter hero tiles (Phase 90 641B, arch §15.3.5, ADR-373). Server-only, not a server action: the
// matter page is the only caller and passes its own capabilities.
//
// Two halves, so the client hero (642B) can copy the shape:
//   loadMatterHeroData  — I/O. Each read runs only under its own gate; gate fails / 403 / 404 →
//                         "absent", any other failure → "error" (the WidgetResult idiom, lib/settle).
//   buildMatterHeroTiles — pure. absent → tile omitted (the strip reflows), error → state "error",
//                         a real zero → the formatted zero.
// Tiles cross the RSC boundary into the "use client" MatterHeaderCard, so every field is a string,
// null, or (value / hint only) a pre-rendered element — never a component reference (tasks OQ #26).

const ABSENT = { state: "absent" } as const;

export const MATTER_HERO_TILE_LABELS = {
  wip: "Unbilled WIP",
  trust: "Trust balance",
  outstanding: "Outstanding",
  court: "Next court date",
} as const;

export interface MatterHeroData {
  /** trust_accounting enabled — also drives the Overview trust MetricCard (unchanged). */
  trustEnabled: boolean;
  unbilledSummary: WidgetResult<UnbilledTimeSummary>;
  trustBalance: WidgetResult<MatterTrustBalance>;
  outstandingSummary: WidgetResult<OutstandingSummary>;
  /** absent when court_calendar is off. */
  upcomingDeadlines: WidgetResult<MatterUpcomingDeadline[]>;
  /** null = no budget set (404) or the read failed. Read once; BudgetPanel and KPIDashboard share it. */
  budget: BudgetStatusResponse | null;
}

/** The matter page's hero reads plus the two it shares with the Overview tab (budget, deadlines). */
export async function loadMatterHeroData(
  matterId: string,
  caps: HomeCaps
): Promise<MatterHeroData> {
  const [trustEnabled, courtCalendarEnabled] = await Promise.all([
    isModuleEnabledServer("trust_accounting"),
    isModuleEnabledServer("court_calendar"),
  ]);
  // PROJECT_MANAGEMENT ∧ (admin ∨ owner ∨ FINANCIAL_VISIBILITY): the unbilled-summary endpoint does
  // no financial redaction, so this frontend gate is the gate (ADR-373 §1).
  const seesUnbilled =
    hasCapability(caps, "PROJECT_MANAGEMENT") && hasCapability(caps, "FINANCIAL_VISIBILITY");

  const [unbilledSummary, trustBalance, outstandingSummary, upcomingDeadlines, budget] =
    await Promise.all([
      settle(seesUnbilled, "matter unbilled summary", () => getMatterUnbilledSummary(matterId)),
      trustEnabled && hasCapability(caps, "VIEW_TRUST")
        ? fetchMatterTrustBalance(matterId)
        : Promise.resolve<WidgetResult<MatterTrustBalance>>(ABSENT),
      hasCapability(caps, "FINANCIAL_VISIBILITY")
        ? fetchOutstandingSummaryAction(matterId)
        : Promise.resolve<WidgetResult<OutstandingSummary>>(ABSENT),
      settle(courtCalendarEnabled, "matter upcoming deadlines", () =>
        getMatterUpcomingDeadlines(matterId)
      ),
      // Non-fatal: 404 means no budget set, other errors show the empty state.
      getMatterBudget(matterId).catch(() => null),
    ]);

  return {
    trustEnabled,
    unbilledSummary,
    trustBalance,
    outstandingSummary,
    upcomingDeadlines,
    budget,
  };
}

function tileFrom<T>(
  id: string,
  label: string,
  result: WidgetResult<T>,
  render: (data: T) => { value: ReactNode | null; hint?: ReactNode }
): HeroTile | null {
  if (result.state === "absent") return null;
  if (result.state === "error") return { id, label, value: null, state: "error" };
  return { id, label, state: "ready", ...render(result.data) };
}

/** "N overdue"; with a --danger icon when N > 0 (colour is not the only signal — the count is). */
function overdueHint(count: number): ReactNode {
  const text = `${count} overdue`;
  if (count <= 0) return text;
  return createElement(
    "span",
    { className: "inline-flex items-center gap-1" },
    createElement(AlertTriangle, {
      "aria-hidden": true,
      className: "text-danger size-3.5 shrink-0",
    }),
    text
  );
}

/** Pure: the hero strip in order (WIP, Trust, Outstanding, Next court date), gated tiles omitted. */
export function buildMatterHeroTiles(data: MatterHeroData): HeroTile[] {
  const L = MATTER_HERO_TILE_LABELS;
  return [
    tileFrom("wip", L.wip, data.unbilledSummary, (s) => ({
      value: formatCurrency(s.totalAmount, s.currency),
      hint: `${formatChartValue(s.totalHours, "hours")} · ${s.entryCount} ${
        s.entryCount === 1 ? "entry" : "entries"
      }`,
    })),
    tileFrom("trust", L.trust, data.trustEnabled ? data.trustBalance : ABSENT, (b) => ({
      value: formatCurrency(b.balance, b.currency),
    })),
    tileFrom("outstanding", L.outstanding, data.outstandingSummary, (s) => ({
      value: formatCurrency(s.outstandingTotal, s.currency),
      hint: overdueHint(s.overdueCount),
    })),
    // Rows are date-ascending, today or later (backend ProjectUpcomingDeadlinesService).
    tileFrom("court", L.court, data.upcomingDeadlines, (rows) =>
      rows.length === 0
        ? { value: null, hint: "None scheduled" }
        : { value: formatLocalDate(rows[0].date), hint: rows[0].description }
    ),
  ].filter((tile): tile is HeroTile => tile !== null);
}

/** What KPIDashboard's UpcomingDeadlinesTile shows: the rows, or [] when off / failed (as before). */
export function upcomingDeadlineRows(data: MatterHeroData): MatterUpcomingDeadline[] {
  return data.upcomingDeadlines.state === "ok" ? data.upcomingDeadlines.data : [];
}
