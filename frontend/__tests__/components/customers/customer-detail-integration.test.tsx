import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// ---------------------------------------------------------------------------
// Mocks — hoisted above component imports
// ---------------------------------------------------------------------------

const mockReplace = vi.fn();
let mockSearchParams = new URLSearchParams();

vi.mock("next/navigation", () => ({
  useSearchParams: () => mockSearchParams,
  useRouter: () => ({ replace: mockReplace, push: vi.fn(), back: vi.fn() }),
}));

vi.mock("@/lib/org-profile", () => ({
  useOrgProfile: () => ({
    isModuleEnabled: () => false,
  }),
}));

vi.mock("@/components/audit/audit-timeline-tab", () => ({
  useAuditTabVisible: () => false,
}));

vi.mock("motion/react", () => ({
  motion: {
    span: "span",
  },
}));

vi.mock("@/lib/terminology", () => ({
  useTerminology: () => ({
    t: (term: string) => term,
    verticalProfile: null,
  }),
}));

vi.mock("@b2mash/shared/terminology-map", () => ({
  auditTabLabel: (t: (s: string) => string) => {
    const v = t("audit.tab");
    return v === "audit.tab" ? "Audit" : v;
  },
}));

vi.mock("@/components/compliance/LifecycleStatusBadge", () => ({
  LifecycleStatusBadge: ({ status }: { status: string }) => (
    <span data-testid="lifecycle-status-badge">{status}</span>
  ),
}));

vi.mock("@/components/customers/kyc-status-badge", () => ({
  KycStatusBadge: ({ summary }: { summary: { state: string } }) => (
    <span data-testid="kyc-badge">{summary.state}</span>
  ),
}));

vi.mock("@/components/customers/XeroContactBadge", () => ({
  XeroContactBadge: () => <span data-testid="xero-badge">Xero</span>,
}));

vi.mock("@/components/customers/client-overflow-menu", () => ({
  ClientOverflowMenu: () => <div data-testid="client-overflow-trigger" />,
}));

vi.mock("@/components/setup/setup-progress-card", () => ({
  SetupProgressCard: () => <div data-testid="setup-progress-card" />,
}));

vi.mock("@/components/setup/template-readiness-card", () => ({
  TemplateReadinessCard: () => <div data-testid="template-readiness-card" />,
}));

vi.mock("@/components/customers/customer-address-block", () => ({
  CustomerAddressBlock: () => <div data-testid="customer-address-block">Address</div>,
}));

vi.mock("@/components/customers/customer-contact-card", () => ({
  CustomerContactCard: () => <div data-testid="customer-contact-card">Contact</div>,
}));

vi.mock("@/components/field-definitions/FieldGroupSelector", () => ({
  FieldGroupSelector: () => <div data-testid="field-group-selector">FieldGroupSelector</div>,
}));

vi.mock("@/components/field-definitions/CustomFieldSection", () => ({
  CustomFieldSection: () => <div data-testid="custom-field-section">CustomFieldSection</div>,
}));

vi.mock("@/components/tags/TagInput", () => ({
  TagInput: () => <div data-testid="tag-input">TagInput</div>,
}));

// ---------------------------------------------------------------------------
// Import components AFTER mocks
// ---------------------------------------------------------------------------

import { ClientHeaderCard } from "@/components/customers/client-header-card";
import type { ClientHeaderCardProps } from "@/components/customers/client-header-card";
import { CustomerGroupedTabs } from "@/components/customers/customer-grouped-tabs";
import { ClientOverviewTab } from "@/components/customers/client-overview-tab";
import { ClientDetailsTab } from "@/components/customers/client-details-tab";
import { ClientFieldsTab } from "@/components/customers/client-fields-tab";
import { ClientTagsTab } from "@/components/customers/client-tags-tab";

// ---------------------------------------------------------------------------
// Test data
// ---------------------------------------------------------------------------

const mockCustomer = {
  id: "cust-1",
  name: "Acme Corporation",
  email: "info@acme.com",
  phone: "+27 11 123 4567",
  idNumber: null,
  status: "ACTIVE" as const,
  notes: null,
  createdBy: "user-1",
  createdByName: "Alice",
  createdAt: "2026-01-10T10:00:00Z",
  updatedAt: "2026-01-15T10:00:00Z",
  lifecycleStatus: "ACTIVE" as const,
  registrationNumber: "2026/123456/07",
  taxNumber: "9876543210",
  entityType: "PTY_LTD",
  financialYearEnd: "2026-02-28",
};

const headerProps: ClientHeaderCardProps = {
  customerId: "cust-1",
  customerName: "Acme Corporation",
  customerStatus: "ACTIVE",
  lifecycleStatus: "ACTIVE",
  email: "info@acme.com",
  phone: "+27 11 123 4567",
  lifecycleStatusChangedAt: "2026-01-15T10:00:00Z",
  linkedProjectCount: 3,
  kycSummary: null,
  xeroConnected: false,
  slug: "test-org",
  isAdmin: true,
  isOwner: true,
  templates: [],
  aiProviderConfigured: false,
  conflictCheckEnabled: false,
  kycConfigured: false,
  kycVerified: false,
  customer: mockCustomer,
};

const overviewBaseProps = {
  setupProgressData: null,
  lifecyclePrompt: null,
  unbilledTimeData: null,
  activeRetainer: null,
  templateReadiness: null,
  pendingSuggestions: null,
  ficaPanel: null,
  customerName: "Acme Corporation",
  lifecycleStatus: "ACTIVE" as const,
  linkedProjectCount: 3,
};

