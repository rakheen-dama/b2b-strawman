import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { LifecycleTransitionMenu } from "./LifecycleTransitionMenu";
import type { Customer } from "@/lib/types";

const mockTransitionCustomer = vi.fn();

vi.mock("@/lib/actions/compliance", () => ({
  transitionCustomer: (...args: unknown[]) => mockTransitionCustomer(...args),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    refresh: vi.fn(),
    push: vi.fn(),
  }),
}));

function makeCustomer(overrides: Partial<Customer> = {}): Customer {
  return {
    id: "cust-1",
    name: "Test Customer",
    email: "test@example.com",
    phone: null,
    idNumber: null,
    status: "ACTIVE",
    lifecycleStatus: "ACTIVE",
    notes: null,
    createdBy: "user-1",
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

describe("LifecycleTransitionMenu", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("shows transition options for ACTIVE customer when admin", async () => {
    const user = userEvent.setup();
    const customer = makeCustomer({ lifecycleStatus: "ACTIVE" });

    render(
      <LifecycleTransitionMenu customer={customer} canManage={true} slug="acme" />,
    );

    await user.click(screen.getByRole("button", { name: /lifecycle transitions/i }));

    expect(screen.getByText("Mark Dormant")).toBeInTheDocument();
    expect(screen.getByText("Offboard")).toBeInTheDocument();
  });

  it("hides menu when canManage is false", () => {
    const customer = makeCustomer({ lifecycleStatus: "ACTIVE" });

    render(
      <LifecycleTransitionMenu customer={customer} canManage={false} slug="acme" />,
    );

    expect(screen.queryByRole("button", { name: /lifecycle transitions/i })).not.toBeInTheDocument();
  });

  it("calls transitionCustomer when a transition is clicked", async () => {
    mockTransitionCustomer.mockResolvedValue({ success: true });
    const user = userEvent.setup();
    const customer = makeCustomer({ lifecycleStatus: "ACTIVE" });

    render(
      <LifecycleTransitionMenu customer={customer} canManage={true} slug="acme" />,
    );

    await user.click(screen.getByRole("button", { name: /lifecycle transitions/i }));
    await user.click(screen.getByText("Mark Dormant"));

    await waitFor(() => {
      expect(mockTransitionCustomer).toHaveBeenCalledWith("acme", "cust-1", "DORMANT");
    });
  });
});
