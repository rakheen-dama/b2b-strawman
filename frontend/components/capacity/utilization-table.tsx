"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { ArrowUpDown } from "lucide-react";

import { cn } from "@/lib/utils";
import {
  Table,
  TableHeader,
  TableBody,
  TableHead,
  TableRow,
  TableCell,
} from "@/components/ui/table";
import { UtilizationBadge } from "@/components/capacity/utilization-badge";
import type { TeamUtilizationResponse } from "@/lib/api/capacity";

interface UtilizationTableProps {
  data: TeamUtilizationResponse;
  slug: string;
}

interface SortButtonProps {
  field: SortField;
  activeField: SortField;
  onToggle: (field: SortField) => void;
  children: React.ReactNode;
}

function SortButton({ field, activeField, onToggle, children }: SortButtonProps) {
  return (
    <button
      onClick={() => onToggle(field)}
      className={cn(
        "inline-flex items-center gap-1 hover:text-slate-900 dark:hover:text-slate-100",
        activeField === field && "text-slate-900 dark:text-slate-100"
      )}
    >
      {children}
      <ArrowUpDown className="size-3" />
    </button>
  );
}

type SortField =
  | "name"
  | "weeklyCapacity"
  | "plannedHours"
  | "actualHours"
  | "billableHours"
  | "plannedUtil"
  | "actualUtil"
  | "billableUtil"
  | "overAllocated";

function sortMembers(
  members: TeamUtilizationResponse["members"],
  field: SortField,
  dir: "asc" | "desc"
) {
  return [...members].sort((a, b) => {
    let cmp = 0;
    switch (field) {
      case "name":
        cmp = a.memberName.localeCompare(b.memberName);
        break;
      case "weeklyCapacity":
        cmp = a.weeklyCapacity - b.weeklyCapacity;
        break;
      case "plannedHours":
        cmp = a.totalPlannedHours - b.totalPlannedHours;
        break;
      case "actualHours":
        cmp = a.totalActualHours - b.totalActualHours;
        break;
      case "billableHours":
        cmp = a.totalBillableHours - b.totalBillableHours;
        break;
      case "plannedUtil":
        cmp = a.avgPlannedUtilizationPct - b.avgPlannedUtilizationPct;
        break;
      case "actualUtil":
        cmp = a.avgActualUtilizationPct - b.avgActualUtilizationPct;
        break;
      case "billableUtil":
        cmp = a.avgBillableUtilizationPct - b.avgBillableUtilizationPct;
        break;
      case "overAllocated":
        cmp = a.overAllocatedWeeks - b.overAllocatedWeeks;
        break;
    }
    return dir === "asc" ? cmp : -cmp;
  });
}

export function UtilizationTable({ data, slug }: UtilizationTableProps) {
  const router = useRouter();
  const [sortField, setSortField] = useState<SortField>("actualUtil");
  const [sortDir, setSortDir] = useState<"asc" | "desc">("desc");

  function toggleSort(field: SortField) {
    if (sortField === field) {
      setSortDir((d) => (d === "asc" ? "desc" : "asc"));
    } else {
      setSortField(field);
      setSortDir("desc");
    }
  }

  if (data.members.length === 0) {
    return (
      <div className="flex h-48 items-center justify-center rounded-lg border border-dashed border-slate-300 text-sm text-slate-500 dark:border-slate-700 dark:text-slate-400">
        No utilization data available
      </div>
    );
  }

  const sorted = sortMembers(data.members, sortField, sortDir);

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>
            <SortButton field="name" activeField={sortField} onToggle={toggleSort}>
              Member
            </SortButton>
          </TableHead>
          <TableHead>
            <SortButton field="weeklyCapacity" activeField={sortField} onToggle={toggleSort}>
              Capacity
            </SortButton>
          </TableHead>
          <TableHead>
            <SortButton field="plannedHours" activeField={sortField} onToggle={toggleSort}>
              Planned
            </SortButton>
          </TableHead>
          <TableHead>
            <SortButton field="actualHours" activeField={sortField} onToggle={toggleSort}>
              Actual
            </SortButton>
          </TableHead>
          <TableHead>
            <SortButton field="billableHours" activeField={sortField} onToggle={toggleSort}>
              Billable
            </SortButton>
          </TableHead>
          <TableHead>
            <SortButton field="plannedUtil" activeField={sortField} onToggle={toggleSort}>
              Planned %
            </SortButton>
          </TableHead>
          <TableHead>
            <SortButton field="actualUtil" activeField={sortField} onToggle={toggleSort}>
              Actual %
            </SortButton>
          </TableHead>
          <TableHead>
            <SortButton field="billableUtil" activeField={sortField} onToggle={toggleSort}>
              Billable %
            </SortButton>
          </TableHead>
          <TableHead>
            <SortButton field="overAllocated" activeField={sortField} onToggle={toggleSort}>
              Over-Alloc
            </SortButton>
          </TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {sorted.map((member) => (
          <TableRow
            key={member.memberId}
            className="cursor-pointer"
            onClick={() => router.push(`/org/${slug}/resources?memberId=${member.memberId}`)}
          >
            <TableCell className="font-medium">{member.memberName}</TableCell>
            <TableCell className="font-mono tabular-nums">{member.weeklyCapacity}h</TableCell>
            <TableCell className="font-mono tabular-nums">{member.totalPlannedHours}h</TableCell>
            <TableCell className="font-mono tabular-nums">{member.totalActualHours}h</TableCell>
            <TableCell className="font-mono tabular-nums">{member.totalBillableHours}h</TableCell>
            <TableCell>
              <UtilizationBadge percentage={member.avgPlannedUtilizationPct} />
            </TableCell>
            <TableCell>
              <UtilizationBadge percentage={member.avgActualUtilizationPct} />
            </TableCell>
            <TableCell>
              <UtilizationBadge percentage={member.avgBillableUtilizationPct} />
            </TableCell>
            <TableCell className="font-mono tabular-nums">{member.overAllocatedWeeks}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