// ---------------------------------------------------------------------------
// Helper: renders the full page structure (header + tabs)
// ---------------------------------------------------------------------------

function renderFullPage(options?: {
  lifecycleStatus?: ClientHeaderCardProps["lifecycleStatus"];
  customerStatus?: ClientHeaderCardProps["customerStatus"];
}) {
  const lc = options?.lifecycleStatus ?? "ACTIVE";
  const cs = options?.customerStatus ?? "ACTIVE";

  return render(
    <div className="space-y-6">
      <ClientHeaderCard
        {...headerProps}
        lifecycleStatus={lc}
        customerStatus={cs}
        customerName={lc === "ANONYMIZED" ? "Anonymized Customer" : "Acme Corporation"}
      />

      {lc === "ANONYMIZED" && (
        <div
          data-testid="anonymized-banner"
          className="flex items-start gap-3 rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-900"
        >
          <div className="text-sm text-slate-700 dark:text-slate-300">
            <p className="font-medium">Customer data anonymized</p>
          </div>
        </div>
      )}

      <CustomerGroupedTabs
        detailsPanel={<ClientDetailsTab customer={mockCustomer} />}
        fieldsPanel={
          <ClientFieldsTab
            entityId="cust-1"
            appliedFieldGroups={[]}
            slug="test-org"
            canManage={true}
            allGroups={[]}
            customFields={{}}
            editable={true}
            fieldDefinitions={[]}
            fieldGroups={[]}
            groupMembers={{}}
            promotedFieldValues={{}}
          />
        }
        tagsPanel={
          <ClientTagsTab
            entityId="cust-1"
            tags={[]}
            allTags={[]}
            editable={true}
            canInlineCreate={true}
            slug="test-org"
          />
        }
        overviewPanel={<ClientOverviewTab {...overviewBaseProps} />}
        projectsPanel={<div data-testid="projects-panel">Projects</div>}
        documentsPanel={<div data-testid="documents-panel">Documents</div>}
      />
    </div>
  );
}

// ---------------------------------------------------------------------------
// Cleanup
// ---------------------------------------------------------------------------

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  mockSearchParams = new URLSearchParams();
});

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("Customer Detail Page — integration", () => {
  it("renders header card and grouped tab bar together", () => {
    renderFullPage();

    // Header card is present with customer name
    expect(screen.getByTestId("client-header-card")).toBeInTheDocument();
    expect(screen.getByTestId("client-name")).toHaveTextContent("Acme Corporation");

    // Tab bar is present with at least the core groups
    expect(screen.getByTestId("grouped-tab-bar")).toBeInTheDocument();
    expect(screen.getByTestId("tab-group-overview")).toBeInTheDocument();
    expect(screen.getByTestId("tab-group-details")).toBeInTheDocument();
    expect(screen.getByTestId("tab-group-work")).toBeInTheDocument();
  });

  it("defaults to overview tab content being visible", () => {
    renderFullPage();

    // Overview group should be active (aria-selected)
    const overviewGroup = screen.getByTestId("tab-group-overview");
    expect(overviewGroup.getAttribute("aria-selected")).toBe("true");

    // Overview tab content should be rendered — the empty state shows customer name
    expect(screen.getByTestId("client-overview-tab")).toBeInTheDocument();
  });

  it("navigates to Details group and shows details tab content", async () => {
    const user = userEvent.setup();
    renderFullPage();

    // Click the Details group tab
    const detailsGroup = screen.getByTestId("tab-group-details");
    await user.click(detailsGroup);

    // Router replace should have been called with ?tab= pointing to a details tab
    expect(mockReplace).toHaveBeenCalled();
    const replaceArg = mockReplace.mock.calls[0][0] as string;
    expect(replaceArg).toContain("tab=");

    // Re-render with the tab param that was set
    cleanup();
    const tabValue = new URLSearchParams(replaceArg.replace("?", "")).get("tab")!;
    mockSearchParams = new URLSearchParams(`tab=${tabValue}`);
    renderFullPage();

    // Details group should now be active
    const detailsGroupAfter = screen.getByTestId("tab-group-details");
    expect(detailsGroupAfter.getAttribute("aria-selected")).toBe("true");

    // Details tab content should be visible
    expect(screen.getByTestId("client-details-tab")).toBeInTheDocument();
  });

  it("renders anonymized banner and hides primary action for ANONYMIZED lifecycle", () => {
    renderFullPage({ lifecycleStatus: "ANONYMIZED" });

    // Anonymized banner should be present
    expect(screen.getByTestId("anonymized-banner")).toBeInTheDocument();
    expect(screen.getByText("Customer data anonymized")).toBeInTheDocument();

    // Primary action button should be absent (ANONYMIZED returns null)
    expect(screen.queryByTestId("smart-primary-action")).not.toBeInTheDocument();

    // Header card should still show the anonymized name
    expect(screen.getByTestId("client-name")).toHaveTextContent("Anonymized Customer");

    // Lifecycle badge should show ANONYMIZED
    const headerCard = screen.getByTestId("client-header-card");
    const lifecycleBadge = within(headerCard).getByTestId("lifecycle-badge");
    expect(lifecycleBadge).toHaveTextContent("ANONYMIZED");
  });
});
