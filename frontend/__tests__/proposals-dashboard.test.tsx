import { describe, it, expect, afterEach, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { ProposalSummaryCards } from "@/components/proposals/proposal-summary-cards";
import { ProposalsAttentionList } from "@/components/proposals/proposals-attention-list";
import { ProposalTable } from "@/components/proposals/proposal-table";
import type { ProposalSummaryDto } from "@/lib/types/proposal";
import type { ProposalResponse } from "@/lib/types/proposal";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
}));

const mockSummary: ProposalSummaryDto = {
  total: 10,
  byStatus: { DRAFT: 3, SENT: 4, ACCEPTED: 2, DECLINED: 1 },
  avgDaysToAcceptance: 5.2,
  conversionRate: 0.333,
  pendingOverdue: [
    {
      id: "p1",
      title: "Annual Audit Proposal",
      customerName: "Acme Corp",
      projectName: "Annual Audit 2026",
      sentAt: "2026-02-01T10:00:00Z",
      daysSinceSent: 43,
    },
    {
      id: "p2",
      title: "Tax Advisory Proposal",
      customerName: "Beta Inc",
      projectName: null,
      sentAt: "2026-02-15T10:00:00Z",
      daysSinceSent: 29,
    },
  ],
};

const mockProposals: ProposalResponse[] = [
  {
    id: "pr1",
    proposalNumber: "PROP-0001",
    title: "Annual Audit Proposal",
    customerId: "c1",
    portalContactId: null,
    status: "DRAFT",
    feeModel: "FIXED",
    fixedFeeAmount: 5000,
    fixedFeeCurrency: "USD",
    hourlyRateNote: null,
    retainerAmount: null,
    retainerCurrency: null,
    retainerHoursIncluded: null,
    contentJson: null,
    projectTemplateId: null,
    sentAt: null,
    expiresAt: null,
    acceptedAt: null,
    declinedAt: null,
    declineReason: null,
    createdProjectId: null,
    createdRetainerId: null,
    createdById: "m1",
    createdAt: "2026-03-01T10:00:00Z",
    updatedAt: "2026-03-01T10:00:00Z",
  },
  {
    id: "pr2",
    proposalNumber: "PROP-0002",
    title: "Tax Advisory Proposal",
    customerId: "c2",
    portalContactId: null,
    status: "SENT",
    feeModel: "HOURLY",
    fixedFeeAmount: null,
    fixedFeeCurrency: null,
    hourlyRateNote: "R850/hr",
    retainerAmount: null,
    retainerCurrency: null,
    retainerHoursIncluded: null,
    contentJson: null,
    projectTemplateId: null,
    sentAt: "2026-03-05T10:00:00Z",
    expiresAt: "2026-04-05T10:00:00Z",
    acceptedAt: null,
    declinedAt: null,
    declineReason: null,
    createdProjectId: null,
    createdRetainerId: null,
    createdById: "m1",
    createdAt: "2026-03-01T10:00:00Z",
    updatedAt: "2026-03-05T10:00:00Z",
  },
  {
    id: "pr3",
    proposalNumber: "PROP-0003",
    title: "Bookkeeping Retainer Proposal",
    customerId: "c3",
    portalContactId: null,
    status: "ACCEPTED",
    feeModel: "RETAINER",
    fixedFeeAmount: null,
    fixedFeeCurrency: null,
    hourlyRateNote: null,
    retainerAmount: 3000,
    retainerCurrency: "USD",
    retainerHoursIncluded: 20,
    contentJson: null,
    projectTemplateId: null,
    sentAt: "2026-02-15T10:00:00Z",
    expiresAt: "2026-03-15T10:00:00Z",
    acceptedAt: "2026-02-20T10:00:00Z",
    declinedAt: null,
    declineReason: null,
    createdProjectId: "proj1",
    createdRetainerId: "ret1",
    createdById: "m1",
    createdAt: "2026-02-10T10:00:00Z",
    updatedAt: "2026-02-20T10:00:00Z",
  },
];

