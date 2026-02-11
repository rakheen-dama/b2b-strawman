import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import BillingPage from "./page";
import type { BillingResponse } from "@/lib/internal-api";

const mockGet = vi.fn();
vi.mock("@/lib/api", () => ({
  api: { get: (...args: unknown[]) => mockGet(...args) },
}));

vi.mock("@/components/ui/badge", () => ({
  Badge: ({
    children,
    variant,
  }: {
    children: React.ReactNode;
    variant: string;
  }) => (
    <span data-testid="badge" data-variant={variant}>
      {children}
    </span>
  ),
}));

vi.mock("@/components/ui/progress", () => ({
  Progress: ({ value }: { value: number }) => (
    <div data-testid="progress" data-value={value} />
  ),
}));

vi.mock("@/components/billing/upgrade-button", () => ({
  UpgradeButton: ({ slug, className }: { slug: string; className?: string }) => (
    <button data-testid="upgrade-button" data-slug={slug} className={className}>
      Upgrade to Pro
    </button>
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
    limits: { maxMembers: 10, currentMembers: 5 },
  };
}

describe("BillingPage", () => {
  it("renders plan name, badge, and member usage for Starter org", async () => {
    mockGet.mockResolvedValue(starterBilling());

    const page = await BillingPage({ params: Promise.resolve({ slug: "acme" }) });
    render(page);

    expect(screen.getByText("Billing")).toBeInTheDocument();
    expect(screen.getByText("Starter Plan")).toBeInTheDocument();
    expect(screen.getByText("1 of 2")).toBeInTheDocument();

    const starterBadge = screen.getAllByTestId("badge").find(
      (el) => el.getAttribute("data-variant") === "starter" && el.textContent === "Starter"
    );
    expect(starterBadge).toBeInTheDocument();
  });

  it("renders back link to settings", async () => {
    mockGet.mockResolvedValue(starterBilling());

    const page = await BillingPage({ params: Promise.resolve({ slug: "acme" }) });
    render(page);

    const backLink = screen.getByRole("link", { name: /Settings/ });
    expect(backLink).toHaveAttribute("href", "/org/acme/settings");
  });

  it("shows pricing cards and upgrade buttons for Starter orgs", async () => {
    mockGet.mockResolvedValue(starterBilling());

    const page = await BillingPage({ params: Promise.resolve({ slug: "acme" }) });
    render(page);

    expect(screen.getByText("Compare Plans")).toBeInTheDocument();
    expect(screen.getByText("Free")).toBeInTheDocument();
    expect(screen.getByText("$29/month")).toBeInTheDocument();
    expect(screen.getByText("Most popular")).toBeInTheDocument();

    // Current plan badge on Starter card
    const currentPlanBadge = screen.getAllByTestId("badge").find(
      (el) => el.textContent === "Current plan"
    );
    expect(currentPlanBadge).toBeInTheDocument();

    // Upgrade buttons (one on Pro card, one in CTA section)
    const upgradeButtons = screen.getAllByTestId("upgrade-button");
    expect(upgradeButtons).toHaveLength(2);
    expect(upgradeButtons[0]).toHaveAttribute("data-slug", "acme");
  });

  it("shows upgrade CTA section for Starter orgs", async () => {
    mockGet.mockResolvedValue(starterBilling());

    const page = await BillingPage({ params: Promise.resolve({ slug: "acme" }) });
    render(page);

    expect(
      screen.getByText("Ready for dedicated infrastructure?")
    ).toBeInTheDocument();
    expect(
      screen.getByText(/schema isolation, more members/)
    ).toBeInTheDocument();
  });

  it("hides upgrade section, pricing cards, and CTA for Pro orgs", async () => {
    mockGet.mockResolvedValue(proBilling());

    const page = await BillingPage({ params: Promise.resolve({ slug: "acme" }) });
    render(page);

    expect(screen.getByText("Pro Plan")).toBeInTheDocument();
    expect(screen.getByText("5 of 10")).toBeInTheDocument();

    const proBadge = screen.getAllByTestId("badge").find(
      (el) => el.getAttribute("data-variant") === "pro" && el.textContent === "Pro"
    );
    expect(proBadge).toBeInTheDocument();

    expect(screen.queryByText("Compare Plans")).not.toBeInTheDocument();
    expect(screen.queryByTestId("upgrade-button")).not.toBeInTheDocument();
    expect(
      screen.queryByText("Ready for dedicated infrastructure?")
    ).not.toBeInTheDocument();
  });
});
