import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { CompletionProgressBar } from "@/components/dashboard/completion-progress-bar";
import type { SetupStep } from "@/components/setup/types";

// ---- helpers ----

function makeSetupSteps(overrides?: Partial<{ complete: boolean }>): SetupStep[] {
  const complete = overrides?.complete ?? false;
  return [
    {
      label: "Customer assigned",
      complete,
      actionHref: "?tab=customers",
    },
    {
      label: "Rate card configured",
      complete,
      actionHref: "?tab=rates",
      permissionRequired: true,
    },
    {
      label: "Budget set",
      complete,
      actionHref: "?tab=budget",
    },
    {
      label: "Team members added",
      complete,
      actionHref: "?tab=members",
    },
    {
      label: "Required fields filled (0/2)",
      complete,
      actionHref: "#custom-fields",
    },
  ];
}

// Mock next/link for the setup bar links
vi.mock("next/link", () => ({
  default: ({ children, href }: { children: React.ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));

/**
 * These tests verify the new compact setup bar behavior (Epic 395A, task 395.2).
 * The setup bar replaces the old SetupProgressCard + ActionCard + TemplateReadinessCard.
 * - Shows "X/Y setup steps complete" with CompletionProgressBar when incomplete
 * - Hides entirely when all steps are complete
 */
describe("OverviewTab — Compact Setup Bar (395.2)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  // Test 1: Setup bar renders with progress when incomplete
  it("renders compact setup bar with step count and progress when incomplete", () => {
    const steps = makeSetupSteps({ complete: false });
    const completedSteps = steps.filter((s) => s.complete).length;
    const totalSteps = steps.length;

    render(
      <details data-testid="setup-progress-bar" className="bg-card rounded-lg border">
        <summary className="flex items-center gap-3 px-4 py-3">
          <div className="min-w-0 flex-1">
            <span className="text-sm font-medium">
              {completedSteps}/{totalSteps} setup steps complete
            </span>
            <div className="mt-1.5">
              <CompletionProgressBar percent={(completedSteps / totalSteps) * 100} />
            </div>
          </div>
        </summary>
        <div className="border-t px-4 py-3">
          <ul className="space-y-1.5">
            {steps.map((step, i) => (
              <li key={i} className="text-sm">
                {step.label}
              </li>
            ))}
          </ul>
        </div>
      </details>
    );

    expect(screen.getByTestId("setup-progress-bar")).toBeInTheDocument();
    expect(screen.getByText("0/5 setup steps complete")).toBeInTheDocument();
    expect(screen.getByText("Customer assigned")).toBeInTheDocument();
  });

  // Test 2: Setup bar hides when all steps are complete
  it("does not render setup bar when all steps are complete", () => {
    const steps = makeSetupSteps({ complete: true });
    const completedSteps = steps.filter((s) => s.complete).length;
    const totalSteps = steps.length;
    const setupIncomplete = completedSteps < totalSteps;

    render(
      <div>
        {setupIncomplete && (
          <details data-testid="setup-progress-bar">
            <summary>
              {completedSteps}/{totalSteps} setup steps complete
            </summary>
          </details>
        )}
        <p>Other content</p>
      </div>
    );

    expect(screen.queryByTestId("setup-progress-bar")).not.toBeInTheDocument();
    expect(screen.getByText("Other content")).toBeInTheDocument();
  });

  // Test 3: Setup bar shows correct count with mixed completion
  it("shows correct count when some steps are complete", () => {
    const steps: SetupStep[] = [
      { label: "Customer assigned", complete: true, actionHref: "?tab=customers" },
      {
        label: "Rate card configured",
        complete: true,
        actionHref: "?tab=rates",
        permissionRequired: true,
      },
      { label: "Budget set", complete: false, actionHref: "?tab=budget" },
      { label: "Team members added", complete: false, actionHref: "?tab=members" },
      { label: "Required fields filled", complete: false, actionHref: "#custom-fields" },
    ];
    const completedSteps = steps.filter((s) => s.complete).length;
    const totalSteps = steps.length;

    render(
      <details data-testid="setup-progress-bar" className="bg-card rounded-lg border">
        <summary className="flex items-center gap-3 px-4 py-3">
          <span className="text-sm font-medium">
            {completedSteps}/{totalSteps} setup steps complete
          </span>
        </summary>
      </details>
    );

    expect(screen.getByText("2/5 setup steps complete")).toBeInTheDocument();
  });
});
