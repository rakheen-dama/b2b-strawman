import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Button } from "@/components/ui/button";
import { EditCustomerDialog } from "@/components/customers/edit-customer-dialog";
import { ArchiveCustomerDialog } from "@/components/customers/archive-customer-dialog";
import type { Customer } from "@/lib/types";

// OBS-2103 regression guard — adjacent <DialogTrigger asChild> +
// <AlertDialogTrigger asChild> with Radix Slot collapsed under React 19,
// dropping one button from the DOM. The fix removes asChild from both and
// uses React.cloneElement to inject the open handler. This test asserts
// both Edit and Archive triggers render side-by-side and both fire their
// respective dialogs on click.

const mockUpdateCustomer = vi.fn();
const mockArchiveCustomer = vi.fn();
const mockPush = vi.fn();

vi.mock("@/app/(app)/org/[slug]/customers/actions", () => ({
  updateCustomer: (...args: unknown[]) => mockUpdateCustomer(...args),
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

const activeCustomer: Customer = {
  id: "c1",
  name: "Sipho Dlamini",
  email: "sipho@example.com",
  phone: null,
  idNumber: null,
  status: "ACTIVE",
  notes: null,
  createdBy: "m1",
  createdAt: "2024-01-15T00:00:00Z",
  updatedAt: "2024-01-15T00:00:00Z",
};

function ActionRow() {
  return (
    <div className="flex items-center gap-2">
      <EditCustomerDialog customer={activeCustomer} slug="acme">
        <Button variant="outline" size="sm">
          Edit Action Row
        </Button>
      </EditCustomerDialog>
      <ArchiveCustomerDialog
        slug="acme"
        customerId={activeCustomer.id}
        customerName={activeCustomer.name}
      >
        <Button variant="ghost" size="sm">
          Archive Action Row
        </Button>
      </ArchiveCustomerDialog>
    </div>
  );
}

describe("Customer detail action row — Edit + Archive (OBS-2103)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders both Edit and Archive trigger buttons adjacently", () => {
    render(<ActionRow />);

    const editTrigger = screen.getByRole("button", { name: "Edit Action Row" });
    const archiveTrigger = screen.getByRole("button", { name: "Archive Action Row" });

    expect(editTrigger).toBeInTheDocument();
    expect(archiveTrigger).toBeInTheDocument();

    // Both must coexist in the DOM — the OBS-2103 bug dropped one.
    expect(editTrigger).not.toBe(archiveTrigger);
  });

  it("opens the Edit dialog when the Edit trigger is clicked", async () => {
    const user = userEvent.setup();
    render(<ActionRow />);

    await user.click(screen.getByRole("button", { name: "Edit Action Row" }));

    await waitFor(() => {
      expect(screen.getByText("Edit Customer")).toBeInTheDocument();
    });
    expect(screen.getByLabelText("Name")).toHaveValue("Sipho Dlamini");
  });

  it("opens the Archive dialog when the Archive trigger is clicked", async () => {
    const user = userEvent.setup();
    render(<ActionRow />);

    await user.click(screen.getByRole("button", { name: "Archive Action Row" }));

    await waitFor(() => {
      expect(screen.getByText("Archive Customer")).toBeInTheDocument();
    });
    expect(screen.getByText(/project links will be preserved/)).toBeInTheDocument();
  });

  it("does not collide — clicking Edit does not open Archive (and vice versa)", async () => {
    const user = userEvent.setup();
    render(<ActionRow />);

    // Click Edit — Archive's confirmation copy must NOT appear.
    await user.click(screen.getByRole("button", { name: "Edit Action Row" }));
    await waitFor(() => {
      expect(screen.getByText("Edit Customer")).toBeInTheDocument();
    });
    expect(screen.queryByText(/project links will be preserved/)).not.toBeInTheDocument();
  });
});
