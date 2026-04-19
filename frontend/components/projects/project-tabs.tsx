"use client";

import { useMemo, useState, type ReactNode } from "react";
import { useSearchParams } from "next/navigation";
import { Tabs as TabsPrimitive } from "radix-ui";
import { motion } from "motion/react";
import { cn } from "@/lib/utils";
import { useOrgProfile } from "@/lib/org-profile";
import { useTerminology } from "@/lib/terminology";

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
  expensesPanel?: ReactNode;
  generatedPanel?: ReactNode;
  requestsPanel?: ReactNode;
  customerCommentsPanel?: ReactNode;
  staffingPanel?: ReactNode;
  courtDatesPanel?: ReactNode;
  adversePartiesPanel?: ReactNode;
  trustPanel?: ReactNode;
  disbursementsPanel?: ReactNode;
}

type TabId =
  | "overview"
  | "documents"
  | "members"
  | "customers"
  | "tasks"
  | "time"
  | "expenses"
  | "budget"
  | "financials"
  | "staffing"
  | "activity"
  | "rates"
  | "generated"
  | "requests"
  | "customer-comments"
  | "court-dates"
  | "adverse-parties"
  | "trust"
  | "disbursements";

interface TabDef {
  id: TabId;
  label: string;
}

function buildBaseTabs(t: (term: string) => string): TabDef[] {
  return [
    { id: "overview", label: "Overview" },
    { id: "documents", label: "Documents" },
    { id: "members", label: "Members" },
    { id: "customers", label: t("Customers") },
    { id: "tasks", label: t("Tasks") },
    { id: "time", label: "Time" },
    { id: "expenses", label: t("Expenses") },
    { id: "budget", label: t("Budget") },
    { id: "financials", label: "Financials" },
    { id: "staffing", label: "Staffing" },
    { id: "rates", label: "Rates" },
    { id: "generated", label: "Generated Docs" },
    { id: "requests", label: "Requests" },
    { id: "customer-comments", label: `${t("Client")} Comments` },
    { id: "court-dates", label: "Court Dates" },
    { id: "adverse-parties", label: "Adverse Parties" },
    { id: "trust", label: "Trust" },
    { id: "disbursements", label: "Disbursements" },
    { id: "activity", label: "Activity" },
  ];
}

const validTabIds = new Set<string>([
  "overview",
  "documents",
  "members",
  "customers",
  "tasks",
  "time",
  "expenses",
  "budget",
  "financials",
  "staffing",
  "activity",
  "rates",
  "generated",
  "requests",
  "customer-comments",
  "court-dates",
  "adverse-parties",
  "trust",
  "disbursements",
]);

export function ProjectTabs({
  overviewPanel,
  documentsPanel,
  membersPanel,
  customersPanel,
  tasksPanel,
  timePanel,
  activityPanel,
  ratesPanel,
  budgetPanel,
  financialsPanel,
  expensesPanel,
  generatedPanel,
  requestsPanel,
  customerCommentsPanel,
  staffingPanel,
  courtDatesPanel,
  adversePartiesPanel,
  trustPanel,
  disbursementsPanel,
}: ProjectTabsProps) {
  const searchParams = useSearchParams();
  const tabParam = searchParams.get("tab");
  const urlTab = tabParam && validTabIds.has(tabParam) ? (tabParam as TabId) : null;
  const [userTab, setUserTab] = useState<TabId | null>(null);
  const { isModuleEnabled } = useOrgProfile();
  const { t } = useTerminology();
  const baseTabs = useMemo(() => buildBaseTabs(t), [t]);

  const requestedTab = urlTab ?? userTab;

  // Module-gated tabs: only show when both the panel is provided and the module is enabled
  const showCourtDates = !!courtDatesPanel && isModuleEnabled("court_calendar");
  const showAdverseParties = !!adversePartiesPanel && isModuleEnabled("conflict_check");
  const showTrust = !!trustPanel && isModuleEnabled("trust_accounting");
  const showDisbursements = !!disbursementsPanel && isModuleEnabled("disbursements");

  const tabs = useMemo(() => {
    let filtered = baseTabs;
    if (!ratesPanel) filtered = filtered.filter((t) => t.id !== "rates");
    if (!financialsPanel) filtered = filtered.filter((t) => t.id !== "financials");
    if (!expensesPanel) filtered = filtered.filter((t) => t.id !== "expenses");
    if (!generatedPanel) filtered = filtered.filter((t) => t.id !== "generated");
    if (!requestsPanel) filtered = filtered.filter((t) => t.id !== "requests");
    if (!customerCommentsPanel) filtered = filtered.filter((t) => t.id !== "customer-comments");
    if (!staffingPanel) filtered = filtered.filter((t) => t.id !== "staffing");
    if (!showCourtDates) filtered = filtered.filter((t) => t.id !== "court-dates");
    if (!showAdverseParties) filtered = filtered.filter((t) => t.id !== "adverse-parties");
    if (!showTrust) filtered = filtered.filter((t) => t.id !== "trust");
    if (!showDisbursements) filtered = filtered.filter((t) => t.id !== "disbursements");
    return filtered;
  }, [
    baseTabs,
    ratesPanel,
    financialsPanel,
    expensesPanel,
    generatedPanel,
    requestsPanel,
    customerCommentsPanel,
    staffingPanel,
    showCourtDates,
    showAdverseParties,
    showTrust,
    showDisbursements,
  ]);

  // Validate activeTab is in the rendered tabs; fall back to "overview" if not
  const activeTab: TabId =
    requestedTab && tabs.some((t) => t.id === requestedTab) ? requestedTab : "overview";

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
              "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-teal-500"
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
      {expensesPanel && (
        <TabsPrimitive.Content value="expenses" className="pt-6 outline-none">
          {expensesPanel}
        </TabsPrimitive.Content>
      )}
      <TabsPrimitive.Content value="budget" className="pt-6 outline-none">
        {budgetPanel}
      </TabsPrimitive.Content>
      {financialsPanel && (
        <TabsPrimitive.Content value="financials" className="pt-6 outline-none">
          {financialsPanel}
        </TabsPrimitive.Content>
      )}
      {staffingPanel && (
        <TabsPrimitive.Content value="staffing" className="pt-6 outline-none">
          {staffingPanel}
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
      {requestsPanel && (
        <TabsPrimitive.Content value="requests" className="pt-6 outline-none">
          {requestsPanel}
        </TabsPrimitive.Content>
      )}
      {customerCommentsPanel && (
        <TabsPrimitive.Content value="customer-comments" className="pt-6 outline-none">
          {customerCommentsPanel}
        </TabsPrimitive.Content>
      )}
      {showCourtDates && (
        <TabsPrimitive.Content value="court-dates" className="pt-6 outline-none">
          {courtDatesPanel}
        </TabsPrimitive.Content>
      )}
      {showAdverseParties && (
        <TabsPrimitive.Content value="adverse-parties" className="pt-6 outline-none">
          {adversePartiesPanel}
        </TabsPrimitive.Content>
      )}
      {showTrust && (
        <TabsPrimitive.Content value="trust" className="pt-6 outline-none">
          {trustPanel}
        </TabsPrimitive.Content>
      )}
      {showDisbursements && (
        <TabsPrimitive.Content value="disbursements" className="pt-6 outline-none">
          {disbursementsPanel}
        </TabsPrimitive.Content>
      )}
      <TabsPrimitive.Content value="activity" className="pt-6 outline-none">
        {activityPanel}
      </TabsPrimitive.Content>
    </TabsPrimitive.Root>
  );
}
