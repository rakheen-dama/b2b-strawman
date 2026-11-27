import { describe, it, expect, afterEach, vi } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import { OnboardingPipelineSection } from "@/components/compliance/OnboardingPipelineSection";

// The table is the kit DataTable, which calls useRouter().
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));

function customer(id: string, name: string, daysAgo: number, completed = 0, total = 0) {
  return {
    id,
    name,
    lifecycleStatusChangedAt: new Date(Date.now() - daysAgo * 86400000).toISOString(),
    checklistProgress: { completed, total },
  };
}

describe("OnboardingPipelineSection", () => {
  afterEach(() => cleanup());

  it("shows empty state when no customers", () => {
    render(<OnboardingPipelineSection customers={[]} orgSlug="test-org" />);
    expect(screen.getByText("No clients currently in onboarding")).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    // The section heading is the only heading (the empty line is a <p>).
    expect(screen.getAllByRole("heading").map((h) => h.textContent)).toEqual([
      "Onboarding Pipeline",
    ]);
  });

  it("renders customer names with links", () => {
    const customers = [customer("c1", "Onboarding Corp", 5, 2, 5)];
    render(<OnboardingPipelineSection customers={customers} orgSlug="test-org" />);
    expect(screen.getByText("Onboarding Corp")).toBeInTheDocument();
    const link = screen.getByRole("link", { name: "Onboarding Corp" });
    expect(link).toHaveAttribute("href", "/org/test-org/clients/c1");
  });

  it("displays duration in days", () => {
    const customers = [customer("c1", "Duration Corp", 10)];
    render(<OnboardingPipelineSection customers={customers} orgSlug="test-org" />);
    expect(screen.getByText(/In onboarding 10 days/)).toBeInTheDocument();
  });

  it("renders one column header per label and the checklist progress", () => {
    const customers = [customer("c1", "Progress Corp", 3, 2, 5)];
    render(<OnboardingPipelineSection customers={customers} orgSlug="test-org" />);
    expect(screen.getAllByRole("columnheader").map((h) => h.textContent)).toEqual([
      "Client",
      "Duration",
      "Progress",
    ]);
    expect(screen.getByText("2/5 items")).toBeInTheDocument();
  });

  it("lists the longest-onboarding client first", () => {
    const customers = [customer("c1", "Recent Corp", 1), customer("c2", "Long Corp", 20)];
    render(<OnboardingPipelineSection customers={customers} orgSlug="test-org" />);
    const rows = within(screen.getByRole("table")).getAllByRole("row").slice(1);
    expect(rows.map((r) => within(r).getByRole("link").textContent)).toEqual([
      "Long Corp",
      "Recent Corp",
    ]);
  });
});
