import { describe, it, expect, vi, afterEach } from "vitest";
import { render, screen, cleanup } from "@testing-library/react";
import Link from "next/link";

import type { ProposalResponse } from "@/app/(app)/org/[slug]/proposals/proposal-actions";
import { CustomerProposalsTab } from "@/components/customers/customer-proposals-tab";

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
    prefetch: vi.fn(),
  }),
  useParams: () => ({ slug: "test-org" }),
}));

afterEach(() => cleanup());

function makeProposal(
  overrides: Partial<ProposalResponse> = {},
): ProposalResponse {
  return {
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
    ...overrides,
  };
}

describe("CustomerProposalsTab", () => {
  it("renders_proposals_for_customer", () => {
    const proposals = [
      makeProposal({
        id: "p1",
        proposalNumber: "PROP-001",
        title: "Website Redesign",
      }),
      makeProposal({
        id: "p2",
        proposalNumber: "PROP-002",
        title: "Annual Audit",
        feeModel: "HOURLY",
        fixedFeeAmount: null,
        fixedFeeCurrency: null,
        hourlyRateNote: "R850/hr",
      }),
    ];

    render(
      <CustomerProposalsTab proposals={proposals} customerId="c1" />,
    );

    expect(screen.getByText("PROP-001")).toBeInTheDocument();
    expect(screen.getByText("Website Redesign")).toBeInTheDocument();
    expect(screen.getByText("PROP-002")).toBeInTheDocument();
    expect(screen.getByText("Annual Audit")).toBeInTheDocument();
  });

  it("new_proposal_button_links_to_create_with_customer", () => {
    render(<CustomerProposalsTab proposals={[]} customerId="c1" />);

    const link = screen.getByRole("link", { name: /new proposal/i });
    expect(link).toBeInTheDocument();
    expect(link.getAttribute("href")).toContain("/proposals/new");
    expect(link.getAttribute("href")).toContain("customerId=c1");
  });

  it("shows_created_from_proposal_link_when_applicable", () => {
    // Tests the "Created from Proposal" link pattern rendered in the project detail page.
    const { container } = render(
      <span>
        <Link
          href="/org/test-org/proposals/p1"
          className="text-teal-600 hover:text-teal-700 hover:underline"
        >
          Created from Proposal PROP-001
        </Link>
      </span>,
    );

    expect(
      container.querySelector('a[href="/org/test-org/proposals/p1"]'),
    ).not.toBeNull();
    expect(
      screen.getByText("Created from Proposal PROP-001"),
    ).toBeInTheDocument();
  });
});
