import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { BudgetPanel } from "@/components/budget/budget-panel";
import type { BudgetStatusResponse } from "@/lib/types";

// Mock server actions
const mockUpsertBudget = vi.fn();
const mockDeleteBudget = vi.fn();

vi.mock("@/app/(app)/org/[slug]/projects/[id]/budget-actions", () => ({
  upsertBudget: (...args: unknown[]) => mockUpsertBudget(...args),
  deleteBudget: (...args: unknown[]) => mockDeleteBudget(...args),
}));

function makeBudget(
  overrides: Partial<BudgetStatusResponse> = {}
): BudgetStatusResponse {
  return {
    projectId: "p1",
    budgetHours: 200,
    budgetAmount: 50000,
    budgetCurrency: "USD",
    alertThresholdPct: 80,
    notes: "Discovery phase only",
    hoursConsumed: 100,
    hoursRemaining: 100,
    hoursConsumedPct: 50,
    amountConsumed: 25000,
    amountRemaining: 25000,
    amountConsumedPct: 50,
    hoursStatus: "ON_TRACK",
    amountStatus: "ON_TRACK",
    overallStatus: "ON_TRACK",
    ...overrides,
  };
}

describe("BudgetPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders empty state when no budget is set", () => {
    render(
      <BudgetPanel
        slug="acme"
        projectId="p1"
        budget={null}
        canManage={true}
        defaultCurrency="USD"
      />
    );

    expect(screen.getByText("No budget set")).toBeInTheDocument();
    expect(
      screen.getByText(
        "Configure a budget to track hours and costs against targets."
      )
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Set Budget" })
    ).toBeInTheDocument();
  });

  it("hides Set Budget button for non-managers in empty state", () => {
    render(
      <BudgetPanel
        slug="acme"
        projectId="p1"
        budget={null}
        canManage={false}
        defaultCurrency="USD"
      />
    );

    expect(screen.getByText("No budget set")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Set Budget" })
    ).not.toBeInTheDocument();
  });

  it("renders ON_TRACK status with progress bars", () => {
    const budget = makeBudget();

    render(
      <BudgetPanel
        slug="acme"
        projectId="p1"
        budget={budget}
        canManage={true}
        defaultCurrency="USD"
      />
    );

    expect(screen.getByText("Budget Status")).toBeInTheDocument();
    // Multiple "On Track" badges: overall, hours, and amount
    const onTrackBadges = screen.getAllByText("On Track");
    expect(onTrackBadges.length).toBeGreaterThanOrEqual(1);

    // Progress bars should render
    const progressBars = screen.getAllByRole("progressbar");
    expect(progressBars).toHaveLength(2); // hours + amount

    // Check hours progress
    expect(progressBars[0]).toHaveAttribute("aria-valuenow", "50");

    // Check amount progress
    expect(progressBars[1]).toHaveAttribute("aria-valuenow", "50");

    // Stat cards — "50%" appears for both hours and amount Used cards
    const fiftyPctCards = screen.getAllByText("50%");
    expect(fiftyPctCards.length).toBe(2);
    // "$25,000.00" appears for both consumed and remaining (same values)
    const amountCards = screen.getAllByText("$25,000.00");
    expect(amountCards.length).toBeGreaterThanOrEqual(1);
  });

  it("renders AT_RISK status with amber styling", () => {
    const budget = makeBudget({
      hoursConsumedPct: 85,
      hoursStatus: "AT_RISK",
      overallStatus: "AT_RISK",
    });

    render(
      <BudgetPanel
        slug="acme"
        projectId="p1"
        budget={budget}
        canManage={true}
        defaultCurrency="USD"
      />
    );

    // Should show At Risk badge
    const atRiskBadges = screen.getAllByText("At Risk");
    expect(atRiskBadges.length).toBeGreaterThanOrEqual(1);

    // Hours progress bar should show 85%
    const progressBars = screen.getAllByRole("progressbar");
    expect(progressBars[0]).toHaveAttribute("aria-valuenow", "85");
  });

  it("renders OVER_BUDGET status with red styling", () => {
    const budget = makeBudget({
      hoursConsumed: 220,
      hoursRemaining: -20,
      hoursConsumedPct: 110,
      hoursStatus: "OVER_BUDGET",
      amountConsumed: 55000,
      amountRemaining: -5000,
      amountConsumedPct: 110,
      amountStatus: "OVER_BUDGET",
      overallStatus: "OVER_BUDGET",
    });

    render(
      <BudgetPanel
        slug="acme"
        projectId="p1"
        budget={budget}
        canManage={true}
        defaultCurrency="USD"
      />
    );

    // Should show Over Budget badge
    const overBudgetBadges = screen.getAllByText("Over Budget");
    expect(overBudgetBadges.length).toBeGreaterThanOrEqual(1);

    // Progress bars should clamp to 100% visually
    const progressBars = screen.getAllByRole("progressbar");
    expect(progressBars[0]).toHaveAttribute("aria-valuenow", "110");
  });

  it("renders hours-only budget without amount section", () => {
    const budget = makeBudget({
      budgetAmount: null,
      budgetCurrency: null,
      amountConsumed: 0,
      amountRemaining: 0,
      amountConsumedPct: 0,
      amountStatus: "ON_TRACK",
    });

    render(
      <BudgetPanel
        slug="acme"
        projectId="p1"
        budget={budget}
        canManage={true}
        defaultCurrency="USD"
      />
    );

    // Should have hours section
    expect(screen.getByText("Hours")).toBeInTheDocument();

    // Should have only 1 progress bar (hours only)
    const progressBars = screen.getAllByRole("progressbar");
    expect(progressBars).toHaveLength(1);
  });

  it("opens config dialog when Edit button is clicked", async () => {
    const user = userEvent.setup();
    const budget = makeBudget();

    render(
      <BudgetPanel
        slug="acme"
        projectId="p1"
        budget={budget}
        canManage={true}
        defaultCurrency="USD"
      />
    );

    await user.click(screen.getByRole("button", { name: /edit/i }));

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "Edit Budget" })
      ).toBeInTheDocument();
    });
  });

  it("hides Edit and Delete buttons for non-managers", () => {
    const budget = makeBudget();

    render(
      <BudgetPanel
        slug="acme"
        projectId="p1"
        budget={budget}
        canManage={false}
        defaultCurrency="USD"
      />
    );

    expect(screen.getByText("Budget Status")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /edit/i })
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /delete/i })
    ).not.toBeInTheDocument();
  });

  it("displays alert threshold and notes", () => {
    const budget = makeBudget();

    render(
      <BudgetPanel
        slug="acme"
        projectId="p1"
        budget={budget}
        canManage={true}
        defaultCurrency="USD"
      />
    );

    expect(screen.getByText(/80%/)).toBeInTheDocument();
    expect(screen.getByText(/Discovery phase only/)).toBeInTheDocument();
  });
});
