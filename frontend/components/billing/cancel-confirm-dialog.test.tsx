import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CancelConfirmDialog } from "./cancel-confirm-dialog";

const mockCancelSubscription = vi.fn();

vi.mock("@/app/(app)/org/[slug]/settings/billing/actions", () => ({
  cancelSubscription: (...args: unknown[]) => mockCancelSubscription(...args),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
  usePathname: () => "/org/acme/settings/billing",
}));

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(() => {
  cleanup();
});

describe("CancelConfirmDialog", () => {
  it("shows confirmation text including the formatted currentPeriodEnd date", async () => {
    const user = userEvent.setup();
    render(<CancelConfirmDialog currentPeriodEnd="2026-05-01T00:00:00Z" />);

    await user.click(screen.getByRole("button", { name: /cancel subscription/i }));

    expect(screen.getByText(/will remain active until/)).toBeInTheDocument();
    // The date is formatted as en-ZA long format: "01 May 2026"
    expect(screen.getByText(/May 2026/)).toBeInTheDocument();
  });

  it("calls cancelSubscription action when confirm button is clicked", async () => {
    mockCancelSubscription.mockResolvedValue({
      status: "PENDING_CANCELLATION",
      trialEndsAt: null,
      currentPeriodEnd: "2026-05-01T00:00:00Z",
      graceEndsAt: null,
      nextBillingAt: null,
      monthlyAmountCents: 49900,
      currency: "ZAR",
      limits: { maxMembers: 10, currentMembers: 3 },
      canSubscribe: true,
      canCancel: false,
    });

    const user = userEvent.setup();
    render(<CancelConfirmDialog currentPeriodEnd="2026-05-01T00:00:00Z" />);

    await user.click(screen.getByRole("button", { name: /cancel subscription/i }));
    await user.click(screen.getByRole("button", { name: /confirm cancellation/i }));

    await waitFor(() => {
      expect(mockCancelSubscription).toHaveBeenCalledOnce();
    });
  });

  it("shows loading state during async call", async () => {
    mockCancelSubscription.mockImplementation(
      () =>
        new Promise((resolve) => setTimeout(() => resolve({ status: "PENDING_CANCELLATION" }), 500))
    );

    const user = userEvent.setup();
    render(<CancelConfirmDialog currentPeriodEnd="2026-05-01T00:00:00Z" />);

    await user.click(screen.getByRole("button", { name: /cancel subscription/i }));
    await user.click(screen.getByRole("button", { name: /confirm cancellation/i }));

    expect(screen.getByText("Cancelling...")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /keep subscription/i })).toBeDisabled();
  });
});
