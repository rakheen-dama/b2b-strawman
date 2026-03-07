"use client";

import { useRouter } from "next/navigation";
import { Users, AlertTriangle } from "lucide-react";
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
  CardFooter,
} from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { MiniProgressRing } from "@/components/dashboard/mini-progress-ring";
import type { TeamCapacityGrid } from "@/lib/api/capacity";

interface TeamCapacityWidgetProps {
  data: TeamCapacityGrid | null;
  orgSlug: string;
}

export function TeamCapacityWidget({ data, orgSlug }: TeamCapacityWidgetProps) {
  const router = useRouter();

  if (data === null) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Users className="h-4 w-4" />
            Team Capacity
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground italic">
            Unable to load team capacity data.
          </p>
        </CardContent>
      </Card>
    );
  }

  if (data.members.length === 0) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Users className="h-4 w-4" />
            Team Capacity
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground italic">
            No team members with capacity data.
          </p>
        </CardContent>
      </Card>
    );
  }

  // Use first week summary for current week stats
  const currentWeek = data.weekSummaries[0];
  const teamUtilization = currentWeek?.teamUtilizationPct ?? 0;
  const totalAllocated = currentWeek?.teamTotalAllocated ?? 0;
  const totalCapacity = currentWeek?.teamTotalCapacity ?? 0;

  // Count over-allocated members (any week)
  const overAllocatedCount = data.members.filter((m) =>
    m.weeks.some((w) => w.overAllocated),
  ).length;

  // Count under-utilized members (avg < 50%)
  const underUtilizedCount = data.members.filter(
    (m) => m.avgUtilizationPct < 50,
  ).length;

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Users className="h-4 w-4" />
          Team Capacity
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex items-center gap-4">
          <MiniProgressRing value={teamUtilization} size={64} />
          <div>
            <p className="text-sm font-medium text-slate-900 dark:text-slate-100">
              <span className="font-mono tabular-nums">{Math.round(teamUtilization)}%</span> utilized
            </p>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              <span className="font-mono tabular-nums">{totalAllocated}h</span> /{" "}
              <span className="font-mono tabular-nums">{totalCapacity}h</span> capacity
            </p>
          </div>
        </div>

        <div className="flex items-center gap-3">
          {overAllocatedCount > 0 && (
            <div className="flex items-center gap-1.5">
              <AlertTriangle className="h-3.5 w-3.5 text-red-500" />
              <Badge variant="destructive" className="font-mono text-xs tabular-nums">
                {overAllocatedCount} over-allocated
              </Badge>
            </div>
          )}
          {underUtilizedCount > 0 && (
            <p className="text-xs text-slate-500 dark:text-slate-400">
              <span className="font-mono tabular-nums">{underUtilizedCount}</span> under-utilized
            </p>
          )}
        </div>
      </CardContent>
      <CardFooter>
        <Button
          variant="ghost"
          size="sm"
          className="text-muted-foreground"
          onClick={() => router.push(`/org/${orgSlug}/resources`)}
        >
          View resources &rarr;
        </Button>
      </CardFooter>
    </Card>
  );
}
