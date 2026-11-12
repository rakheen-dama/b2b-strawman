import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SetupProgressCard } from "@/components/setup/setup-progress-card";
import type { SetupStep, ContextGroup } from "@/components/setup/types";
import { MiniProgressRing } from "@/components/dashboard/mini-progress-ring";

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
      />
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
      />
    );

    // Missing fields should not be visible initially
    expect(screen.queryByText("Billing Address")).not.toBeInTheDocument();

    // Click the context group to expand
    await user.click(screen.getByRole("button", { name: "Toggle Required Fields details" }));

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
});
