import React, { useMemo } from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import Link from "next/link";
import { DataTable, type DataTableProps } from "@/components/data-table/data-table";
import { CSV_BOM, toCsvField } from "@/components/data-table/data-table-csv";
import { EmptyCell, MoneyCell } from "@/components/data-table/cells";
import { defineColumns } from "@/components/data-table/define-columns";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { buildSortHref, type MatterSort } from "@/lib/matters-list";
import { readSourceTree } from "../../helpers/source-scan";

const mockPush = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush, replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => "/org/acme/matters",
}));

// `scroll` / `prefetch` are next/link props, not DOM attributes: drop them before spreading.
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

beforeEach(() => vi.clearAllMocks());
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

// ---- Fixture ---------------------------------------------------------------------------------

interface Row {
  id: string;
  name: string;
  client: string | null;
  amount: number | null;
  note: string;
}

function makeRows(count: number): Row[] {
  return Array.from({ length: count }, (_, i) => {
    const n = String(i + 1).padStart(3, "0");
    return {
      id: `r${n}`,
      name: `Row ${n}`,
      client: `Client ${n}`,
      amount: i * 10,
      note: `Note ${n}`,
    };
  });
}

const ROWS: Row[] = [
  { id: "a", name: "Estate late Dlamini", client: "Dlamini, Thandi", amount: 1500, note: "plain" },
  { id: "b", name: "Smith v Jones", client: null, amount: null, note: 'say "hi"' },
  { id: "c", name: "Transfer Erf 12", client: "Naidoo", amount: 99.5, note: "line1\nline2" },
];

/** Module-level row menu (react-hooks/static-components: never declare components in render). */
function FixtureRowActions({ id }: { id: string }) {
  return (
    <span className="inline-flex items-center gap-1">
      <button type="button">Flag {id}</button>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <button type="button">
            <span className="sr-only">Fixture row menu {id}</span>
          </button>
        </DropdownMenuTrigger>
        <DropdownMenuContent>
          <DropdownMenuItem>Fixture open {id}</DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </span>
  );
}

const columns = defineColumns<Row>([
  {
    id: "name",
    enableHiding: false,
    meta: { label: "Matter", sortKey: "name", csv: (r) => r.name },
    cell: ({ row }) => (
      <Link href={`/org/acme/matters/${row.original.id}`}>{row.original.name}</Link>
    ),
  },
  {
    id: "client",
    meta: { label: "Client", sortKey: "client", priority: "low", csv: (r) => r.client },
    cell: ({ row }) => row.original.client ?? <EmptyCell />,
  },
  {
    id: "amount",
    meta: { label: "Amount", sortKey: "amount", align: "end", csv: (r) => r.amount },
    cell: ({ row }) => <MoneyCell amount={row.original.amount} />,
  },
  {
    id: "note",
    meta: { label: "Note", priority: "lowest", csv: (r) => r.note },
    cell: ({ row }) => <span>{row.original.note}</span>,
  },
  {
    id: "actions",
    enableHiding: false,
    header: () => <span className="sr-only">Actions</span>,
    cell: ({ row }) => <FixtureRowActions id={row.original.id} />,
  },
]);

const hrefFor = (key: string) => `/x?sort=${key}`;

function tableProps(overrides: Partial<DataTableProps<Row>> = {}): DataTableProps<Row> {
  return {
    columns,
    data: ROWS,
    getRowId: (row) => row.id,
    empty: <p>Nothing in this fixture.</p>,
    rowTestId: "fixture-row",
    ...overrides,
  };
}

function renderTable(overrides: Partial<DataTableProps<Row>> = {}) {
  return render(<DataTable {...tableProps(overrides)} />);
}

