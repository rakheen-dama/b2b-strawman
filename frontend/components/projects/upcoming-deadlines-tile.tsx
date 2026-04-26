import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { CalendarDays } from "lucide-react";
import type { ProjectUpcomingDeadline } from "@/lib/actions/dashboard";

interface UpcomingDeadlinesTileProps {
  deadlines: ProjectUpcomingDeadline[];
}

/**
 * Matter-Overview "Upcoming Deadlines" tile (GAP-L-58 / E9.3).
 *
 * Renders the union of court dates + regulatory deadlines returned by
 * GET /api/projects/{id}/upcoming-deadlines as a flat sorted list,
 * with a small slate badge to distinguish COURT vs REGULATORY rows.
 */
export function UpcomingDeadlinesTile({ deadlines }: UpcomingDeadlinesTileProps) {
  return (
    <Card data-testid="matter-upcoming-deadlines-tile">
      <CardHeader className="pb-2">
        <CardTitle className="text-sm font-medium">Upcoming Deadlines</CardTitle>
      </CardHeader>
      <CardContent>
        {deadlines.length === 0 ? (
          <p className="text-muted-foreground text-sm italic">No upcoming deadlines.</p>
        ) : (
          <div className="space-y-1">
            {deadlines.map((d, i) => (
              <div
                key={`${d.type}-${d.date}-${i}`}
                className="flex items-center gap-2 py-1.5 pr-2 pl-2"
                data-testid={`matter-deadline-row-${d.type.toLowerCase()}`}
              >
                <CalendarDays className="size-3.5 shrink-0 text-slate-400 dark:text-slate-500" />
                <Badge variant="neutral" className="shrink-0 text-[10px]">
                  {d.type === "COURT" ? "Court" : "Regulatory"}
                </Badge>
                <span className="min-w-0 flex-1 truncate text-sm text-slate-700 dark:text-slate-300">
                  {d.description}
                </span>
                <span className="text-muted-foreground shrink-0 font-mono text-xs tabular-nums">
                  {formatShortDate(d.date)}
                </span>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function formatShortDate(iso: string): string {
  // ISO date like "2026-06-10" → "Jun 10"
  const d = new Date(iso + "T00:00:00");
  return d.toLocaleDateString("en-US", { month: "short", day: "numeric" });
}
