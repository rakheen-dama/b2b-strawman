import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { DeletionConfirmDialog } from "@/components/compliance/DeletionConfirmDialog";

// Mock server actions
vi.mock("@/app/(app)/org/[slug]/compliance/requests/actions", () => ({
  executeDeletion: vi.fn().mockResolvedValue({
    success: true,
    summary: {
      customerAnonymized: true,
      documentsDeleted: 3,
      commentsRedacted: 5,
      portalContactsAnonymized: 1,
      invoicesPreserved: 2,
    },
  }),
}));

import { executeDeletion } from "@/app/(app)/org/[slug]/compliance/requests/actions";

const defaultProps = {
  open: true,
  onOpenChange: vi.fn(),
  slug: "acme",
  requestId: "req-1",
  customerName: "Acme Corp",
};

describe("DeletionConfirmDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("confirm button is disabled when input is empty", () => {
    render(<DeletionConfirmDialog {...defaultProps} />);
    const confirmButton = screen.getByRole("button", { name: /execute deletion/i });
    expect(confirmButton).toBeDisabled();
  });

  it("confirm button is disabled when input does not match customer name", async () => {
    const user = userEvent.setup();
    render(<DeletionConfirmDialog {...defaultProps} />);
    const input = screen.getByPlaceholderText("Acme Corp");
    await user.type(input, "Wrong Name");
    const confirmButton = screen.getByRole("button", { name: /execute deletion/i });
    expect(confirmButton).toBeDisabled();
  });

  it("confirm button is enabled when input matches customer name exactly", async () => {
    const user = userEvent.setup();
    render(<DeletionConfirmDialog {...defaultProps} />);
    const input = screen.getByPlaceholderText("Acme Corp");
    await user.type(input, "Acme Corp");
    const confirmButton = screen.getByRole("button", { name: /execute deletion/i });
    expect(confirmButton).not.toBeDisabled();
  });

  it("calls executeDeletion with correct args on confirm", async () => {
    const user = userEvent.setup();
    render(<DeletionConfirmDialog {...defaultProps} />);
    const input = screen.getByPlaceholderText("Acme Corp");
    await user.type(input, "Acme Corp");
    const confirmButton = screen.getByRole("button", { name: /execute deletion/i });
    await user.click(confirmButton);
    expect(executeDeletion).toHaveBeenCalledWith("acme", "req-1", "Acme Corp");
  });
});
