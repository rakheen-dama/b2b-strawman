import Link from "next/link";
import { notFound } from "next/navigation";
import { Users } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api } from "@/lib/api";
import { getTeamCapacityGrid } from "@/lib/api/capacity";
import type { TeamCapacityGrid } from "@/lib/api/capacity";
import type { Project } from "@/lib/types";
import { AllocationGrid } from "@/components/capacity/allocation-grid";
import { EmptyState } from "@/components/empty-state";
import { docsLink } from "@/lib/docs";
import { WeekRangeSelector } from "@/components/capacity/week-range-selector";
import { getCurrentMonday, formatDate, addWeeks } from "@/lib/date-utils";
import { ModuleGate } from "@/components/module-gate";
import { ModuleDisabledFallback } from "@/components/module-disabled-fallback";

function computeWeekCount(weekStart: string, weekEnd: string): number {
  const start = new Date(weekStart);
  const end = new Date(weekEnd);
  const diffMs = end.getTime() - start.getTime();
  return Math.round(diffMs / (7 * 24 * 60 * 60 * 1000));
}

export default async function ResourcesPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<{ weekStart?: string; weekEnd?: string }>;
}) {
  const { slug } = await params;
  const sp = await searchParams;
  const capData = await fetchMyCapabilities();

  if (!capData.isAdmin && !capData.isOwner && !capData.capabilities.includes("RESOURCE_PLANNING")) {
    notFound();
  }

  const defaultMonday = getCurrentMonday();
  const weekStart = sp.weekStart ?? formatDate(defaultMonday);
  const weekEnd = sp.weekEnd ?? formatDate(addWeeks(defaultMonday, 4));
  const weekCount = computeWeekCount(weekStart, weekEnd);

  let grid: TeamCapacityGrid = { members: [], weekSummaries: [] };
  let projectOptions: { id: string; name: string }[] = [];

  try {
    const raw = await getTeamCapacityGrid(weekStart, weekEnd);
    grid = {
      members: (raw.members ?? []).map((m) => ({
        ...m,
        memberName: m.memberName ?? "Unknown",
        weeks: (m.weeks ?? []).map((w) => ({
          ...w,
          allocations: w.allocations ?? [],
        })),
      })),
      weekSummaries: raw.weekSummaries ?? [],
    };
  } catch (err) {
    console.error("Failed to load team capacity grid", err);
  }

  try {
    const raw = await api.get<Project[] | { content: Project[] }>(
      "/api/projects",
    );
    const projects = Array.isArray(raw)
      ? raw
      : ((raw as { content: Project[] }).content ?? []);
    projectOptions = projects
      .filter((p) => p.status === "ACTIVE")
      .map((p) => ({ id: p.id, name: p.name }));
  } catch (err) {
    console.error("Failed to load projects", err);
  }

  return (
    <ModuleGate
      module="resource_planning"
      fallback={
        <ModuleDisabledFallback moduleName="Resource Planning" slug={slug} />
      }
    >
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
              Resources
            </h1>
            <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
              Team capacity planning and allocation overview
            </p>
            <Link
              href={`/org/${slug}/resources/utilization`}
              className="mt-2 inline-block text-sm font-medium text-teal-600 hover:text-teal-700 dark:text-teal-400 dark:hover:text-teal-300"
            >
              View Utilization →
            </Link>
          </div>
          <WeekRangeSelector weekStart={weekStart} weekCount={weekCount} />
        </div>

        {grid.members.length === 0 ? (
          <EmptyState
            icon={Users}
            title="No resource allocations"
            description="Allocate team members to projects to plan capacity."
            secondaryLink={{ label: "Read the guide", href: docsLink("/features/resource-planning") }}
          />
        ) : (
          <AllocationGrid grid={grid} projects={projectOptions} slug={slug} />
        )}
      </div>
    </ModuleGate>
  );
}
