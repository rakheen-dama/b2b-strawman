import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { EditCustomerDialog } from "@/components/customers/edit-customer-dialog";
import { ArchiveCustomerDialog } from "@/components/customers/archive-customer-dialog";
import type { Customer } from "@/lib/types";

// OBS-2103 + OBS-2103b regression guard.
//
// OBS-2103 (cycle 16):  adjacent <DialogTrigger asChild> + <AlertDialogTrigger asChild> with
//                       Radix Slot collapsed under React 19, dropping one button from the DOM.
//                       PR #1239 switched to React.cloneElement to avoid the Slot wrapper.
//
// OBS-2103b (cycle 17): cloneElement-injected onClick was stripped on one of the two adjacent
//                       siblings (one dialog received a lazy/RSC element where children.props
//                       was undefined, so cloneElement returned an element with default props
//                       only). Edit click no-op'd on Sipho; Archive click no-op'd on Moroka.
//
// Fix:                  the dialog component owns the trigger button — no Slot, no cloneElement,
//                       no consumer-supplied children. `triggerLabel`/`triggerVariant`/`triggerIcon`
//                       props drive the button.
//
// This test guards both bugs together: both triggers must render, and clicking each must
// open the *correct* dialog (independent setOpen state) — including across remount cycles.

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
      <EditCustomerDialog
        customer={activeCustomer}
        slug="acme"
        triggerLabel="Edit Action Row"
        triggerVariant="outline"
        triggerSize="sm"
      />
      <ArchiveCustomerDialog
        slug="acme"
        customerId={activeCustomer.id}
        customerName={activeCustomer.name}
        triggerLabel="Archive Action Row"
        triggerVariant="ghost"
        triggerSize="sm"
      />
    </div>
  );
}

describe("Customer detail action row — Edit + Archive (OBS-2103 + OBS-2103b)", () => {
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

  it("clicking Edit opens the Edit dialog (OBS-2103b: onClick survives commit)", async () => {
    const user = userEvent.setup();
    render(<ActionRow />);

    await user.click(screen.getByRole("button", { name: "Edit Action Row" }));

    await waitFor(() => {
      expect(screen.getByText("Edit Customer")).toBeInTheDocument();
    });
    expect(screen.getByLabelText("Name")).toHaveValue("Sipho Dlamini");
    // Archive dialog must NOT have opened.
    expect(screen.queryByText(/project links will be preserved/)).not.toBeInTheDocument();
  });

  it("clicking Archive opens the Archive dialog (OBS-2103b: onClick survives commit)", async () => {
    const user = userEvent.setup();
    render(<ActionRow />);

    await user.click(screen.getByRole("button", { name: "Archive Action Row" }));

    await waitFor(() => {
      expect(screen.getByText("Archive Customer")).toBeInTheDocument();
    });
    expect(screen.getByText(/project links will be preserved/)).toBeInTheDocument();
    // Edit dialog must NOT have opened.
    expect(screen.queryByText("Edit Customer")).not.toBeInTheDocument();
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

  it("both dialogs operate independently after a remount cycle", async () => {
    const user = userEvent.setup();
    const { unmount } = render(<ActionRow />);

    // First mount — open + close Edit.
    await user.click(screen.getByRole("button", { name: "Edit Action Row" }));
    await waitFor(() => {
      expect(screen.getByText("Edit Customer")).toBeInTheDocument();
    });
    await user.click(screen.getByRole("button", { name: "Cancel" }));

    act(() => {
      unmount();
    });

    // Second mount — both triggers must still work.
    render(<ActionRow />);
    await user.click(screen.getByRole("button", { name: "Archive Action Row" }));
    await waitFor(() => {
      expect(screen.getByText("Archive Customer")).toBeInTheDocument();
    });
    expect(screen.queryByText("Edit Customer")).not.toBeInTheDocument();
  });
});
