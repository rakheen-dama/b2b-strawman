import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, act } from "@testing-library/react";
import { PayFastResultPoller } from "./payfast-result-poller";

// Mock next/navigation
const mockRefresh = vi.fn();
let mockSearchParams = new URLSearchParams();

vi.mock("next/navigation", () => ({
  useSearchParams: () => mockSearchParams,
  useRouter: () => ({ refresh: mockRefresh }),
}));

// Mock getSubscription
const mockGetSubscription = vi.fn();
vi.mock("@/app/(app)/org/[slug]/settings/billing/actions", () => ({
  getSubscription: (...args: unknown[]) => mockGetSubscription(...args),
}));

beforeEach(() => {
  vi.useFakeTimers();
  mockRefresh.mockReset();
  mockGetSubscription.mockReset();
  mockSearchParams = new URLSearchParams();
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

describe("PayFastResultPoller", () => {
  it("renders nothing when result is absent (idle state)", () => {
    mockSearchParams = new URLSearchParams();
    const { container } = render(<PayFastResultPoller initialStatus="TRIALING" />);
    expect(container.innerHTML).toBe("");
  });

  it("renders nothing when result is an unrecognized value", () => {
    mockSearchParams = new URLSearchParams("result=unknown");
    const { container } = render(<PayFastResultPoller initialStatus="TRIALING" />);
    expect(container.innerHTML).toBe("");
  });

  it("shows cancelled message when result=cancelled", () => {
    mockSearchParams = new URLSearchParams("result=cancelled");
    render(<PayFastResultPoller initialStatus="TRIALING" />);
    expect(
      screen.getByText("Payment was cancelled. You can try again when you are ready.")
    ).toBeInTheDocument();
  });

  it("shows success immediately when result=success and already ACTIVE", () => {
    mockSearchParams = new URLSearchParams("result=success");
    render(<PayFastResultPoller initialStatus="ACTIVE" />);
    expect(
      screen.getByText("Payment successful! Your subscription is now active.")
    ).toBeInTheDocument();
    // Should NOT start polling
    expect(mockGetSubscription).not.toHaveBeenCalled();
  });

  it("polls and transitions to success when getSubscription returns ACTIVE", async () => {
    mockSearchParams = new URLSearchParams("result=success");
    mockGetSubscription.mockResolvedValue({ status: "ACTIVE" });

    render(<PayFastResultPoller initialStatus="TRIALING" />);

    // Initially shows polling state
    expect(screen.getByText("Processing your payment...")).toBeInTheDocument();

    // Advance past the first poll interval
    await act(async () => {
      vi.advanceTimersByTime(2000);
    });
    // Let the resolved promise flush
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });

    expect(mockGetSubscription).toHaveBeenCalledTimes(1);
    expect(
      screen.getByText("Payment successful! Your subscription is now active.")
    ).toBeInTheDocument();
    expect(mockRefresh).toHaveBeenCalled();
  });

  it("transitions to timeout after 30s of polling", async () => {
    mockSearchParams = new URLSearchParams("result=success");
    mockGetSubscription.mockResolvedValue({ status: "TRIALING" });

    render(<PayFastResultPoller initialStatus="TRIALING" />);

    expect(screen.getByText("Processing your payment...")).toBeInTheDocument();

    // Advance time well past the 30s timeout. advanceTimersByTimeAsync
    // handles the recursive setTimeout + async callback chain correctly.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(35000);
    });

    // Should have transitioned to timeout
    expect(screen.getByText(/still being processed/)).toBeInTheDocument();

    // Verify at least some polls happened before timeout
    expect(mockGetSubscription.mock.calls.length).toBeGreaterThan(0);
  });

  it("continues polling when getSubscription throws", async () => {
    mockSearchParams = new URLSearchParams("result=success");
    mockGetSubscription
      .mockRejectedValueOnce(new Error("network error"))
      .mockResolvedValueOnce({ status: "ACTIVE" });

    render(<PayFastResultPoller initialStatus="TRIALING" />);

    // First poll - fails
    await act(async () => {
      vi.advanceTimersByTime(2000);
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });

    expect(mockGetSubscription).toHaveBeenCalledTimes(1);
    // Still polling
    expect(screen.getByText("Processing your payment...")).toBeInTheDocument();

    // Second poll - succeeds
    await act(async () => {
      vi.advanceTimersByTime(2000);
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });

    expect(mockGetSubscription).toHaveBeenCalledTimes(2);
    expect(
      screen.getByText("Payment successful! Your subscription is now active.")
    ).toBeInTheDocument();
  });
});
