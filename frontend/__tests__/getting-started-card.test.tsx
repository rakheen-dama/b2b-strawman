import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

vi.mock("server-only", () => ({}));

vi.mock("@/hooks/use-onboarding-progress", () => ({
  useOnboardingProgress: vi.fn(),
}));

import { useOnboardingProgress } from "@/hooks/use-onboarding-progress";
import { GettingStartedCard } from "@/components/dashboard/getting-started-card";

const mockHook = vi.mocked(useOnboardingProgress);

const defaultSteps = [
  { code: "CREATE_PROJECT", completed: true },
  { code: "ADD_CUSTOMER", completed: true },
  { code: "INVITE_MEMBER", completed: false },
  { code: "LOG_TIME", completed: false },
  { code: "SETUP_RATES", completed: false },
  { code: "CREATE_INVOICE", completed: false },
];

function mockProgress(overrides: Partial<ReturnType<typeof useOnboardingProgress>> = {}) {
  mockHook.mockReturnValue({
    steps: defaultSteps,
    completedCount: 2,
    totalCount: 6,
    percentComplete: 33,
    allComplete: false,
    dismissed: false,
    loading: false,
    dismiss: vi.fn(),
    ...overrides,
  });
}

describe("GettingStartedCard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders progress bar with correct percentage", () => {
    mockProgress();
    render(<GettingStartedCard />);

    expect(screen.getByText("Getting started with DocTeams")).toBeInTheDocument();
    expect(screen.getByText("2 of 6 complete")).toBeInTheDocument();
    expect(screen.getByText("33%")).toBeInTheDocument();

    // Verify progress indicator renders with correct transform
    const progressBar = screen.getByRole("progressbar");
    expect(progressBar).toBeInTheDocument();
    const indicator = progressBar.querySelector("[data-slot='progress-indicator']");
    expect(indicator).not.toBeNull();
    expect(indicator).toHaveStyle({ transform: "translateX(-67%)" });
  });

  it("renders completed steps with teal check icon", () => {
    mockProgress();
    render(<GettingStartedCard />);

    expect(screen.getByText("Create your first project")).toBeInTheDocument();
    expect(screen.getByText("Add a customer")).toBeInTheDocument();

    // Completed steps have CheckCircle2 icons (teal)
    const listItems = screen.getAllByRole("listitem");
    const completedItems = listItems.slice(0, 2);
    for (const item of completedItems) {
      const svg = item.querySelector("svg");
      expect(svg).not.toBeNull();
      expect(svg?.classList.contains("text-teal-500")).toBe(true);
    }
  });

  it("renders incomplete steps with slate circle icon", () => {
    mockProgress();
    render(<GettingStartedCard />);

    expect(screen.getByText("Invite a team member")).toBeInTheDocument();
    expect(screen.getByText("Log time on a task")).toBeInTheDocument();

    // Incomplete steps have Circle icons (slate)
    const listItems = screen.getAllByRole("listitem");
    const incompleteItems = listItems.slice(2);
    for (const item of incompleteItems) {
      const svg = item.querySelector("svg");
      expect(svg).not.toBeNull();
      expect(svg?.classList.contains("text-slate-300")).toBe(true);
    }
  });

  it("hides when dismissed is true", () => {
    mockProgress({ dismissed: true });
    const { container } = render(<GettingStartedCard />);

    expect(container.firstChild).toBeNull();
    expect(screen.queryByText("Getting started with DocTeams")).not.toBeInTheDocument();
  });

  it("hides when all steps are complete", () => {
    mockProgress({
      allComplete: true,
      completedCount: 6,
      totalCount: 6,
      percentComplete: 100,
      steps: defaultSteps.map((s) => ({ ...s, completed: true })),
    });
    const { container } = render(<GettingStartedCard />);

    expect(container.firstChild).toBeNull();
    expect(screen.queryByText("Getting started with DocTeams")).not.toBeInTheDocument();
  });
});
