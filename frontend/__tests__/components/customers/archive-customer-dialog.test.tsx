import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ArchiveCustomerDialog } from "@/components/customers/archive-customer-dialog";

const mockArchiveCustomer = vi.fn();
const mockPush = vi.fn();

vi.mock("@/app/(app)/org/[slug]/customers/actions", () => ({
  archiveCustomer: (...args: unknown[]) => mockArchiveCustomer(...args),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: mockPush,
    replace: vi.fn(),
    refresh: vi.fn(),
    back: vi.fn(),
    forward: vi.fn(),
    prefetch: vi.fn(),
  }),
}));

describe("ArchiveCustomerDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("shows customer name in confirmation message", async () => {
    const user = userEvent.setup();

    render(
      <ArchiveCustomerDialog slug="acme" customerId="c1" customerName="Acme Corp">
        <button>Archive Trigger</button>
      </ArchiveCustomerDialog>,
    );

    await user.click(screen.getByText("Archive Trigger"));

    expect(screen.getByText("Acme Corp")).toBeInTheDocument();
    expect(screen.getByText(/project links will be preserved/)).toBeInTheDocument();
  });

  it("calls archiveCustomer and navigates on success", async () => {
    mockArchiveCustomer.mockResolvedValue({ success: true });
    const user = userEvent.setup();

    render(
      <ArchiveCustomerDialog slug="acme" customerId="c1" customerName="Acme Corp">
        <button>Archive Trigger</button>
      </ArchiveCustomerDialog>,
    );

    await user.click(screen.getByText("Archive Trigger"));
    await user.click(screen.getByText("Archive"));

    await waitFor(() => {
      expect(mockArchiveCustomer).toHaveBeenCalledWith("acme", "c1");
    });

    await waitFor(() => {
      expect(mockPush).toHaveBeenCalledWith("/org/acme/customers");
    });
  });

  it("displays error when archive fails", async () => {
    mockArchiveCustomer.mockResolvedValue({ success: false, error: "Permission denied" });
    const user = userEvent.setup();

    render(
      <ArchiveCustomerDialog slug="acme" customerId="c1" customerName="Acme Corp">
        <button>Archive Trigger</button>
      </ArchiveCustomerDialog>,
    );

    await user.click(screen.getByText("Archive Trigger"));
    await user.click(screen.getByText("Archive"));

    await waitFor(() => {
      expect(screen.getByText("Permission denied")).toBeInTheDocument();
    });
  });
});
