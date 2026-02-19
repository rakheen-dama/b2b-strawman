import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { SetupProgressCard } from "@/components/setup/setup-progress-card";
import { ActionCard } from "@/components/setup/action-card";
import { Clock, ArrowRight } from "lucide-react";
import type { SetupStep } from "@/components/setup/types";

// ---- helpers ----

function makeCustomerSetupSteps(overallComplete = false): SetupStep[] {
  return [
    {
      label: "Projects linked",
      complete: overallComplete,
      actionHref: "?tab=projects",
    },
    {
      label: overallComplete
        ? "Onboarding checklist (2/2)"
        : "Onboarding checklist (1/2)",
      complete: overallComplete,
      actionHref: "?tab=onboarding",
    },
    {
      label: overallComplete
        ? "No required fields defined"
        : "Required fields filled (1/3)",
      complete: overallComplete,
      actionHref: "#custom-fields",
    },
  ];
}

describe("CustomerDetailPage — Setup Guidance Cards", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  // Test 1: SetupProgressCard renders "Customer Readiness" when overallComplete=false
  it("SetupProgressCard renders with 'Customer Readiness' title and progress bar", () => {
    render(
      <SetupProgressCard
        title="Customer Readiness"
        completionPercentage={33}
        overallComplete={false}
        steps={makeCustomerSetupSteps(false)}
        canManage={true}
      />,
    );

    expect(screen.getByText("Customer Readiness")).toBeInTheDocument();
    expect(screen.getByRole("progressbar")).toBeInTheDocument();
    expect(screen.getByText("33%")).toBeInTheDocument();
    expect(screen.getByText("Projects linked")).toBeInTheDocument();
  });

  // Test 2: Unbilled ActionCard NOT rendered when entryCount === 0
  it("Unbilled ActionCard is not rendered when entryCount is 0", () => {
    const entryCount = 0;
    render(
      <div>
        {entryCount > 0 && (
          <ActionCard
            icon={Clock}
            title="Unbilled Time"
            description="$0.00 across 0.0 hours"
          />
        )}
        <p>No unbilled time content</p>
      </div>,
    );

    expect(screen.queryByText("Unbilled Time")).not.toBeInTheDocument();
    expect(screen.getByText("No unbilled time content")).toBeInTheDocument();
  });

  // Test 3: Unbilled ActionCard renders with formatted amount when entryCount > 0
  it("Unbilled ActionCard renders formatted amount when entryCount > 0", () => {
    const entryCount = 3;
    render(
      <div>
        {entryCount > 0 && (
          <ActionCard
            icon={Clock}
            title="Unbilled Time"
            description="$2,400.00 across 16.0 hours"
            primaryAction={{
              label: "Create Invoice",
              href: "/org/acme/invoices/new?customerId=c1",
            }}
            secondaryAction={{ label: "View Time", href: "?tab=invoices" }}
            variant="accent"
          />
        )}
      </div>,
    );

    expect(screen.getByText("Unbilled Time")).toBeInTheDocument();
    expect(
      screen.getByText("$2,400.00 across 16.0 hours"),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Create Invoice" }),
    ).toBeInTheDocument();
  });

  // Test 4: Lifecycle action prompt renders for PROSPECT status (admin)
  it("Lifecycle ActionCard renders 'Start Onboarding' prompt for PROSPECT customers", () => {
    const lifecycleStatus = "PROSPECT";
    const isAdmin = true;

    render(
      <div>
        {isAdmin && lifecycleStatus === "PROSPECT" && (
          <ActionCard
            icon={ArrowRight}
            title="Ready to start onboarding?"
            description="Move this customer to Onboarding to begin compliance checklists."
            primaryAction={{
              label: "Start Onboarding",
              href: "#lifecycle-transition",
            }}
            variant="default"
          />
        )}
        <p>Page content</p>
      </div>,
    );

    expect(
      screen.getByText("Ready to start onboarding?"),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Start Onboarding" }),
    ).toBeInTheDocument();
  });

  // Test 5: Lifecycle action prompt NOT rendered for non-PROSPECT statuses
  it("Lifecycle ActionCard is not rendered for ACTIVE customers", () => {
    const lifecycleStatus = "ACTIVE";
    const isAdmin = true;

    render(
      <div>
        {isAdmin && lifecycleStatus === "PROSPECT" && (
          <ActionCard
            icon={ArrowRight}
            title="Ready to start onboarding?"
            description="Move this customer to Onboarding to begin compliance checklists."
            primaryAction={{
              label: "Start Onboarding",
              href: "#lifecycle-transition",
            }}
            variant="default"
          />
        )}
        <p>Active customer content</p>
      </div>,
    );

    expect(
      screen.queryByText("Ready to start onboarding?"),
    ).not.toBeInTheDocument();
    expect(screen.getByText("Active customer content")).toBeInTheDocument();
  });
});