/** Captures the CSV Blob and the download name of the next "Export CSV" click. */
function stubDownload() {
  const captured: { blob: Blob | null; downloads: string[] } = { blob: null, downloads: [] };
  const originalCreate = URL.createObjectURL;
  const originalRevoke = URL.revokeObjectURL;
  URL.createObjectURL = vi.fn((blob: Blob) => {
    captured.blob = blob;
    return "blob:mock";
  }) as typeof URL.createObjectURL;
  URL.revokeObjectURL = vi.fn();
  vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (
    this: HTMLAnchorElement
  ) {
    captured.downloads.push(this.download);
  });
  return {
    captured,
    restore() {
      URL.createObjectURL = originalCreate;
      URL.revokeObjectURL = originalRevoke;
    },
  };
}

async function blobText(blob: Blob | null): Promise<string> {
  if (!blob) throw new Error("no CSV blob was created");
  // ignoreBOM keeps a leading BOM in the decoded text so the test can assert it.
  return new TextDecoder("utf-8", { ignoreBOM: true }).decode(await blob.arrayBuffer());
}

// ---- Tests -----------------------------------------------------------------------------------

describe("DataTable — semantics", () => {
  it("renders a semantic table (table/row/columnheader/cell), never a grid", () => {
    renderTable();
    expect(screen.getByRole("table")).toBeInTheDocument();
    expect(screen.queryByRole("grid")).toBeNull();
    expect(screen.getAllByRole("row")).toHaveLength(1 + ROWS.length);
    const headers = screen.getAllByRole("columnheader");
    expect(headers).toHaveLength(5);
    headers.forEach((th) => expect(th).toHaveAttribute("scope", "col"));
    expect(screen.getAllByRole("cell")).toHaveLength(5 * ROWS.length);
  });

  it("forwards rowTestId to every body <tr> (not the header) and tableTestId to <table>", () => {
    renderTable({ tableTestId: "transactions-table" });
    const rows = screen.getAllByTestId("fixture-row");
    expect(rows).toHaveLength(ROWS.length);
    rows.forEach((tr) => expect(tr.tagName).toBe("TR"));
    expect(screen.getAllByRole("row")[0]).not.toHaveAttribute("data-testid");
    expect(screen.getByTestId("transactions-table").tagName).toBe("TABLE");
  });

  it("renders an sr-only caption that names the table", () => {
    renderTable({ caption: "Matters — All matters" });
    const table = screen.getByRole("table", { name: "Matters — All matters" });
    const caption = table.querySelector("caption");
    expect(caption).toHaveTextContent("Matters — All matters");
    expect(caption).toHaveClass("sr-only");
  });
});

describe("DataTable — URL sort headers", () => {
  it("renders sortable headers as links to hrefFor(sortKey); unsortable headers are plain", () => {
    renderTable({ sort: { key: "name", dir: "asc", hrefFor } });
    const matter = screen.getByRole("columnheader", { name: "Matter" });
    expect(within(matter).getByRole("link")).toHaveAttribute("href", "/x?sort=name");
    const client = screen.getByRole("columnheader", { name: "Client" });
    expect(within(client).getByRole("link")).toHaveAttribute("href", "/x?sort=client");
    expect(
      within(screen.getByRole("columnheader", { name: "Amount" })).getByRole("link")
    ).toHaveAttribute("href", "/x?sort=amount");
    expect(
      within(screen.getByRole("columnheader", { name: "Note" })).queryByRole("link")
    ).toBeNull();
    expect(
      within(screen.getByRole("columnheader", { name: "Actions" })).queryByRole("link")
    ).toBeNull();
  });

  it("sets aria-sort ascending/descending on the active th, none on other sortable ones", () => {
    const { rerender } = renderTable({ sort: { key: "name", dir: "asc", hrefFor } });
    expect(screen.getByRole("columnheader", { name: "Matter" })).toHaveAttribute(
      "aria-sort",
      "ascending"
    );
    expect(screen.getByRole("columnheader", { name: "Client" })).toHaveAttribute(
      "aria-sort",
      "none"
    );
    expect(screen.getByRole("columnheader", { name: "Note" })).not.toHaveAttribute("aria-sort");

    rerender(<DataTable {...tableProps({ sort: { key: "name", dir: "desc", hrefFor } })} />);
    expect(screen.getByRole("columnheader", { name: "Matter" })).toHaveAttribute(
      "aria-sort",
      "descending"
    );
  });

  it("keeps the columnheader name equal to the label (sort icon is aria-hidden)", () => {
    renderTable({ sort: { key: "client", dir: "asc", hrefFor } });
    const client = screen.getByRole("columnheader", { name: "Client" });
    expect(client.querySelector("svg")).toHaveAttribute("aria-hidden", "true");
    expect(within(client).getByRole("link", { name: "Client" })).toBeInTheDocument();
  });

  it("without a sort prop, headers are plain labels with no aria-sort", () => {
    renderTable();
    expect(screen.queryAllByRole("link", { name: "Matter" })).toHaveLength(0);
    expect(screen.getByRole("columnheader", { name: "Matter" })).not.toHaveAttribute("aria-sort");
  });
});

