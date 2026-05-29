import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ClientOverflowMenu } from "@/components/customers/client-overflow-menu";
import type { TemplateListResponse } from "@/lib/types";

// ---- Mocks ----

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
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

const mockTemplates: TemplateListResponse[] = [
  { id: "tpl-1", name: "Invoice Template", format: "HTML" } as TemplateListResponse,
];

const mockCustomer = {
  id: "cust-1",
  name: "Acme Corp",
  email: "acme@test.com",
  phone: "+27 11 123 4567",
  idNumber: null,
  status: "ACTIVE" as const,
  notes: null,
  createdBy: "user-1",
  createdByName: "Alice",
  createdAt: "2026-01-10T10:00:00Z",
  updatedAt: "2026-01-15T10:00:00Z",
  lifecycleStatus: "ACTIVE" as const,
};

const mockOnSummariseActivity = vi.fn();

const defaultProps = {
  customerId: "cust-1",
  customerName: "Acme Corp",
  customerStatus: "ACTIVE" as const,
  lifecycleStatus: "ACTIVE" as const,
  slug: "test-org",
  isAdmin: true,
  isOwner: true,
  isAnonymized: false,
  templates: mockTemplates,
  aiProviderConfigured: true,
  conflictCheckEnabled: true,
  kycConfigured: true,
  kycVerified: false,
  customer: mockCustomer,
  onSummariseActivity: mockOnSummariseActivity,
};

// ---- Tests ----

describe("ClientOverflowMenu", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders all menu items for admin user with all modules enabled", async () => {
    const user = userEvent.setup();
    render(<ClientOverflowMenu {...defaultProps} />);
    await user.click(screen.getByTestId("client-overflow-trigger"));

    expect(screen.getByText("Edit Client")).toBeInTheDocument();
    expect(screen.getByText("Summarise Activity")).toBeInTheDocument();
    expect(screen.getByText("Generate Document")).toBeInTheDocument();
    expect(screen.getByText("Run Conflict Check")).toBeInTheDocument();
    expect(screen.getByText("Verify KYC")).toBeInTheDocument();
    expect(screen.getByText("Export Data")).toBeInTheDocument();
    expect(screen.getByText("Anonymize")).toBeInTheDocument();
    expect(screen.getByText("Archive")).toBeInTheDocument();
  });

  it("hides Anonymize for non-owner", async () => {
    const user = userEvent.setup();
    render(<ClientOverflowMenu {...defaultProps} isOwner={false} />);
    await user.click(screen.getByTestId("client-overflow-trigger"));

    expect(screen.queryByText("Anonymize")).not.toBeInTheDocument();
    // Other items still present
    expect(screen.getByText("Edit Client")).toBeInTheDocument();
    expect(screen.getByText("Archive")).toBeInTheDocument();
  });

  it("hides Archive for non-admin", async () => {
    const user = userEvent.setup();
    render(<ClientOverflowMenu {...defaultProps} isAdmin={false} />);
    await user.click(screen.getByTestId("client-overflow-trigger"));

    expect(screen.queryByText("Archive")).not.toBeInTheDocument();
    // Generate Document also hidden (requires isAdmin)
    expect(screen.queryByText("Generate Document")).not.toBeInTheDocument();
    // Other items still present
    expect(screen.getByText("Edit Client")).toBeInTheDocument();
  });

  it("shows only Export Data for ANONYMIZED customer", async () => {
    const user = userEvent.setup();
    render(
      <ClientOverflowMenu {...defaultProps} isAnonymized={true} lifecycleStatus="ANONYMIZED" />
    );
    await user.click(screen.getByTestId("client-overflow-trigger"));

    expect(screen.getByText("Export Data")).toBeInTheDocument();
    expect(screen.queryByText("Edit Client")).not.toBeInTheDocument();
    expect(screen.queryByText("Generate Document")).not.toBeInTheDocument();
    expect(screen.queryByText("Run Conflict Check")).not.toBeInTheDocument();
    expect(screen.queryByText("Verify KYC")).not.toBeInTheDocument();
    expect(screen.queryByText("Anonymize")).not.toBeInTheDocument();
    expect(screen.queryByText("Archive")).not.toBeInTheDocument();
  });

  it("hides Generate Document when no templates", async () => {
    const user = userEvent.setup();
    render(<ClientOverflowMenu {...defaultProps} templates={[]} />);
    await user.click(screen.getByTestId("client-overflow-trigger"));

    expect(screen.queryByText("Generate Document")).not.toBeInTheDocument();
    // Other items still present
    expect(screen.getByText("Edit Client")).toBeInTheDocument();
  });

  it("hides Run Conflict Check when conflictCheckEnabled is false", async () => {
    const user = userEvent.setup();
    render(<ClientOverflowMenu {...defaultProps} conflictCheckEnabled={false} />);
    await user.click(screen.getByTestId("client-overflow-trigger"));

    expect(screen.queryByText("Run Conflict Check")).not.toBeInTheDocument();
    // Other items still present
    expect(screen.getByText("Edit Client")).toBeInTheDocument();
  });
});
