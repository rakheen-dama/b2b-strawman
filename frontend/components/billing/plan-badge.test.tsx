import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { PlanBadgeDisplay, PlanBadge } from "./plan-badge";

const mockAuth = vi.fn();
vi.mock("@clerk/nextjs/server", () => ({
  auth: () => mockAuth(),
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
    const mockHas = vi.fn().mockReturnValue(true);
    mockAuth.mockResolvedValue({ has: mockHas });

    const jsx = await PlanBadge();
    render(jsx);

    expect(screen.getByText("Pro")).toBeInTheDocument();
    expect(mockHas).toHaveBeenCalledWith({ plan: "pro" });
  });

  it("renders 'Starter' when org does not have pro plan", async () => {
    mockAuth.mockResolvedValue({ has: vi.fn().mockReturnValue(false) });

    const jsx = await PlanBadge();
    render(jsx);

    expect(screen.getByText("Starter")).toBeInTheDocument();
  });

  it("renders 'Starter' when has is undefined", async () => {
    mockAuth.mockResolvedValue({ has: undefined });

    const jsx = await PlanBadge();
    render(jsx);

    expect(screen.getByText("Starter")).toBeInTheDocument();
  });
});
