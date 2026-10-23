"use client";

import { useMemo, useState, type ReactNode } from "react";
import { useSearchParams } from "next/navigation";
import { Tabs as TabsPrimitive } from "radix-ui";
import { useOrgProfile } from "@/lib/org-profile";
import { useAuditTabVisible } from "@/components/audit/audit-timeline-tab";
import { GroupedTabBar } from "@/components/shared/grouped-tab-bar";
import { MATTER_TAB_GROUPS } from "@/lib/constants/matter-tab-groups";
import { aliasLegacyTabId } from "@/lib/constants/legacy-tab-ids";

interface MatterTabsProps {
  detailsPanel: ReactNode;
  fieldsPanel: ReactNode;
  teamPanel?: ReactNode;
  overviewPanel: ReactNode;
  documentsPanel: ReactNode;
  correspondencePanel?: ReactNode;
  clientsPanel: ReactNode;
  tasksPanel: ReactNode;
  timePanel: ReactNode;
  activityPanel: ReactNode;
  ratesPanel?: ReactNode;
  budgetPanel?: ReactNode;
  financialsPanel?: ReactNode;
  generatedPanel?: ReactNode;
  requestsPanel?: ReactNode;
  customerCommentsPanel?: ReactNode;
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
  | "team"
  | "overview"
  | "documents"
  | "correspondence"
  | "clients"
  | "tasks"
  | "time"
  | "budget"
  | "financials"
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

// The 22 tab ids of MATTER_TAB_GROUPS (pinned by __tests__/components/matters/matter-tabs.test.tsx).
const validTabIds = new Set<string>([
  "details",
  "fields",
  "team",
  "overview",
  "documents",
  "correspondence",
  "clients",
  "tasks",
  "time",
  "budget",
  "financials",
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

export function MatterTabs({
  detailsPanel,
  fieldsPanel,
  teamPanel,
  overviewPanel,
  documentsPanel,
  correspondencePanel,
  clientsPanel,
  tasksPanel,
  timePanel,
  activityPanel,
  ratesPanel,
  budgetPanel,
  financialsPanel,
  generatedPanel,
  requestsPanel,
  customerCommentsPanel,
  courtDatesPanel,
  adversePartiesPanel,
  trustPanel,
  disbursementsPanel,
  statementsPanel,
  auditPanel,
}: MatterTabsProps) {
  const searchParams = useSearchParams();
  // Old ?tab= ids (customers, expenses, …) resolve to their renamed tab (LEGACY_TAB_IDS, §12.6.5).
  const tabParam = aliasLegacyTabId(searchParams.get("tab"));
  const urlTab = tabParam && validTabIds.has(tabParam) ? (tabParam as TabId) : null;
  const [userTab, setUserTab] = useState<TabId | null>(null);
  const { isModuleEnabled } = useOrgProfile();

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

  // Build visible groups from MATTER_TAB_GROUPS with module-gating
  const visibleGroups = useMemo(() => {
    /** Per-tab visibility map — true means the tab should be shown */
    const tabVisibility: Record<string, boolean> = {
      // Details group — details/fields always visible; team is panel-gated
      details: true,
      fields: true,
      team: !!teamPanel,
      // Always-visible tabs
      overview: true,
      tasks: true,
      documents: true,
      time: true,
      clients: true,
      activity: true,
      // Panel-gated tabs (visible only when the panel prop is provided)
      correspondence: !!correspondencePanel,
      generated: !!generatedPanel,
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

    // Labels come straight from MATTER_TAB_GROUPS (legal words; ADR-338).
    return MATTER_TAB_GROUPS.map((group) => ({
      ...group,
      visible: group.tabs.some((t) => tabVisibility[t.id] !== false),
      tabs: group.tabs.map((tab) => ({
        ...tab,
        visible: tabVisibility[tab.id] ?? true,
      })),
    }));
  }, [
    teamPanel,
    correspondencePanel,
    generatedPanel,
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
      {/* GroupedTabBar replaces the flat TabsPrimitive.List — groups 22 sub-tabs into 7 top-level groups */}
      <GroupedTabBar groups={visibleGroups} activeTab={activeTab} onTabChange={handleTabChange} />

      {/* Radix Content provides role="tabpanel" + aria-labelledby */}
      <TabsPrimitive.Content value="details" className="pt-6 outline-none">
        {detailsPanel}
      </TabsPrimitive.Content>
      <TabsPrimitive.Content value="fields" className="pt-6 outline-none">
        {fieldsPanel}
      </TabsPrimitive.Content>
      {teamPanel && (
        <TabsPrimitive.Content value="team" className="pt-6 outline-none">
          {teamPanel}
        </TabsPrimitive.Content>
      )}
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
      <TabsPrimitive.Content value="clients" className="pt-6 outline-none">
        {clientsPanel}
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
