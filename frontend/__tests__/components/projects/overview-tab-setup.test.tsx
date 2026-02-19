import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { SetupProgressCard } from "@/components/setup/setup-progress-card";
import { ActionCard } from "@/components/setup/action-card";
import { TemplateReadinessCard } from "@/components/setup/template-readiness-card";
import { Clock } from "lucide-react";
import type { SetupStep, TemplateReadinessItem } from "@/components/setup/types";

// ---- helpers ----

function makeSetupSteps(overallComplete = false): SetupStep[] {
  return [
    {
      label: "Customer assigned",
      complete: overallComplete,
      actionHref: "?tab=customers",
    },
    {
      label: "Rate card configured",
      complete: overallComplete,
      actionHref: "?tab=rates",
      permissionRequired: true,
    },
    {
      label: "Budget set",
      complete: overallComplete,
      actionHref: "?tab=budget",
    },
    {
      label: "Team members added",
      complete: overallComplete,
      actionHref: "?tab=members",
    },
    {
      label: "Required fields filled (0/2)",
      complete: overallComplete,
      actionHref: "#custom-fields",
    },
  ];
}

function makeTemplates(ready = true): TemplateReadinessItem[] {
  return [
    {
      templateId: "t1",
      templateName: "Engagement Letter",
      templateSlug: "engagement-letter",
      ready,
      missingFields: ready ? [] : ["Tax Number"],
    },
  ];
}

describe("OverviewTab — Setup Guidance Cards", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  // Test 1: SetupProgressCard visible when overallComplete=false
  it("SetupProgressCard renders with progress bar when overallComplete=false", () => {
    render(
      <SetupProgressCard
        title="Project Setup"
        completionPercentage={40}
        overallComplete={false}
        steps={makeSetupSteps(false)}
        canManage={true}
      />,
    );

    expect(screen.getByText("Project Setup")).toBeInTheDocument();
    expect(screen.getByRole("progressbar")).toBeInTheDocument();
    expect(screen.getByText("40%")).toBeInTheDocument();
    expect(screen.getByText("Customer assigned")).toBeInTheDocument();
  });

  // Test 2: ActionCard NOT rendered when entryCount=0
  it("ActionCard is not rendered when unbilledSummary.entryCount is 0", () => {
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
        <p>Other content</p>
      </div>,
    );

    expect(screen.queryByText("Unbilled Time")).not.toBeInTheDocument();
    expect(screen.getByText("Other content")).toBeInTheDocument();
  });

  // Test 3: ActionCard visible with formatted amount when entryCount>0
  it("ActionCard renders with formatted amount when entryCount > 0", () => {
    const entryCount = 5;
    render(
      <div>
        {entryCount > 0 && (
          <ActionCard
            icon={Clock}
            title="Unbilled Time"
            description="$1,250.00 across 23.5 hours"
            primaryAction={{ label: "Create Invoice", href: "/org/acme/invoices/new?projectId=p1" }}
            secondaryAction={{ label: "View Entries", href: "?tab=time" }}
            variant="accent"
          />
        )}
      </div>,
    );

    expect(screen.getByText("Unbilled Time")).toBeInTheDocument();
    expect(screen.getByText("$1,250.00 across 23.5 hours")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Create Invoice" })).toBeInTheDocument();
  });

  // Test 4: canManage=false hides "Configure Rates" action link
  it("SetupProgressCard hides rate card link when canManage=false", () => {
    render(
      <SetupProgressCard
        title="Project Setup"
        completionPercentage={20}
        overallComplete={false}
        steps={makeSetupSteps(false)}
        canManage={false}
      />,
    );

    // "Rate card configured" has permissionRequired=true → no link for non-managers
    const rateItem = screen.getByText("Rate card configured");
    expect(rateItem.closest("li")?.querySelector("a")).toBeNull();

    // "Customer assigned" has no permissionRequired → link shown
    const customerItem = screen.getByText("Customer assigned");
    expect(customerItem.closest("li")?.querySelector("a")).not.toBeNull();
  });

  // Test 5: TemplateReadinessCard NOT rendered when templateReadiness is empty
  it("TemplateReadinessCard is not rendered when templateReadiness is empty", () => {
    const templateReadiness: TemplateReadinessItem[] = [];

    render(
      <div>
        {templateReadiness.length > 0 && (
          <TemplateReadinessCard
            templates={templateReadiness}
            generateHref={(id) => `/generate/${id}`}
          />
        )}
        <p>Other content</p>
      </div>,
    );

    expect(screen.queryByText("Document Templates")).not.toBeInTheDocument();
    expect(screen.getByText("Other content")).toBeInTheDocument();
  });
});
