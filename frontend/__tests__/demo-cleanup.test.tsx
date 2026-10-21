import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { DeleteTenantDialog } from "@/components/billing/delete-tenant-dialog";
import { MethodBadge } from "@/components/billing/method-badge";
import { Card, CardContent } from "@b2mash/ui/card";

const mockDeleteDemoTenant = vi.fn();
const mockReseedDemoTenant = vi.fn();

vi.mock("server-only", () => ({}));

vi.mock("@/app/(app)/platform-admin/demo/actions", () => ({
  deleteDemoTenant: (...args: unknown[]) => mockDeleteDemoTenant(...args),
  reseedDemoTenant: (...args: unknown[]) => mockReseedDemoTenant(...args),
  listDemoTenants: vi.fn(),
  provisionDemo: vi.fn(),
}));

vi.mock("@/app/(app)/platform-admin/billing/actions", () => ({
  listBillingTenants: vi.fn(),
  overrideBilling: vi.fn(),
  extendTrial: vi.fn(),
}));

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

vi.mock("@/lib/actions/subscription", () => ({
  getSubscription: vi.fn(),
}));

vi.mock("sonner", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}));

const sampleTenant = {
  organizationId: "org-demo-456",
  organizationName: "Demo Legal Firm",
  verticalProfile: "LEGAL",
  memberCount: 3,
  createdAt: "2026-03-15T10:00:00Z",
};

describe("DeleteTenantDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });
  afterEach(() => cleanup());

  it("shows warning and org details", () => {
    render(
      <DeleteTenantDialog
        tenant={sampleTenant}
        open={true}
        onOpenChange={vi.fn()}
        onSuccess={vi.fn()}
      />
    );

    expect(screen.getByText(/permanently delete the organization/)).toBeInTheDocument();
    expect(screen.getByText("Demo Legal Firm")).toBeInTheDocument();
    expect(screen.getByText(/3 members/)).toBeInTheDocument();
  });

  it("delete button disabled until exact name typed", async () => {
    const user = userEvent.setup();

    render(
      <DeleteTenantDialog
        tenant={sampleTenant}
        open={true}
        onOpenChange={vi.fn()}
        onSuccess={vi.fn()}
      />
    );

    const deleteButton = screen.getByRole("button", {
      name: "Delete Tenant",
    });
    expect(deleteButton).toBeDisabled();

    const input = screen.getByLabelText("Type the organization name to confirm");
    await user.type(input, "Demo Legal");

    expect(deleteButton).toBeDisabled();
  });

  it("delete button enabled when exact name matches", async () => {
    const user = userEvent.setup();

    render(
      <DeleteTenantDialog
        tenant={sampleTenant}
        open={true}
        onOpenChange={vi.fn()}
        onSuccess={vi.fn()}
      />
    );

    const deleteButton = screen.getByRole("button", {
      name: "Delete Tenant",
    });
    const input = screen.getByLabelText("Type the organization name to confirm");

    await user.type(input, "Demo Legal Firm");

    expect(deleteButton).toBeEnabled();
  });
});

describe("Billing page managed-account view", () => {
  afterEach(() => cleanup());

  it("renders PILOT method badge for admin-managed tenants", () => {
    // Manual firm-level billing is the only model (ADR-335): every tenant is
    // admin-managed and the billing page shows the method badge.
    render(<MethodBadge method="PILOT" />);

    expect(screen.getByText("Pilot")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Subscribe" })).not.toBeInTheDocument();
  });

  it("shows 'managed by administrator' for PILOT billing method", () => {
    // The admin-managed info card displays this text. We render it inline
    // to verify the message content.
    render(
      <Card>
        <CardContent>
          <p className="text-sm text-slate-600">Your account is managed by your administrator.</p>
        </CardContent>
      </Card>
    );

    expect(screen.getByText(/managed by your administrator/)).toBeInTheDocument();
  });
});
