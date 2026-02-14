"use client";

import { useMemo, useState, type ReactNode } from "react";
import { Tabs as TabsPrimitive } from "radix-ui";
import { motion } from "motion/react";
import { cn } from "@/lib/utils";

interface CustomerTabsProps {
  projectsPanel: ReactNode;
  documentsPanel: ReactNode;
  ratesPanel?: ReactNode;
  financialsPanel?: ReactNode;
  invoicesPanel?: ReactNode;
}

type TabId = "projects" | "documents" | "rates" | "financials" | "invoices";

interface TabDef {
  id: TabId;
  label: string;
}

const baseTabs: TabDef[] = [
  { id: "projects", label: "Projects" },
  { id: "documents", label: "Documents" },
  { id: "invoices", label: "Invoices" },
  { id: "rates", label: "Rates" },
  { id: "financials", label: "Financials" },
];

export function CustomerTabs({
  projectsPanel,
  documentsPanel,
  ratesPanel,
  financialsPanel,
  invoicesPanel,
}: CustomerTabsProps) {
  const [activeTab, setActiveTab] = useState<TabId>("projects");

  const tabs = useMemo(() => {
    return baseTabs.filter((t) => {
      if (t.id === "invoices" && !invoicesPanel) return false;
      if (t.id === "rates" && !ratesPanel) return false;
      if (t.id === "financials" && !financialsPanel) return false;
      return true;
    });
  }, [invoicesPanel, ratesPanel, financialsPanel]);

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
      {invoicesPanel && (
        <TabsPrimitive.Content value="invoices" className="pt-6 outline-none">
          {invoicesPanel}
        </TabsPrimitive.Content>
      )}
      {ratesPanel && (
        <TabsPrimitive.Content value="rates" className="pt-6 outline-none">
          {ratesPanel}
        </TabsPrimitive.Content>
      )}
      {financialsPanel && (
        <TabsPrimitive.Content value="financials" className="pt-6 outline-none">
          {financialsPanel}
        </TabsPrimitive.Content>
      )}
    </TabsPrimitive.Root>
  );
}
