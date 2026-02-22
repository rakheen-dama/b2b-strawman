import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { PlanBadgeDisplay, PlanBadge } from "./plan-badge";

const mockHasPlan = vi.fn();
vi.mock("@/lib/auth", () => ({
  hasPlan: (...args: unknown[]) => mockHasPlan(...args),
}));

afterEach(() => cleanup());
beforeEach(() => vi.clearAllMocks());

describe("PlanBadgeDisplay", () => {
  it("renders 'Pro' for pro plan", () => {
    render(<PlanBadgeDisplay isPro={true} />);
    expect(screen.getByText("Pro")).toBeInTheDocument();
  });

  it("renders 'Starter' for starter plan", () => {
    render(<PlanBadgeDisplay isPro={false} />);
    expect(screen.getByText("Starter")).toBeInTheDocument();
  });
});

describe("PlanBadge", () => {
  it("renders 'Pro' when org has pro plan", async () => {
    mockHasPlan.mockResolvedValue(true);

    const jsx = await PlanBadge();
    render(jsx);

    expect(screen.getByText("Pro")).toBeInTheDocument();
    expect(mockHasPlan).toHaveBeenCalledWith("pro");
  });

  it("renders 'Starter' when org does not have pro plan", async () => {
    mockHasPlan.mockResolvedValue(false);

    const jsx = await PlanBadge();
    render(jsx);

    expect(screen.getByText("Starter")).toBeInTheDocument();
  });
});
