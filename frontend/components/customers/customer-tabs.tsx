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
  generatedPanel?: ReactNode;
  onboardingPanel?: ReactNode;
}

type TabId = "projects" | "documents" | "onboarding" | "rates" | "financials" | "invoices" | "generated";

interface TabDef {
  id: TabId;
  label: string;
}

const baseTabs: TabDef[] = [
  { id: "projects", label: "Projects" },
  { id: "documents", label: "Documents" },
  { id: "onboarding", label: "Onboarding" },
  { id: "invoices", label: "Invoices" },
  { id: "rates", label: "Rates" },
  { id: "generated", label: "Generated Docs" },
  { id: "financials", label: "Financials" },
];

export function CustomerTabs({
  projectsPanel,
  documentsPanel,
  ratesPanel,
  financialsPanel,
  invoicesPanel,
  generatedPanel,
  onboardingPanel,
}: CustomerTabsProps) {
  const [activeTab, setActiveTab] = useState<TabId>("projects");

  const tabs = useMemo(() => {
    return baseTabs.filter((t) => {
      if (t.id === "onboarding" && !onboardingPanel) return false;
      if (t.id === "invoices" && !invoicesPanel) return false;
      if (t.id === "rates" && !ratesPanel) return false;
      if (t.id === "generated" && !generatedPanel) return false;
      if (t.id === "financials" && !financialsPanel) return false;
      return true;
    });
  }, [onboardingPanel, invoicesPanel, ratesPanel, financialsPanel, generatedPanel]);

  return (
    <TabsPrimitive.Root value={activeTab} onValueChange={(v) => setActiveTab(v as TabId)}>
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
      {onboardingPanel && (
        <TabsPrimitive.Content value="onboarding" className="pt-6 outline-none">
          {onboardingPanel}
        </TabsPrimitive.Content>
      )}
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
      {generatedPanel && (
        <TabsPrimitive.Content value="generated" className="pt-6 outline-none">
          {generatedPanel}
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
