import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { OnboardingPipelineSection } from "@/components/compliance/OnboardingPipelineSection";

describe("OnboardingPipelineSection", () => {
  afterEach(() => cleanup());

  it("shows empty state when no customers", () => {
    render(<OnboardingPipelineSection customers={[]} orgSlug="test-org" />);
    expect(screen.getByText("No customers currently in onboarding")).toBeInTheDocument();
  });

  it("renders customer names with links", () => {
    const customers = [
      {
        id: "c1",
        name: "Onboarding Corp",
        lifecycleStatusChangedAt: new Date(Date.now() - 5 * 86400000).toISOString(),
        checklistProgress: { completed: 2, total: 5 },
      },
    ];
    render(<OnboardingPipelineSection customers={customers} orgSlug="test-org" />);
    expect(screen.getByText("Onboarding Corp")).toBeInTheDocument();
    const link = screen.getByRole("link", { name: "Onboarding Corp" });
    expect(link).toHaveAttribute("href", "/org/test-org/customers/c1");
  });

  it("displays duration in days", () => {
    const customers = [
      {
        id: "c1",
        name: "Duration Corp",
        lifecycleStatusChangedAt: new Date(Date.now() - 10 * 86400000).toISOString(),
        checklistProgress: { completed: 0, total: 0 },
      },
    ];
    render(<OnboardingPipelineSection customers={customers} orgSlug="test-org" />);
    expect(screen.getByText(/In onboarding 10 days/)).toBeInTheDocument();
  });
});
