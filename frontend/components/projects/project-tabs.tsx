"use client";

import { useMemo, useState, type ReactNode } from "react";
import { Tabs as TabsPrimitive } from "radix-ui";
import { motion } from "motion/react";
import { cn } from "@/lib/utils";

interface ProjectTabsProps {
  documentsPanel: ReactNode;
  membersPanel: ReactNode;
  customersPanel: ReactNode;
  tasksPanel: ReactNode;
  timePanel: ReactNode;
  activityPanel: ReactNode;
  ratesPanel?: ReactNode;
}

type TabId = "documents" | "members" | "customers" | "tasks" | "time" | "activity" | "rates";

interface TabDef {
  id: TabId;
  label: string;
}

const baseTabs: TabDef[] = [
  { id: "documents", label: "Documents" },
  { id: "members", label: "Members" },
  { id: "customers", label: "Customers" },
  { id: "tasks", label: "Tasks" },
  { id: "time", label: "Time" },
  { id: "rates", label: "Rates" },
  { id: "activity", label: "Activity" },
];

export function ProjectTabs({ documentsPanel, membersPanel, customersPanel, tasksPanel, timePanel, activityPanel, ratesPanel }: ProjectTabsProps) {
  const [activeTab, setActiveTab] = useState<TabId>("documents");

  const tabs = useMemo(() => {
    if (ratesPanel) return baseTabs;
    return baseTabs.filter((t) => t.id !== "rates");
  }, [ratesPanel]);

  return (
    <TabsPrimitive.Root value={activeTab} onValueChange={(v) => setActiveTab(v as TabId)}>
      {/* Radix List provides role="tablist" + arrow-key navigation + roving focus */}
      <TabsPrimitive.List className="relative flex gap-6 border-b border-olive-200 dark:border-olive-800">
        {tabs.map((tab) => (
          <TabsPrimitive.Trigger
            key={tab.id}
            value={tab.id}
            className={cn(
              "relative pb-3 text-sm font-medium transition-colors outline-none",
              "text-olive-600 hover:text-olive-900 dark:text-olive-400 dark:hover:text-olive-200",
              "data-[state=active]:text-olive-950 dark:data-[state=active]:text-olive-50",
              "focus-visible:outline-2 focus-visible:outline-indigo-500 focus-visible:outline-offset-2"
            )}
          >
            {tab.label}
            {activeTab === tab.id && (
              <motion.span
                className="absolute inset-x-0 bottom-0 h-0.5 bg-indigo-500"
                layoutId="project-tab-indicator"
                transition={{ type: "spring", stiffness: 300, damping: 25 }}
                aria-hidden="true"
              />
            )}
          </TabsPrimitive.Trigger>
        ))}
      </TabsPrimitive.List>

      {/* Radix Content provides role="tabpanel" + aria-labelledby */}
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
      {ratesPanel && (
        <TabsPrimitive.Content value="rates" className="pt-6 outline-none">
          {ratesPanel}
        </TabsPrimitive.Content>
      )}
      <TabsPrimitive.Content value="activity" className="pt-6 outline-none">
        {activityPanel}
      </TabsPrimitive.Content>
    </TabsPrimitive.Root>
  );
}
