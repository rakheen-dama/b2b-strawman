import { describe, it, expect, vi, afterEach } from "vitest";
import { render, screen, cleanup, waitFor } from "@testing-library/react";

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
    prefetch: vi.fn(),
    back: vi.fn(),
    refresh: vi.fn(),
  }),
}));

const mockGetPortalContacts = vi.fn();
const mockSendProposal = vi.fn();

vi.mock("@/app/(app)/org/[slug]/proposals/proposal-actions", () => ({
  getPortalContacts: (...args: unknown[]) => mockGetPortalContacts(...args),
  sendProposal: (...args: unknown[]) => mockSendProposal(...args),
}));

vi.mock("@/lib/toast", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}));

import { SendProposalDialog } from "@/components/proposals/send-proposal-dialog";

afterEach(() => cleanup());

describe("SendProposalDialog", () => {
  it("renders dialog with contact selection when contacts are loaded", async () => {
    mockGetPortalContacts.mockResolvedValue([
      { id: "pc1", displayName: "Jane Doe", email: "jane@acme.com" },
      { id: "pc2", displayName: "John Smith", email: "john@acme.com" },
    ]);

    render(
      <SendProposalDialog
        open={true}
        onOpenChange={vi.fn()}
        proposalId="p1"
        customerId="c1"
        existingPortalContactId={null}
      />,
    );

    expect(
      screen.getByRole("heading", { name: "Send Proposal" }),
    ).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText("Select a contact")).toBeInTheDocument();
    });
  });

  it("shows send button disabled when no contact is selected", async () => {
    mockGetPortalContacts.mockResolvedValue([
      { id: "pc1", displayName: "Jane Doe", email: "jane@acme.com" },
    ]);

    render(
      <SendProposalDialog
        open={true}
        onOpenChange={vi.fn()}
        proposalId="p1"
        customerId="c1"
        existingPortalContactId={null}
      />,
    );

    await waitFor(() => {
      const sendButton = screen.getByRole("button", {
        name: "Send Proposal",
      });
      expect(sendButton).toBeDisabled();
    });
  });
});
