import { DonutChart } from "@/components/charts/donut-chart";
import {
  OTHER_KEY,
  OTHER_LABEL,
  seriesColor,
  type ChartSegment,
  type ChartSlot,
} from "@/components/charts/chart-summary";
import type { PersonalMatterBreakdown } from "@/lib/dashboard-types";

const TITLE = "Time Breakdown";

interface TimeBreakdownProps {
  data: PersonalMatterBreakdown[] | null;
}

// No "use client": no hooks or handlers here — the kit DonutChart is the client boundary and
// every prop it receives is serializable.
export function TimeBreakdown({ data }: TimeBreakdownProps) {
  if (!data || data.length === 0) {
    return (
      <DonutChart
        title={TITLE}
        segments={[]}
        format="hours"
        error={data === null ? "Unable to load time breakdown. Please try again." : undefined}
        emptyTitle="No time logged this period."
        data-testid="time-activity-panel"
      />
    );
  }

  // Take top 4 matters and aggregate the rest as "Other"
  const sorted = [...data].sort((a, b) => b.hours - a.hours);
  const top4 = sorted.slice(0, 4);
  const rest = sorted.slice(4);

  const items = [...top4];

  if (rest.length > 0) {
    items.push({
      matterId: OTHER_KEY,
      matterName: OTHER_LABEL,
      hours: rest.reduce((sum, p) => sum + p.hours, 0),
      percent: rest.reduce((sum, p) => sum + p.percent, 0),
    });
  }

  const totalHours = items.reduce((sum, p) => sum + p.hours, 0);
  // Slots pinned by rank (top matter = slot 1); the list swatches read the same segments, so the
  // donut and the list cannot disagree. "Other" is muted (seriesColor ignores its slot).
  const segments: ChartSegment[] = items.map((item, idx) => ({
    key: item.matterId,
    label: item.matterName,
    value: item.hours,
    slot: (idx + 1) as ChartSlot,
  }));

  return (
    <DonutChart
      title={TITLE}
      segments={segments}
      format="hours"
      centerValue={`${totalHours.toFixed(1)}h`}
      centerLabel="total"
      height={180}
      // The itemised list below names every matter beside its swatch (it is the legend and the
      // direct labels), so the chart draws neither.
      showLegend={false}
      directLabels={false}
      categoryLabel="Matter"
      valueLabel="Hours"
      data-testid="time-activity-panel"
      footer={
        <div className="mt-2 space-y-1">
          {items.map((item, idx) => (
            <div key={item.matterId} className="flex items-center justify-between text-sm">
              <div className="flex items-center gap-2">
                <span
                  aria-hidden="true"
                  className="inline-block size-2.5 rounded-full"
                  style={{ backgroundColor: seriesColor(segments[idx], idx) }}
                />
                <span className="text-foreground">{item.matterName}</span>
              </div>
              <span className="text-muted-foreground tabular-nums">
                {item.hours.toFixed(1)}h ({Math.round(item.percent)}%)
              </span>
            </div>
          ))}
        </div>
      }
    />
  );
}
