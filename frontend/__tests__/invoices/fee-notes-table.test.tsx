import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { FeeNotesTable } from "@/components/invoices/fee-notes-table";
import { formatCurrency, formatDateInSA } from "@/lib/format";
import type { InvoiceResponse } from "@/lib/types";
import { csvRecords, stubDownload } from "@/__tests__/helpers/csv-download";

// Phase 91 666A: the Fee notes list on DataTable (client mode, CSV, row click).

const mockPush = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush, replace: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
  usePathname: () => "/org/acme/invoices",
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    ...props
  }: {
    children: React.ReactNode;
    href: string;
    [key: string]: unknown;
  }) => {
    const anchorProps = { ...props };
    delete anchorProps.scroll;
    delete anchorProps.prefetch;
    return (
      <a href={href} {...anchorProps}>
        {children}
      </a>
    );
  },
}));

function invoice(overrides: Partial<InvoiceResponse> = {}): InvoiceResponse {
  return {
    id: "inv-1",
    invoiceNumber: "INV-0001",
    status: "SENT",
    currency: "ZAR",
    issueDate: "2026-10-01",
    dueDate: "2026-10-31",
    total: 9999,
    balanceDue: 9999,
    paidAt: null,
    clientName: "Sipho Ndlovu",
    paymentUrl: null,
    takeOnBatchId: null,
    ...overrides,
  } as InvoiceResponse;
}

const HEADERS = ["Fee note", "Client", "Status", "Issue Date", "Due Date", "Total", "Currency"];

function renderTable(invoices: InvoiceResponse[]) {
  return render(
    <FeeNotesTable slug="acme" invoices={invoices} empty={<p>No fixture fee notes.</p>} />
  );
}

/** The body row's cell under the column headed `label`. */
function cellUnder(label: string, rowIndex = 0): HTMLElement {
  const headers = screen.getAllByRole("columnheader").map((h) => h.textContent?.trim());
  const index = headers.indexOf(label);
  if (index === -1) throw new Error(`No "${label}" column; headers: ${headers.join(", ")}`);
  const bodyRows = screen.getAllByRole("row").slice(1);
  return within(bodyRows[rowIndex]).getAllByRole("cell")[index];
}

beforeEach(() => vi.clearAllMocks());
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe("FeeNotesTable", () => {
  it("renders the old table's headers in order", () => {
    renderTable([invoice()]);
    expect(screen.getAllByRole("columnheader").map((h) => h.textContent)).toEqual(HEADERS);
  });

  it("links the fee-note number (exact text) to its detail; Draft when unnumbered", () => {
    renderTable([invoice(), invoice({ id: "inv-2", invoiceNumber: null, status: "DRAFT" })]);
    expect(screen.getByRole("link", { name: "INV-0001" })).toHaveAttribute(
      "href",
      "/org/acme/invoices/inv-1"
    );
    expect(screen.getByRole("link", { name: "Draft" })).toHaveAttribute(
      "href",
      "/org/acme/invoices/inv-2"
    );
  });

  it("opens the detail on a row click, but not on a click on the link itself", () => {
    renderTable([invoice()]);
    fireEvent.click(cellUnder("Client"));
    expect(mockPush).toHaveBeenCalledWith("/org/acme/invoices/inv-1");
    mockPush.mockClear();
    fireEvent.click(screen.getByRole("link", { name: "INV-0001" }));
    expect(mockPush).not.toHaveBeenCalled();
  });

  it("keeps the status badge, the take-on badge and the online-payment icon in the Status cell", () => {
    renderTable([invoice({ takeOnBatchId: "batch-1", paymentUrl: "https://pay.example/x" })]);
    const status = cellUnder("Status");
    expect(within(status).getByText("Sent")).toBeInTheDocument();
    expect(within(status).getByTestId("fee-note-takeon-badge")).toHaveTextContent(
      "Brought forward — take-on"
    );
    expect(within(status).getByTitle("Online payment enabled")).toBeInTheDocument();
  });

  it("shows the balance due under a part-paid total only, and the amount alone otherwise", () => {
    renderTable([
      invoice({ id: "pp", status: "PARTIALLY_PAID", total: 42182, balanceDue: 37182 }),
      invoice({ id: "sent", invoiceNumber: "INV-0002" }),
    ]);
    const hints = screen.getAllByTestId("row-balance-due");
    expect(hints).toHaveLength(1);
    expect(hints[0].textContent).toBe(`Balance ${formatCurrency(37182, "ZAR")}`);
    // take-on.spec.ts needs a SENT row's whole Total cell text to equal the amount.
    expect(cellUnder("Total", 1).textContent).toBe(formatCurrency(9999, "ZAR"));
  });

  it("formats dates on the South African calendar and shows a dash for a missing date", () => {
    renderTable([invoice({ dueDate: null })]);
    expect(cellUnder("Issue Date").textContent).toBe(formatDateInSA("2026-10-01"));
    expect(cellUnder("Issue Date").textContent).toBe("1 Oct 2026");
    expect(cellUnder("Due Date").textContent).toBe("—");
  });

  it("always shows Issue/Due Date and hides only Currency below an 80rem frame (column priority)", () => {
    renderTable([invoice()]);
    for (const label of ["Fee note", "Client", "Status", "Issue Date", "Due Date", "Total"]) {
      expect(cellUnder(label)).not.toHaveClass("hidden");
    }
    expect(cellUnder("Currency")).toHaveClass("hidden", "@7xl:table-cell");
  });

  it("gives the wrapping Client column an 8rem floor (header and cells)", () => {
    renderTable([invoice()]);
    expect(cellUnder("Client").style.minWidth).toBe("8rem");
    expect(screen.getByRole("columnheader", { name: "Client" }).style.minWidth).toBe("8rem");
  });

  it("renders the empty slot instead of the table", () => {
    renderTable([]);
    expect(screen.getByText("No fixture fee notes.")).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Export CSV" })).not.toBeInTheDocument();
  });

  it("exports the rendered rows as fee-notes.csv", async () => {
    const user = userEvent.setup();
    const stub = stubDownload();
    try {
      renderTable([
        invoice({ takeOnBatchId: "batch-1" }),
        invoice({ id: "inv-2", invoiceNumber: null, status: "DRAFT", issueDate: null, total: 0 }),
      ]);
      await user.click(screen.getByRole("button", { name: "Export CSV" }));
      expect(stub.captured.downloads).toEqual(["fee-notes.csv"]);

      const [header, first, second, end] = await csvRecords(stub.captured.blobs[0]);
      // `buildCsv` exports TanStack's visible leaf columns; column priority only hides cells with
      // CSS (container queries), so all 7 columns are exported at every width.
      expect(header).toBe(HEADERS.join(","));
      expect(first).toBe("INV-0001,Sipho Ndlovu,Sent,1 Oct 2026,31 Oct 2026,9999,ZAR");
      expect(second).toBe("Draft,Sipho Ndlovu,Draft,,31 Oct 2026,0,ZAR");
      expect(end).toBe("");
    } finally {
      await stub.restore();
    }
  });
});
