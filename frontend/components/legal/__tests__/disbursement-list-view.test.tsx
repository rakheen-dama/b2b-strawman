import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { DisbursementListView } from "@/components/legal/disbursement-list-view";
import type { DisbursementResponse } from "@/lib/api/legal-disbursements";

function makeDisbursement(overrides: Partial<DisbursementResponse> = {}): DisbursementResponse {
  return {
    id: "d1",
    matterId: "p1",
    clientId: "c1",
    category: "SHERIFF_FEES",
    description: "Sheriff service of summons",
    amount: 500,
    vatTreatment: "ZERO_RATED_PASS_THROUGH",
    vatAmount: 0,
    paymentSource: "OFFICE_ACCOUNT",
    trustTransactionId: null,
    incurredDate: "2026-02-12",
    supplierName: "Sheriff Sandton",
    supplierReference: null,
    receiptDocumentId: null,
    approvalStatus: "DRAFT",
    approvedBy: null,
    approvedAt: null,
    approvalNotes: null,
    billingStatus: "UNBILLED",
    invoiceLineId: null,
    writeOffReason: null,
    createdBy: "u1",
    createdAt: "2026-02-12T10:00:00Z",
    updatedAt: "2026-02-12T10:00:00Z",
    ...overrides,
  };
}

function headerFor(label: string): HTMLElement {
  const header = screen.getAllByRole("columnheader").find((h) => h.textContent?.trim() === label);
  if (!header) throw new Error(`No "${label}" header`);
  return header;
}

function cellUnder(label: string, rowId = "d1"): HTMLElement {
  const headers = screen.getAllByRole("columnheader").map((h) => h.textContent?.trim());
  const index = headers.indexOf(label);
  if (index === -1) throw new Error(`No "${label}" column; headers: ${headers.join(", ")}`);
  return within(screen.getByTestId(`disbursement-row-${rowId}`)).getAllByRole("cell")[index];
}

