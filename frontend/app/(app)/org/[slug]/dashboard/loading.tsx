import { Skeleton } from "@b2mash/ui/skeleton";
import { StatCard, type StatCardDelta } from "@b2mash/ui/stat-card";
import { WidgetCard } from "@/components/dashboard/widget-parts";
import { rowSpanClass } from "@/lib/dashboard-home";
import { cn } from "@/lib/utils";
import { DashboardHeader } from "./dashboard-header";

// Home's loading state (Phase 90 640C, §15.10 E4C): the same wrappers as page.tsx (root
// `space-y-6`, 12-column rows, `rowSpanClass` cells) so skeleton → content does not shift. It
// cannot know the audience (no props, no data), so it draws the owner rows — firm differs only in
// row 4, below the fold; a fee earner has no KPI row, so their diary moves up on load. No
// test ids: e2e waits on `home-page` / `home-cell-*`, which only the loaded page renders.

/** Mirrors kpi-row.tsx PERIOD_LABEL / TILE_LABELS (not exported — keep in step). */
const PERIOD_LABEL = "Last 30 days";

/** Any non-undefined delta makes StatCard draw its delta skeleton (the steady-state anatomy). */
const LOADING_DELTA: StatCardDelta = {
  change: 0,
  unit: "percent",
  polarity: "up-is-good",
  comparisonLabel: "previous 30 days",
};

interface CellSkeleton {
  key: string;
  /** Content height (the WidgetCard chrome around it is 86px); fixed so the row cannot jump. */
  contentClassName: string;
}

/**
 * AUDIENCE_ROWS.owner after the KPI row (lib/dashboard-home.ts). Content heights are the
 * empty-tenant widgets at 1440 (cell − 86px chrome); a widget with rows is taller once loaded.
 */
const ROWS: ReadonlyArray<ReadonlyArray<CellSkeleton>> = [
  [{ key: "diary", contentClassName: "h-5" }],
  [
    { key: "unbilledWip", contentClassName: "h-[164px]" },
    { key: "debtors", contentClassName: "h-[164px]" },
    { key: "trustPosition", contentClassName: "h-[162px]" },
  ],
  [
    { key: "ficaOutstanding", contentClassName: "h-[176px]" },
    { key: "matterHealth", contentClassName: "h-[176px]" },
    { key: "sensitive", contentClassName: "h-[219px]" },
  ],
  [{ key: "activity", contentClassName: "h-[260px]" }],
];

const CELL_CLASS = "flex min-w-0 flex-col [&>*]:flex-1";

function KpiRowSkeleton() {
  return (
    <div className="@container flex flex-col gap-2">
      <p className="text-muted-foreground text-xs font-medium">{PERIOD_LABEL}</p>
      {/* 2×2 below @3xl, 4×1 at @3xl — as KpiRow. */}
      <div className="grid grid-cols-2 gap-4 @3xl:grid-cols-4">
        <StatCard label="Active matters" value={null} state="loading" className="h-full" />
        <StatCard
          label="Hours logged"
          value={null}
          state="loading"
          delta={LOADING_DELTA}
          // StatCard draws no sparkline slot while loading: hold the 40px SparklineTrend's place.
          footer={<Skeleton className="h-10 w-full" />}
          className="h-full"
        />
        <StatCard
          label="Billable %"
          value={null}
          state="loading"
          delta={{ ...LOADING_DELTA, unit: "points" }}
          className="h-full"
        />
        <StatCard label="Overdue tasks" value={null} state="loading" className="h-full" />
      </div>
    </div>
  );
}

function WidgetSkeleton({ cell }: { cell: CellSkeleton }) {
  return (
    <WidgetCard title={<Skeleton className="h-5 w-28" />}>
      {cell.key === "diary" ? (
        <Skeleton className={cn(cell.contentClassName, "w-48")} />
      ) : (
        <div className={cn("flex flex-col gap-3", cell.contentClassName)}>
          <Skeleton className="h-8 w-28" />
          <Skeleton className="min-h-0 flex-1" />
        </div>
      )}
    </WidgetCard>
  );
}

export default function DashboardLoading() {
  return (
    <div className="space-y-6" aria-busy="true">
      <DashboardHeader />

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-12">
        <div className={cn(rowSpanClass(1), CELL_CLASS)}>
          <KpiRowSkeleton />
        </div>
      </div>

      {ROWS.map((row) => (
        <div
          key={row.map((cell) => cell.key).join("+")}
          className="grid grid-cols-1 gap-4 lg:grid-cols-12"
        >
          {row.map((cell) => (
            <div key={cell.key} className={cn(rowSpanClass(row.length), CELL_CLASS)}>
              <WidgetSkeleton cell={cell} />
            </div>
          ))}
        </div>
      ))}
    </div>
  );
}
