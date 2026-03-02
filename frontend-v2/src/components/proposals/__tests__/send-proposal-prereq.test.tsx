import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
    prefetch: vi.fn(),
    back: vi.fn(),
    refresh: vi.fn(),
  }),
}));

const mockCheckPrerequisitesAction = vi.fn();
const mockGetPortalContacts = vi.fn();
const mockSendProposal = vi.fn();

vi.mock("@/lib/actions/prerequisite-actions", () => ({
  checkPrerequisitesAction: (...args: unknown[]) =>
    mockCheckPrerequisitesAction(...args),
}));

vi.mock("@/app/(app)/org/[slug]/proposals/proposal-actions", () => ({
  getPortalContacts: (...args: unknown[]) => mockGetPortalContacts(...args),
  sendProposal: (...args: unknown[]) => mockSendProposal(...args),
  deleteProposal: vi.fn(),
  withdrawProposal: vi.fn(),
  createProposal: vi.fn(),
  replaceMilestones: vi.fn(),
  replaceTeamMembers: vi.fn(),
}));

vi.mock("@/lib/toast", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}));

// Import after mocks
import { toast } from "@/lib/toast";
import { ProposalDetailClient } from "@/components/proposals/proposal-detail-client";
import type { ProposalDetailResponse } from "@/app/(app)/org/[slug]/proposals/proposal-actions";

const mockProposal: ProposalDetailResponse = {
  id: "p1",
  proposalNumber: "PROP-0001",
  title: "Test Proposal",
  status: "DRAFT",
  customerId: "c1",
  portalContactId: null,
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
  createdById: "user-1",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
  milestones: [],
  teamMembers: [],
};

afterEach(() => cleanup());

describe("ProposalDetailClient — prerequisite gate for Send", () => {
  it("shows error toast when prerequisites fail", async () => {
    const user = userEvent.setup();
    mockCheckPrerequisitesAction.mockResolvedValueOnce({
      passed: false,
      context: "PROPOSAL_SEND",
      violations: [
        {
          code: "NO_PORTAL_CONTACT",
          message: "Customer must have a portal contact",
          entityType: "CUSTOMER",
          entityId: "c1",
          fieldSlug: null,
          groupName: null,
          resolution: "Add a portal contact to the customer",
        },
      ],
    });

    render(<ProposalDetailClient proposal={mockProposal} orgSlug="acme" />);

    const sendButton = screen.getByRole("button", { name: /Send/i });
    await user.click(sendButton);

    await waitFor(() => {
      expect(mockCheckPrerequisitesAction).toHaveBeenCalledWith(
        "PROPOSAL_SEND",
        "CUSTOMER",
        "c1",
      );
    });

    // Verify error shown via toast
    await waitFor(() => {
      expect(vi.mocked(toast.error)).toHaveBeenCalled();
    });
  });
});
