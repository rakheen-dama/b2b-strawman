import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { TransferLeadDialog } from "./transfer-lead-dialog";

const mockTransferLead = vi.fn();

vi.mock("@/app/(app)/org/[slug]/projects/[id]/member-actions", () => ({
  transferLead: (...args: unknown[]) => mockTransferLead(...args),
}));

describe("TransferLeadDialog", () => {
  const defaultProps = {
    slug: "acme",
    projectId: "proj1",
    targetMemberId: "user2",
    targetMemberName: "Bob Member",
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("displays target member name in confirmation", async () => {
    const user = userEvent.setup();

    render(
      <TransferLeadDialog {...defaultProps}>
        <button>Open Transfer</button>
      </TransferLeadDialog>,
    );

    await user.click(screen.getByText("Open Transfer"));

    expect(screen.getByText("Bob Member")).toBeInTheDocument();
    expect(screen.getByText(/You will become a regular member/)).toBeInTheDocument();
  });

  it("calls transferLead when confirmed", async () => {
    mockTransferLead.mockResolvedValue({ success: true });
    const user = userEvent.setup();

    render(
      <TransferLeadDialog {...defaultProps}>
        <button>Open Transfer</button>
      </TransferLeadDialog>,
    );

    await user.click(screen.getByText("Open Transfer"));

    const confirmButton = screen.getByRole("button", { name: /^transfer$/i });
    await user.click(confirmButton);

    expect(mockTransferLead).toHaveBeenCalledWith("acme", "proj1", "user2");
  });

  it("displays error when transfer fails", async () => {
    mockTransferLead.mockResolvedValue({ success: false, error: "Not authorized" });
    const user = userEvent.setup();

    render(
      <TransferLeadDialog {...defaultProps}>
        <button>Open Transfer</button>
      </TransferLeadDialog>,
    );

    await user.click(screen.getByText("Open Transfer"));
    await user.click(screen.getByRole("button", { name: /^transfer$/i }));

    await waitFor(() => {
      expect(screen.getByText("Not authorized")).toBeInTheDocument();
    });
  });

  it("disables buttons during transfer", async () => {
    mockTransferLead.mockImplementation(
      () => new Promise((resolve) => setTimeout(() => resolve({ success: true }), 500)),
    );
    const user = userEvent.setup();

    render(
      <TransferLeadDialog {...defaultProps}>
        <button>Open Transfer</button>
      </TransferLeadDialog>,
    );

    await user.click(screen.getByText("Open Transfer"));
    const confirmButton = screen.getByRole("button", { name: /^transfer$/i });
    await user.click(confirmButton);

    expect(screen.getByText("Transferring...")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /cancel/i })).toBeDisabled();
  });
});
