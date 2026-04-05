"use client";

import { useMemo, useState, type ReactNode } from "react";
import { useSearchParams } from "next/navigation";
import { Tabs as TabsPrimitive } from "radix-ui";
import { motion } from "motion/react";
import { cn } from "@/lib/utils";
import { useOrgProfile } from "@/lib/org-profile";

interface CustomerTabsProps {
  projectsPanel: ReactNode;
  documentsPanel: ReactNode;
  ratesPanel?: ReactNode;
  financialsPanel?: ReactNode;
  invoicesPanel?: ReactNode;
  retainerPanel?: ReactNode;
  requestsPanel?: ReactNode;
  generatedPanel?: ReactNode;
  onboardingPanel?: ReactNode;
  trustPanel?: ReactNode;
}

type TabId = "projects" | "documents" | "onboarding" | "rates" | "financials" | "invoices" | "retainer" | "requests" | "generated" | "trust";

interface TabDef {
  id: TabId;
  label: string;
}

const baseTabs: TabDef[] = [
  { id: "projects", label: "Projects" },
  { id: "documents", label: "Documents" },
  { id: "onboarding", label: "Onboarding" },
  { id: "invoices", label: "Invoices" },
  { id: "retainer", label: "Retainer" },
  { id: "requests", label: "Requests" },
  { id: "rates", label: "Rates" },
  { id: "generated", label: "Generated Docs" },
  { id: "financials", label: "Financials" },
  { id: "trust", label: "Trust" },
];

const validTabIds = new Set<string>(["projects", "documents", "onboarding", "invoices", "retainer", "requests", "rates", "generated", "financials", "trust"]);

export function CustomerTabs({
  projectsPanel,
  documentsPanel,
  ratesPanel,
  financialsPanel,
  invoicesPanel,
  retainerPanel,
  requestsPanel,
  generatedPanel,
  onboardingPanel,
  trustPanel,
}: CustomerTabsProps) {
  const searchParams = useSearchParams();
  const tabParam = searchParams.get("tab");
  const urlTab = tabParam && validTabIds.has(tabParam) ? (tabParam as TabId) : null;
  const [userTab, setUserTab] = useState<TabId | null>(null);
  const { isModuleEnabled } = useOrgProfile();

  const requestedTab = urlTab ?? userTab;

  // Module-gated tabs
  const showTrust = !!trustPanel && isModuleEnabled("trust_accounting");

  const tabs = useMemo(() => {
    return baseTabs.filter((t) => {
      if (t.id === "onboarding" && !onboardingPanel) return false;
      if (t.id === "invoices" && !invoicesPanel) return false;
      if (t.id === "retainer" && !retainerPanel) return false;
      if (t.id === "requests" && !requestsPanel) return false;
      if (t.id === "rates" && !ratesPanel) return false;
      if (t.id === "generated" && !generatedPanel) return false;
      if (t.id === "financials" && !financialsPanel) return false;
      if (t.id === "trust" && !showTrust) return false;
      return true;
    });
  }, [onboardingPanel, invoicesPanel, retainerPanel, requestsPanel, ratesPanel, financialsPanel, generatedPanel, showTrust]);

  // Validate activeTab is in the rendered tabs; fall back to "projects" if not
  const activeTab: TabId =
    requestedTab && tabs.some((t) => t.id === requestedTab)
      ? requestedTab
      : "projects";

  return (
    <TabsPrimitive.Root value={activeTab} onValueChange={(v) => setUserTab(v as TabId)}>
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
      {retainerPanel && (
        <TabsPrimitive.Content value="retainer" className="pt-6 outline-none">
          {retainerPanel}
        </TabsPrimitive.Content>
      )}
      {requestsPanel && (
        <TabsPrimitive.Content value="requests" className="pt-6 outline-none">
          {requestsPanel}
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
      {showTrust && (
        <TabsPrimitive.Content value="trust" className="pt-6 outline-none">
          {trustPanel}
        </TabsPrimitive.Content>
      )}
    </TabsPrimitive.Root>
  );
}
