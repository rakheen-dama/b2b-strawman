import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import BillingPage from "./page";
import type { BillingResponse } from "@/lib/internal-api";

const mockGet = vi.fn();
vi.mock("@/lib/api", () => ({
  api: { get: (...args: unknown[]) => mockGet(...args) },
}));

vi.mock("@/components/billing/plan-badge", () => ({
  PlanBadgeDisplay: ({ isPro }: { isPro: boolean }) => (
    <span data-testid="plan-badge">{isPro ? "Pro" : "Starter"}</span>
  ),
}));

vi.mock("@/components/ui/progress", () => ({
  Progress: ({ value }: { value: number }) => (
    <div data-testid="progress" data-value={value} />
  ),
}));

afterEach(() => cleanup());

function starterBilling(): BillingResponse {
  return {
    planSlug: "starter",
    tier: "STARTER",
    status: "ACTIVE",
    limits: { maxMembers: 2, currentMembers: 1 },
  };
}

function proBilling(): BillingResponse {
  return {
    planSlug: "pro",
    tier: "PRO",
    status: "ACTIVE",
    limits: { maxMembers: 25, currentMembers: 5 },
  };
}

describe("BillingPage", () => {
  it("renders plan name and member usage from API response", async () => {
    mockGet.mockResolvedValue(starterBilling());

    const page = await BillingPage({ params: Promise.resolve({ slug: "acme" }) });
    render(page);

    expect(screen.getByText("Billing")).toBeInTheDocument();
    expect(screen.getByText("Current Plan")).toBeInTheDocument();
    expect(screen.getByTestId("plan-badge")).toHaveTextContent("Starter");
    expect(screen.getByText("1 of 2")).toBeInTheDocument();
  });

  it("shows upgrade CTA for Starter orgs", async () => {
    mockGet.mockResolvedValue(starterBilling());

    const page = await BillingPage({ params: Promise.resolve({ slug: "acme" }) });
    render(page);

    expect(screen.getByText("Upgrade to Pro")).toBeInTheDocument();
    expect(screen.getByText(/Contact us/)).toBeInTheDocument();
    const mailto = screen.getByRole("link", { name: "sales@docteams.com" });
    expect(mailto).toHaveAttribute("href", "mailto:sales@docteams.com?subject=Upgrade acme to Pro");
  });

  it("shows Pro confirmation and hides upgrade CTA for Pro orgs", async () => {
    mockGet.mockResolvedValue(proBilling());

    const page = await BillingPage({ params: Promise.resolve({ slug: "acme" }) });
    render(page);

    expect(screen.getByTestId("plan-badge")).toHaveTextContent("Pro");
    expect(
      screen.getByText(/Pro plan with dedicated infrastructure/)
    ).toBeInTheDocument();
    expect(screen.getByText("5 of 25")).toBeInTheDocument();
    expect(screen.queryByText("Upgrade to Pro")).not.toBeInTheDocument();
  });
});