describe("DisbursementListView", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders an empty state when no disbursements are provided", () => {
    render(<DisbursementListView disbursements={[]} />);

    expect(screen.getByText("No disbursements found.")).toBeInTheDocument();
    expect(screen.queryByTestId("disbursement-list")).not.toBeInTheDocument();
  });

  it("renders one row per disbursement with supplier, category and amount", () => {
    const rows = [
      makeDisbursement({ id: "d1", supplierName: "Sheriff Sandton" }),
      makeDisbursement({
        id: "d2",
        supplierName: "Adv. Jane Doe",
        category: "COUNSEL_FEES",
        amount: 2000,
        vatAmount: 300,
      }),
    ];

    render(<DisbursementListView disbursements={rows} />);

    expect(screen.getByTestId("disbursement-list")).toBeInTheDocument();
    expect(screen.getByTestId("disbursement-row-d1")).toBeInTheDocument();
    expect(screen.getByTestId("disbursement-row-d2")).toBeInTheDocument();
    // Supplier and Category render in their own columns (and again on the Description's
    // second line for narrow containers), so read them from their column cells.
    expect(cellUnder("Supplier", "d1")).toHaveTextContent("Sheriff Sandton");
    expect(cellUnder("Supplier", "d2")).toHaveTextContent("Adv. Jane Doe");
    expect(cellUnder("Category", "d1")).toHaveTextContent("Sheriff Fees");
    expect(cellUnder("Category", "d2")).toHaveTextContent("Counsel Fees");
  });

  it("calls onSelect when a row is clicked", async () => {
    const user = userEvent.setup();
    const onSelect = vi.fn();
    const row = makeDisbursement({ id: "row-1" });

    render(<DisbursementListView disbursements={[row]} onSelect={onSelect} />);

    await user.click(screen.getByTestId("disbursement-row-row-1"));

    expect(onSelect).toHaveBeenCalledTimes(1);
    expect(onSelect).toHaveBeenCalledWith(row);
  });

  it("resolves matter name from projectNames map", () => {
    const row = makeDisbursement({ id: "d1", matterId: "p1" });
    render(<DisbursementListView disbursements={[row]} projectNames={{ p1: "Matter 2026/001" }} />);

    expect(cellUnder("Matter", "d1")).toHaveTextContent("Matter 2026/001");
  });

  it("hides the actions menu for APPROVED rows", () => {
    const approved = makeDisbursement({
      id: "d-approved",
      approvalStatus: "APPROVED",
    });

    render(
      <DisbursementListView disbursements={[approved]} onEdit={vi.fn()} onUploadReceipt={vi.fn()} />
    );

    // Actions dropdown trigger should not be rendered for APPROVED status
    expect(screen.queryByRole("button", { name: /Actions/i })).not.toBeInTheDocument();
  });

  it("offers Write off on an UNBILLED row (any approval state) without selecting the row", async () => {
    const user = userEvent.setup();
    const onSelect = vi.fn();
    const onWriteOff = vi.fn();
    const approved = makeDisbursement({ id: "d-wo", approvalStatus: "APPROVED" });

    render(
      <DisbursementListView
        disbursements={[approved]}
        onSelect={onSelect}
        onWriteOff={onWriteOff}
        onRestore={vi.fn()}
      />
    );

    await user.click(screen.getByTestId("disbursement-actions-d-wo"));
    expect(screen.queryByRole("menuitem", { name: "Undo write-off" })).not.toBeInTheDocument();
    await user.click(screen.getByTestId("disbursement-write-off-item-d-wo"));

    // Deferred until the menu has fully closed (its onCloseAutoFocus), then focus is back on "…".
    await waitFor(() => expect(onWriteOff).toHaveBeenCalledWith(approved));
    expect(onWriteOff).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId("disbursement-actions-d-wo")).toHaveFocus();
    expect(onSelect).not.toHaveBeenCalled();
  });

  it("offers Undo write-off on a WRITTEN_OFF row", async () => {
    const user = userEvent.setup();
    const onSelect = vi.fn();
    const onRestore = vi.fn();
    const writtenOff = makeDisbursement({
      id: "d-ro",
      approvalStatus: "APPROVED",
      billingStatus: "WRITTEN_OFF",
      writeOffReason: "Absorbed",
    });

    render(
      <DisbursementListView
        disbursements={[writtenOff]}
        onSelect={onSelect}
        onWriteOff={vi.fn()}
        onRestore={onRestore}
      />
    );

    await user.click(screen.getByTestId("disbursement-actions-d-ro"));
    expect(screen.queryByRole("menuitem", { name: "Write off" })).not.toBeInTheDocument();
    await user.click(screen.getByRole("menuitem", { name: "Undo write-off" }));

    expect(onRestore).toHaveBeenCalledWith(writtenOff);
    expect(onSelect).not.toHaveBeenCalled();
  });

  it("shows no write-off actions on a BILLED row", () => {
    const billed = makeDisbursement({
      id: "d-billed",
      approvalStatus: "APPROVED",
      billingStatus: "BILLED",
    });

    render(
      <DisbursementListView disbursements={[billed]} onWriteOff={vi.fn()} onRestore={vi.fn()} />
    );

    expect(screen.queryByTestId("disbursement-actions-d-billed")).not.toBeInTheDocument();
  });

  it("never renders a test id prefixed disbursement-row- other than the row itself", () => {
    const row = makeDisbursement({ id: "d-pref" });
    const { container } = render(
      <DisbursementListView
        disbursements={[row]}
        onEdit={vi.fn()}
        onWriteOff={vi.fn()}
        onRestore={vi.fn()}
      />
    );

    const prefixed = Array.from(container.querySelectorAll('[data-testid^="disbursement-row-"]'));
    expect(prefixed.map((el) => el.getAttribute("data-testid"))).toEqual([
      "disbursement-row-d-pref",
    ]);
  });
});

