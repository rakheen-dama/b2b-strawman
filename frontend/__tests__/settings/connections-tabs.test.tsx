import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Phase 91 665F: the Settings › Connections tab loaders and the three log sub-pages render their h1
// through PageHeader in every branch (K-124: Notifications and MCP h1s are the tab labels), so each
// keeps exactly one level-1 heading. Disconnect Xero and Revoke access sit in untitled destructive
// sections; pack cards keep their h3 + testid (K-121 badge); the logs move onto DataTable (Sync Log
// URL-paged, AI history callback-paged, execution log client rows + its own pager, K-122 trigger
// mapping); the notification list names the 13 raw types (K-115).

vi.mock("server-only", () => ({}));
const h = vi.hoisted(() => ({
  mockCaps: vi.fn(),
  mockPush: vi.fn(),
  mockMcpStatus: vi.fn(),
  mockPrefs: vi.fn(),
  mockCatalog: vi.fn(),
  mockInstalled: vi.fn(),
  mockListRules: vi.fn(),
  mockListExecutions: vi.fn(),
  mockFetchExecutions: vi.fn(),
  mockGetAiProfile: vi.fn(),
  mockGetAiCostSummary: vi.fn(),
  mockGetAiExecutions: vi.fn(),
  mockGetSyncEntries: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: h.mockPush, refresh: vi.fn(), replace: vi.fn(), prefetch: vi.fn() }),
  usePathname: () => "/org/acme/settings/connections",
  useSearchParams: () => new URLSearchParams(),
  redirect: vi.fn(),
}));
vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    ...props
  }: {
    children: React.ReactNode;
    href: string;
    [k: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
vi.mock("@/lib/api/capabilities", () => ({ fetchMyCapabilities: () => h.mockCaps() }));
vi.mock("@/app/(app)/org/[slug]/settings/connections/_tabs/mcp-actions", () => ({
  getMcpStatusAction: () => h.mockMcpStatus(),
  enableMcpAction: vi.fn(),
  revokeMcpAction: vi.fn(),
}));
vi.mock("@/lib/actions/notifications", () => ({
  fetchNotificationPreferences: () => h.mockPrefs(),
  updateNotificationPreferences: vi.fn(),
}));
vi.mock("@/lib/api/packs", () => ({
  listPackCatalog: () => h.mockCatalog(),
  listInstalledPacks: () => h.mockInstalled(),
}));
vi.mock("@/app/(app)/org/[slug]/settings/connections/_tabs/packs-actions", () => ({
  fetchCatalogAction: async () => ({ success: true, data: await h.mockCatalog() }),
  fetchUninstallCheckAction: async () => ({
    success: true,
    data: { canUninstall: true, blockingReason: null },
  }),
  installPackAction: vi.fn(),
  uninstallPackAction: vi.fn(),
}));
vi.mock("@/lib/api/automations", () => ({
  listRules: () => h.mockListRules(),
  listExecutions: () => h.mockListExecutions(),
}));
vi.mock("@/app/(app)/org/[slug]/settings/connections/_tabs/automations-actions", () => ({
  toggleRuleAction: vi.fn(),
  fetchExecutionsAction: (...args: unknown[]) => h.mockFetchExecutions(...args),
}));
vi.mock("@/lib/api/ai", () => ({
  getAiProfile: () => h.mockGetAiProfile(),
  getAiCostSummary: () => h.mockGetAiCostSummary(),
  getAiExecutions: () => h.mockGetAiExecutions(),
}));
vi.mock("@/app/(app)/org/[slug]/settings/connections/_tabs/ai-actions", () => ({
  updateAiProfileAction: vi.fn(),
}));
vi.mock("@/lib/api/integrations", () => ({
  getSyncEntries: (...args: unknown[]) => h.mockGetSyncEntries(...args),
}));
vi.mock("@/app/(app)/org/[slug]/settings/connections/xero/sync-log/actions", () => ({
  retrySyncEntryAction: vi.fn(),
  reconcileSyncEntryAction: vi.fn(),
}));
vi.mock("@/app/(app)/org/[slug]/settings/connections/_tabs/xero-actions", () => ({
  initiateXeroConnectAction: vi.fn(),
  disconnectXeroAction: vi.fn(),
}));

import { renderMcpTab } from "@/app/(app)/org/[slug]/settings/connections/_tabs/mcp";
import { renderAiTab } from "@/app/(app)/org/[slug]/settings/connections/_tabs/ai";
import { renderNotificationsTab } from "@/app/(app)/org/[slug]/settings/connections/_tabs/notifications";
import { renderPacksTab } from "@/app/(app)/org/[slug]/settings/connections/_tabs/packs";
import { renderAutomationsTab } from "@/app/(app)/org/[slug]/settings/connections/_tabs/automations";
import { McpConnectorCard } from "@/app/(app)/org/[slug]/settings/connections/_tabs/mcp-connector-card";
import SyncLogPage from "@/app/(app)/org/[slug]/settings/connections/xero/sync-log/page";
import ExecutionHistoryPage from "@/app/(app)/org/[slug]/settings/connections/ai/history/page";
import { ExecutionHistoryClient } from "@/app/(app)/org/[slug]/settings/connections/ai/history/history-client";
import ExecutionLogPage from "@/app/(app)/org/[slug]/settings/connections/automations/executions/page";
import { XeroConnectionCard } from "@/components/integrations/XeroConnectionCard";
import { SyncLogTable } from "@/components/integrations/SyncLogTable";
import { PackCard } from "@/components/settings/pack-card";
import { NotificationPreferencesForm } from "@/components/notifications/notification-preferences-form";
import { ExecutionLog } from "@/components/automations/execution-log";
import { triggerTypeFromEvent } from "@/components/automations/trigger-type-badge";
import type { AiExecutionListItem } from "@/lib/api/ai";
import type { AutomationExecutionResponse, PaginatedResponse } from "@/lib/api/automations";
import type { McpStatus, SyncEntryResponse } from "@/lib/types";

const OWNER = { capabilities: [], role: "owner", isAdmin: false, isOwner: true };
const MEMBER = { capabilities: [], role: "member", isAdmin: false, isOwner: false };
const TAB = { slug: "acme", searchParams: {} };
const params = Promise.resolve({ slug: "acme" });
const EMPTY_PAGE = { totalElements: 0, totalPages: 0, size: 20, number: 0 };

/** The page's one and only level-1 heading. */
function theH1() {
  const h1s = screen.getAllByRole("heading", { level: 1 });
  expect(h1s).toHaveLength(1);
  return h1s[0];
}

/** `a` comes before `b` in document order. */
function precedes(a: Node, b: Node) {
  return Boolean(a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING);
}

function sectionOf(element: HTMLElement) {
  return element.closest('[data-slot="settings-section"]');
}

const MCP_ENABLED: McpStatus = {
  effectivelyEnabled: true,
  integrationEnabled: true,
  serverUrl: "https://gw.example/mcp",
  consent: {
    granted: true,
    action: "GRANTED",
    version: "popia-egress-v1",
    consentedBy: null,
    consentedAt: null,
  },
};

const execution: AutomationExecutionResponse = {
  id: "exec-1",
  ruleId: "rule-1",
  ruleName: "Matter Onboarding Reminder",
  triggerEventType: "CustomerStatusChangedEvent",
  triggerEventData: {},
  conditionsMet: true,
  status: "ACTIONS_COMPLETED",
  startedAt: "2026-11-26T10:00:00Z",
  completedAt: "2026-11-26T10:00:01Z",
  errorMessage: null,
  createdAt: "2026-11-26T10:00:00Z",
  actionExecutions: [],
};

beforeEach(() => {
  vi.clearAllMocks();
  h.mockCaps.mockResolvedValue(OWNER);
  h.mockMcpStatus.mockResolvedValue(null);
  h.mockPrefs.mockResolvedValue({ preferences: [] });
  h.mockCatalog.mockResolvedValue([]);
  h.mockInstalled.mockResolvedValue([]);
  h.mockListRules.mockResolvedValue([]);
  h.mockListExecutions.mockResolvedValue({ content: [], page: EMPTY_PAGE });
  h.mockFetchExecutions.mockResolvedValue({ content: [], page: EMPTY_PAGE });
  h.mockGetAiProfile.mockRejectedValue(new Error("404"));
  h.mockGetAiCostSummary.mockRejectedValue(new Error("404"));
  h.mockGetAiExecutions.mockResolvedValue({ content: [], page: EMPTY_PAGE });
  h.mockGetSyncEntries.mockResolvedValue({ content: [], page: EMPTY_PAGE });
});

afterEach(() => {
  cleanup();
});

describe("Connections tab loaders — one PageHeader h1 per branch", () => {
  it("MCP: the h1 is the tab label in both branches (K-124); the card keeps its h2", async () => {
    render(await renderMcpTab(TAB));
    expect(theH1()).toHaveTextContent(/^MCP$/);
    expect(
      screen.getByRole("heading", { level: 2, name: "Claude / MCP Connector" })
    ).toBeInTheDocument();
    cleanup();

    h.mockCaps.mockResolvedValue(MEMBER);
    render(await renderMcpTab(TAB));
    expect(theH1()).toHaveTextContent(/^MCP$/);
    expect(
      screen.getByText(/You do not have permission to manage integrations/)
    ).toBeInTheDocument();
  });

  it("Notifications: the h1 is the tab label (K-124), HelpTip kept inside it", async () => {
    render(await renderNotificationsTab());
    const h1 = theH1();
    expect(h1).toHaveAccessibleName(/^Notifications/);
    expect(h1.textContent).not.toMatch(/Preferences/);
    expect(within(h1).getByRole("button", { name: /^Help:/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save preferences" })).toBeInTheDocument();
  });

  it("Packs: h1 'Packs'; the member note follows the header", async () => {
    render(await renderPacksTab(TAB));
    expect(theH1()).toHaveTextContent("Packs");
    expect(screen.queryByText(/Only admins and owners/)).not.toBeInTheDocument();
    cleanup();

    h.mockCaps.mockResolvedValue(MEMBER);
    render(await renderPacksTab(TAB));
    expect(theH1()).toHaveTextContent("Packs");
    expect(
      screen.getByText("Only admins and owners can install or uninstall packs.")
    ).toBeInTheDocument();
  });

  it("Automations: h1 in both branches; the execution-log link only for admins", async () => {
    render(await renderAutomationsTab(TAB));
    expect(theH1()).toHaveTextContent("Automations");
    expect(screen.getByRole("link", { name: /View Execution Log/ })).toHaveAttribute(
      "href",
      "/org/acme/settings/connections/automations/executions"
    );
    cleanup();

    h.mockCaps.mockResolvedValue(MEMBER);
    render(await renderAutomationsTab(TAB));
    expect(theH1()).toHaveTextContent("Automations");
    expect(screen.queryByRole("link", { name: /View Execution Log/ })).not.toBeInTheDocument();
  });

  it("AI: h1 unchanged (DA-13); form sections + one submit inside the form; side sections", async () => {
    render(await renderAiTab(TAB));
    expect(theH1()).toHaveTextContent("Set Up AI");
    for (const title of [
      "Practice Areas",
      "Jurisdiction",
      "Risk Calibration",
      "House Style Notes",
      "FICA Requirements",
      "Fee Estimation Notes",
      "Model Preference",
      "Monthly Budget",
      "Usage Summary",
      "Execution History",
      "AI Integration",
    ]) {
      const heading = screen.getByRole("heading", { level: 2, name: title });
      expect(sectionOf(heading)).toHaveAttribute("data-variant", "default");
    }
    const submit = screen.getByRole("button", { name: "Complete Setup" });
    expect(submit).toHaveAttribute("type", "submit");
    expect(submit.closest("form")).not.toBeNull();
    expect(submit.closest('[data-slot="settings-action-bar"]')).not.toBeNull();
    cleanup();

    h.mockCaps.mockResolvedValue(MEMBER);
    render(await renderAiTab(TAB));
    expect(theH1()).toHaveTextContent("AI Configuration");
  });
});

describe("Connections log sub-pages — one PageHeader h1 per branch", () => {
  it("Sync Log: h1 in both branches; empty → shared EmptyState", async () => {
    render(await SyncLogPage({ params, searchParams: Promise.resolve({}) }));
    expect(theH1()).toHaveTextContent("Sync Log");
    expect(
      screen.getByRole("heading", { level: 2, name: "No sync entries found" })
    ).toBeInTheDocument();
    cleanup();

    h.mockCaps.mockResolvedValue(MEMBER);
    render(await SyncLogPage({ params, searchParams: Promise.resolve({}) }));
    expect(theH1()).toHaveTextContent("Sync Log");
    expect(
      screen.getByText("You do not have permission to view the sync log.")
    ).toBeInTheDocument();
  });

  it("AI history: h1 'Execution History' in both branches; empty → shared EmptyState", async () => {
    render(await ExecutionHistoryPage({ params, searchParams: Promise.resolve({}) }));
    expect(theH1()).toHaveTextContent("Execution History");
    expect(
      screen.getByRole("heading", { level: 2, name: "No executions yet" })
    ).toBeInTheDocument();
    cleanup();

    h.mockCaps.mockResolvedValue(MEMBER);
    render(await ExecutionHistoryPage({ params, searchParams: Promise.resolve({}) }));
    expect(theH1()).toHaveTextContent("Execution History");
  });

  it("Execution Log: h1 'Execution Log' (admin)", async () => {
    render(await ExecutionLogPage({ params, searchParams: Promise.resolve({}) }));
    expect(theH1()).toHaveTextContent("Execution Log");
    expect(screen.getByText("No executions found.")).toBeInTheDocument();
  });
});

describe("Destructive sections (DA-9, DA-10)", () => {
  it("Disconnect Xero sits alone in an untitled destructive section; the connection section is h3", () => {
    render(
      <XeroConnectionCard
        slug="acme"
        connection={
          {
            status: "CONNECTED",
            xeroOrgName: "Acme Ltd",
            connectedAt: "2026-11-01T00:00:00Z",
          } as never
        }
      />
    );
    const disconnect = screen.getByRole("button", { name: /Disconnect/ });
    const danger = sectionOf(disconnect);
    expect(danger).toHaveAttribute("data-variant", "destructive");
    expect(within(danger as HTMLElement).queryByRole("heading")).not.toBeInTheDocument();
    const title = screen.getByRole("heading", { level: 3, name: "Xero Connection" });
    expect(sectionOf(title)).toHaveAttribute("data-variant", "default");
  });

  it("no destructive section while Xero is not connected", () => {
    render(<XeroConnectionCard slug="acme" connection={null} />);
    expect(screen.queryByRole("button", { name: /Disconnect/ })).not.toBeInTheDocument();
    expect(document.querySelector('[data-variant="destructive"]')).toBeNull();
    expect(screen.getByRole("button", { name: /Connect to Xero/ })).toBeInTheDocument();
  });

  it("Revoke access sits in an untitled destructive section only while enabled", () => {
    render(
      <McpConnectorCard slug="acme" serverUrl="https://gw.example/mcp" status={MCP_ENABLED} />
    );
    const revoke = screen.getByRole("button", { name: "Revoke access" });
    expect(sectionOf(revoke)).toHaveAttribute("data-variant", "destructive");
    expect(sectionOf(screen.getByRole("switch", { name: "Enabled" }))).toHaveAttribute(
      "data-variant",
      "default"
    );
    cleanup();

    render(<McpConnectorCard slug="acme" serverUrl="https://gw.example/mcp" status={null} />);
    expect(screen.queryByRole("button", { name: "Revoke access" })).not.toBeInTheDocument();
    expect(document.querySelector('[data-variant="destructive"]')).toBeNull();
  });
});

describe("Pack card (SEL-COMPAT + K-121)", () => {
  const pack = {
    packId: "legal-za",
    name: "SA Legal Templates",
    description: "Templates",
    version: "1.0.0",
    type: "DOCUMENT_TEMPLATE" as const,
    verticalProfile: "legal-za",
    itemCount: 7,
    installed: true,
    installedAt: "2026-11-26T00:00:00Z",
  };

  it("keeps pack-card-<id> with an h3 title and shows 'Legal (South Africa)'", () => {
    render(<PackCard pack={pack} variant="available" />);
    const card = screen.getByTestId("pack-card-legal-za");
    expect(
      within(card).getByRole("heading", { level: 3, name: "SA Legal Templates" })
    ).toBeInTheDocument();
    expect(within(card).getByText("Legal (South Africa)")).toBeInTheDocument();
    expect(within(card).queryByText("Legal - Za")).not.toBeInTheDocument();
  });

  it("other profiles keep today's rendering; no profile is 'Universal'", () => {
    render(
      <PackCard
        pack={{ ...pack, packId: "a", verticalProfile: "accounting-za" }}
        variant="available"
      />
    );
    expect(screen.getByText("Accounting - Za")).toBeInTheDocument();
    cleanup();
    render(<PackCard pack={{ ...pack, packId: "b", verticalProfile: null }} variant="available" />);
    expect(screen.getByText("Universal")).toBeInTheDocument();
  });

  it("the Uninstall button's direct parent is the tooltip trigger span (packs.spec.ts:178)", () => {
    render(
      <PackCard pack={pack} variant="installed" onUninstall={vi.fn()} uninstallCheck={null} />
    );
    const uninstall = screen.getByRole("button", { name: "Uninstall" });
    expect(uninstall.parentElement?.tagName).toBe("SPAN");
    expect(uninstall.parentElement).toHaveAttribute("tabindex", "0");
  });
});

describe("Sync Log table (server-paged, URL-owned — DA-17)", () => {
  const entry: SyncEntryResponse = {
    id: "s1",
    entityType: "INVOICE",
    entityId: "11111111-2222-3333-4444-555555555555",
    providerId: "xero",
    direction: "PUSH",
    state: "COMPLETED",
    attemptCount: 1,
    externalReference: null,
    externalId: null,
    lastErrorCode: null,
    lastErrorDetail: null,
    trigger: "EVENT",
    createdAt: "2026-11-26T10:00:00Z",
    completedAt: "2026-11-26T10:00:05Z",
  };

  it("pager links keep the filters and set the ONE-based page", () => {
    render(
      <SyncLogTable
        entries={[entry]}
        slug="acme"
        canReconcile={false}
        pageIndex={1}
        pageCount={3}
        basePath="/org/acme/settings/connections/xero/sync-log"
        query={{ state: "COMPLETED", size: "10" }}
        filters={<div data-testid="sync-filters" />}
        emptyDescription="none"
      />
    );
    expect(screen.getByTestId("sync-filters")).toBeInTheDocument();
    expect(screen.getByText("Page 2 of 3")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Previous/ })).toHaveAttribute(
      "href",
      "/org/acme/settings/connections/xero/sync-log?state=COMPLETED&size=10&page=1"
    );
    expect(screen.getByRole("link", { name: /Next/ })).toHaveAttribute(
      "href",
      "/org/acme/settings/connections/xero/sync-log?state=COMPLETED&size=10&page=3"
    );
    expect(screen.getByRole("columnheader", { name: "Entity Type" })).toBeInTheDocument();
    expect(screen.getByText("Fee note")).toBeInTheDocument();
  });

  it("one page → no pager (as before)", () => {
    render(
      <SyncLogTable
        entries={[entry]}
        slug="acme"
        canReconcile={false}
        pageIndex={0}
        pageCount={1}
        basePath="/x"
        query={{}}
        filters={null}
        emptyDescription="none"
      />
    );
    expect(screen.queryByText(/Page 1 of 1/)).not.toBeInTheDocument();
  });
});

describe("AI history (server-paged, callback-owned — DA-18)", () => {
  const exec: AiExecutionListItem = {
    id: "ai1",
    skillId: "matter-brief",
    entityType: "PROJECT",
    entityId: "p1",
    status: "COMPLETED",
    inputSummary: null,
    model: "claude",
    inputTokens: 1000,
    outputTokens: 200,
    costCents: 1234,
    durationMs: 1500,
    createdAt: "2026-11-27T08:00:00Z",
  };

  it("Next / Previous push the zero-based page inside the URL (page 0 omitted)", async () => {
    const user = userEvent.setup();
    render(
      <ExecutionHistoryClient
        slug="acme"
        executions={[exec]}
        currentPage={0}
        totalPages={2}
        totalElements={21}
        currentSkillId="matter-brief"
      />
    );
    expect(screen.getByText("Matter Brief")).toBeInTheDocument();
    expect(screen.getByText("27 Nov 2026")).toBeInTheDocument();
    expect(screen.getByText("21 executions total")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Previous/ })).toBeDisabled();
    await user.click(screen.getByRole("button", { name: /Next/ }));
    expect(h.mockPush).toHaveBeenCalledWith(
      "/org/acme/settings/connections/ai/history?skillId=matter-brief&page=1"
    );
    cleanup();

    render(
      <ExecutionHistoryClient
        slug="acme"
        executions={[exec]}
        currentPage={1}
        totalPages={2}
        totalElements={21}
      />
    );
    await user.click(screen.getByRole("button", { name: /Previous/ }));
    expect(h.mockPush).toHaveBeenLastCalledWith("/org/acme/settings/connections/ai/history");
  });
});

describe("Execution log (client rows + kept pager — DA-15, DA-18, K-122)", () => {
  function page(
    content: AutomationExecutionResponse[],
    totalPages: number,
    totalElements: number
  ): PaginatedResponse<AutomationExecutionResponse> {
    return { content, page: { totalElements, totalPages, size: 20, number: 0 } };
  }

  it("keeps the pager text 'Page n of m (N total)'", () => {
    render(<ExecutionLog initialExecutions={page([execution], 2, 25)} rules={[]} />);
    expect(screen.getByText("Page 1 of 2 (25 total)")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Previous" })).toBeDisabled();
  });

  it("K-122: the recorded event class renders the rule list's trigger label", () => {
    render(<ExecutionLog initialExecutions={page([execution], 1, 1)} rules={[]} />);
    expect(screen.getByText("Client Status")).toBeInTheDocument();
    expect(screen.queryByText("CustomerStatusChangedEvent")).not.toBeInTheDocument();
  });

  it("the Rule cell is a button that opens the detail Sheet (keyboard path)", async () => {
    const user = userEvent.setup();
    render(<ExecutionLog initialExecutions={page([execution], 1, 1)} rules={[]} />);
    const opener = screen.getByRole("button", { name: "Matter Onboarding Reminder" });
    opener.focus();
    await user.keyboard("{Enter}");
    expect(await screen.findByText("Execution Detail")).toBeInTheDocument();
  });

  it("with the Rule column hidden (ruleId), the Triggered At cell opens the Sheet", () => {
    render(<ExecutionLog initialExecutions={page([execution], 1, 1)} ruleId="rule-1" />);
    expect(screen.queryByRole("columnheader", { name: "Rule" })).not.toBeInTheDocument();
    const cell = screen.getAllByRole("cell")[1];
    fireEvent.click(within(cell).getByRole("button"));
    expect(screen.getByText("Execution Detail")).toBeInTheDocument();
  });

  it("triggerTypeFromEvent maps event classes and passes anything else through", () => {
    expect(triggerTypeFromEvent("CustomerStatusChangedEvent")).toBe("CUSTOMER_STATUS_CHANGED");
    expect(triggerTypeFromEvent("InvoicePaidEvent")).toBe("INVOICE_STATUS_CHANGED");
    expect(triggerTypeFromEvent("ScheduledTrigger")).toBe("SCHEDULED");
    expect(triggerTypeFromEvent("TASK_STATUS_CHANGED")).toBe("TASK_STATUS_CHANGED");
    expect(triggerTypeFromEvent("SomethingNewEvent")).toBe("SomethingNewEvent");
  });
});

describe("Notification preferences (K-115, K-85, page-level save)", () => {
  const NEW_LABELS: [string, string, string][] = [
    ["COURT_DATE_REMINDER", "Court date reminder", "Diary"],
    ["PRESCRIPTION_WARNING", "Prescription warning", "Diary"],
    ["TRUST_PAYMENT_AWAITING_APPROVAL", "Trust payment awaiting approval", "Trust"],
    ["TRUST_PAYMENT_APPROVED", "Trust payment approved", "Trust"],
    ["TRUST_PAYMENT_REJECTED", "Trust payment rejected", "Trust"],
    ["TRUST_RECONCILIATION_OVERDUE", "Trust reconciliation overdue", "Trust"],
    ["TRUST_INVESTMENT_MATURING", "Trust investment maturing", "Trust"],
    ["TRUST_APPROVAL_AGING", "Trust approval waiting too long", "Trust"],
    ["TRUST_RECONCILIATION_COMPLETED", "Trust reconciliation completed", "Trust"],
    ["MATTER_CLOSED", "Matter closed", "Matters"],
    ["MATTER_REOPENED", "Matter reopened", "Matters"],
    ["COLLECTION_ESCALATED", "Collection escalated", "Billing"],
    ["CASH_DIGEST", "Daily cash summary", "Billing"],
  ];
  const prefs = [
    "TASK_ASSIGNED",
    "BILLING_RUN_COMPLETED",
    "DSAR_DEADLINE_WARNING",
    ...NEW_LABELS.map(([type]) => type),
  ].map((notificationType) => ({ notificationType, inAppEnabled: true, emailEnabled: false }));

  it("names all 13 formerly raw types under Diary / Trust / Matters / Billing; no 'Other' group", () => {
    render(<NotificationPreferencesForm initialPreferences={prefs} />);
    for (const [type, label] of NEW_LABELS) {
      expect(screen.getByRole("switch", { name: label })).toHaveAttribute("id", `inapp-${type}`);
      expect(screen.queryByText(type)).not.toBeInTheDocument();
    }
    expect(screen.queryByText("Other", { exact: true })).not.toBeInTheDocument();
    // Existing labels and category strings stay byte-identical (COPY-FREEZE; founder copy pending).
    expect(screen.getByText("Task Assigned")).toBeInTheDocument();
    expect(screen.getByText("Billing Run Completed")).toBeInTheDocument();
  });

  it("category headers render in the stable order and each label sits under its own header", () => {
    render(<NotificationPreferencesForm initialPreferences={prefs} />);
    const order = ["Tasks", "Matters", "Billing", "Security", "Diary", "Trust"];
    const headers = order.map((category) => screen.getByText(category, { exact: true }));
    headers.slice(1).forEach((header, i) => expect(precedes(headers[i], header)).toBe(true));
    for (const [, label, category] of NEW_LABELS) {
      const at = order.indexOf(category);
      const row = screen.getByText(label, { exact: true });
      expect(precedes(headers[at], row)).toBe(true);
      if (at + 1 < headers.length) expect(precedes(row, headers[at + 1])).toBe(true);
    }
  });

  it("Save sits in the page-level action bar; the list is one default section", () => {
    render(<NotificationPreferencesForm initialPreferences={prefs} />);
    const label = screen.getByText("Trust payment awaiting approval");
    const save = screen.getByRole("button", { name: "Save preferences" });
    expect(save.closest('[data-slot="settings-action-bar"]')).not.toBeNull();
    expect(sectionOf(label)).toHaveAttribute("data-variant", "default");
  });
});