describe("DataTable — column priority", () => {
  it("applies the container-query classes, except on the actively sorted column", () => {
    const { rerender } = renderTable({ sort: { key: "name", dir: "asc", hrefFor } });
    const clientTh = screen.getByRole("columnheader", { name: "Client" });
    expect(clientTh).toHaveClass("hidden", "@min-[67rem]:table-cell");
    const firstRow = screen.getAllByTestId("fixture-row")[0];
    const clientTd = within(firstRow).getAllByRole("cell")[1];
    expect(clientTd).toHaveClass("hidden", "@min-[67rem]:table-cell");
    const noteTh = screen.getByRole("columnheader", { name: "Note" });
    expect(noteTh).toHaveClass("hidden", "@7xl:table-cell");
    expect(screen.getByRole("columnheader", { name: "Matter" })).not.toHaveClass("hidden");

    rerender(<DataTable {...tableProps({ sort: { key: "client", dir: "asc", hrefFor } })} />);
    expect(screen.getByRole("columnheader", { name: "Client" })).not.toHaveClass("hidden");
    expect(within(screen.getAllByTestId("fixture-row")[0]).getAllByRole("cell")[1]).not.toHaveClass(
      "hidden"
    );
  });

  it("right-aligns align: end columns", () => {
    renderTable();
    expect(screen.getByRole("columnheader", { name: "Amount" })).toHaveClass("text-right");
    expect(within(screen.getAllByTestId("fixture-row")[0]).getAllByRole("cell")[2]).toHaveClass(
      "text-right"
    );
  });
});

describe("DataTable — sticky header", () => {
  it("by default sticks the header cells under the floating header; the container drops overflow-x-auto", () => {
    renderTable();
    screen.getAllByRole("columnheader").forEach((th) => {
      expect(th).toHaveClass(
        "sticky",
        "top-[calc(var(--app-header-height)+0.5rem)]",
        "z-10",
        "bg-card"
      );
    });
    const container = screen.getByRole("table").closest('[data-slot="table-container"]');
    expect(container).toHaveClass("overflow-x-visible");
    expect(container).not.toHaveClass("overflow-x-auto");
    // The frame is never a card (finance specs count [data-slot="card"]).
    expect(document.querySelector('[data-slot="card"]')).toBeNull();
  });

  it("stickyHeader={false} keeps the default horizontal scroll container", () => {
    renderTable({ stickyHeader: false });
    screen.getAllByRole("columnheader").forEach((th) => expect(th).not.toHaveClass("sticky"));
    const container = screen.getByRole("table").closest('[data-slot="table-container"]');
    expect(container).toHaveClass("overflow-x-auto");
  });
});

