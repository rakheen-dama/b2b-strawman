import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { TemplateReadinessCard } from "@/components/setup/template-readiness-card";
import type { TemplateReadinessItem } from "@/components/setup/types";

function makeTemplates(
  overrides: Partial<TemplateReadinessItem>[] = [],
): TemplateReadinessItem[] {
  const defaults: TemplateReadinessItem[] = [
    {
      templateId: "t1",
      templateName: "Engagement Letter",
      templateSlug: "engagement-letter",
      ready: true,
      missingFields: [],
    },
    {
      templateId: "t2",
      templateName: "Statement of Work",
      templateSlug: "sow",
      ready: false,
      missingFields: ["Tax Number", "Company Reg"],
    },
  ];
  return defaults.map((tpl, i) => ({ ...tpl, ...overrides[i] }));
}

const generateHref = (templateId: string) =>
  `/templates/${templateId}/generate`;

describe("TemplateReadinessCard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders ready template with check icon and enabled generate link", () => {
    render(
      <TemplateReadinessCard
        templates={makeTemplates()}
        entityType="PROJECT"
        entityId="p1"
        generateHref={generateHref}
      />,
    );

    expect(screen.getByText("Engagement Letter")).toBeInTheDocument();
    const link = screen.getByRole("link", { name: "Generate" });
    expect(link).toHaveAttribute("href", "/templates/t1/generate");
  });

  it("renders not-ready template with warning icon and disabled button", () => {
    render(
      <TemplateReadinessCard
        templates={makeTemplates()}
        entityType="PROJECT"
        entityId="p1"
        generateHref={generateHref}
      />,
    );

    expect(screen.getByText("Statement of Work")).toBeInTheDocument();

    // The disabled button — there should be exactly one disabled button
    const buttons = screen.getAllByRole("button");
    const disabledButtons = buttons.filter((btn) => btn.hasAttribute("disabled"));
    expect(disabledButtons).toHaveLength(1);
  });

  it("disabled generate button has disabled attribute", () => {
    render(
      <TemplateReadinessCard
        templates={[
          {
            templateId: "t2",
            templateName: "Statement of Work",
            templateSlug: "sow",
            ready: false,
            missingFields: ["Tax Number"],
          },
        ]}
        entityType="PROJECT"
        entityId="p1"
        generateHref={generateHref}
      />,
    );

    const button = screen.getByRole("button", { name: "Generate" });
    expect(button).toBeDisabled();
  });

  it("renders all templates in the list", () => {
    const templates: TemplateReadinessItem[] = [
      {
        templateId: "t1",
        templateName: "Engagement Letter",
        templateSlug: "engagement-letter",
        ready: true,
        missingFields: [],
      },
      {
        templateId: "t2",
        templateName: "Statement of Work",
        templateSlug: "sow",
        ready: true,
        missingFields: [],
      },
      {
        templateId: "t3",
        templateName: "NDA",
        templateSlug: "nda",
        ready: false,
        missingFields: ["Address"],
      },
    ];

    render(
      <TemplateReadinessCard
        templates={templates}
        entityType="CUSTOMER"
        entityId="c1"
        generateHref={generateHref}
      />,
    );

    expect(screen.getByText("Engagement Letter")).toBeInTheDocument();
    expect(screen.getByText("Statement of Work")).toBeInTheDocument();
    expect(screen.getByText("NDA")).toBeInTheDocument();
  });
});
