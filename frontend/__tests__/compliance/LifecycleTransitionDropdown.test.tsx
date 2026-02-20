import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { LifecycleTransitionDropdown } from "@/components/compliance/LifecycleTransitionDropdown";

// Mock the server action called inside TransitionConfirmDialog
const mockTransitionCustomerLifecycle = vi.fn();

vi.mock("@/app/(app)/org/[slug]/customers/[id]/lifecycle-actions", () => ({
  transitionCustomerLifecycle: (...args: unknown[]) => mockTransitionCustomerLifecycle(...args),
}));

describe("LifecycleTransitionDropdown", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders 'Change Status' button for PROSPECT status", () => {
    render(
      <LifecycleTransitionDropdown
        currentStatus="PROSPECT"
        customerId="c1"
        slug="acme"
      />,
    );
    expect(screen.getByText("Change Status")).toBeInTheDocument();
  });

  it("shows 'Start Onboarding' transition for PROSPECT", async () => {
    const user = userEvent.setup();

    render(
      <LifecycleTransitionDropdown
        currentStatus="PROSPECT"
        customerId="c1"
        slug="acme"
      />,
    );

    await user.click(screen.getByText("Change Status"));
    expect(screen.getByText("Start Onboarding")).toBeInTheDocument();
  });

  it("shows 'Reactivate' transition for DORMANT status", async () => {
    const user = userEvent.setup();

    render(
      <LifecycleTransitionDropdown
        currentStatus="DORMANT"
        customerId="c1"
        slug="acme"
      />,
    );

    await user.click(screen.getByText("Change Status"));
    expect(screen.getByText("Reactivate")).toBeInTheDocument();
  });

  it("shows 'Offboard Customer' transition for ACTIVE status", async () => {
    const user = userEvent.setup();

    render(
      <LifecycleTransitionDropdown
        currentStatus="ACTIVE"
        customerId="c1"
        slug="acme"
      />,
    );

    await user.click(screen.getByText("Change Status"));
    expect(screen.getByText("Offboard Customer")).toBeInTheDocument();
  });

  it("shows 'Reactivate' transition for OFFBOARDED status", async () => {
    const user = userEvent.setup();

    render(
      <LifecycleTransitionDropdown
        currentStatus="OFFBOARDED"
        customerId="c1"
        slug="acme"
      />,
    );

    await user.click(screen.getByText("Change Status"));
    expect(screen.getByText("Reactivate")).toBeInTheDocument();
  });

  it("PROSPECT only shows Start Onboarding (not Activate)", async () => {
    const user = userEvent.setup();

    render(
      <LifecycleTransitionDropdown
        currentStatus="PROSPECT"
        customerId="c1"
        slug="acme"
      />,
    );

    await user.click(screen.getByText("Change Status"));
    expect(screen.getByText("Start Onboarding")).toBeInTheDocument();
    expect(screen.queryByText("Activate")).not.toBeInTheDocument();
  });

  it("calls onTransition callback after successful transition", async () => {
    mockTransitionCustomerLifecycle.mockResolvedValue({ success: true });
    const onTransition = vi.fn();
    const user = userEvent.setup();

    render(
      <LifecycleTransitionDropdown
        currentStatus="PROSPECT"
        customerId="c1"
        slug="acme"
        onTransition={onTransition}
      />,
    );

    await user.click(screen.getByText("Change Status"));
    await user.click(screen.getByText("Start Onboarding"));
    // Dialog opens — click confirm button
    await user.click(screen.getByRole("button", { name: "Start Onboarding" }));

    await waitFor(() => {
      expect(onTransition).toHaveBeenCalled();
    });
  });
});
