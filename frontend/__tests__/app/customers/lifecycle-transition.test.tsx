import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { LifecycleTransitionDropdown } from "@/components/compliance/LifecycleTransitionDropdown";
import { SetupProgressCard } from "@/components/setup/setup-progress-card";
import type { PrerequisiteCheck } from "@/components/prerequisite/types";

// Mock server actions and prerequisite actions
vi.mock("@/lib/actions/prerequisite-actions", () => ({
  checkPrerequisitesAction: vi.fn(),
  updateEntityCustomFieldsAction: vi.fn(),
}));

vi.mock("@/app/(app)/org/[slug]/customers/[id]/lifecycle-actions", () => ({
  transitionCustomerLifecycle: vi.fn(),
}));

import { checkPrerequisitesAction } from "@/lib/actions/prerequisite-actions";
import { transitionCustomerLifecycle } from "@/app/(app)/org/[slug]/customers/[id]/lifecycle-actions";

const mockCheckPrereqs = vi.mocked(checkPrerequisitesAction);
const mockTransition = vi.mocked(transitionCustomerLifecycle);

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(() => {
  cleanup();
});

const failedCheck: PrerequisiteCheck = {
  passed: false,
  context: "LIFECYCLE_ACTIVATION",
  violations: [
    {
      code: "MISSING_FIELD",
      message: "VAT number is required for activation",
      entityType: "CUSTOMER",
      entityId: "cust-1",
      fieldSlug: "vat_number",
      groupName: "Billing",
      resolution: "Enter the VAT number",
    },
    {
      code: "NO_PORTAL_CONTACT",
      message: "A portal contact must be assigned",
      entityType: "CUSTOMER",
      entityId: "cust-1",
      fieldSlug: null,
      groupName: null,
      resolution: "Assign a portal contact",
    },
  ],
};

const passedCheck: PrerequisiteCheck = {
  passed: true,
  context: "LIFECYCLE_ACTIVATION",
  violations: [],
};

describe("LifecycleTransitionDropdown — prerequisite integration", () => {
  it("opens PrerequisiteModal when activating and prerequisites are not met", async () => {
    const user = userEvent.setup();
    mockCheckPrereqs.mockResolvedValueOnce(failedCheck);

    render(
      <LifecycleTransitionDropdown
        currentStatus="ONBOARDING"
        customerId="cust-1"
        slug="test-org"
      />,
    );

    // Open the dropdown
    await user.click(screen.getByRole("button", { name: /change status/i }));

    // Click "Activate"
    await user.click(screen.getByRole("menuitem", { name: /activate/i }));

    // Should have called prerequisite check
    await waitFor(() => {
      expect(mockCheckPrereqs).toHaveBeenCalledWith(
        "LIFECYCLE_ACTIVATION",
        "CUSTOMER",
        "cust-1",
      );
    });

    // PrerequisiteModal should be shown with violations
    await waitFor(() => {
      expect(
        screen.getByText("Prerequisites: Customer Activation"),
      ).toBeInTheDocument();
    });
    expect(
      screen.getByText("VAT number is required for activation"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("A portal contact must be assigned"),
    ).toBeInTheDocument();
  });

  it("opens TransitionConfirmDialog directly when prerequisites are met", async () => {
    const user = userEvent.setup();
    mockCheckPrereqs.mockResolvedValueOnce(passedCheck);

    render(
      <LifecycleTransitionDropdown
        currentStatus="ONBOARDING"
        customerId="cust-1"
        slug="test-org"
      />,
    );

    await user.click(screen.getByRole("button", { name: /change status/i }));
    await user.click(screen.getByRole("menuitem", { name: /activate/i }));

    await waitFor(() => {
      expect(mockCheckPrereqs).toHaveBeenCalledWith(
        "LIFECYCLE_ACTIVATION",
        "CUSTOMER",
        "cust-1",
      );
    });

    // TransitionConfirmDialog should be shown (not PrerequisiteModal)
    await waitFor(() => {
      expect(screen.getByText("Activate Customer")).toBeInTheDocument();
    });
    // Should NOT show prerequisite modal
    expect(
      screen.queryByText("Prerequisites: Customer Activation"),
    ).not.toBeInTheDocument();
  });

  it("opens PrerequisiteModal when backend returns 422 during transition", async () => {
    const user = userEvent.setup();

    // For a non-ONBOARDING->ACTIVE transition that unexpectedly returns 422
    // Or simulate: prerequisites passed initially, but backend rejects on actual transition
    mockCheckPrereqs.mockResolvedValueOnce(passedCheck);
    mockTransition.mockResolvedValueOnce({
      success: false,
      error: "Prerequisites not met",
      prerequisiteCheck: failedCheck,
    });

    render(
      <LifecycleTransitionDropdown
        currentStatus="ONBOARDING"
        customerId="cust-1"
        slug="test-org"
      />,
    );

    await user.click(screen.getByRole("button", { name: /change status/i }));
    await user.click(screen.getByRole("menuitem", { name: /activate/i }));

    // Wait for prereq check to pass, showing confirm dialog
    await waitFor(() => {
      expect(screen.getByText("Activate Customer")).toBeInTheDocument();
    });

    // Click Activate in the confirm dialog
    await user.click(screen.getByRole("button", { name: /^activate$/i }));

    // Backend returned 422 — should switch to PrerequisiteModal
    await waitFor(() => {
      expect(
        screen.getByText("Prerequisites: Customer Activation"),
      ).toBeInTheDocument();
    });
  });
});

describe("SetupProgressCard — activation blockers", () => {
  it("displays activation blocker messages when provided", () => {
    render(
      <SetupProgressCard
        title="Customer Readiness"
        completionPercentage={50}
        overallComplete={false}
        steps={[
          { label: "Projects linked", complete: true },
          { label: "Required fields filled (1/2)", complete: false },
        ]}
        activationBlockers={[
          "VAT number is required for activation",
          "A portal contact must be assigned",
        ]}
      />,
    );

    expect(screen.getByText("Blocking activation")).toBeInTheDocument();
    expect(
      screen.getByText("VAT number is required for activation"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("A portal contact must be assigned"),
    ).toBeInTheDocument();
  });

  it("does not display blocker section when no blockers", () => {
    render(
      <SetupProgressCard
        title="Customer Readiness"
        completionPercentage={100}
        overallComplete={false}
        steps={[{ label: "Projects linked", complete: true }]}
      />,
    );

    expect(screen.queryByText("Blocking activation")).not.toBeInTheDocument();
  });
});
