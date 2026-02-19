import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SetupProgressCard } from "@/components/setup/setup-progress-card";
import type { SetupStep } from "@/components/setup/types";

function makeSteps(overrides: Partial<SetupStep>[] = []): SetupStep[] {
  const defaults: SetupStep[] = [
    { label: "Assign customer", complete: true, actionHref: "/customers" },
    { label: "Configure rate card", complete: false, actionHref: "/rates" },
    {
      label: "Set budget",
      complete: false,
      actionHref: "/budget",
      permissionRequired: true,
    },
    { label: "Assign team", complete: false },
  ];
  return defaults.map((step, i) => ({ ...step, ...overrides[i] }));
}

describe("SetupProgressCard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders incomplete state with progress bar", () => {
    render(
      <SetupProgressCard
        title="Project Setup"
        completionPercentage={25}
        overallComplete={false}
        steps={makeSteps()}
      />,
    );

    expect(screen.getByText("Project Setup")).toBeInTheDocument();
    expect(screen.getByText("25%")).toBeInTheDocument();
    expect(screen.getByRole("progressbar")).toBeInTheDocument();
    expect(screen.getByText("Assign customer")).toBeInTheDocument();
    expect(screen.getByText("Configure rate card")).toBeInTheDocument();
  });

  it("renders complete state with collapsed badge", () => {
    render(
      <SetupProgressCard
        title="Project Setup"
        completionPercentage={100}
        overallComplete={true}
        steps={makeSteps([
          { complete: true },
          { complete: true },
          { complete: true },
          { complete: true },
        ])}
      />,
    );

    expect(screen.getByText("Setup complete")).toBeInTheDocument();
    // Steps should be hidden (collapsed by default when complete)
    expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();
  });

  it("expands collapsed complete card on toggle click", async () => {
    const user = userEvent.setup();

    render(
      <SetupProgressCard
        title="Project Setup"
        completionPercentage={100}
        overallComplete={true}
        steps={makeSteps([
          { complete: true },
          { complete: true },
          { complete: true },
          { complete: true },
        ])}
      />,
    );

    // Collapsed — no progress bar
    expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();

    // Click expand
    await user.click(
      screen.getByRole("button", { name: "Expand setup steps" }),
    );

    // Now should show progress bar and steps
    expect(screen.getByRole("progressbar")).toBeInTheDocument();
    expect(screen.getByText("Assign customer")).toBeInTheDocument();
  });

  it("renders action links for steps with actionHref", () => {
    render(
      <SetupProgressCard
        title="Project Setup"
        completionPercentage={25}
        overallComplete={false}
        steps={makeSteps()}
        canManage={true}
      />,
    );

    // Steps with actionHref should have links
    const links = screen.getAllByRole("link");
    expect(links.length).toBeGreaterThanOrEqual(3); // customer, rates, budget

    // Step without actionHref ("Assign team") should NOT have a link
    const assignTeamItem = screen.getByText("Assign team");
    expect(assignTeamItem.closest("li")?.querySelector("a")).toBeNull();
  });

  it("hides permission-gated action link when canManage is false", () => {
    render(
      <SetupProgressCard
        title="Project Setup"
        completionPercentage={25}
        overallComplete={false}
        steps={makeSteps()}
        canManage={false}
      />,
    );

    // "Set budget" has permissionRequired=true and canManage=false → no link
    const budgetItem = screen.getByText("Set budget");
    expect(budgetItem.closest("li")?.querySelector("a")).toBeNull();

    // "Assign customer" has no permissionRequired → link should still show
    const customerItem = screen.getByText("Assign customer");
    expect(customerItem.closest("li")?.querySelector("a")).not.toBeNull();
  });
});
