import { describe, it, expect, vi, afterEach } from "vitest";
import { render, screen, cleanup } from "@testing-library/react";

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
    prefetch: vi.fn(),
    back: vi.fn(),
    refresh: vi.fn(),
  }),
}));

vi.mock("@/app/(app)/org/[slug]/proposals/proposal-actions", () => ({
  createProposal: vi.fn().mockResolvedValue({ id: "p1" }),
  deleteProposal: vi.fn().mockResolvedValue(undefined),
  withdrawProposal: vi.fn().mockResolvedValue({ id: "p1", status: "DRAFT" }),
  sendProposal: vi.fn().mockResolvedValue({ id: "p1", status: "SENT" }),
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

import { ProposalDetailClient } from "@/components/proposals/proposal-detail-client";
import type { ProposalDetailResponse } from "@/app/(app)/org/[slug]/proposals/proposal-actions";

afterEach(() => cleanup());

function makeProposal(
  overrides: Partial<ProposalDetailResponse> = {},
): ProposalDetailResponse {
  return {
    id: "p1",
    proposalNumber: "PROP-001",
    title: "Website Redesign",
    customerId: "c1",
    portalContactId: null,
    status: "DRAFT",
    feeModel: "FIXED",
    fixedFeeAmount: 15000,
    fixedFeeCurrency: "USD",
    hourlyRateNote: null,
    retainerAmount: null,
    retainerCurrency: null,
    retainerHoursIncluded: null,
    contentJson: { text: "Proposal body content here" },
    projectTemplateId: null,
    sentAt: null,
    expiresAt: null,
    acceptedAt: null,
    declinedAt: null,
    declineReason: null,
    createdProjectId: null,
    createdRetainerId: null,
    createdById: "m1",
    createdAt: "2026-01-15T10:00:00Z",
    updatedAt: "2026-01-15T10:00:00Z",
    customerName: "Acme Corp",
    portalContactName: undefined,
    projectTemplateName: undefined,
    milestones: [],
    teamMembers: [
      {
        id: "tm1",
        memberId: "m1",
        memberName: "Alice Smith",
        role: "Lead Developer",
        sortOrder: 0,
      },
      {
        id: "tm2",
        memberId: "m2",
        memberName: "Bob Jones",
        role: "Designer",
        sortOrder: 1,
      },
    ],
    createdByName: "Alice Smith",
    ...overrides,
  };
}

describe("ProposalDetailClient", () => {
  it("renders proposal header with number, title, and status badge", () => {
    render(
      <ProposalDetailClient proposal={makeProposal()} orgSlug="test-org" />,
    );

    expect(screen.getByText("PROP-001")).toBeInTheDocument();
    expect(screen.getByText("Website Redesign")).toBeInTheDocument();
    expect(screen.getByText("Draft")).toBeInTheDocument();
    expect(screen.getByText("Acme Corp")).toBeInTheDocument();
  });

  it("shows fee summary card with correct amounts for FIXED model", () => {
    render(
      <ProposalDetailClient
        proposal={makeProposal({
          feeModel: "FIXED",
          fixedFeeAmount: 15000,
          fixedFeeCurrency: "USD",
          milestones: [
            {
              id: "ms1",
              description: "Design Phase",
              percentage: 30,
              relativeDueDays: 14,
              sortOrder: 0,
              invoiceId: null,
            },
            {
              id: "ms2",
              description: "Development Phase",
              percentage: 70,
              relativeDueDays: 30,
              sortOrder: 1,
              invoiceId: null,
            },
          ],
        })}
        orgSlug="test-org"
      />,
    );

    expect(screen.getByText("Fixed Fee")).toBeInTheDocument();
    expect(screen.getByText("$15,000.00")).toBeInTheDocument();
    expect(screen.getByText("Milestones")).toBeInTheDocument();
    expect(screen.getByText("Design Phase")).toBeInTheDocument();
    expect(screen.getByText("Development Phase")).toBeInTheDocument();
  });

  it("shows team members list", () => {
    render(
      <ProposalDetailClient proposal={makeProposal()} orgSlug="test-org" />,
    );

    expect(screen.getByText("Team Members")).toBeInTheDocument();
    expect(screen.getByText("Alice Smith")).toBeInTheDocument();
    expect(screen.getByText("Bob Jones")).toBeInTheDocument();
    expect(screen.getByText("Lead Developer")).toBeInTheDocument();
    expect(screen.getByText("Designer")).toBeInTheDocument();
  });
});
