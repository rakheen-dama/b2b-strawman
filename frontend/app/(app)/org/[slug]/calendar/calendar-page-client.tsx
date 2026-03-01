"use client";

import { useState, useTransition, useCallback, useEffect } from "react";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { CalendarMonthView } from "./calendar-month-view";
import { CalendarListView } from "./calendar-list-view";
import {
  getCalendarItems,
  getCalendarProjects,
  getCalendarMembers,
} from "./calendar-actions";
import type { CalendarItem } from "./calendar-types";
import { formatDate } from "./calendar-types";

interface CalendarPageClientProps {
  initialItems: CalendarItem[];
  initialOverdueCount: number;
  initialYear: number;
  initialMonth: number; // 1-indexed
  slug: string;
}

export function CalendarPageClient({
  initialItems,
  initialOverdueCount,
  initialYear,
  initialMonth,
  slug,
}: CalendarPageClientProps) {
  const [items, setItems] = useState(initialItems);
  const [overdueCount, setOverdueCount] = useState(initialOverdueCount);
  const [year, setYear] = useState(initialYear);
  const [month, setMonth] = useState(initialMonth);
  const [view, setView] = useState<"month" | "list">("month");
  const [isPending, startTransition] = useTransition();

  // Filter state
  const [projectId, setProjectId] = useState<string | undefined>(undefined);
  const [type, setType] = useState<"TASK" | "PROJECT" | undefined>(undefined);
  const [assigneeId, setAssigneeId] = useState<string | undefined>(undefined);
  const [projects, setProjects] = useState<{ id: string; name: string }[]>([]);
  const [members, setMembers] = useState<{ id: string; name: string }[]>([]);

  // Load projects and members on mount
  useEffect(() => {
    getCalendarProjects().then(setProjects).catch(console.error);
    getCalendarMembers().then(setMembers).catch(console.error);
  }, []);

  const refetch = useCallback(
    (
      newProjectId?: string,
      newType?: "TASK" | "PROJECT",
      newAssigneeId?: string
    ) => {
      const firstDay = new Date(year, month - 1, 1);
      const lastDay = new Date(year, month, 0);
      const from = formatDate(firstDay);
      const to = formatDate(lastDay);
      startTransition(async () => {
        const result = await getCalendarItems(from, to, {
          projectId: newProjectId,
          type: newType,
          assigneeId: newAssigneeId,
          overdue: true,
        });
        setItems(result.items);
        setOverdueCount(result.overdueCount);
      });
    },
    [year, month]
  );

  const navigateMonth = useCallback(
    (newYear: number, newMonth: number) => {
      setYear(newYear);
      setMonth(newMonth);

      const firstDay = new Date(newYear, newMonth - 1, 1);
      const lastDay = new Date(newYear, newMonth, 0);
      const from = formatDate(firstDay);
      const to = formatDate(lastDay);

      startTransition(async () => {
        const result = await getCalendarItems(from, to, {
          projectId,
          type,
          assigneeId,
          overdue: true,
        });
        setItems(result.items);
        setOverdueCount(result.overdueCount);
      });
    },
    [projectId, type, assigneeId]
  );

  // Compute overdue items for list view
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const todayStr = formatDate(today);
  const overdueItems = items.filter((item) => item.dueDate < todayStr);
  const regularItems = items.filter((item) => item.dueDate >= todayStr);

  return (
    <div className="space-y-4">
      {/* Filter Bar */}
      <div className="flex flex-wrap items-center gap-3">
        <Select
          value={projectId ?? "all"}
          onValueChange={(v) => {
            const newVal = v === "all" ? undefined : v;
            setProjectId(newVal);
            refetch(newVal, type, assigneeId);
          }}
        >
          <SelectTrigger className="w-[180px]">
            <SelectValue placeholder="All Projects" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All Projects</SelectItem>
            {projects.map((p) => (
              <SelectItem key={p.id} value={p.id}>
                {p.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        {/* Type toggle chips */}
        <div className="flex items-center gap-1">
          {(["ALL", "TASK", "PROJECT"] as const).map((t) => {
            const isActive =
              (t === "ALL" && !type) || (t !== "ALL" && type === t);
            return (
              <button
                key={t}
                type="button"
                aria-pressed={isActive}
                onClick={() => {
                  const newType =
                    t === "ALL" ? undefined : (t as "TASK" | "PROJECT");
                  setType(newType);
                  refetch(projectId, newType, assigneeId);
                }}
                className={cn(
                  "rounded-full px-3 py-1 text-sm font-medium transition-colors",
                  isActive
                    ? "bg-slate-900 text-slate-50 dark:bg-slate-100 dark:text-slate-900"
                    : "bg-slate-100 text-slate-600 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-400 dark:hover:bg-slate-700"
                )}
              >
                {t === "ALL" ? "All" : t === "TASK" ? "Tasks" : "Projects"}
              </button>
            );
          })}
        </div>

        <Select
          value={assigneeId ?? "all"}
          onValueChange={(v) => {
            const newVal = v === "all" ? undefined : v;
            setAssigneeId(newVal);
            refetch(projectId, type, newVal);
          }}
        >
          <SelectTrigger className="w-[180px]">
            <SelectValue placeholder="All Members" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All Members</SelectItem>
            {members.map((m) => (
              <SelectItem key={m.id} value={m.id}>
                {m.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {/* View Toggle + Overdue Badge */}
      <div className="flex items-center justify-between">
        <Tabs
          value={view}
          onValueChange={(v) => setView(v as "month" | "list")}
        >
          <TabsList>
            <TabsTrigger value="month">Month</TabsTrigger>
            <TabsTrigger value="list">List</TabsTrigger>
          </TabsList>
        </Tabs>
        {overdueCount > 0 && (
          <Badge variant="destructive" className="shrink-0">
            {overdueCount} overdue
          </Badge>
        )}
      </div>

      {/* Views */}
      {view === "month" ? (
        <CalendarMonthView
          items={items}
          year={year}
          month={month}
          onNavigate={navigateMonth}
          isPending={isPending}
          slug={slug}
        />
      ) : (
        <CalendarListView
          items={regularItems}
          overdueItems={overdueItems}
          slug={slug}
        />
      )}
    </div>
  );
}