describe("DataTable — column visibility", () => {
  it("the Columns menu lists hideable labelled columns and hides one", async () => {
    const user = userEvent.setup();
    renderTable();
    await user.click(screen.getByRole("button", { name: "Columns" }));
    const client = await screen.findByRole("menuitemcheckbox", { name: "Client" });
    expect(screen.getAllByRole("menuitemcheckbox").map((item) => item.textContent)).toEqual([
      "Client",
      "Amount",
      "Note",
    ]);
    expect(screen.queryByRole("menuitemcheckbox", { name: "Matter" })).toBeNull();

    await user.click(client);
    // The menu stays open for further toggles; its checkbox reflects the hidden column.
    expect(client).toHaveAttribute("aria-checked", "false");
    await user.keyboard("{Escape}");
    expect(screen.queryByRole("columnheader", { name: "Client" })).toBeNull();
    expect(within(screen.getAllByTestId("fixture-row")[0]).getAllByRole("cell")).toHaveLength(4);
    expect(screen.queryByText("Dlamini, Thandi")).toBeNull();
  });

  it("resets column visibility when a new data array arrives (navigation), not on a re-render", async () => {
    const user = userEvent.setup();
    const { rerender } = renderTable();
    await user.click(screen.getByRole("button", { name: "Columns" }));
    await user.click(await screen.findByRole("menuitemcheckbox", { name: "Client" }));
    await user.keyboard("{Escape}");
    expect(screen.queryByRole("columnheader", { name: "Client" })).toBeNull();

    // Same array identity: the local choice survives.
    rerender(<DataTable {...tableProps({ data: ROWS })} />);
    expect(screen.queryByRole("columnheader", { name: "Client" })).toBeNull();

    // A new array (every RSC navigation deserialises one): back to the default columns.
    rerender(<DataTable {...tableProps({ data: [...ROWS] })} />);
    expect(screen.getByRole("columnheader", { name: "Client" })).toBeInTheDocument();
  });

  it("columnVisibility={false} renders no Columns menu", () => {
    renderTable({ columnVisibility: false });
    expect(screen.queryByRole("button", { name: "Columns" })).toBeNull();
  });
});

