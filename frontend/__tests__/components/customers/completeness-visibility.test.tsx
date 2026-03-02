import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SetupProgressCard } from "@/components/setup/setup-progress-card";
import type { SetupStep, ContextGroup } from "@/components/setup/types";
import { MiniProgressRing } from "@/components/dashboard/mini-progress-ring";
import { IncompleteProfilesWidget } from "@/components/dashboard/incomplete-profiles-widget";
import type { AggregatedCompletenessResponse } from "@/lib/types";

// Mock next/navigation for IncompleteProfilesWidget
const mockPush = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush }),
  usePathname: () => "/org/test-org/dashboard",
}));

function makeSteps(): SetupStep[] {
  return [
    { label: "Projects linked", complete: true, actionHref: "?tab=projects" },
    { label: "Onboarding checklist (2/3)", complete: false },
    { label: "Required fields filled (2/4)", complete: false, actionHref: "#custom-fields" },
  ];
}

function makeContextGroups(): ContextGroup[] {
  return [
    {
      contextLabel: "Required Fields",
      filled: 2,
      total: 4,
      fields: [
        { name: "Company Name", slug: "company-name", filled: true },
        { name: "VAT Number", slug: "vat-number", filled: true },
        { name: "Billing Address", slug: "billing-address", filled: false },
        { name: "Tax Reference", slug: "tax-reference", filled: false },
      ],
    },
  ];
}

function makeAggregatedData(): AggregatedCompletenessResponse {
  return {
    incompleteCount: 5,
    totalCount: 12,
    topMissingFields: [
      { fieldName: "Billing Address", fieldSlug: "billing-address", customerCount: 5 },
      { fieldName: "VAT Number", fieldSlug: "vat-number", customerCount: 3 },
    ],
  };
}

describe("Completeness Visibility (Epic 251B)", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("setupProgressCard shows context-grouped display", () => {
    render(
      <SetupProgressCard
        title="Customer Readiness"
        completionPercentage={33}
        overallComplete={false}
        steps={makeSteps()}
        contextGroups={makeContextGroups()}
      />,
    );

    expect(screen.getByText("Field Completeness")).toBeInTheDocument();
    expect(screen.getByText(/Required Fields: 2\/4 fields/)).toBeInTheDocument();
  });

  it("setupProgressCard expandable group shows missing fields on click", async () => {
    const user = userEvent.setup();

    render(
      <SetupProgressCard
        title="Customer Readiness"
        completionPercentage={33}
        overallComplete={false}
        steps={makeSteps()}
        contextGroups={makeContextGroups()}
      />,
    );

    // Missing fields should not be visible initially
    expect(screen.queryByText("Billing Address")).not.toBeInTheDocument();

    // Click the context group to expand
    await user.click(
      screen.getByRole("button", { name: "Toggle Required Fields details" }),
    );

    // Missing fields should now be visible
    expect(screen.getByText("Billing Address")).toBeInTheDocument();
    expect(screen.getByText("Tax Reference")).toBeInTheDocument();
    // Filled fields should NOT appear in the expanded list
    expect(screen.queryByText("Company Name")).not.toBeInTheDocument();
  });

  it("completeness ring shows percentage via aria-label", () => {
    render(<MiniProgressRing value={75} size={48} />);

    expect(screen.getByLabelText("75%")).toBeInTheDocument();
  });

  it("dashboard widget shows incomplete count", () => {
    render(
      <IncompleteProfilesWidget data={makeAggregatedData()} orgSlug="test-org" />,
    );

    expect(screen.getByText("5")).toBeInTheDocument();
    expect(screen.getByText("12")).toBeInTheDocument();
    expect(screen.getByText(/customers have incomplete profiles/)).toBeInTheDocument();
    expect(screen.getByText("Billing Address")).toBeInTheDocument();
    expect(screen.getByText(/5 customers/)).toBeInTheDocument();
  });

  it("dashboard widget click navigates to filtered customer list", async () => {
    const user = userEvent.setup();

    render(
      <IncompleteProfilesWidget data={makeAggregatedData()} orgSlug="test-org" />,
    );

    // Click a missing field row
    const billingRow = screen.getByText("Billing Address").closest("button");
    expect(billingRow).not.toBeNull();
    await user.click(billingRow!);

    expect(mockPush).toHaveBeenCalledWith(
      "/org/test-org/customers?showIncomplete=true",
    );
  });
});
