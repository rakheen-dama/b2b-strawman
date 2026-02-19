"use client";

import { useMemo, useState, type ReactNode } from "react";
import { useSearchParams } from "next/navigation";
import { Tabs as TabsPrimitive } from "radix-ui";
import { motion } from "motion/react";
import { cn } from "@/lib/utils";

interface ProjectTabsProps {
  overviewPanel: ReactNode;
  documentsPanel: ReactNode;
  membersPanel: ReactNode;
  customersPanel: ReactNode;
  tasksPanel: ReactNode;
  timePanel: ReactNode;
  activityPanel: ReactNode;
  ratesPanel?: ReactNode;
  budgetPanel?: ReactNode;
  financialsPanel?: ReactNode;
  generatedPanel?: ReactNode;
}

type TabId = "overview" | "documents" | "members" | "customers" | "tasks" | "time" | "budget" | "financials" | "activity" | "rates" | "generated";

interface TabDef {
  id: TabId;
  label: string;
}

const baseTabs: TabDef[] = [
  { id: "overview", label: "Overview" },
  { id: "documents", label: "Documents" },
  { id: "members", label: "Members" },
  { id: "customers", label: "Customers" },
  { id: "tasks", label: "Tasks" },
  { id: "time", label: "Time" },
  { id: "budget", label: "Budget" },
  { id: "financials", label: "Financials" },
  { id: "rates", label: "Rates" },
  { id: "generated", label: "Generated Docs" },
  { id: "activity", label: "Activity" },
];

const validTabIds = new Set<string>(["overview", "documents", "members", "customers", "tasks", "time", "budget", "financials", "activity", "rates", "generated"]);

export function ProjectTabs({ overviewPanel, documentsPanel, membersPanel, customersPanel, tasksPanel, timePanel, activityPanel, ratesPanel, budgetPanel, financialsPanel, generatedPanel }: ProjectTabsProps) {
  const searchParams = useSearchParams();
  const tabParam = searchParams.get("tab");
  const urlTab = tabParam && validTabIds.has(tabParam) ? (tabParam as TabId) : null;
  const [userTab, setUserTab] = useState<TabId | null>(null);

  // URL param takes precedence, then user's manual selection, then default
  const activeTab = urlTab ?? userTab ?? "overview";

  const tabs = useMemo(() => {
    let filtered = baseTabs;
    if (!ratesPanel) filtered = filtered.filter((t) => t.id !== "rates");
    if (!financialsPanel) filtered = filtered.filter((t) => t.id !== "financials");
    if (!generatedPanel) filtered = filtered.filter((t) => t.id !== "generated");
    return filtered;
  }, [ratesPanel, financialsPanel, generatedPanel]);

  return (
    <TabsPrimitive.Root value={activeTab} onValueChange={(v) => setUserTab(v as TabId)}>
      {/* Radix List provides role="tablist" + arrow-key navigation + roving focus */}
      <TabsPrimitive.List className="relative flex gap-6 border-b border-slate-200 dark:border-slate-800">
        {tabs.map((tab) => (
          <TabsPrimitive.Trigger
            key={tab.id}
            value={tab.id}
            className={cn(
              "relative pb-3 text-sm font-medium transition-colors outline-none",
              "text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-200",
              "data-[state=active]:text-slate-950 dark:data-[state=active]:text-slate-50",
              "focus-visible:outline-2 focus-visible:outline-teal-500 focus-visible:outline-offset-2"
            )}
          >
            {tab.label}
            {activeTab === tab.id && (
              <motion.span
                className="absolute inset-x-0 bottom-0 h-0.5 bg-teal-500"
                layoutId="project-tab-indicator"
                transition={{ type: "spring", stiffness: 300, damping: 25 }}
                aria-hidden="true"
              />
            )}
          </TabsPrimitive.Trigger>
        ))}
      </TabsPrimitive.List>

      {/* Radix Content provides role="tabpanel" + aria-labelledby */}
      <TabsPrimitive.Content value="overview" className="pt-6 outline-none">
        {overviewPanel}
      </TabsPrimitive.Content>
      <TabsPrimitive.Content value="documents" className="pt-6 outline-none">
        {documentsPanel}
      </TabsPrimitive.Content>
      <TabsPrimitive.Content value="members" className="pt-6 outline-none">
        {membersPanel}
      </TabsPrimitive.Content>
      <TabsPrimitive.Content value="customers" className="pt-6 outline-none">
        {customersPanel}
      </TabsPrimitive.Content>
      <TabsPrimitive.Content value="tasks" className="pt-6 outline-none">
        {tasksPanel}
      </TabsPrimitive.Content>
      <TabsPrimitive.Content value="time" className="pt-6 outline-none">
        {timePanel}
      </TabsPrimitive.Content>
      <TabsPrimitive.Content value="budget" className="pt-6 outline-none">
        {budgetPanel}
      </TabsPrimitive.Content>
      {financialsPanel && (
        <TabsPrimitive.Content value="financials" className="pt-6 outline-none">
          {financialsPanel}
        </TabsPrimitive.Content>
      )}
      {ratesPanel && (
        <TabsPrimitive.Content value="rates" className="pt-6 outline-none">
          {ratesPanel}
        </TabsPrimitive.Content>
      )}
      {generatedPanel && (
        <TabsPrimitive.Content value="generated" className="pt-6 outline-none">
          {generatedPanel}
        </TabsPrimitive.Content>
      )}
      <TabsPrimitive.Content value="activity" className="pt-6 outline-none">
        {activityPanel}
      </TabsPrimitive.Content>
    </TabsPrimitive.Root>
  );
}
