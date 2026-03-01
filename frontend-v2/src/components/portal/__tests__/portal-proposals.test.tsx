import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { render, screen, cleanup, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
    prefetch: vi.fn(),
    back: vi.fn(),
    refresh: vi.fn(),
  }),
  useParams: () => ({ id: "p1" }),
}));

vi.mock("@/lib/toast", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}));

vi.mock("@/app/portal/proposals/proposal-actions", () => ({
  listPortalProposals: vi.fn(),
  getPortalProposal: vi.fn(),
  acceptProposal: vi.fn(),
  declineProposal: vi.fn(),
}));

import {
  listPortalProposals,
  getPortalProposal,
  acceptProposal,
  declineProposal,
} from "@/app/portal/proposals/proposal-actions";
import type {
  PortalProposalSummary,
  PortalProposalDetail,
} from "@/app/portal/proposals/proposal-actions";
import { toast } from "@/lib/toast";

afterEach(() => cleanup());

const mockListProposals = listPortalProposals as ReturnType<typeof vi.fn>;
const mockGetProposal = getPortalProposal as ReturnType<typeof vi.fn>;
const mockAcceptProposal = acceptProposal as ReturnType<typeof vi.fn>;
const mockDeclineProposal = declineProposal as ReturnType<typeof vi.fn>;

const summaryBase: PortalProposalSummary = {
  id: "p1",
  proposalNumber: "PROP-001",
  title: "Web Design Package",
  status: "SENT",
  feeModel: "FIXED",
  feeAmount: 10000,
  feeCurrency: "USD",
  sentAt: "2026-01-15T10:00:00Z",
};

const detailBase: PortalProposalDetail = {
  id: "p1",
  proposalNumber: "PROP-001",
  title: "Web Design Package",
  status: "SENT",
  feeModel: "FIXED",
  feeAmount: 10000,
  feeCurrency: "USD",
  contentHtml: "<p>Scope of work details here.</p>",
  milestonesJson: JSON.stringify([
    {
      description: "Initial deposit",
      percentage: 30,
      relativeDueDays: 0,
      sortOrder: 1,
    },
    {
      description: "Final payment",
      percentage: 70,
      relativeDueDays: 30,
      sortOrder: 2,
    },
  ]),
  sentAt: "2026-01-15T10:00:00Z",
  expiresAt: "2026-02-15T10:00:00Z",
  orgName: "Acme Corp",
  orgLogoUrl: null,
  orgBrandColor: "#4f46e5",
};

describe("PortalProposalsPage (list)", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("renders_proposal_list_with_status_badges", async () => {
    const proposals: PortalProposalSummary[] = [
      summaryBase,
      { ...summaryBase, id: "p2", proposalNumber: "PROP-002", title: "SEO Audit", status: "ACCEPTED" },
      { ...summaryBase, id: "p3", proposalNumber: "PROP-003", title: "Marketing Plan", status: "EXPIRED" },
    ];
    mockListProposals.mockResolvedValue(proposals);

    const { default: PortalProposalsPage } = await import(
      "@/app/portal/proposals/page"
    );
    render(<PortalProposalsPage />);

    await waitFor(() => {
      expect(screen.getByText("Web Design Package")).toBeInTheDocument();
    });

    expect(screen.getByText("SEO Audit")).toBeInTheDocument();
    expect(screen.getByText("Marketing Plan")).toBeInTheDocument();
    expect(screen.getByText("Pending")).toBeInTheDocument();
    expect(screen.getByText("Accepted")).toBeInTheDocument();
    expect(screen.getByText("Expired")).toBeInTheDocument();
  });

  it("sent_proposals_show_respond_cta", async () => {
    const proposals: PortalProposalSummary[] = [
      summaryBase,
      { ...summaryBase, id: "p2", proposalNumber: "PROP-002", title: "SEO Audit", status: "ACCEPTED" },
    ];
    mockListProposals.mockResolvedValue(proposals);

    const { default: PortalProposalsPage } = await import(
      "@/app/portal/proposals/page"
    );
    render(<PortalProposalsPage />);

    await waitFor(() => {
      expect(screen.getByText("Web Design Package")).toBeInTheDocument();
    });

    expect(screen.getByText("View & Respond")).toBeInTheDocument();
    expect(screen.getByText("View")).toBeInTheDocument();
  });
});

