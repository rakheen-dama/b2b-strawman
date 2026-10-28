import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { renderXeroTab } from "@/app/(app)/org/[slug]/settings/connections/_tabs/xero";

// Phase 87 S9 (616D, ADR-341 §12.7.1): the Connections › Xero tab merges the former integrations
// hub and the former Xero page — the hub for everyone, then a Xero section that keeps the former
// Xero page's INTEGRATION_MANAGE denial.
const { mockCaps, mockGetXeroConnection, mockGetXeroTaxMappings, mockGetXeroSettings } = vi.hoisted(
  () => ({
    mockCaps: vi.fn(),
    mockGetXeroConnection: vi.fn(),
    mockGetXeroTaxMappings: vi.fn(),
    mockGetXeroSettings: vi.fn(),
  })
);

vi.mock("@/lib/api/capabilities", () => ({ fetchMyCapabilities: () => mockCaps() }));

vi.mock("@/lib/api/integrations", () => ({
  listIntegrations: vi.fn().mockResolvedValue([]),
  listProviders: vi.fn().mockResolvedValue({}),
  getXeroConnection: () => mockGetXeroConnection(),
  getXeroTaxMappings: () => mockGetXeroTaxMappings(),
  getXeroSettings: () => mockGetXeroSettings(),
}));

vi.mock("@/app/(app)/org/[slug]/settings/connections/_tabs/integrations-actions", () => ({
  upsertIntegrationAction: vi.fn().mockResolvedValue({ success: true }),
  setApiKeyAction: vi.fn().mockResolvedValue({ success: true }),
  deleteApiKeyAction: vi.fn().mockResolvedValue({ success: true }),
  toggleIntegrationAction: vi.fn().mockResolvedValue({ success: true }),
  testConnectionAction: vi.fn().mockResolvedValue({ success: true }),
}));

vi.mock("@/app/(app)/org/[slug]/settings/connections/_tabs/mcp-actions", () => ({
  getMcpStatusAction: vi.fn().mockResolvedValue(null),
}));

// The former Xero page's client components — marker stubs (this test pins the loader's composition).
vi.mock("@/components/integrations/XeroConnectionCard", () => ({
  XeroConnectionCard: () => <div data-testid="xero-connection-card" />,
}));
vi.mock("@/components/integrations/XeroTaxMappingEditor", () => ({
  XeroTaxMappingEditor: () => <div data-testid="xero-tax-mapping-editor" />,
}));
vi.mock("@/components/integrations/XeroCustomerImport", () => ({
  XeroCustomerImport: () => <div data-testid="xero-customer-import" />,
}));
vi.mock("@/components/integrations/XeroSyncSummary", () => ({
  XeroSyncSummary: () => <div data-testid="xero-sync-summary" />,
}));
vi.mock("@/components/integrations/XeroSettingsForm", () => ({
  XeroSettingsForm: () => <div data-testid="xero-settings-form" />,
}));

vi.mock("@/lib/actions/email", () => ({
  getEmailStats: vi.fn().mockResolvedValue({
    success: true,
    data: {
      sent24h: 0,
      bounced7d: 0,
      failed7d: 0,
      rateLimited7d: 0,
      currentHourUsage: 0,
      hourlyLimit: 100,
      providerSlug: null,
    },
  }),
  sendTestEmail: vi.fn().mockResolvedValue({ success: true }),
}));

const OWNER = { capabilities: [], role: "owner", isAdmin: false, isOwner: true };
const MEMBER = { capabilities: [], role: "member", isAdmin: false, isOwner: false };
const INTEGRATION_MANAGER = {
  capabilities: ["INTEGRATION_MANAGE"],
  role: "member",
  isAdmin: false,
  isOwner: false,
};
const CONNECTED = { status: "CONNECTED", xeroOrgName: "Acme Ltd" };

async function renderTab() {
  render(await renderXeroTab({ slug: "acme", searchParams: {} }));
}

describe("Connections › Xero tab (renderXeroTab)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockCaps.mockResolvedValue(OWNER);
    mockGetXeroConnection.mockRejectedValue(new Error("404"));
    mockGetXeroTaxMappings.mockResolvedValue([]);
    mockGetXeroSettings.mockResolvedValue({ syncEnabled: true });
  });

  afterEach(() => {
    cleanup();
  });

  it("renders page heading", async () => {
    await renderTab();
    expect(screen.getByRole("heading", { level: 1, name: /Integrations/ })).toBeInTheDocument();
  });

  it("renders 5 domain cards", async () => {
    await renderTab();
    expect(screen.getByText("Accounting")).toBeInTheDocument();
    expect(screen.getByText("AI Assistant")).toBeInTheDocument();
    expect(screen.getByText("Document Signing")).toBeInTheDocument();
    expect(screen.getByText("Email Delivery")).toBeInTheDocument();
    expect(screen.getByText("Payment Gateway")).toBeInTheDocument();
  });

  it("drops the former ← Settings back link (616A D12)", async () => {
    await renderTab();
    expect(screen.queryByRole("link", { name: /Settings/ })).not.toBeInTheDocument();
  });

  it("points Configure Xero at the Xero section of this tab", async () => {
    await renderTab();
    expect(screen.getByRole("link", { name: /Configure Xero/ })).toHaveAttribute(
      "href",
      "/org/acme/settings/connections?tab=xero#xero"
    );
  });

  it("shows an owner the Xero section with the connection card (not connected)", async () => {
    await renderTab();
    expect(screen.getByRole("heading", { level: 2, name: "Xero Integration" })).toBeInTheDocument();
    expect(screen.getByTestId("xero-connection-card")).toBeInTheDocument();
    expect(screen.queryByTestId("xero-tax-mapping-editor")).not.toBeInTheDocument();
    expect(mockGetXeroTaxMappings).not.toHaveBeenCalled();
  });

  it("shows the full Xero management stack when connected, fetching the connection once", async () => {
    mockGetXeroConnection.mockResolvedValue(CONNECTED);
    await renderTab();
    expect(mockGetXeroConnection).toHaveBeenCalledTimes(1);
    expect(screen.getByText("Connected to Acme Ltd via Xero")).toBeInTheDocument();
    for (const id of [
      "xero-connection-card",
      "xero-tax-mapping-editor",
      "xero-customer-import",
      "xero-sync-summary",
      "xero-settings-form",
    ]) {
      expect(screen.getByTestId(id)).toBeInTheDocument();
    }
  });

  it("lets an INTEGRATION_MANAGE holder manage Xero", async () => {
    mockCaps.mockResolvedValue(INTEGRATION_MANAGER);
    await renderTab();
    expect(screen.getByTestId("xero-connection-card")).toBeInTheDocument();
  });

  it("shows a member the hub and the former Xero denial instead of the management stack", async () => {
    mockCaps.mockResolvedValue(MEMBER);
    mockGetXeroConnection.mockResolvedValue(CONNECTED);
    await renderTab();
    expect(screen.getByRole("heading", { level: 1, name: /Integrations/ })).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 2, name: "Xero Integration" })).toBeInTheDocument();
    expect(
      screen.getByText(
        "You do not have permission to manage integrations. Contact your administrator."
      )
    ).toBeInTheDocument();
    expect(screen.queryByTestId("xero-connection-card")).not.toBeInTheDocument();
    expect(screen.queryByTestId("xero-tax-mapping-editor")).not.toBeInTheDocument();
    expect(mockGetXeroTaxMappings).not.toHaveBeenCalled();
    expect(mockGetXeroSettings).not.toHaveBeenCalled();
  });
});
