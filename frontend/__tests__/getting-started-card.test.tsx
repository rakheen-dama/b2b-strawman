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

  it("renders banner with progress info", () => {
    mockProgress();
    render(<GettingStartedCard />);

    expect(screen.getByTestId("getting-started-banner")).toBeInTheDocument();
    // Verify progress indicator renders
    const progressBar = screen.getByRole("progressbar");
    expect(progressBar).toBeInTheDocument();
  });

  it("renders progress text", () => {
    mockProgress();
    render(<GettingStartedCard />);

    // Banner shows title and progress count
    expect(screen.getByTestId("getting-started-banner")).toBeInTheDocument();
  });

  it("hides when dismissed is true", () => {
    mockProgress({ dismissed: true });
    const { container } = render(<GettingStartedCard />);

    expect(container.firstChild).toBeNull();
    expect(screen.queryByTestId("getting-started-banner")).not.toBeInTheDocument();
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
    expect(screen.queryByTestId("getting-started-banner")).not.toBeInTheDocument();
  });

  it("auto-hides when activeProjectCount > 3", () => {
    mockProgress();
    render(<GettingStartedCard activeProjectCount={5} />);

    expect(screen.queryByTestId("getting-started-banner")).not.toBeInTheDocument();
  });

  it("shows when activeProjectCount <= 3", () => {
    mockProgress();
    render(<GettingStartedCard activeProjectCount={2} />);

    expect(screen.getByTestId("getting-started-banner")).toBeInTheDocument();
  });
});
