import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { ClientHeaderCard } from "@/components/customers/client-header-card";
import type { ClientHeaderCardProps } from "@/components/customers/client-header-card";

// ---- Mocks ----

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock("@/components/compliance/LifecycleStatusBadge", () => ({
  LifecycleStatusBadge: ({ status }: { status: string }) => (
    <span data-testid="lifecycle-badge">{status}</span>
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

vi.mock("@/components/customers/edit-customer-dialog", () => ({
  EditCustomerDialog: () => <div data-testid="edit-customer-dialog" />,
}));

vi.mock("@/components/customers/archive-customer-dialog", () => ({
  ArchiveCustomerDialog: () => <div data-testid="archive-customer-dialog" />,
}));

vi.mock("@/components/customers/anonymize-customer-dialog", () => ({
  AnonymizeCustomerDialog: () => <div data-testid="anonymize-customer-dialog" />,
}));

vi.mock("@/components/customers/data-export-dialog", () => ({
  DataExportDialog: () => <div data-testid="data-export-dialog" />,
}));

// ---- Test Data ----

const defaultProps: ClientHeaderCardProps = {
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
};

// ---- Tests ----

describe("ClientHeaderCard", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders customer name with line-clamp", () => {
    render(<ClientHeaderCard {...defaultProps} />);
    const nameEl = screen.getByTestId("client-name");
    expect(nameEl).toHaveTextContent("Acme Corporation");
    expect(nameEl.getAttribute("title")).toBe("Acme Corporation");
    expect(nameEl.className).toContain("line-clamp-2");
  });

  it('renders "Start Onboarding" button for PROSPECT lifecycle', () => {
    render(<ClientHeaderCard {...defaultProps} lifecycleStatus="PROSPECT" />);
    const actionBtn = screen.getByTestId("smart-primary-action");
    expect(actionBtn).toHaveTextContent("Start Onboarding");
  });

  it('renders "Edit" button for ACTIVE lifecycle', () => {
    render(<ClientHeaderCard {...defaultProps} lifecycleStatus="ACTIVE" />);
    const actionBtn = screen.getByTestId("smart-primary-action");
    expect(actionBtn).toHaveTextContent("Edit");
  });

  it("renders no primary action for OFFBOARDED lifecycle", () => {
    render(<ClientHeaderCard {...defaultProps} lifecycleStatus="OFFBOARDED" />);
    expect(screen.queryByTestId("smart-primary-action")).not.toBeInTheDocument();
  });

  it("renders KYC and Xero badges when present", () => {
    render(
      <ClientHeaderCard
        {...defaultProps}
        kycSummary={{ state: "verified", provider: "Onfido", verifiedAt: "2026-01-20T10:00:00Z" }}
        xeroConnected={true}
      />
    );
    expect(screen.getByTestId("kyc-badge")).toBeInTheDocument();
    expect(screen.getByTestId("xero-badge")).toBeInTheDocument();
  });

  it("hides primary action and shows minimal badges for ANONYMIZED state", () => {
    render(
      <ClientHeaderCard
        {...defaultProps}
        lifecycleStatus="ANONYMIZED"
        kycSummary={null}
        xeroConnected={false}
      />
    );
    expect(screen.queryByTestId("smart-primary-action")).not.toBeInTheDocument();
    expect(screen.getByTestId("lifecycle-badge")).toHaveTextContent("ANONYMIZED");
    expect(screen.queryByTestId("kyc-badge")).not.toBeInTheDocument();
    expect(screen.queryByTestId("xero-badge")).not.toBeInTheDocument();
  });
});
