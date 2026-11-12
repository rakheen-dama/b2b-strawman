import { Skeleton } from "@b2mash/ui/skeleton";
import { KpiRowSkeleton } from "@/components/dashboard/kpi-row";
import { WidgetCard } from "@/components/dashboard/widget-parts";
import { HOME_ROW_CLASS, OWNER_HOME_ROWS, homeCellClass } from "@/lib/dashboard-home";
import { DashboardHeader } from "./dashboard-header";

// Home's loading state (Phase 90 640C, §15.10 E4C): the page's own row/cell classes, KPI row frame
// and owner layout (all imported, not copied) so skeleton → content does not shift. It cannot know
// the audience (no props, no data), so it draws the owner rows — firm differs only in row 4, below
// the fold; a fee earner has no KPI row, so their diary moves up on load. No test ids: e2e waits on
// `home-page` / `home-cell-*`, which only the loaded page renders.

type OwnerWidget = (typeof OWNER_HOME_ROWS)[number][number];
type SkeletonWidget = Exclude<OwnerWidget, "kpis">;

/** WidgetCard's chrome around its content (padding, title row, gap), measured at 1440. */
const WIDGET_CHROME_PX = 86;

/**
 * Each owner widget's cell height for an empty tenant at 1440 (a widget with rows is taller once
 * loaded). Keyed by the owner rows' literal type: a widget added there won't compile until it has
 * an entry here.
 */
const CELL_HEIGHT_PX: Record<SkeletonWidget, number> = {
  diary: 106,
  unbilledWip: 250,
  debtors: 250,
  trustPosition: 248,
  ficaOutstanding: 262,
  matterHealth: 262,
  sensitive: 305,
  activity: 346,
};

function WidgetSkeleton({ widget }: { widget: SkeletonWidget }) {
  // Fixed content height so the row cannot jump.
  const height = CELL_HEIGHT_PX[widget] - WIDGET_CHROME_PX;
  return (
    <WidgetCard title={<Skeleton className="h-5 w-28" />}>
      {widget === "diary" ? (
        <Skeleton className="w-48" style={{ height }} />
      ) : (
        <div className="flex flex-col gap-3" style={{ height }}>
          <Skeleton className="h-8 w-28" />
          <Skeleton className="min-h-0 flex-1" />
        </div>
      )}
    </WidgetCard>
  );
}

const ROWS: ReadonlyArray<ReadonlyArray<OwnerWidget>> = OWNER_HOME_ROWS;

export default function DashboardLoading() {
  return (
    <div className="space-y-6" aria-busy="true">
      <DashboardHeader />

      {ROWS.map((row) => (
        <div key={row.join("+")} className={HOME_ROW_CLASS}>
          {row.map((widget) => (
            <div key={widget} className={homeCellClass(row.length)}>
              {widget === "kpis" ? <KpiRowSkeleton /> : <WidgetSkeleton widget={widget} />}
            </div>
          ))}
        </div>
      ))}
    </div>
  );
}