describe("DisbursementListView — fitting the container", () => {
  afterEach(() => {
    cleanup();
  });

  const LONG_DESCRIPTION =
    "Counsel's opinion on shareholders' agreement — Khumalo Property Holdings transfer";

  function renderRow(overrides: Partial<DisbursementResponse> = {}) {
    const row = makeDisbursement({
      id: "d1",
      matterId: "p1",
      category: "COUNSEL_FEES",
      description: LONG_DESCRIPTION,
      supplierName: "Adv. N. Mokoena SC",
      approvalStatus: "APPROVED",
      billingStatus: "UNBILLED",
      ...overrides,
    });
    render(
      <DisbursementListView
        disbursements={[row]}
        projectNames={{ p1: "Khumalo Property Holdings — Erf 1234 Sandton Transfer" }}
        onWriteOff={vi.fn()}
      />
    );
    return row;
  }

  /** The Description cell's muted second line (the hidden columns' values). */
  function secondaryLine(): HTMLElement {
    const line = cellUnder("Description").children[1];
    if (!(line instanceof HTMLElement)) throw new Error("No second line under the Description");
    return line;
  }

  it("is its own size container, keeping the relative scroll box as a fallback", () => {
    renderRow();
    expect(screen.getByTestId("disbursement-list")).toHaveClass(
      "@container",
      "relative",
      "overflow-x-auto"
    );
  });

  it("hides Supplier, then Category, then Matter (header and cells) as the container narrows", () => {
    renderRow();
    const expected: Record<string, string> = {
      Matter: "@min-[66rem]:table-cell",
      Category: "@min-[75rem]:table-cell",
      Supplier: "@min-[84rem]:table-cell",
    };
    for (const [label, query] of Object.entries(expected)) {
      expect(headerFor(label), label).toHaveClass("hidden", query);
      expect(cellUnder(label), label).toHaveClass("hidden", query);
    }
  });

  it("never hides Date, Description, Amount, Approval or Actions", () => {
    renderRow();
    for (const label of ["Date", "Description", "Amount (incl VAT)", "Approval", "Actions"]) {
      expect(headerFor(label), label).not.toHaveClass("hidden");
      expect(cellUnder(label), label).not.toHaveClass("hidden");
    }
    expect(within(cellUnder("Actions")).getByTestId("disbursement-actions-d1")).toBeInTheDocument();
  });

  it("keeps Billing visible: its own column when wide, stacked under Approval when compact", () => {
    renderRow();
    expect(headerFor("Billing")).toHaveClass("hidden", "@min-[58rem]:table-cell");
    expect(cellUnder("Billing")).toHaveClass("hidden", "@min-[58rem]:table-cell");
    expect(cellUnder("Billing")).toHaveTextContent("Unbilled");

    const approval = cellUnder("Approval");
    expect(within(approval).getByText("Approved")).toBeInTheDocument();
    const stacked = within(approval).getByText("Unbilled");
    expect(stacked.parentElement).toHaveClass("@min-[58rem]:hidden");
    expect(stacked.parentElement).not.toHaveClass("hidden");
  });

  it("shows each hidden column's value on the Description's second line until its column returns", () => {
    renderRow();
    const line = secondaryLine();
    expect(line).toHaveClass("text-xs", "text-slate-500", "@min-[84rem]:hidden");

    const matter = within(line).getByText("Khumalo Property Holdings — Erf 1234 Sandton Transfer");
    expect(matter).toHaveClass("truncate");
    expect(matter).toHaveAttribute(
      "title",
      "Khumalo Property Holdings — Erf 1234 Sandton Transfer"
    );
    expect(matter.parentElement).toHaveClass("@min-[66rem]:hidden");
    const category = within(line).getByText("Counsel Fees");
    expect(category).toHaveClass("truncate");
    expect(category.parentElement).toHaveClass("@min-[75rem]:hidden");
    const supplier = within(line).getByText("Adv. N. Mokoena SC");
    expect(supplier).toHaveClass("truncate", "@min-[84rem]:hidden");
    expect(supplier).toHaveAttribute("title", "Adv. N. Mokoena SC");
  });

  it("leaves the matter out of the second line when the mount has no matter names", () => {
    const row = makeDisbursement({ id: "d1", category: "SHERIFF_FEES" });
    render(<DisbursementListView disbursements={[row]} />);
    expect(cellUnder("Matter")).toHaveTextContent("\u2014");
    expect(secondaryLine().textContent).toBe("Sheriff Fees·Sheriff Sandton");
  });

  it("caps the Description to one truncated line with the full text as a title", () => {
    renderRow();
    const cell = cellUnder("Description");
    expect(cell).toHaveClass("max-w-[20ch]", "@min-[58rem]:max-w-[28ch]");
    const description = within(cell).getByText(LONG_DESCRIPTION);
    expect(description).toHaveClass("truncate");
    expect(description).toHaveAttribute("title", LONG_DESCRIPTION);
  });
});
