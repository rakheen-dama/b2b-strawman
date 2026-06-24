"use client";

import { useMemo, type ReactNode } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Tabs as TabsPrimitive } from "radix-ui";
import { useOrgProfile } from "@/lib/org-profile";
import { useTerminology } from "@/lib/terminology";
import { auditTabLabel } from "@b2mash/shared/terminology-map";
import { useAuditTabVisible } from "@/components/audit/audit-timeline-tab";
import { GroupedTabBar } from "@/components/shared/grouped-tab-bar";
import { CUSTOMER_TAB_GROUPS } from "@/lib/constants/customer-tab-groups";
import { resolveTabFromUrl } from "@/lib/constants/tab-group-types";

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------

interface CustomerGroupedTabsProps {
  // Details group
  detailsPanel: ReactNode;
  fieldsPanel: ReactNode;
  tagsPanel: ReactNode;
  // Overview group
  overviewPanel: ReactNode;
  // Work group
  projectsPanel: ReactNode;
  documentsPanel: ReactNode;
  generatedPanel?: ReactNode;
  dealsPanel?: ReactNode;
  // Finance group
  invoicesPanel?: ReactNode;
  ratesPanel?: ReactNode;
  retainerPanel?: ReactNode;
  financialsPanel?: ReactNode;
  trustPanel?: ReactNode;
  // Compliance group
  onboardingPanel?: ReactNode;
  requestsPanel?: ReactNode;
  // Activity group
  auditPanel?: ReactNode;
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export function CustomerGroupedTabs({
  detailsPanel,
  fieldsPanel,
  tagsPanel,
  overviewPanel,
  projectsPanel,
  documentsPanel,
  generatedPanel,
  dealsPanel,
  invoicesPanel,
  ratesPanel,
  retainerPanel,
  financialsPanel,
  trustPanel,
  onboardingPanel,
  requestsPanel,
  auditPanel,
}: CustomerGroupedTabsProps) {
  const searchParams = useSearchParams();
  const router = useRouter();
  const tabParam = searchParams.get("tab");
  const { isModuleEnabled } = useOrgProfile();
  const { t } = useTerminology();

  // Module-gated tabs
  const showTrust = !!trustPanel && isModuleEnabled("trust_accounting");
  // Capability-gated: members without TEAM_OVERSIGHT must not see the Audit tab
  const auditVisible = useAuditTabVisible();
  const showAudit = !!auditPanel && auditVisible;

  // Build visible groups from CUSTOMER_TAB_GROUPS with module-gating and terminology rewriting
  const visibleGroups = useMemo(() => {
    const tabVisibility: Record<string, boolean> = {
      // Details group — always visible
      details: true,
      fields: true,
      tags: true,
      // Overview — always visible
      overview: true,
      // Work group
      projects: true,
      documents: true,
      generated: !!generatedPanel,
      deals: !!dealsPanel,
      // Finance group — panel-gated
      invoices: !!invoicesPanel,
      rates: !!ratesPanel,
      retainer: !!retainerPanel,
      financials: !!financialsPanel,
      trust: showTrust,
      // Compliance group — panel-gated
      onboarding: !!onboardingPanel,
      requests: !!requestsPanel,
      // Activity group — capability-gated
      audit: showAudit,
    };

    return CUSTOMER_TAB_GROUPS.map((group) => ({
      ...group,
      visible: group.tabs.some((tab) => tabVisibility[tab.id] !== false),
      tabs: group.tabs.map((tab) => {
        // Apply terminology rewriting to specific tab labels
        let label = tab.label;
        if (tab.id === "projects") label = t("Projects");
        if (tab.id === "invoices") label = t("Invoices");
        if (tab.id === "retainer") label = t("Retainer");
        if (tab.id === "audit") label = auditTabLabel(t);

        return {
          ...tab,
          label,
          visible: tabVisibility[tab.id] ?? true,
        };
      }),
    }));
  }, [
    t,
    generatedPanel,
    dealsPanel,
    invoicesPanel,
    ratesPanel,
    retainerPanel,
    financialsPanel,
    showTrust,
    onboardingPanel,
    requestsPanel,
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

  // Resolve the active tab from URL — single source of truth
  const resolved = useMemo(
    () => resolveTabFromUrl(tabParam, visibleGroups),
    [tabParam, visibleGroups]
  );

  const activeTab =
    resolved.tabId && visibleTabIds.has(resolved.tabId) ? resolved.tabId : "overview";

  const handleTabChange = (tabId: string) => {
    const params = new URLSearchParams(searchParams.toString());
    params.set("tab", tabId);
    router.replace(`?${params.toString()}`);
  };

  return (
    <TabsPrimitive.Root value={activeTab}>
      <GroupedTabBar groups={visibleGroups} activeTab={activeTab} onTabChange={handleTabChange} />

      {/* Details group */}
      <TabsPrimitive.Content value="details" className="pt-6 outline-none">
        {detailsPanel}
      </TabsPrimitive.Content>
      <TabsPrimitive.Content value="fields" className="pt-6 outline-none">
        {fieldsPanel}
      </TabsPrimitive.Content>
      <TabsPrimitive.Content value="tags" className="pt-6 outline-none">
        {tagsPanel}
      </TabsPrimitive.Content>

      {/* Overview group */}
      <TabsPrimitive.Content value="overview" className="pt-6 outline-none">
        {overviewPanel}
      </TabsPrimitive.Content>

      {/* Work group */}
      <TabsPrimitive.Content value="projects" className="pt-6 outline-none">
        {projectsPanel}
      </TabsPrimitive.Content>
      <TabsPrimitive.Content value="documents" className="pt-6 outline-none">
        {documentsPanel}
      </TabsPrimitive.Content>
      {generatedPanel && (
        <TabsPrimitive.Content value="generated" className="pt-6 outline-none">
          {generatedPanel}
        </TabsPrimitive.Content>
      )}
      {dealsPanel && (
        <TabsPrimitive.Content value="deals" className="pt-6 outline-none">
          {dealsPanel}
        </TabsPrimitive.Content>
      )}

      {/* Finance group */}
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
      {retainerPanel && (
        <TabsPrimitive.Content value="retainer" className="pt-6 outline-none">
          {retainerPanel}
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

      {/* Compliance group */}
      {onboardingPanel && (
        <TabsPrimitive.Content value="onboarding" className="pt-6 outline-none">
          {onboardingPanel}
        </TabsPrimitive.Content>
      )}
      {requestsPanel && (
        <TabsPrimitive.Content value="requests" className="pt-6 outline-none">
          {requestsPanel}
        </TabsPrimitive.Content>
      )}

      {/* Activity group */}
      {showAudit && (
        <TabsPrimitive.Content value="audit" className="pt-6 outline-none">
          {auditPanel}
        </TabsPrimitive.Content>
      )}
    </TabsPrimitive.Root>
  );
}
