import "server-only";

import type { HeroTile } from "@/components/shared/detail-hero";
import { formatCurrency, formatLocalDate } from "@/lib/format";
import { formatChartValue } from "@/lib/chart-format";
import { overdueHint, tileFrom } from "@/lib/hero-tiles";
import { getMatterUnbilledSummary, getMatterUpcomingDeadlines } from "@/lib/api/matters";
import { fetchOutstandingSummaryAction } from "@/app/(app)/org/[slug]/invoices/invoice-crud-actions";
import { fetchMatterTrustBalance } from "@/app/(app)/org/[slug]/trust-accounting/actions";
import { hasCapability, type HomeCaps, type WidgetResult } from "@/lib/dashboard-home";
import { settle } from "@/lib/settle";
import type {
  MatterTrustBalance,
  MatterUpcomingDeadline,
  OutstandingSummary,
  UnbilledTimeSummary,
} from "@/lib/types";

// Matter hero tiles (Phase 90 641B, arch §15.3.5, ADR-373). Server-only, not a server action: the
// matter page is the only caller and passes its own capabilities.
//
// Two halves (the client hero, 642B, has the same shape; tileFrom / overdueHint live in
// @/lib/hero-tiles, shared by both):
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

/** The module flags that gate hero reads. The page reads them once (it also needs trustEnabled). */
export interface MatterHeroModules {
  trustEnabled: boolean;
  courtCalendarEnabled: boolean;
}

export interface MatterHeroData {
  /** absent when trust_accounting is off or the caller lacks VIEW_TRUST. */
  trustBalance: WidgetResult<MatterTrustBalance>;
  unbilledSummary: WidgetResult<UnbilledTimeSummary>;
  outstandingSummary: WidgetResult<OutstandingSummary>;
  /** absent when court_calendar is off. Shared with the Overview tab's deadlines list. */
  upcomingDeadlines: WidgetResult<MatterUpcomingDeadline[]>;
}

/** The matter page's hero reads (deadlines are also shared with the Overview tab). */
export async function loadMatterHeroData(
  matterId: string,
  caps: HomeCaps,
  { trustEnabled, courtCalendarEnabled }: MatterHeroModules
): Promise<MatterHeroData> {
  // PROJECT_MANAGEMENT ∧ (admin ∨ owner ∨ FINANCIAL_VISIBILITY): the unbilled-summary endpoint does
  // no financial redaction, so this frontend gate is the gate (ADR-373 §1).
  const seesUnbilled =
    hasCapability(caps, "PROJECT_MANAGEMENT") && hasCapability(caps, "FINANCIAL_VISIBILITY");

  const [unbilledSummary, trustBalance, outstandingSummary, upcomingDeadlines] = await Promise.all([
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
  ]);

  return { unbilledSummary, trustBalance, outstandingSummary, upcomingDeadlines };
}

/**
 * The earliest live hearing. Rows are date-ascending, today or later, but filtered by date only
 * (backend ProjectUpcomingDeadlinesService): a postponement keeps the original row as POSTPONED
 * beside a new SCHEDULED one (CourtCalendarService.postponeCourtDate), and CANCELLED / HEARD rows
 * stay too — only SCHEDULED is a hearing still to happen.
 */
function nextScheduledCourtDate(rows: MatterUpcomingDeadline[]): MatterUpcomingDeadline | null {
  return rows.find((row) => row.status === "SCHEDULED") ?? null;
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
    tileFrom("trust", L.trust, data.trustBalance, (b) => ({
      value: formatCurrency(b.balance, b.currency),
    })),
    tileFrom("outstanding", L.outstanding, data.outstandingSummary, (s) => ({
      value: formatCurrency(s.outstandingTotal, s.currency),
      hint: overdueHint(s.overdueCount),
    })),
    tileFrom("court", L.court, data.upcomingDeadlines, (rows) => {
      const next = nextScheduledCourtDate(rows);
      return next
        ? { value: formatLocalDate(next.date), hint: next.description }
        : { value: null, hint: "None scheduled" };
    }),
  ].filter((tile): tile is HeroTile => tile !== null);
}

/** What KPIDashboard's UpcomingDeadlinesTile shows: the rows, or [] when off / failed (as before). */
export function upcomingDeadlineRows(data: MatterHeroData): MatterUpcomingDeadline[] {
  return data.upcomingDeadlines.state === "ok" ? data.upcomingDeadlines.data : [];
}