describe("ProposalSummaryCards", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders correct counts from summary data", () => {
    render(<ProposalSummaryCards summary={mockSummary} />);

    expect(screen.getByText("Total Proposals")).toBeInTheDocument();
    expect(screen.getByText("10")).toBeInTheDocument();

    expect(screen.getByText("Pending")).toBeInTheDocument();
    expect(screen.getByText("4")).toBeInTheDocument();

    expect(screen.getByText("Accepted")).toBeInTheDocument();
    expect(screen.getByText("2")).toBeInTheDocument();

    expect(screen.getByText("Conversion Rate")).toBeInTheDocument();
    expect(screen.getByText("33.3%")).toBeInTheDocument();
  });

  it("highlights pending card amber when count > 0", () => {
    const { container } = render(<ProposalSummaryCards summary={mockSummary} />);
    const amberCards = container.querySelectorAll(".bg-amber-50");
    expect(amberCards.length).toBeGreaterThanOrEqual(1);
  });

  it("shows dash for conversion rate when total is 0", () => {
    const emptySummary: ProposalSummaryDto = {
      total: 0,
      byStatus: {},
      avgDaysToAcceptance: 0,
      conversionRate: 0,
      pendingOverdue: [],
    };
    render(<ProposalSummaryCards summary={emptySummary} />);
    expect(screen.getByText("\u2014")).toBeInTheDocument();
  });
});

describe("ProposalsAttentionList", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders overdue proposals with customer, project and days", () => {
    render(<ProposalsAttentionList summary={mockSummary} slug="acme" />);

    expect(screen.getByText("Needs Attention")).toBeInTheDocument();
    expect(screen.getByText("Acme Corp")).toBeInTheDocument();
    expect(screen.getByText("Annual Audit 2026")).toBeInTheDocument();
    expect(screen.getByText("43 days")).toBeInTheDocument();

    expect(screen.getByText("Beta Inc")).toBeInTheDocument();
    expect(screen.getByText("29 days")).toBeInTheDocument();
  });

  it("renders empty state when no overdue proposals", () => {
    const emptySummary: ProposalSummaryDto = {
      total: 5,
      byStatus: { DRAFT: 5 },
      avgDaysToAcceptance: 0,
      conversionRate: 0,
      pendingOverdue: [],
    };
    render(<ProposalsAttentionList summary={emptySummary} slug="acme" />);
    expect(screen.getByText(/No overdue proposals/)).toBeInTheDocument();
  });
});

describe("ProposalTable", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders all proposals with correct status badges", () => {
    render(<ProposalTable proposals={mockProposals} slug="acme" now={Date.now()} />);

    expect(screen.getByText("Annual Audit Proposal")).toBeInTheDocument();
    expect(screen.getByText("Tax Advisory Proposal")).toBeInTheDocument();
    expect(screen.getByText("Bookkeeping Retainer Proposal")).toBeInTheDocument();

    // Check status badges via data-slot="badge" to distinguish from filter tab buttons
    const badges = screen
      .getAllByText(/Draft|Sent|Accepted/)
      .filter((el) => el.getAttribute("data-slot") === "badge");
    expect(badges).toHaveLength(3);

    const draftBadge = badges.find((b) => b.textContent === "Draft");
    const sentBadge = badges.find((b) => b.textContent === "Sent");
    const acceptedBadge = badges.find((b) => b.textContent === "Accepted");

    expect(draftBadge).toHaveAttribute("data-variant", "neutral");
    expect(sentBadge).toHaveAttribute("data-variant", "lead");
    expect(acceptedBadge).toHaveAttribute("data-variant", "success");
  });

  it("renders filter tabs for all statuses", () => {
    render(<ProposalTable proposals={mockProposals} slug="acme" now={Date.now()} />);

    expect(screen.getByRole("button", { name: "All" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Draft" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Sent" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Accepted" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Declined" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Expired" })).toBeInTheDocument();
  });
});
