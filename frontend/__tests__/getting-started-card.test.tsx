import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

vi.mock("server-only", () => ({}));

vi.mock("@/hooks/use-onboarding-progress", () => ({
  useOnboardingProgress: vi.fn(),
}));

import { useOnboardingProgress } from "@/hooks/use-onboarding-progress";
import { GettingStartedCard } from "@/components/dashboard/getting-started-card";

const mockHook = vi.mocked(useOnboardingProgress);

// No matter yet (CREATE_PROJECT open) — the card shows until the firm's first matter exists.
const defaultSteps = [
  { code: "CREATE_PROJECT", completed: false },
  { code: "ADD_CUSTOMER", completed: true },
  { code: "INVITE_MEMBER", completed: true },
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

  it("hides once the first matter exists (CREATE_PROJECT step complete)", () => {
    mockProgress({
      steps: defaultSteps.map((s) => (s.code === "CREATE_PROJECT" ? { ...s, completed: true } : s)),
      completedCount: 3,
    });
    render(<GettingStartedCard />);

    expect(screen.queryByTestId("getting-started-banner")).not.toBeInTheDocument();
  });

  it("shows until the first matter exists, even with other steps complete", () => {
    mockProgress({
      steps: defaultSteps.map((s) => (s.code === "CREATE_PROJECT" ? s : { ...s, completed: true })),
      completedCount: 5,
    });
    render(<GettingStartedCard />);

    expect(screen.getByTestId("getting-started-banner")).toBeInTheDocument();
  });
});