describe("DataTable — CSV export", () => {
  it("escapes fields per RFC 4180", () => {
    expect(toCsvField("a,b")).toBe('"a,b"');
    expect(toCsvField('say "hi"')).toBe('"say ""hi"""');
    expect(toCsvField("line1\nline2")).toBe('"line1\nline2"');
    expect(toCsvField("cr\rhere")).toBe('"cr\rhere"');
    expect(toCsvField("plain")).toBe("plain");
    expect(toCsvField(null)).toBe("");
    expect(toCsvField(undefined)).toBe("");
    expect(toCsvField(42)).toBe("42");
  });

  it("neutralises spreadsheet formulas in strings, never in numbers", () => {
    expect(toCsvField("=SUM(A1:A2)")).toBe("'=SUM(A1:A2)");
    expect(toCsvField("+27 82 000 0000")).toBe("'+27 82 000 0000");
    expect(toCsvField("-R 100,00")).toBe(`"'-R 100,00"`);
    expect(toCsvField("@cmd")).toBe("'@cmd");
    expect(toCsvField("\tlead")).toBe("'\tlead");
    expect(toCsvField("\rlead")).toBe(`"'\rlead"`);
    expect(toCsvField(-5)).toBe("-5");
  });

  it("downloads the visible columns × rendered rows with a BOM, CRLF records and the filename", async () => {
    const user = userEvent.setup();
    const stub = stubDownload();
    try {
      renderTable({ exportCsv: { filename: "matters.csv" } });
      await user.click(screen.getByRole("button", { name: "Export CSV" }));

      expect(stub.captured.downloads).toEqual(["matters.csv"]);
      expect(stub.captured.blob?.type).toBe("text/csv;charset=utf-8");
      const text = await blobText(stub.captured.blob);
      expect(text.startsWith(CSV_BOM)).toBe(true);
      expect(text.slice(CSV_BOM.length)).toBe(
        [
          "Matter,Client,Amount,Note",
          'Estate late Dlamini,"Dlamini, Thandi",1500,plain',
          'Smith v Jones,,,"say ""hi"""',
          'Transfer Erf 12,Naidoo,99.5,"line1\nline2"',
        ].join("\r\n") + "\r\n"
      );
      expect(mockPush).not.toHaveBeenCalled();
    } finally {
      stub.restore();
    }
  });

  it("omits a column hidden through the Columns menu", async () => {
    const user = userEvent.setup();
    const stub = stubDownload();
    try {
      renderTable({ exportCsv: { filename: "hidden.csv" } });
      await user.click(screen.getByRole("button", { name: "Columns" }));
      await user.click(await screen.findByRole("menuitemcheckbox", { name: "Client" }));
      await user.keyboard("{Escape}");
      await user.click(screen.getByRole("button", { name: "Export CSV" }));

      const text = await blobText(stub.captured.blob);
      const [header, first] = text.slice(CSV_BOM.length).split("\r\n");
      expect(header).toBe("Matter,Amount,Note");
      expect(first).toBe("Estate late Dlamini,1500,plain");
    } finally {
      stub.restore();
    }
  });

  it("exports only the selected rows when selection is on (none selected → all rendered rows)", async () => {
    const user = userEvent.setup();
    const stub = stubDownload();
    try {
      renderTable({
        selectable: true,
        exportCsv: { filename: "selected.csv" },
        rowHref: (row) => `/org/acme/matters/${row.id}`,
      });
      await user.click(screen.getByRole("button", { name: "Export CSV" }));
      let lines = (await blobText(stub.captured.blob)).slice(CSV_BOM.length).split("\r\n");
      expect(lines.filter(Boolean)).toHaveLength(1 + ROWS.length);
      // The selection column is never exported.
      expect(lines[0]).toBe("Matter,Client,Amount,Note");

      const boxes = screen.getAllByRole("checkbox", { name: "Select row" });
      await user.click(boxes[0]);
      await user.click(boxes[2]);
      expect(screen.getByRole("checkbox", { name: "Select all rows" })).toHaveAttribute(
        "aria-checked",
        "mixed"
      );
      await user.click(screen.getByRole("button", { name: "Export CSV" }));
      lines = (await blobText(stub.captured.blob)).slice(CSV_BOM.length).split("\r\n");
      expect(lines.filter(Boolean).map((line) => line.split(",")[0])).toEqual([
        "Matter",
        "Estate late Dlamini",
        "Transfer Erf 12",
      ]);
      // Ticking a checkbox never opens the row.
      expect(mockPush).not.toHaveBeenCalled();
    } finally {
      stub.restore();
    }
  });

  it("with pageSize set, exports the current page's rows", async () => {
    const user = userEvent.setup();
    const stub = stubDownload();
    try {
      renderTable({ data: makeRows(60), pageSize: 25, exportCsv: { filename: "page.csv" } });
      await user.click(screen.getByRole("button", { name: "Go to next page" }));
      await user.click(screen.getByRole("button", { name: "Export CSV" }));
      const lines = (await blobText(stub.captured.blob))
        .slice(CSV_BOM.length)
        .split("\r\n")
        .filter(Boolean);
      expect(lines).toHaveLength(1 + 25);
      expect(lines[1].startsWith("Row 026,")).toBe(true);
    } finally {
      stub.restore();
    }
  });
});

