import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { StatusBadge } from "@/components/billing/status-badge";
import { MethodBadge } from "@/components/billing/method-badge";
import { BillingDetailSheet } from "@/components/billing/billing-detail-sheet";
import { BillingTenantsTable } from "@/components/billing/billing-tenants-table";
import { DemoTenantsTable } from "@/components/billing/demo-tenants-table";
import type { AdminTenantBilling } from "@/app/(app)/platform-admin/billing/actions";

const mockOverrideBilling = vi.fn();
const mockExtendTrial = vi.fn();

vi.mock("server-only", () => ({}));

vi.mock("@/app/(app)/platform-admin/billing/actions", () => ({
  overrideBilling: (...args: unknown[]) => mockOverrideBilling(...args),
  extendTrial: (...args: unknown[]) => mockExtendTrial(...args),
  listBillingTenants: vi.fn(),
  getBillingTenant: vi.fn(),
}));

const mockReseedDemoTenant = vi.fn();
const mockDeleteDemoTenant = vi.fn();

vi.mock("@/app/(app)/platform-admin/demo/actions", () => ({
  reseedDemoTenant: (...args: unknown[]) => mockReseedDemoTenant(...args),
  deleteDemoTenant: (...args: unknown[]) => mockDeleteDemoTenant(...args),
}));

vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
    refresh: vi.fn(),
    back: vi.fn(),
    forward: vi.fn(),
    prefetch: vi.fn(),
  }),
}));

const sampleTenant: AdminTenantBilling = {
  organizationId: "org-123",
  organizationName: "Acme Corp",
  verticalProfile: "ACCOUNTING",
  subscriptionStatus: "TRIALING",
  billingMethod: "PILOT",
  trialEndsAt: "2026-05-01T00:00:00Z",
  currentPeriodEnd: null,
  graceEndsAt: null,
  createdAt: "2026-03-01T00:00:00Z",
  memberCount: 5,
  adminNote: "Initial pilot setup",
  isDemoTenant: true,
};

describe("StatusBadge", () => {
  afterEach(() => cleanup());

  it("renders ACTIVE with success variant", () => {
    render(<StatusBadge status="ACTIVE" />);
    const badge = screen.getByText("Active");
    expect(badge).toBeInTheDocument();
    expect(badge.getAttribute("data-variant")).toBe("success");
  });

  it("renders LOCKED with destructive variant", () => {
    render(<StatusBadge status="LOCKED" />);
    const badge = screen.getByText("Locked");
    expect(badge).toBeInTheDocument();
    expect(badge.getAttribute("data-variant")).toBe("destructive");
  });

  it("renders TRIALING with blue styling", () => {
    render(<StatusBadge status="TRIALING" />);
    const badge = screen.getByText("Trialing");
    expect(badge).toBeInTheDocument();
    // TRIALING uses neutral variant with blue className override
    expect(badge.getAttribute("data-variant")).toBe("neutral");
    expect(badge.className).toContain("bg-blue-100");
  });

  it("renders unknown status with neutral variant", () => {
    render(<StatusBadge status="UNKNOWN_STATUS" />);
    const badge = screen.getByText("UNKNOWN_STATUS");
    expect(badge).toBeInTheDocument();
    expect(badge.getAttribute("data-variant")).toBe("neutral");
  });
});

describe("MethodBadge", () => {
  afterEach(() => cleanup());

  it("renders DEBIT_ORDER with teal styling", () => {
    render(<MethodBadge method="DEBIT_ORDER" />);
    const badge = screen.getByText("Debit Order");
    expect(badge).toBeInTheDocument();
    expect(badge.className).toContain("bg-teal-100");
  });

  it("renders MANUAL with neutral variant", () => {
    render(<MethodBadge method="MANUAL" />);
    const badge = screen.getByText("Manual");
    expect(badge).toBeInTheDocument();
    expect(badge.getAttribute("data-variant")).toBe("neutral");
  });

  it("renders PILOT with purple styling", () => {
    render(<MethodBadge method="PILOT" />);
    const badge = screen.getByText("Pilot");
    expect(badge).toBeInTheDocument();
    expect(badge.getAttribute("data-variant")).toBe("neutral");
    expect(badge.className).toContain("bg-purple-100");
  });
});

describe("BillingDetailSheet", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });
  afterEach(() => cleanup());

  it("renders tenant data when open", () => {
    render(
      <BillingDetailSheet
        tenant={sampleTenant}
        open={true}
        onOpenChange={vi.fn()}
        onSuccess={vi.fn()}
      />
    );

    expect(screen.getByTestId("sheet-org-name")).toHaveTextContent("Acme Corp");
    expect(screen.getByTestId("trial-ends")).toHaveTextContent("01 May 2026");
    expect(screen.getByTestId("period-end")).toHaveTextContent("N/A");
    // Admin note appears in the current note display and pre-filled in the textarea
    expect(screen.getAllByText("Initial pilot setup").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText("Demo")).toBeInTheDocument();
  });

  it("requires admin note for save", async () => {
    const user = userEvent.setup();

    render(
      <BillingDetailSheet
        tenant={sampleTenant}
        open={true}
        onOpenChange={vi.fn()}
        onSuccess={vi.fn()}
      />
    );

    // Clear the pre-filled admin note
    const noteInput = screen.getByLabelText("Admin Note");
    await user.clear(noteInput);

    // Click save
    const saveButton = screen.getByRole("button", { name: "Save Changes" });
    await user.click(saveButton);

    // Should show validation error
    await waitFor(() => {
      expect(screen.getByTestId("note-error")).toHaveTextContent("Admin note is required");
    });

    // Should NOT have called the API
    expect(mockOverrideBilling).not.toHaveBeenCalled();
    expect(mockExtendTrial).not.toHaveBeenCalled();
  });

  it("renders nothing when tenant is null", () => {
    const { container } = render(
      <BillingDetailSheet tenant={null} open={true} onOpenChange={vi.fn()} onSuccess={vi.fn()} />
    );

    // Sheet should not render any content
    expect(container.querySelector('[data-testid="sheet-org-name"]')).toBeNull();
  });
});

