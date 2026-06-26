"use client";

import { useEffect, useMemo, useState, type ReactNode } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Tabs as TabsPrimitive } from "radix-ui";
import { useOrgProfile } from "@/lib/org-profile";
import { useTerminology } from "@/lib/terminology";
import { auditTabLabel } from "@b2mash/shared/terminology-map";
import { useAuditTabVisible } from "@/components/audit/audit-timeline-tab";
import { GroupedTabBar } from "@/components/shared/grouped-tab-bar";
import { TAB_GROUPS, MEMBERS_TAB_REDIRECT } from "@/lib/constants/project-tab-groups";

interface ProjectTabsProps {
  detailsPanel: ReactNode;
  fieldsPanel: ReactNode;
  overviewPanel: ReactNode;
  documentsPanel: ReactNode;
  correspondencePanel?: ReactNode;
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
  statementsPanel?: ReactNode;
  auditPanel?: ReactNode;
}

type TabId =
  | "details"
  | "fields"
  | "overview"
  | "documents"
  | "correspondence"
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
  | "disbursements"
  | "statements"
  | "audit";

const validTabIds = new Set<string>([
  "details",
  "fields",
  "overview",
  "documents",
  "correspondence",
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
  "statements",
  "audit",
]);

export function ProjectTabs({
  detailsPanel,
  fieldsPanel,
  overviewPanel,
  documentsPanel,
  correspondencePanel,
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
  statementsPanel,
  auditPanel,
}: ProjectTabsProps) {
  const searchParams = useSearchParams();
  const router = useRouter();
  const tabParam = searchParams.get("tab");
  const urlTab = tabParam && validTabIds.has(tabParam) ? (tabParam as TabId) : null;
  const [userTab, setUserTab] = useState<TabId | null>(null);
  const { isModuleEnabled } = useOrgProfile();
  const { t } = useTerminology();

  // Members backward-compat redirect: ?tab=members → ?tab=staffing (no history entry)
  useEffect(() => {
    if (tabParam === "members") {
      const params = new URLSearchParams(searchParams.toString());
      params.set("tab", MEMBERS_TAB_REDIRECT);
      router.replace(`?${params.toString()}`);
    }
  }, [tabParam, router, searchParams]);

  const requestedTab = urlTab ?? userTab;

  // Module-gated tabs: only show when both the panel is provided and the module is enabled
  const showCourtDates = !!courtDatesPanel && isModuleEnabled("court_calendar");
  const showAdverseParties = !!adversePartiesPanel && isModuleEnabled("conflict_check");
  const showTrust = !!trustPanel && isModuleEnabled("trust_accounting");
  const showDisbursements = !!disbursementsPanel && isModuleEnabled("disbursements");
  // Statements of Account are co-gated with the disbursements module per ADR-250
  // (there is no separate statement_of_account module).
  const showStatements = !!statementsPanel && isModuleEnabled("disbursements");
  // Capability-gated: members without TEAM_OVERSIGHT must not see the Audit tab
  // at all — otherwise they click through to an empty pane (PR #1281 follow-up).
  const auditVisible = useAuditTabVisible();
  const showAudit = !!auditPanel && auditVisible;

  // Build visible groups from TAB_GROUPS with module-gating and terminology rewriting
  const visibleGroups = useMemo(() => {
    /** Per-tab visibility map — true means the tab should be shown */
    const tabVisibility: Record<string, boolean> = {
      // Details group — always visible
      details: true,
      fields: true,
      // Always-visible tabs
      overview: true,
      tasks: true,
      documents: true,
      time: true,
      customers: true,
      activity: true,
      // Panel-gated tabs (visible only when the panel prop is provided)
      correspondence: !!correspondencePanel,
      generated: !!generatedPanel,
      staffing: !!staffingPanel,
      // Dedupe: when the legal-specific Disbursements tab is shown AND the
      // generic Expenses tab is present, their labels collide in legal-za
      // (terminology maps "Expenses" → "Disbursements"). Prefer the legal
      // tab and hide the generic one. GAP-L-38.
      expenses: !!expensesPanel && !showDisbursements,
      budget: true,
      rates: !!ratesPanel,
      financials: !!financialsPanel,
      requests: !!requestsPanel,
      "customer-comments": !!customerCommentsPanel,
      // Module-gated tabs
      "court-dates": showCourtDates,
      "adverse-parties": showAdverseParties,
      trust: showTrust,
      disbursements: showDisbursements,
      statements: showStatements,
      audit: showAudit,
    };

    return TAB_GROUPS.map((group) => ({
      ...group,
      visible: group.tabs.some((t) => tabVisibility[t.id] !== false),
      tabs: group.tabs.map((tab) => {
        // Apply terminology rewriting to specific tab labels
        let label = tab.label;
        if (tab.id === "customers") label = t("Customers");
        if (tab.id === "expenses") label = t("Expenses");
        if (tab.id === "budget") label = t("Budget");
        if (tab.id === "customer-comments") label = `${t("Client")} Comments`;
        if (tab.id === "audit") label = auditTabLabel(t);
        // Note: "Tasks" stays literal — GAP-L-38

        return {
          ...tab,
          label,
          visible: tabVisibility[tab.id] ?? true,
        };
      }),
    }));
  }, [
    t,
    correspondencePanel,
    generatedPanel,
    staffingPanel,
    expensesPanel,
    ratesPanel,
    financialsPanel,
    requestsPanel,
    customerCommentsPanel,
    showCourtDates,
    showAdverseParties,
    showTrust,
    showDisbursements,
    showStatements,
    showAudit,
  ]);

  // Collect all visible tab IDs for activeTab validation
  const visibleTabIds = useMemo(() => {
    const ids = new Set<string>();
    for (const group of visibleGroups) {
      if (!group.visible) continue;
      for (const tab of group.tabs) {
        if (tab.visible !== false) ids.add(tab.id);
      }
    }
    return ids;
  }, [visibleGroups]);

  // Validate activeTab is in the visible tabs; fall back to "overview" if not
  const activeTab: TabId =
    requestedTab && visibleTabIds.has(requestedTab) ? requestedTab : "overview";

  const handleTabChange = (tabId: string) => {
    setUserTab(tabId as TabId);
  };

  return (
    <TabsPrimitive.Root value={activeTab}>
      {/* GroupedTabBar replaces the flat TabsPrimitive.List — groups 21 sub-tabs into 6 top-level groups */}
      <GroupedTabBar groups={visibleGroups} activeTab={activeTab} onTabChange={handleTabChange} />

      {/* Radix Content provides role="tabpanel" + aria-labelledby */}
      <TabsPrimitive.Content value="details" className="pt-6 outline-none">
        {detailsPanel}
      </TabsPrimitive.Content>
      <TabsPrimitive.Content value="fields" className="pt-6 outline-none">
        {fieldsPanel}
      </TabsPrimitive.Content>
      <TabsPrimitive.Content value="overview" className="pt-6 outline-none">
        {overviewPanel}
      </TabsPrimitive.Content>
      <TabsPrimitive.Content value="documents" className="pt-6 outline-none">
        {documentsPanel}
      </TabsPrimitive.Content>
      {correspondencePanel && (
        <TabsPrimitive.Content value="correspondence" className="pt-6 outline-none">
          {correspondencePanel}
        </TabsPrimitive.Content>
      )}
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
      {showStatements && (
        <TabsPrimitive.Content value="statements" className="pt-6 outline-none">
          {statementsPanel}
        </TabsPrimitive.Content>
      )}
      <TabsPrimitive.Content value="activity" className="pt-6 outline-none">
        {activityPanel}
      </TabsPrimitive.Content>
      {showAudit && (
        <TabsPrimitive.Content value="audit" className="pt-6 outline-none">
          {auditPanel}
        </TabsPrimitive.Content>
      )}
    </TabsPrimitive.Root>
  );
}
