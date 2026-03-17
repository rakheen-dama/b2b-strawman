"use client";

import { useState, useCallback } from "react";

import { cn } from "@/lib/utils";
import type { TeamCapacityGrid, MemberRow } from "@/lib/api/capacity";
import { CapacityCell } from "./capacity-cell";
import { UtilizationBadge } from "./utilization-badge";
import { AllocationPopover } from "./allocation-popover";
import { MemberDetailPanel } from "./member-detail-panel";
import { GridFilters } from "./grid-filters";

interface ProjectOption {
  id: string;
  name: string;
}

interface AllocationGridProps {
  grid: TeamCapacityGrid;
  projects: ProjectOption[];
  slug: string;
}

function formatWeekHeader(weekStart: string): string {
  if (!weekStart) return "—";
  const [y, m, d] = weekStart.split("-").map(Number);
  const date = new Date(y, m - 1, d);
  return date.toLocaleDateString("en-GB", { day: "numeric", month: "short" });
}

export function AllocationGrid({
  grid,
  projects,
  slug,
}: AllocationGridProps) {
  const [filteredGrid, setFilteredGrid] = useState<TeamCapacityGrid>(grid);
  const [selectedMember, setSelectedMember] = useState<MemberRow | null>(null);
  const [panelOpen, setPanelOpen] = useState(false);

  const handleFilteredGrid = useCallback((newGrid: TeamCapacityGrid) => {
    setFilteredGrid(newGrid);
  }, []);

  const { members, weekSummaries } = filteredGrid;

  if (grid.members.length === 0) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-8 text-center dark:border-slate-700 dark:bg-slate-800">
        <p className="text-slate-600 dark:text-slate-400">
          No team members found. Add members to your organization to start
          planning capacity.
        </p>
      </div>
    );
  }

  const weekStarts = members[0]?.weeks?.map((w) => w.weekStart) ?? [];

  function handleMemberClick(member: MemberRow) {
    setSelectedMember(member);
    setPanelOpen(true);
  }

  return (
    <div className="space-y-4">
      {projects.length > 0 && (
        <GridFilters
          grid={grid}
          projects={projects}
          onFilteredGrid={handleFilteredGrid}
        />
      )}

      <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-800">
        <table className="w-full border-collapse">
          <thead>
            <tr className="border-b border-slate-200 dark:border-slate-700">
              <th className="sticky left-0 z-10 min-w-[180px] bg-white px-4 py-3 text-left text-sm font-medium text-slate-600 dark:bg-slate-800 dark:text-slate-400">
                Member
              </th>
              {weekStarts.map((ws) => (
                <th
                  key={ws}
                  className="min-w-[100px] px-2 py-3 text-center text-sm font-medium text-slate-600 dark:text-slate-400"
                >
                  {formatWeekHeader(ws)}
                </th>
              ))}
              <th className="min-w-[80px] px-2 py-3 text-center text-sm font-medium text-slate-600 dark:text-slate-400">
                Avg
              </th>
            </tr>
          </thead>
          <tbody>
            {members.map((member) => (
              <tr
                key={member.memberId}
                className="border-b border-slate-100 dark:border-slate-700/50"
              >
                <td className="sticky left-0 z-10 bg-white px-4 py-2 dark:bg-slate-800">
                  <button
                    className="flex items-center gap-2 text-left"
                    onClick={() => handleMemberClick(member)}
                    data-testid={`member-name-${member.memberId}`}
                  >
                    {member.avatarUrl &&
                    /^https?:\/\//.test(member.avatarUrl) ? (
                      <img
                        src={member.avatarUrl}
                        alt=""
                        className="h-7 w-7 rounded-full"
                      />
                    ) : (
                      <div className="flex h-7 w-7 items-center justify-center rounded-full bg-slate-200 text-xs font-medium text-slate-600 dark:bg-slate-700 dark:text-slate-300">
                        {(member.memberName || "?")
                          .split(" ")
                          .map((n) => n[0])
                          .join("")
                          .slice(0, 2)
                          .toUpperCase()}
                      </div>
                    )}
                    <span className="text-sm font-medium text-slate-900 hover:text-teal-600 dark:text-slate-100 dark:hover:text-teal-400">
                      {member.memberName}
                    </span>
                  </button>
                </td>
                {(member.weeks ?? []).map((week) => (
                  <td key={week.weekStart} className="px-2 py-2">
                    <AllocationPopover
                      memberId={member.memberId}
                      memberName={member.memberName}
                      weekStart={week.weekStart}
                      cell={week}
                      projects={projects}
                      slug={slug}
                    >
                      <div>
                        <CapacityCell cell={week} />
                      </div>
                    </AllocationPopover>
                  </td>
                ))}
                <td className="px-2 py-2 text-center">
                  <UtilizationBadge percentage={member.avgUtilizationPct} />
                </td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr className="border-t border-slate-200 bg-slate-50 dark:border-slate-700 dark:bg-slate-800/50">
              <td className="sticky left-0 z-10 bg-slate-50 px-4 py-3 text-sm font-medium text-slate-600 dark:bg-slate-800/50 dark:text-slate-400">
                Team Total
              </td>
              {weekSummaries.map((ws) => (
                <td
                  key={ws.weekStart}
                  className={cn(
                    "px-2 py-3 text-center font-mono text-xs tabular-nums font-medium",
                    ws.teamUtilizationPct > 100
                      ? "text-red-600 dark:text-red-400"
                      : ws.teamUtilizationPct >= 80
                        ? "text-amber-600 dark:text-amber-400"
                        : "text-emerald-600 dark:text-emerald-400",
                  )}
                >
                  {ws.teamTotalAllocated}/{ws.teamTotalCapacity}h
                </td>
              ))}
              <td className="px-2 py-3 text-center">
                {weekSummaries.length > 0 && (
                  <UtilizationBadge
                    percentage={Math.round(
                      weekSummaries.reduce(
                        (sum, ws) => sum + ws.teamUtilizationPct,
                        0,
                      ) / weekSummaries.length,
                    )}
                  />
                )}
              </td>
            </tr>
          </tfoot>
        </table>
      </div>

      <MemberDetailPanel
        open={panelOpen}
        onOpenChange={setPanelOpen}
        member={selectedMember}
        slug={slug}
        projects={projects}
      />
    </div>
  );
}
