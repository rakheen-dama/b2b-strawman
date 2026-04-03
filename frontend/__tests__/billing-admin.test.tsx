import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { StatusBadge } from "@/components/billing/status-badge";
import { MethodBadge } from "@/components/billing/method-badge";
import { BillingDetailSheet } from "@/components/billing/billing-detail-sheet";
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

  it("renders PAYFAST with success variant", () => {
    render(<MethodBadge method="PAYFAST" />);
    const badge = screen.getByText("PayFast");
    expect(badge).toBeInTheDocument();
    expect(badge.getAttribute("data-variant")).toBe("success");
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
      />,
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
      />,
    );

    // Clear the pre-filled admin note
    const noteInput = screen.getByLabelText("Admin Note");
    await user.clear(noteInput);

    // Click save
    const saveButton = screen.getByRole("button", { name: "Save Changes" });
    await user.click(saveButton);

    // Should show validation error
    await waitFor(() => {
      expect(screen.getByTestId("note-error")).toHaveTextContent(
        "Admin note is required",
      );
    });

    // Should NOT have called the API
    expect(mockOverrideBilling).not.toHaveBeenCalled();
    expect(mockExtendTrial).not.toHaveBeenCalled();
  });

  it("renders nothing when tenant is null", () => {
    const { container } = render(
      <BillingDetailSheet
        tenant={null}
        open={true}
        onOpenChange={vi.fn()}
        onSuccess={vi.fn()}
      />,
    );

    // Sheet should not render any content
    expect(
      container.querySelector('[data-testid="sheet-org-name"]'),
    ).toBeNull();
  });
});
