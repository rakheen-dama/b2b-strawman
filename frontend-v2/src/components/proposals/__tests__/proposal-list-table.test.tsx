import { describe, it, expect, vi, afterEach } from "vitest";
import { render, screen, cleanup } from "@testing-library/react";

import type { ProposalResponse } from "@/app/(app)/org/[slug]/proposals/proposal-actions";
import { ProposalListTable } from "@/components/proposals/proposal-list-table";

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
    prefetch: vi.fn(),
  }),
}));

afterEach(() => cleanup());

const mockProposals: ProposalResponse[] = [
  {
    id: "p1",
    proposalNumber: "PROP-001",
    title: "Website Redesign",
    customerId: "c1",
    portalContactId: null,
    status: "SENT",
    feeModel: "FIXED",
    fixedFeeAmount: 5000,
    fixedFeeCurrency: "USD",
    hourlyRateNote: null,
    retainerAmount: null,
    retainerCurrency: null,
    retainerHoursIncluded: null,
    contentJson: null,
    projectTemplateId: null,
    sentAt: "2026-01-15T10:00:00Z",
    expiresAt: null,
    acceptedAt: null,
    declinedAt: null,
    declineReason: null,
    createdProjectId: null,
    createdRetainerId: null,
    createdById: "m1",
    createdAt: "2026-01-10T10:00:00Z",
    updatedAt: "2026-01-15T10:00:00Z",
  },
  {
    id: "p2",
    proposalNumber: "PROP-002",
    title: "Annual Audit",
    customerId: "c2",
    portalContactId: null,
    status: "ACCEPTED",
    feeModel: "HOURLY",
    fixedFeeAmount: null,
    fixedFeeCurrency: null,
    hourlyRateNote: "R850/hr",
    retainerAmount: null,
    retainerCurrency: null,
    retainerHoursIncluded: null,
    contentJson: null,
    projectTemplateId: null,
    sentAt: "2026-02-01T10:00:00Z",
    expiresAt: null,
    acceptedAt: "2026-02-05T10:00:00Z",
    declinedAt: null,
    declineReason: null,
    createdProjectId: null,
    createdRetainerId: null,
    createdById: "m1",
    createdAt: "2026-01-20T10:00:00Z",
    updatedAt: "2026-02-05T10:00:00Z",
  },
];

describe("ProposalListTable", () => {
  it("renders proposal rows with correct columns", () => {
    render(
      <ProposalListTable
        proposals={mockProposals}
        orgSlug="test-org"
        activeStatus="ALL"
      />,
    );

    expect(screen.getByText("PROP-001")).toBeInTheDocument();
    expect(screen.getByText("Website Redesign")).toBeInTheDocument();
    expect(screen.getByText("PROP-002")).toBeInTheDocument();
    expect(screen.getByText("Annual Audit")).toBeInTheDocument();
    expect(screen.getByText("Fixed Fee")).toBeInTheDocument();
    expect(screen.getByText("Hourly")).toBeInTheDocument();
  });

  it("shows empty state when no proposals", () => {
    render(
      <ProposalListTable
        proposals={[]}
        orgSlug="test-org"
        activeStatus="ALL"
      />,
    );

    expect(screen.getByText("No proposals yet")).toBeInTheDocument();
  });

  it("renders status badge with correct color for ACCEPTED", () => {
    render(
      <ProposalListTable
        proposals={[mockProposals[1]]}
        orgSlug="test-org"
        activeStatus="ALL"
      />,
    );

    // "Accepted" appears both in status tabs and in the badge.
    // The badge has emerald classes — find the one inside the table body.
    const badges = screen.getAllByText("Accepted");
    const emeraldBadge = badges.find(
      (el) =>
        el.className.includes("bg-emerald-100") &&
        el.className.includes("text-emerald-700"),
    );
    expect(emeraldBadge).toBeDefined();
  });
});
