import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { UpgradeConfirmDialog } from "./upgrade-confirm-dialog";

describe("UpgradeConfirmDialog", () => {
  const mockOnConfirm = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders with Pro benefits description", async () => {
    const user = userEvent.setup();

    render(
      <UpgradeConfirmDialog
        onConfirm={mockOnConfirm}
        trigger={<button>Open Upgrade</button>}
      />,
    );

    await user.click(screen.getByText("Open Upgrade"));

    expect(screen.getByText("Upgrade to Pro")).toBeInTheDocument();
    expect(screen.getByText(/Dedicated database infrastructure/)).toBeInTheDocument();
    expect(screen.getByText(/Up to 10 team members/)).toBeInTheDocument();
    expect(screen.getByText(/Schema-level data isolation/)).toBeInTheDocument();
  });

  it("calls onConfirm when Upgrade Now is clicked", async () => {
    mockOnConfirm.mockResolvedValue(undefined);
    const user = userEvent.setup();

    render(
      <UpgradeConfirmDialog
        onConfirm={mockOnConfirm}
        trigger={<button>Open Upgrade</button>}
      />,
    );

    await user.click(screen.getByText("Open Upgrade"));
    await user.click(screen.getByRole("button", { name: /upgrade now/i }));

    expect(mockOnConfirm).toHaveBeenCalledOnce();
  });

  it("shows loading state during upgrade", async () => {
    mockOnConfirm.mockImplementation(
      () => new Promise((resolve) => setTimeout(() => resolve(undefined), 500)),
    );
    const user = userEvent.setup();

    render(
      <UpgradeConfirmDialog
        onConfirm={mockOnConfirm}
        trigger={<button>Open Upgrade</button>}
      />,
    );

    await user.click(screen.getByText("Open Upgrade"));
    await user.click(screen.getByRole("button", { name: /upgrade now/i }));

    expect(screen.getByText("Upgrading…")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /cancel/i })).toBeDisabled();
  });

  it("shows error message when upgrade fails", async () => {
    mockOnConfirm.mockRejectedValue(new Error("Upgrade failed"));
    const user = userEvent.setup();

    render(
      <UpgradeConfirmDialog
        onConfirm={mockOnConfirm}
        trigger={<button>Open Upgrade</button>}
      />,
    );

    await user.click(screen.getByText("Open Upgrade"));
    await user.click(screen.getByRole("button", { name: /upgrade now/i }));

    await waitFor(() => {
      expect(screen.getByText("Upgrade failed")).toBeInTheDocument();
    });
  });
});
