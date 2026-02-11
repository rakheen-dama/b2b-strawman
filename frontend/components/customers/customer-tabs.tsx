"use client";

import { useState, type ReactNode } from "react";
import { Tabs as TabsPrimitive } from "radix-ui";
import { motion } from "motion/react";
import { cn } from "@/lib/utils";

interface CustomerTabsProps {
  projectsPanel: ReactNode;
  documentsPanel: ReactNode;
}

const tabs = [
  { id: "projects", label: "Projects" },
  { id: "documents", label: "Documents" },
] as const;

type TabId = (typeof tabs)[number]["id"];

export function CustomerTabs({ projectsPanel, documentsPanel }: CustomerTabsProps) {
  const [activeTab, setActiveTab] = useState<TabId>("projects");

  return (
    <TabsPrimitive.Root value={activeTab} onValueChange={(v) => setActiveTab(v as TabId)}>
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
                layoutId="customer-tab-indicator"
                transition={{ type: "spring", stiffness: 300, damping: 25 }}
                aria-hidden="true"
              />
            )}
          </TabsPrimitive.Trigger>
        ))}
      </TabsPrimitive.List>

      <TabsPrimitive.Content value="projects" className="pt-6 outline-none">
        {projectsPanel}
      </TabsPrimitive.Content>
      <TabsPrimitive.Content value="documents" className="pt-6 outline-none">
        {documentsPanel}
      </TabsPrimitive.Content>
    </TabsPrimitive.Root>
  );
}
