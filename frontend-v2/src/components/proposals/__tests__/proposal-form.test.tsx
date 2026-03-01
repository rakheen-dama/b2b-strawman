import { describe, it, expect, vi, afterEach } from "vitest";
import { render, screen, cleanup, fireEvent } from "@testing-library/react";

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
    prefetch: vi.fn(),
    back: vi.fn(),
  }),
}));

vi.mock("@/app/(app)/org/[slug]/proposals/proposal-actions", () => ({
  createProposal: vi.fn().mockResolvedValue({ id: "p1" }),
  updateProposal: vi.fn().mockResolvedValue({ id: "p1" }),
  replaceMilestones: vi.fn().mockResolvedValue(undefined),
  replaceTeamMembers: vi.fn().mockResolvedValue(undefined),
  getPortalContacts: vi.fn().mockResolvedValue([]),
}));

vi.mock("@/lib/toast", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}));

import { ProposalForm } from "@/components/proposals/proposal-form";
import type { Customer, OrgMember } from "@/lib/types";
import type { ProjectTemplateResponse } from "@/lib/api/templates";

afterEach(() => cleanup());

const mockCustomers: Customer[] = [
  {
    id: "c1",
    name: "Acme Corp",
    email: "acme@test.com",
    phone: null,
    idNumber: null,
    status: "ACTIVE",
    notes: null,
    createdBy: "m1",
    createdByName: "Alice",
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
  },
];

const mockOrgMembers: OrgMember[] = [
  {
    id: "m1",
    name: "Alice Smith",
    email: "alice@test.com",
    avatarUrl: null,
    orgRole: "owner",
  },
];

const mockTemplates: ProjectTemplateResponse[] = [
  {
    id: "t1",
    name: "Website Project",
    namePattern: "Website - {customer}",
    description: null,
    billableDefault: true,
    source: "MANUAL",
    sourceProjectId: null,
    active: true,
    taskCount: 5,
    tagCount: 2,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
  },
];

describe("ProposalForm", () => {
  it("renders all form sections", () => {
    render(
      <ProposalForm
        mode="create"
        orgSlug="test-org"
        customers={mockCustomers}
        orgMembers={mockOrgMembers}
        projectTemplates={mockTemplates}
      />,
    );

    expect(screen.getByText("Basics")).toBeInTheDocument();
    expect(screen.getByText("Fee Configuration")).toBeInTheDocument();
    expect(screen.getByText("Proposal Body")).toBeInTheDocument();
    expect(screen.getByText("Team & Project")).toBeInTheDocument();
  });

  it("fee model switch shows correct fields", () => {
    render(
      <ProposalForm
        mode="create"
        orgSlug="test-org"
        customers={mockCustomers}
        orgMembers={mockOrgMembers}
        projectTemplates={mockTemplates}
      />,
    );

    // Default is FIXED — should show Amount input
    expect(screen.getByLabelText("Amount")).toBeInTheDocument();

    // Switch to HOURLY
    fireEvent.click(screen.getByText("Hourly"));
    expect(screen.getByLabelText("Rate Note")).toBeInTheDocument();

    // Switch to RETAINER
    fireEvent.click(screen.getByText("Retainer"));
    expect(screen.getByLabelText("Monthly Amount")).toBeInTheDocument();
    expect(screen.getByLabelText("Included Hours")).toBeInTheDocument();
  });

  it("milestone percentage validation shows in UI", () => {
    render(
      <ProposalForm
        mode="create"
        orgSlug="test-org"
        customers={mockCustomers}
        orgMembers={mockOrgMembers}
        projectTemplates={mockTemplates}
      />,
    );

    // Enable milestones toggle
    const toggle = screen.getByLabelText("Add milestones");
    fireEvent.click(toggle);

    // Add a milestone
    fireEvent.click(screen.getByText("Add milestone"));

    // Should show 0% / 100% in red
    const indicator = screen.getByTestId("milestone-total");
    expect(indicator.textContent).toBe("0% / 100%");
    expect(indicator.className).toContain("text-red-600");
  });

  it("renders save and cancel buttons", () => {
    render(
      <ProposalForm
        mode="create"
        orgSlug="test-org"
        customers={mockCustomers}
        orgMembers={mockOrgMembers}
        projectTemplates={mockTemplates}
      />,
    );

    expect(
      screen.getByRole("button", { name: "Save Draft" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Cancel" }),
    ).toBeInTheDocument();
  });
});