describe("DataTable — pagination", () => {
  it("pageSize null (default) renders every row and no pagination control", () => {
    renderTable({ data: makeRows(250) });
    expect(screen.getAllByTestId("fixture-row")).toHaveLength(250);
    expect(screen.queryByRole("combobox", { name: "Rows per page" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Go to next page" })).toBeNull();
  });

  it("pageSize 25 paginates; Next moves a page; the page-size select changes the page size", async () => {
    const user = userEvent.setup();
    renderTable({ data: makeRows(250), pageSize: 25 });
    expect(screen.getAllByTestId("fixture-row")).toHaveLength(25);
    expect(screen.getByText("Page 1 of 10")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Go to previous page" })).toBeDisabled();

    await user.click(screen.getByRole("button", { name: "Go to next page" }));
    expect(screen.getByText("Page 2 of 10")).toBeInTheDocument();
    expect(screen.getAllByTestId("fixture-row")[0]).toHaveTextContent("Row 026");

    await user.click(screen.getByRole("combobox", { name: "Rows per page" }));
    await user.click(await screen.findByRole("option", { name: "50" }));
    expect(screen.getAllByTestId("fixture-row")).toHaveLength(50);
    expect(screen.getByText("Page 1 of 5")).toBeInTheDocument();
  });

  it("resets the page index and the selection when a new data array arrives", async () => {
    const user = userEvent.setup();
    const data = makeRows(60);
    const { rerender } = renderTable({ data, pageSize: 25, selectable: true });
    await user.click(screen.getAllByRole("checkbox", { name: "Select row" })[0]);
    await user.click(screen.getByRole("button", { name: "Go to next page" }));
    expect(screen.getByText("Page 2 of 3")).toBeInTheDocument();

    rerender(<DataTable {...tableProps({ data: [...data], pageSize: 25, selectable: true })} />);
    expect(screen.getByText("Page 1 of 3")).toBeInTheDocument();
    screen
      .getAllByRole("checkbox", { name: "Select row" })
      .forEach((box) => expect(box).toHaveAttribute("aria-checked", "false"));
  });
});

describe("DataTable — row click", () => {
  const rowHref = (row: Row) => `/org/acme/matters/${row.id}`;

  it("opens rowHref on a plain-cell click", () => {
    renderTable({ rowHref });
    const row = screen.getAllByTestId("fixture-row")[2];
    expect(row).toHaveClass("cursor-pointer");
    fireEvent.click(within(row).getByText("Naidoo"));
    expect(mockPush).toHaveBeenCalledWith("/org/acme/matters/c");
  });

  it("ignores clicks on links, buttons, menus, non-primary buttons and the selection cell", async () => {
    const user = userEvent.setup();
    renderTable({ rowHref, selectable: true });
    const row = screen.getAllByTestId("fixture-row")[0];

    fireEvent.click(within(row).getByRole("link", { name: "Estate late Dlamini" }));
    fireEvent.click(within(row).getByRole("button", { name: "Flag a" }));
    fireEvent.click(within(row).getByText("plain"), { button: 1 });
    fireEvent.click(within(row).getByRole("checkbox", { name: "Select row" }).closest("td")!);

    await user.click(within(row).getByRole("button", { name: "Fixture row menu a" }));
    await user.click(await screen.findByRole("menuitem", { name: "Fixture open a" }));

    expect(mockPush).not.toHaveBeenCalled();
  });
});

describe("DataTable — empty, loading, toolbar", () => {
  it("replaces the table with the empty slot: no <table>, no footer, no table controls", () => {
    renderTable({
      data: [],
      footer: <p>Showing 0 fixture rows</p>,
      exportCsv: { filename: "x.csv" },
      toolbar: { search: <input aria-label="Fixture search" /> },
    });
    expect(screen.getByText("Nothing in this fixture.")).toBeInTheDocument();
    expect(screen.queryByRole("table")).toBeNull();
    expect(screen.queryByText("Showing 0 fixture rows")).toBeNull();
    expect(screen.queryByRole("button", { name: "Columns" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Export CSV" })).toBeNull();
    // Toolbar slots stay (search and filters remain usable on an empty list).
    expect(screen.getByRole("textbox", { name: "Fixture search" })).toBeInTheDocument();
  });

  it("loading renders 8 skeleton rows, no test-id rows, no empty slot and no footer", () => {
    renderTable({ data: [], loading: true, footer: <p>Fixture footer</p> });
    const table = screen.getByRole("table");
    expect(table).toHaveAttribute("aria-busy", "true");
    const bodyRows = table.querySelectorAll("tbody tr");
    expect(bodyRows).toHaveLength(8);
    bodyRows.forEach((tr) => {
      expect(tr.querySelector('[data-slot="skeleton"]')).not.toBeNull();
      expect(tr).not.toHaveAttribute("data-testid");
    });
    expect(screen.queryByText("Nothing in this fixture.")).toBeNull();
    expect(screen.queryByText("Fixture footer")).toBeNull();
  });

  it("renders the toolbar slots and the footer under the rows", () => {
    renderTable({
      footer: <p>Showing 3 fixture rows</p>,
      toolbar: {
        views: <nav aria-label="Fixture views">views</nav>,
        search: <input aria-label="Fixture search" />,
        filters: <button type="button">Fixture filter</button>,
        actions: <button type="button">Fixture action</button>,
      },
      exportCsv: { filename: "x.csv" },
    });
    expect(screen.getByRole("navigation", { name: "Fixture views" })).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Fixture search" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Fixture filter" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Fixture action" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Columns" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Export CSV" })).toBeInTheDocument();
    expect(screen.getByText("Showing 3 fixture rows")).toBeInTheDocument();
  });
});

// ---- RSC boundary (639.16 / OQ #19) ------------------------------------------------------------

interface LiteMatterRow {
  id: string;
  name: string;
  clientName: string;
}

interface FixtureMattersTableProps {
  slug: string;
  rows: LiteMatterRow[];
  sort: MatterSort;
  query: Record<string, string>;
}

/**
 * The consumer pattern: a "use client" wrapper takes only serializable props (what an RSC may
 * pass) and builds the columns, `hrefFor` and `rowHref` itself.
 */
function FixtureMattersTable({ slug, rows, sort, query }: FixtureMattersTableProps) {
  const fixtureColumns = useMemo(
    () =>
      defineColumns<LiteMatterRow>([
        {
          id: "name",
          enableHiding: false,
          meta: { label: "Matter", sortKey: "name", csv: (r) => r.name },
          cell: ({ row }) => (
            <Link href={`/org/${slug}/matters/${row.original.id}`}>{row.original.name}</Link>
          ),
        },
        {
          id: "clientName",
          meta: { label: "Client", sortKey: "clientName", csv: (r) => r.clientName },
          cell: ({ row }) => row.original.clientName,
        },
      ]),
    [slug]
  );
  return (
    <DataTable
      columns={fixtureColumns}
      data={rows}
      getRowId={(row) => row.id}
      sort={{
        key: sort.key,
        dir: sort.dir,
        hrefFor: (key) => buildSortHref(slug, query, key as MatterSort["key"], sort),
      }}
      rowHref={(row) => `/org/${slug}/matters/${row.id}`}
      rowTestId="fixture-matter-row"
      empty={<p>No fixture matters.</p>}
    />
  );
}

describe("DataTable — RSC boundary rule", () => {
  it("a client wrapper takes serializable props and builds the function props inside", () => {
    const props: FixtureMattersTableProps = {
      slug: "acme",
      rows: [{ id: "m1", name: "Estate late Mokoena", clientName: "Mokoena" }],
      sort: { key: "name", dir: "asc" },
      query: { view: "system:dormant", search: "raf" },
    };
    // Everything an RSC would pass survives serialization unchanged; nothing is a function.
    expect(JSON.parse(JSON.stringify(props))).toEqual(props);
    expect(Object.values(props).some((value) => typeof value === "function")).toBe(false);

    render(<FixtureMattersTable {...props} />);
    const href = within(screen.getByRole("columnheader", { name: "Client" }))
      .getByRole("link")
      .getAttribute("href")!;
    const url = new URL(href, "http://x");
    expect(url.pathname).toBe("/org/acme/matters");
    expect(Object.fromEntries(url.searchParams)).toEqual({
      view: "system:dormant",
      search: "raf",
      sort: "clientName",
      dir: "asc",
    });

    fireEvent.click(within(screen.getByTestId("fixture-matter-row")).getByText("Mokoena"));
    expect(mockPush).toHaveBeenCalledWith("/org/acme/matters/m1");
  });

  it("every module that imports DataTable is a client module", () => {
    const sources = readSourceTree(["app", "components", "lib", "hooks"]);
    const dataTable = sources.find((s) => s.rel.endsWith("components/data-table/data-table.tsx"));
    expect(dataTable?.text).toMatch(/^"use client";$/m);

    const importers = sources.filter(
      (s) =>
        /from "@\/components\/data-table\/data-table"/.test(s.text) ||
        (/components\/data-table\//.test(s.rel) && /from "\.\/data-table"/.test(s.text))
    );
    const serverImporters = importers
      .filter((s) => !/^"use client";$/m.test(s.text))
      .map((s) => s.rel);
    expect(serverImporters).toEqual([]);
  });
});
