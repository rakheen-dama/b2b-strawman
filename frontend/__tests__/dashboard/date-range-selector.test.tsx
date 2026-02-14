import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, fireEvent } from "@testing-library/react";
import { DateRangeSelector } from "@/components/dashboard/date-range-selector";

const mockPush = vi.fn();
const mockRefresh = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: mockPush,
    replace: vi.fn(),
    refresh: mockRefresh,
    back: vi.fn(),
    forward: vi.fn(),
    prefetch: vi.fn(),
  }),
  usePathname: () => "/org/acme/dashboard",
}));

describe("DateRangeSelector", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  const defaultValue = {
    from: new Date(2026, 0, 1),
    to: new Date(2026, 0, 31),
  };

  it("renders preset buttons", () => {
    const onChange = vi.fn();
    render(<DateRangeSelector value={defaultValue} onChange={onChange} />);

    expect(screen.getByText("This Week")).toBeInTheDocument();
    expect(screen.getByText("This Month")).toBeInTheDocument();
    expect(screen.getByText("Last 30 Days")).toBeInTheDocument();
    expect(screen.getByText("This Quarter")).toBeInTheDocument();
    expect(screen.getByText("Custom")).toBeInTheDocument();
  });

  it("selecting 'This Month' calls onChange with correct dates", () => {
    const onChange = vi.fn();
    render(<DateRangeSelector value={defaultValue} onChange={onChange} />);

    fireEvent.click(screen.getByText("This Month"));

    expect(onChange).toHaveBeenCalledTimes(1);
    const [range] = onChange.mock.calls[0];

    // Should be first day and last day of current month
    const now = new Date();
    expect(range.from.getFullYear()).toBe(now.getFullYear());
    expect(range.from.getMonth()).toBe(now.getMonth());
    expect(range.from.getDate()).toBe(1);

    // Last day of month
    const lastDay = new Date(now.getFullYear(), now.getMonth() + 1, 0);
    expect(range.to.getDate()).toBe(lastDay.getDate());
  });

  it("renders refresh button", () => {
    const onChange = vi.fn();
    render(<DateRangeSelector value={defaultValue} onChange={onChange} />);

    expect(screen.getByLabelText("Refresh data")).toBeInTheDocument();
  });

  it("refresh button calls router.refresh()", () => {
    const onChange = vi.fn();
    render(<DateRangeSelector value={defaultValue} onChange={onChange} />);

    fireEvent.click(screen.getByLabelText("Refresh data"));

    expect(mockRefresh).toHaveBeenCalledTimes(1);
  });
});