describe("PortalProposalDetailPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("renders_fee_summary_card", async () => {
    mockGetProposal.mockResolvedValue(detailBase);

    const { default: PortalProposalDetailPage } = await import(
      "@/app/portal/proposals/[id]/page"
    );
    render(<PortalProposalDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("Fee Summary")).toBeInTheDocument();
    });

    expect(screen.getByText("Fixed Fee")).toBeInTheDocument();
    expect(screen.getByText("$10,000.00")).toBeInTheDocument();
    expect(screen.getByText("Initial deposit")).toBeInTheDocument();
    expect(screen.getByText("30%")).toBeInTheDocument();
    expect(screen.getByText("$3,000.00")).toBeInTheDocument();
    expect(screen.getByText("Final payment")).toBeInTheDocument();
    expect(screen.getByText("70%")).toBeInTheDocument();
    expect(screen.getByText("$7,000.00")).toBeInTheDocument();
  });

  it("shows_accept_decline_for_sent_only", async () => {
    mockGetProposal.mockResolvedValue(detailBase);

    const { default: PortalProposalDetailPage } = await import(
      "@/app/portal/proposals/[id]/page"
    );
    const { unmount } = render(<PortalProposalDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("Accept Proposal")).toBeInTheDocument();
    });

    expect(screen.getByText("Decline")).toBeInTheDocument();

    unmount();
    cleanup();

    // ACCEPTED status should NOT show action buttons
    mockGetProposal.mockResolvedValue({ ...detailBase, status: "ACCEPTED" });

    render(<PortalProposalDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("Web Design Package")).toBeInTheDocument();
    });

    expect(screen.queryByText("Accept Proposal")).not.toBeInTheDocument();
    expect(screen.queryByText("Decline")).not.toBeInTheDocument();
  });

  it("expired_shows_banner_no_buttons", async () => {
    mockGetProposal.mockResolvedValue({ ...detailBase, status: "EXPIRED" });

    const { default: PortalProposalDetailPage } = await import(
      "@/app/portal/proposals/[id]/page"
    );
    render(<PortalProposalDetailPage />);

    await waitFor(() => {
      expect(screen.getByText(/This proposal has expired/)).toBeInTheDocument();
    });

    expect(
      screen.getByText(/This proposal has expired\. Contact Acme Corp/),
    ).toBeInTheDocument();
    expect(screen.queryByText("Accept Proposal")).not.toBeInTheDocument();
    expect(screen.queryByText("Decline")).not.toBeInTheDocument();
  });

  it("accept_proposal_calls_action_and_shows_success_toast", async () => {
    const user = userEvent.setup();
    mockGetProposal.mockResolvedValue(detailBase);
    mockAcceptProposal.mockResolvedValue({ message: "Proposal accepted successfully." });

    const { default: PortalProposalDetailPage } = await import(
      "@/app/portal/proposals/[id]/page"
    );
    render(<PortalProposalDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("Accept Proposal")).toBeInTheDocument();
    });

    // Click the Accept Proposal button to open confirmation dialog
    await user.click(screen.getByRole("button", { name: /Accept Proposal/ }));

    // Wait for dialog description to appear (rendered via Radix AlertDialog portal)
    await waitFor(() => {
      expect(screen.getByText(/By accepting this proposal/)).toBeInTheDocument();
    });

    // Find the confirm button inside the dialog by its text — it's the second "Accept Proposal" button
    const allAcceptButtons = screen.getAllByText("Accept Proposal");
    // Click the last one (inside the dialog footer)
    await user.click(allAcceptButtons[allAcceptButtons.length - 1]);

    await waitFor(() => {
      expect(mockAcceptProposal).toHaveBeenCalledWith("p1");
    });
    expect(toast.success).toHaveBeenCalledWith("Proposal accepted successfully.");
  });

  it("decline_proposal_calls_action_with_reason", async () => {
    const user = userEvent.setup();
    mockGetProposal.mockResolvedValue(detailBase);
    mockDeclineProposal.mockResolvedValue(undefined);

    const { default: PortalProposalDetailPage } = await import(
      "@/app/portal/proposals/[id]/page"
    );
    render(<PortalProposalDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("Decline")).toBeInTheDocument();
    });

    // Click Decline to open dialog
    await user.click(screen.getByRole("button", { name: "Decline" }));

    await waitFor(() => {
      expect(screen.getByPlaceholderText(/Reason for declining/)).toBeInTheDocument();
    });

    // Type a reason
    await user.type(screen.getByPlaceholderText(/Reason for declining/), "Too expensive");

    // Click Decline Proposal button
    await user.click(screen.getByRole("button", { name: "Decline Proposal" }));

    await waitFor(() => {
      expect(mockDeclineProposal).toHaveBeenCalledWith("p1", "Too expensive");
    });
    expect(toast.success).toHaveBeenCalledWith("Proposal declined.");
  });

  it("accept_proposal_error_shows_error_toast", async () => {
    const user = userEvent.setup();
    mockGetProposal.mockResolvedValue(detailBase);
    mockAcceptProposal.mockRejectedValue(new Error("Server error"));

    const { default: PortalProposalDetailPage } = await import(
      "@/app/portal/proposals/[id]/page"
    );
    render(<PortalProposalDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("Accept Proposal")).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: /Accept Proposal/ }));

    await waitFor(() => {
      expect(screen.getByText(/By accepting this proposal/)).toBeInTheDocument();
    });

    const allAcceptButtons = screen.getAllByText("Accept Proposal");
    await user.click(allAcceptButtons[allAcceptButtons.length - 1]);

    await waitFor(() => {
      expect(mockAcceptProposal).toHaveBeenCalledWith("p1");
    });
    expect(toast.error).toHaveBeenCalledWith("Server error");
  });
});