describe("BillingTenantsTable (669B: DataTable)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });
  afterEach(() => cleanup());

  const olderTenant: AdminTenantBilling = {
    ...sampleTenant,
    organizationId: "org-456",
    organizationName: "Beta Legal",
    createdAt: "2026-01-01T00:00:00Z",
    trialEndsAt: null,
    currentPeriodEnd: "2026-06-30T00:00:00Z",
  };

  it("renders today's headers and rows newest first, with the billing dates", () => {
    render(<BillingTenantsTable tenants={[olderTenant, sampleTenant]} />);

    expect(screen.getByTestId("billing-tenants-page")).toBeInTheDocument();
    const table = screen.getByRole("table");
    expect(
      within(table)
        .getAllByRole("columnheader")
        .map((h) => h.textContent)
    ).toEqual([
      "Org Name",
      "Profile",
      "Status",
      "Billing Method",
      "Trial / Period End",
      "Members",
      "Created",
      "Actions",
    ]);
    const bodyRows = within(table).getAllByRole("row").slice(1);
    expect(bodyRows).toHaveLength(2);
    expect(within(bodyRows[0]).getByText("Acme Corp")).toBeInTheDocument();
    // Kept verbatim (D-95): components/billing/utils formatDate.
    expect(within(bodyRows[0]).getByText("01 May 2026")).toBeInTheDocument();
    expect(within(bodyRows[0]).getByText("01 Mar 2026")).toBeInTheDocument();
    expect(within(bodyRows[1]).getByText("Beta Legal")).toBeInTheDocument();
    expect(within(bodyRows[1]).getByText("30 Jun 2026")).toBeInTheDocument();
    // Profile cell classes verbatim (D-95 / DA-9).
    expect(within(bodyRows[0]).getByText("ACCOUNTING")).toHaveClass("text-sm", "text-slate-500");
  });

  it("opens the detail sheet from the row's Open button", async () => {
    const user = userEvent.setup();
    render(<BillingTenantsTable tenants={[sampleTenant]} />);

    await user.click(screen.getByRole("button", { name: "Open" }));

    expect(await screen.findByTestId("sheet-org-name")).toHaveTextContent("Acme Corp");
  });

  it("shows the inline empty state (a <p>, not a heading) when the search matches nothing", async () => {
    const user = userEvent.setup();
    render(<BillingTenantsTable tenants={[sampleTenant]} />);

    await user.type(screen.getByPlaceholderText("Search by org name..."), "zzz");

    const title = screen.getByText("No tenants found");
    expect(title.tagName).toBe("P");
    expect(title.closest('[data-slot="empty-state"]')).toHaveAttribute("data-variant", "inline");
    expect(screen.getByText("No tenants match the current filters.")).toBeInTheDocument();
    expect(screen.queryByRole("heading")).toBeNull();
    expect(screen.queryByRole("table")).toBeNull();
  });
});

describe("DemoTenantsTable (669B: DataTable)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });
  afterEach(() => cleanup());

  it("renders today's headers on the demo-tenants-table and a row testid per tenant", () => {
    render(<DemoTenantsTable tenants={[sampleTenant]} />);

    const table = screen.getByTestId("demo-tenants-table");
    expect(table.tagName).toBe("TABLE");
    expect(
      within(table)
        .getAllByRole("columnheader")
        .map((h) => h.textContent)
    ).toEqual(["Org Name", "Profile", "Status", "Billing Method", "Created", "Members", "Actions"]);
    const row = screen.getByTestId("demo-tenant-row-Acme Corp");
    expect(row.tagName).toBe("TR");
    expect(within(row).getByText("01 Mar 2026")).toBeInTheDocument();
    expect(within(row).getByRole("button", { name: "Reseed Data" })).toBeEnabled();
    expect(within(row).getByRole("button", { name: "Delete Tenant" })).toBeInTheDocument();
  });

  it("shows the compact empty state and no table when there are no demo tenants", () => {
    render(<DemoTenantsTable tenants={[]} />);

    expect(
      screen.getByText("No demo tenants").closest('[data-slot="empty-state"]')
    ).toHaveAttribute("data-variant", "compact");
    expect(screen.getByText("No demo tenants have been provisioned yet.")).toBeInTheDocument();
    expect(screen.queryByTestId("demo-tenants-table")).toBeNull();
  });

  it("reseeds a tenant: the button shows the pending state until the action settles", async () => {
    let settle: (v: unknown) => void = () => {};
    mockReseedDemoTenant.mockReturnValue(new Promise((r) => (settle = r)));
    const user = userEvent.setup();
    render(<DemoTenantsTable tenants={[sampleTenant]} />);

    await user.click(screen.getByRole("button", { name: "Reseed Data" }));

    expect(mockReseedDemoTenant).toHaveBeenCalledWith("org-123");
    expect(screen.getByRole("button", { name: "Reseeding..." })).toBeDisabled();
    settle({ success: true });
    expect(await screen.findByRole("button", { name: "Reseed Data" })).toBeEnabled();
  });

  it("opens the delete dialog from Delete Tenant", async () => {
    const user = userEvent.setup();
    render(<DemoTenantsTable tenants={[sampleTenant]} />);

    await user.click(screen.getByRole("button", { name: "Delete Tenant" }));

    expect(await screen.findByRole("alertdialog")).toBeInTheDocument();
  });
});
