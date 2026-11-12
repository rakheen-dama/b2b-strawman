import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ClientsDataTable } from "@/components/clients/clients-table";
import { ClientLifecycleFilter } from "@/components/clients/client-lifecycle-filter";
import { CSV_BOM } from "@/components/data-table/data-table-csv";
import { defaultClientColumns, type ClientSort } from "@/lib/clients-list";
import { formatCurrency, formatDate, formatDateInSA } from "@/lib/format";
import type { ClientListRow } from "@/lib/types";

const mockPush = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush, replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => "/org/acme/clients",
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

function row(overrides: Partial<ClientListRow> = {}): ClientListRow {
  return {
    id: "c1",
    name: "Sipho Ndlovu",
    email: "sipho@example.com",
    phone: "+27 82 555 0101",
    clientType: "INDIVIDUAL",
    lifecycleStatus: "ACTIVE",
    status: "ACTIVE",
    createdAt: "2026-05-01T08:00:00Z",
    lifecycleStatusChangedAt: null,
    ficaStatus: "DONE",
    ficaLastVerifiedAt: "2026-05-03T08:00:00Z",
    outstandingFeeNoteTotal: 18400,
    outstandingFeeNoteCount: 2,
    currency: "ZAR",
    trustBalance: 5000,
    openMatterCount: 3,
    completenessPercent: 80,
    tags: [],
    customFields: {},
    ...overrides,
  };
}

const DEFAULT_SORT: ClientSort = { key: "name", dir: "asc" };

interface RenderOptions {
  columns?: string[];
  sort?: ClientSort;
  query?: Record<string, string>;
  hideLowPriorityColumns?: boolean;
  footer?: React.ReactNode;
}

/** The serializable props the server page passes (no `empty` / `footer` / `toolbar` nodes). */
function serializableProps(rows: ClientListRow[], opts: RenderOptions = {}) {
  return {
    slug: "acme",
    rows,
    columns: opts.columns ?? defaultClientColumns(true),
    customFieldNames: { vat_number: "VAT number" },
    sort: opts.sort ?? DEFAULT_SORT,
    query: opts.query ?? {},
    caption: "Clients — All clients",
    hideLowPriorityColumns: opts.hideLowPriorityColumns,
  };
}

function renderTable(rows: ClientListRow[], opts: RenderOptions = {}) {
  return render(
    <ClientsDataTable
      {...serializableProps(rows, opts)}
      empty={<p>No fixture clients.</p>}
      footer={opts.footer}
    />
  );
}

/** Captures the CSV Blob and the download name of each "Export CSV" click (639C pattern). */
function stubDownload() {
  const captured: { blobs: Blob[]; downloads: string[] } = { blobs: [], downloads: [] };
  const originalCreate = URL.createObjectURL;
  const originalRevoke = URL.revokeObjectURL;
  URL.createObjectURL = vi.fn((blob: Blob) => {
    captured.blobs.push(blob);
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
    /** Waits one task first so the deferred `revokeObjectURL` still hits the mock. */
    async restore() {
      await new Promise((resolve) => setTimeout(resolve, 0));
      URL.createObjectURL = originalCreate;
      URL.revokeObjectURL = originalRevoke;
    },
  };
}

async function blobText(blob: Blob | undefined): Promise<string> {
  if (!blob) throw new Error("no CSV blob was created");
  // ignoreBOM keeps a leading BOM in the decoded text so the test can assert it.
  return new TextDecoder("utf-8", { ignoreBOM: true }).decode(await blob.arrayBuffer());
}

/** CSV records after the BOM (the trailing CRLF leaves an empty last element). */
async function csvRecords(blob: Blob | undefined): Promise<string[]> {
  const text = await blobText(blob);
  expect(text.startsWith(CSV_BOM)).toBe(true);
  return text.slice(CSV_BOM.length).split("\r\n");
}

beforeEach(() => vi.clearAllMocks());
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

// The kit's sort icons are svg (no text); the old arrows are stripped for safety.
const headerText = (h: HTMLElement) => h.textContent?.replace(/[↑↓]/g, "").trim();

/** The body row's cell under the column headed `label`. */
function cellUnder(label: string, rowIndex = 0): HTMLElement {
  const headers = screen.getAllByRole("columnheader").map(headerText);
  const index = headers.indexOf(label);
  if (index === -1) throw new Error(`No "${label}" column; headers: ${headers.join(", ")}`);
  return within(screen.getAllByTestId("client-row")[rowIndex]).getAllByRole("cell")[index];
}

const cellText = (label: string, rowIndex = 0) => cellUnder(label, rowIndex).textContent?.trim();
const headerCell = (label: string) =>
  screen.getAllByRole("columnheader").find((h) => headerText(h) === label);

const DEFAULT_HEADERS = [
  "Client",
  "Lifecycle",
  "FICA",
  "Open matters",
  "Outstanding fee notes",
  "Trust balance",
  "Completeness",
  "Created",
  "Contact",
];

describe("ClientsDataTable — columns", () => {
  it("renders a visually hidden caption and scope=col headers in §12.6.4 order", () => {
    renderTable([row()]);
    const table = screen.getByRole("table");
    expect(table).toHaveAttribute("data-testid", "clients-table");
    const caption = table.querySelector("caption");
    expect(caption).toHaveTextContent("Clients — All clients");
    expect(caption).toHaveClass("sr-only");

    const headers = within(table).getAllByRole("columnheader");
    expect(headers.map(headerText)).toEqual([...DEFAULT_HEADERS, "Actions"]);
    for (const h of headers) expect(h).toHaveAttribute("scope", "col");
  });

  it("omits the Trust balance column when trust accounting is off (F-8)", () => {
    renderTable([row()], { columns: defaultClientColumns(false) });
    expect(headerCell("Trust balance")).toBeUndefined();
  });

  it("renders one body row per client from ClientListRow fields", () => {
    renderTable([row(), row({ id: "c2", name: "Apex Holdings", clientType: "COMPANY" })]);
    const rows = screen.getAllByTestId("client-row");
    expect(rows).toHaveLength(2);
    rows.forEach((tr) => expect(tr.tagName).toBe("TR"));
    expect(cellText("Lifecycle")).toBe("Active");
    expect(cellText("Open matters")).toBe("3");
    expect(cellText("Trust balance")).toBe(formatCurrency(5000, "ZAR"));
    expect(cellText("Completeness")).toBe("80%");
    expect(cellText("Created")).toBe(formatDateInSA("2026-05-01T08:00:00Z"));
    // Email and phone stack, each truncated with its full text in the title.
    expect(within(cellUnder("Contact")).getByTitle("sipho@example.com")).toBeInTheDocument();
    expect(within(cellUnder("Contact")).getByTitle("+27 82 555 0101")).toBeInTheDocument();
  });

  it("right-aligns the numeric columns in mono tabular figures", () => {
    renderTable([row()]);
    for (const label of ["Open matters", "Outstanding fee notes", "Trust balance"]) {
      expect(headerCell(label), label).toHaveClass("text-right");
      expect(cellUnder(label), label).toHaveClass("text-right", "font-mono", "tabular-nums");
    }
    expect(cellUnder("Lifecycle")).not.toHaveClass("font-mono");
  });
});

describe("ClientsDataTable — Client cell (D-9)", () => {
  it("keeps the client type outside the name link, so the link name is exactly the name", () => {
    renderTable([row({ clientType: "TRUST", name: "Moroka Family Trust" })]);
    const link = screen.getByRole("link", { name: "Moroka Family Trust" });
    expect(link).toHaveAttribute("href", "/org/acme/clients/c1");
    expect(link).toHaveAttribute("data-testid", "client-link");
    expect(link).toHaveAttribute("title", "Moroka Family Trust");
    const suffix = screen.getByTestId("client-type");
    expect(suffix).toHaveTextContent("Trust");
    expect(link).not.toContainElement(suffix);
  });

  it("renders no suffix for a null client type, and at most three tag chips", () => {
    const tags = ["a", "b", "c", "d"].map((t) => ({
      id: t,
      name: `Tag ${t}`,
      slug: t,
      color: null,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-01T00:00:00Z",
    }));
    renderTable([row({ clientType: null, tags })]);
    expect(screen.queryByTestId("client-type")).not.toBeInTheDocument();
    expect(screen.getByText("Tag c")).toBeInTheDocument();
    expect(screen.queryByText("Tag d")).not.toBeInTheDocument();
    expect(screen.getByText("+1")).toBeInTheDocument();
  });
});

describe("ClientsDataTable — FICA, fee notes, financial redaction", () => {
  it("renders the three FICA variants", () => {
    renderTable([
      row({ id: "a", ficaStatus: "DONE", ficaLastVerifiedAt: "2026-10-03T08:00:00Z" }),
      row({ id: "b", ficaStatus: "IN_PROGRESS", ficaLastVerifiedAt: null }),
      row({ id: "c", ficaStatus: "NOT_STARTED", ficaLastVerifiedAt: null }),
    ]);
    const [done, inProgress, notStarted] = screen.getAllByTestId("client-fica-status");
    expect(done).toHaveTextContent("Done");
    expect(done).toHaveAttribute("data-variant", "default");
    expect(cellText("FICA", 0)).toContain(formatDateInSA("2026-10-03T08:00:00Z"));
    expect(inProgress).toHaveTextContent("In progress");
    expect(inProgress).toHaveAttribute("data-variant", "outline");
    expect(notStarted).toHaveTextContent("Not started");
    expect(notStarted.tagName).toBe("SPAN");
    expect(notStarted).toHaveClass("text-destructive");
  });

  it('renders outstanding fee notes as "R … · n", count only when redacted, "—" when none', () => {
    renderTable([
      row({ id: "a", outstandingFeeNoteTotal: 18400, outstandingFeeNoteCount: 2 }),
      row({ id: "b", outstandingFeeNoteTotal: null, outstandingFeeNoteCount: 2 }),
      row({ id: "c", outstandingFeeNoteTotal: 0, outstandingFeeNoteCount: 0 }),
    ]);
    expect(cellText("Outstanding fee notes", 0)).toBe(`${formatCurrency(18400, "ZAR")} · 2`);
    expect(cellText("Outstanding fee notes", 1)).toBe("2");
    expect(cellText("Outstanding fee notes", 2)).toBe("—");
  });

  it("renders — (never 0) for a redacted trust balance, and N/A for null completeness", () => {
    renderTable([row({ trustBalance: null, completenessPercent: null })]);
    expect(cellText("Trust balance")).toBe("—");
    expect(cellText("Completeness")).toBe("N/A");
  });

  it("renders the muted — placeholder for a null lifecycle and no contact details", () => {
    renderTable([row({ lifecycleStatus: null, email: "", phone: null })]);
    expect(cellText("Lifecycle")).toBe("—");
    expect(within(cellUnder("Lifecycle")).getByText("—")).toHaveClass("text-muted-foreground");
    expect(cellText("Contact")).toBe("—");
  });
});

describe("ClientsDataTable — sorting", () => {
  it("sort links keep the URL params and use sortBy/sortDir; Contact is not sortable", () => {
    renderTable([row()], { query: { view: "system:active", showIncomplete: "true" } });
    const completeness = within(headerCell("Completeness")!).getByRole("link");
    expect(completeness).toHaveAttribute(
      "href",
      "/org/acme/clients?view=system%3Aactive&showIncomplete=true&sortBy=completeness&sortDir=asc"
    );
    expect(within(headerCell("Contact")!).queryByRole("link")).not.toBeInTheDocument();
    expect(headerCell("Contact")).not.toHaveAttribute("aria-sort");
  });

  it("every other client column is a sort link", () => {
    renderTable([row()]);
    for (const label of DEFAULT_HEADERS.filter((l) => l !== "Contact")) {
      expect(within(headerCell(label)!).getByRole("link"), label).toHaveAttribute(
        "href",
        expect.stringMatching(/^\/org\/acme\/clients\?sortBy=\w+&sortDir=(asc|desc)$/)
      );
    }
  });

  it("marks the active column and toggles its direction", () => {
    renderTable([row()], { sort: { key: "completeness", dir: "asc" } });
    expect(headerCell("Completeness")).toHaveAttribute("aria-sort", "ascending");
    expect(headerCell("Client")).toHaveAttribute("aria-sort", "none");
    expect(within(headerCell("Completeness")!).getByRole("link")).toHaveAttribute(
      "href",
      "/org/acme/clients?sortBy=completeness&sortDir=desc"
    );
  });

  it("descending active sort → aria-sort descending, next click goes asc", () => {
    renderTable([row()], { sort: { key: "createdAt", dir: "desc" } });
    expect(headerCell("Created")).toHaveAttribute("aria-sort", "descending");
    expect(within(headerCell("Created")!).getByRole("link")).toHaveAttribute(
      "href",
      "/org/acme/clients?sortBy=createdAt&sortDir=asc"
    );
  });
});

describe("ClientsDataTable — responsive columns (D-8)", () => {
  it("hides low-priority columns in narrow containers (Contact first, Completeness last)", () => {
    renderTable([row()]);
    expect(headerCell("Completeness")).toHaveClass("hidden", "@min-[50rem]:table-cell");
    expect(headerCell("Open matters")).toHaveClass("hidden", "@min-[55rem]:table-cell");
    expect(headerCell("Created")).toHaveClass("hidden", "@min-[61rem]:table-cell");
    expect(headerCell("Contact")).toHaveClass("hidden", "@min-[74rem]:table-cell");
    expect(cellUnder("Completeness")).toHaveClass("hidden", "@min-[50rem]:table-cell");
    expect(cellUnder("Open matters")).toHaveClass("hidden", "@min-[55rem]:table-cell");
    expect(cellUnder("Created")).toHaveClass("hidden", "@min-[61rem]:table-cell");
    expect(cellUnder("Contact")).toHaveClass("hidden", "@min-[74rem]:table-cell");
    for (const label of ["Client", "Lifecycle", "FICA", "Outstanding fee notes", "Trust balance"]) {
      expect(headerCell(label), label).not.toHaveClass("hidden");
      expect(cellUnder(label), label).not.toHaveClass("hidden");
    }
    expect(headerCell("Actions")).not.toHaveClass("hidden");
  });

  it("is its own size container, so column queries measure the table's box", () => {
    renderTable([row()]);
    expect(screen.getByTestId("clients-table").closest(".\\@container")).not.toBeNull();
  });

  it("lets the Client column take the spare width without adding to the minimum", () => {
    renderTable([row()]);
    expect(headerCell("Client")).toHaveClass("w-full", "max-w-0");
    expect(cellUnder("Client")).toHaveClass("w-full", "max-w-0");
  });

  it("breaks a multi-word header after its first word, keeping its text", () => {
    renderTable([row()]);
    const header = headerCell("Outstanding fee notes")!;
    expect(within(header).getByText("Outstanding")).toBeInTheDocument();
    expect(within(header).getByText("fee notes")).toBeInTheDocument();
    // The accessible name (and the sort link's name) stays the whole label.
    expect(screen.getByRole("columnheader", { name: "Outstanding fee notes" })).toBe(header);
    expect(within(header).getByRole("link", { name: "Outstanding fee notes" })).toBeInTheDocument();
    expect(header.querySelector("svg")).toHaveAttribute("aria-hidden", "true");
  });

  it("never hides the active sort column", () => {
    renderTable([row()], { sort: { key: "createdAt", dir: "desc" } });
    expect(headerCell("Created")).not.toHaveClass("hidden");
    expect(cellUnder("Created")).not.toHaveClass("hidden");
    expect(headerCell("Contact")).toHaveClass("hidden");
  });

  it("never hides columns a saved view chose", () => {
    renderTable([row()], {
      columns: ["name", "openMatterCount", "contact", "cf:vat_number"],
      hideLowPriorityColumns: false,
    });
    expect(headerCell("Open matters")).not.toHaveClass("hidden");
    expect(headerCell("Contact")).not.toHaveClass("hidden");
    expect(cellUnder("Contact")).not.toHaveClass("hidden");
    expect(headerCell("VAT number")).toBeDefined();
  });
});

describe("ClientsDataTable — sticky header (639C OBS-CARRY)", () => {
  it("the default column set sticks its header from a 42rem frame; narrower frames scroll in place", () => {
    renderTable([row()]);
    screen.getAllByRole("columnheader").forEach((th) => {
      expect(th).toHaveClass("@2xl:sticky");
      expect(th).not.toHaveClass("sticky");
    });
    const container = screen.getByRole("table").closest('[data-slot="table-container"]');
    expect(container).toHaveClass("overflow-x-auto", "@2xl:overflow-x-visible");
  });

  it("a saved view's own columns scroll sideways in the frame, header not sticky", () => {
    renderTable([row()], { hideLowPriorityColumns: false });
    screen.getAllByRole("columnheader").forEach((th) => {
      expect(th).not.toHaveClass("sticky");
      expect(th).not.toHaveClass("@2xl:sticky");
    });
    const container = screen.getByRole("table").closest('[data-slot="table-container"]');
    expect(container).toHaveClass("overflow-x-auto");
    expect(container).not.toHaveClass("@2xl:overflow-x-visible");
  });
});

describe("ClientsDataTable — custom-field columns", () => {
  it("custom-field headers are plain text (no sort link, no aria-sort), slug when unnamed", () => {
    renderTable([row({ customFields: { vat_number: "4123456789" } })], {
      columns: ["name", "cf:vat_number", "cf:unknown_slug"],
    });
    const vat = screen.getByRole("columnheader", { name: "VAT number" });
    expect(within(vat).queryByRole("link")).toBeNull();
    expect(vat).not.toHaveAttribute("aria-sort");
    expect(screen.getAllByRole("columnheader").map(headerText)).toEqual([
      "Client",
      "VAT number",
      "unknown_slug",
      "Actions",
    ]);
    expect(cellText("VAT number")).toBe("4123456789");
    expect(cellText("unknown_slug")).toBe("—");
  });
});

describe("ClientsDataTable — row interaction", () => {
  it("a row click opens the client; clicks on links and the menu do not", async () => {
    renderTable([row()]);
    const tr = screen.getByTestId("client-row");
    expect(tr).toHaveClass("cursor-pointer");
    fireEvent.click(cellUnder("Lifecycle"));
    expect(mockPush).toHaveBeenCalledWith("/org/acme/clients/c1");

    mockPush.mockClear();
    fireEvent.click(screen.getByTestId("client-link"));
    fireEvent.click(screen.getByTestId("client-row-actions-trigger"));
    expect(mockPush).not.toHaveBeenCalled();
  });

  it("the row menu has a fixed label and an Open client link", async () => {
    const user = userEvent.setup();
    renderTable([row()]);
    const trigger = screen.getByTestId("client-row-actions-trigger");
    expect(trigger).not.toHaveAttribute("aria-label");
    expect(trigger).toHaveTextContent("Client actions");
    await user.click(trigger);
    expect(await screen.findByRole("menuitem", { name: "Open client" })).toHaveAttribute(
      "href",
      "/org/acme/clients/c1"
    );
    expect(mockPush).not.toHaveBeenCalled();
  });
});

describe("ClientsDataTable — table controls", () => {
  it("the Columns menu lists every hideable column (not Client, not Actions) and hides one", async () => {
    const user = userEvent.setup();
    renderTable([row()]);
    await user.click(screen.getByRole("button", { name: "Columns" }));
    const lifecycle = await screen.findByRole("menuitemcheckbox", { name: "Lifecycle" });
    expect(screen.getAllByRole("menuitemcheckbox").map((item) => item.textContent)).toEqual(
      DEFAULT_HEADERS.slice(1)
    );
    await user.click(lifecycle);
    await user.keyboard("{Escape}");
    expect(screen.queryByRole("columnheader", { name: "Lifecycle" })).toBeNull();
    expect(screen.queryByText("Active")).toBeNull();
    expect(mockPush).not.toHaveBeenCalled();
  });

  it("renders every row with no pagination control", () => {
    const rows = Array.from({ length: 30 }, (_, i) =>
      row({ id: `c${i + 1}`, name: `Client ${i + 1}` })
    );
    renderTable(rows);
    expect(screen.getAllByTestId("client-row")).toHaveLength(30);
    expect(screen.queryByText("Rows per page")).toBeNull();
    expect(screen.queryByRole("button", { name: /Next|Previous/ })).toBeNull();
  });

  it("no rows → the empty node instead of the table, footer and controls", () => {
    renderTable([], { footer: <p data-testid="clients-footer">Showing 0 clients</p> });
    expect(screen.getByText("No fixture clients.")).toBeInTheDocument();
    expect(screen.queryByRole("table")).toBeNull();
    expect(screen.queryByTestId("clients-footer")).toBeNull();
    expect(screen.queryByRole("button", { name: "Columns" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Export CSV" })).toBeNull();
  });

  it("renders the footer after the table when rows render", () => {
    renderTable([row(), row({ id: "c2", name: "Apex Holdings" })], {
      footer: <p data-testid="clients-footer">Showing 2 clients</p>,
    });
    const footer = screen.getByTestId("clients-footer");
    expect(footer).toHaveTextContent("Showing 2 clients");
    expect(
      screen.getByTestId("clients-table").compareDocumentPosition(footer) &
        Node.DOCUMENT_POSITION_FOLLOWING
    ).toBeTruthy();
  });
});

describe("ClientsDataTable — CSV export", () => {
  it("exports the visible columns × rendered rows as clients.csv; a hidden column drops out", async () => {
    const user = userEvent.setup();
    const stub = stubDownload();
    try {
      renderTable([
        row(),
        row({
          id: "c2",
          name: "Apex Holdings",
          lifecycleStatus: null,
          ficaStatus: "NOT_STARTED",
          ficaLastVerifiedAt: null,
          outstandingFeeNoteTotal: null,
          outstandingFeeNoteCount: 2,
          trustBalance: null,
          completenessPercent: null,
          email: "",
        }),
      ]);
      await user.click(screen.getByRole("button", { name: "Export CSV" }));
      expect(stub.captured.downloads).toEqual(["clients.csv"]);

      const [header, first, second, end] = await csvRecords(stub.captured.blobs[0]);
      expect(header).toBe(DEFAULT_HEADERS.join(","));
      // The outstanding text holds a decimal comma, hence the quotes.
      expect(first).toBe(
        [
          "Sipho Ndlovu",
          "Active",
          "Done · 3 May 2026",
          "3",
          `"${formatCurrency(18400, "ZAR")} · 2"`,
          "5000",
          "80%",
          "1 May 2026",
          "sipho@example.com · +27 82 555 0101",
        ].join(",")
      );
      // Redacted total → the count only; redacted trust → empty; a lone phone keeps the formula
      // guard (it starts with "+").
      expect(second).toBe("Apex Holdings,,Not started,3,2,,N/A,1 May 2026,'+27 82 555 0101");
      expect(end).toBe("");

      await user.click(screen.getByRole("button", { name: "Columns" }));
      await user.click(await screen.findByRole("menuitemcheckbox", { name: "Lifecycle" }));
      await user.keyboard("{Escape}");
      await user.click(screen.getByRole("button", { name: "Export CSV" }));
      const [hiddenHeader] = await csvRecords(stub.captured.blobs[1]);
      expect(hiddenHeader).toBe(DEFAULT_HEADERS.filter((l) => l !== "Lifecycle").join(","));
      expect(mockPush).not.toHaveBeenCalled();
    } finally {
      await stub.restore();
    }
  });

  it("custom-field numbers export as numbers; text keeps the formula guard", async () => {
    const user = userEvent.setup();
    const stub = stubDownload();
    try {
      renderTable(
        [
          row({ customFields: { vat_number: "=CMD()", fee: 1250.5 } }),
          row({ id: "c2", name: "Apex Holdings", customFields: {} }),
        ],
        { columns: ["name", "cf:vat_number", "cf:fee"] }
      );
      await user.click(screen.getByRole("button", { name: "Export CSV" }));
      const [header, first, second] = await csvRecords(stub.captured.blobs[0]);
      expect(header).toBe("Client,VAT number,fee");
      expect(first).toBe("Sipho Ndlovu,'=CMD(),1250.5");
      expect(second).toBe("Apex Holdings,,");
    } finally {
      await stub.restore();
    }
  });
});

describe("ClientsDataTable — dates across time zones", () => {
  // 01:30 SAST on 2 May; still 1 May in UTC (the SSR server) and in New York.
  const LATE_INSTANT = "2026-05-01T23:30:00Z";

  /** Moves the process zone (Date and Intl follow), guarding against a vacuous stub. */
  function inZone(zone: string) {
    vi.stubEnv("TZ", zone);
    expect(Intl.DateTimeFormat().resolvedOptions().timeZone).toBe(zone);
  }

  afterEach(() => vi.unstubAllEnvs());

  it("a zone-less format would differ by viewer (the stub is not vacuous)", () => {
    inZone("UTC");
    expect(formatDate(LATE_INSTANT)).toBe("1 May 2026");
    inZone("Pacific/Kiritimati");
    expect(formatDate(LATE_INSTANT)).toBe("2 May 2026");
  });

  it.each(["UTC", "America/New_York", "Africa/Johannesburg", "Pacific/Kiritimati"])(
    "Created and the FICA verified date show the South African date (cell and CSV) under TZ=%s",
    async (zone) => {
      inZone(zone);
      const user = userEvent.setup();
      const stub = stubDownload();
      try {
        renderTable([row({ createdAt: LATE_INSTANT, ficaLastVerifiedAt: LATE_INSTANT })], {
          columns: ["name", "ficaStatus", "createdAt"],
          hideLowPriorityColumns: false,
        });
        expect(cellText("Created")).toBe("2 May 2026");
        expect(cellText("FICA")).toBe("Done2 May 2026");

        await user.click(screen.getByRole("button", { name: "Export CSV" }));
        const [, first] = await csvRecords(stub.captured.blobs[0]);
        expect(first).toBe("Sipho Ndlovu,Done · 2 May 2026,2 May 2026");
      } finally {
        await stub.restore();
      }
    }
  );
});

describe("ClientsDataTable — RSC boundary", () => {
  it("takes only serializable props: a JSON round-trip renders the same table", () => {
    const props = serializableProps([row()], {
      query: { view: "system:active" },
      sort: { key: "completeness", dir: "desc" },
    });
    const roundTripped = JSON.parse(JSON.stringify(props));
    expect(roundTripped).toEqual(props);
    render(<ClientsDataTable {...roundTripped} empty={null} />);
    expect(screen.getAllByTestId("client-row")).toHaveLength(1);
    expect(headerCell("Completeness")).toHaveAttribute("aria-sort", "descending");
  });
});

describe("ClientLifecycleFilter", () => {
  const base = {
    slug: "acme",
    query: { search: "apex" },
    activeLifecycle: null,
    viewLifecycle: null,
    showLifecycle: true,
    showIncomplete: false,
  } as const;

  it("All is active with no lifecycle; pills keep the other params", () => {
    render(<ClientLifecycleFilter {...base} />);
    const group = screen.getByRole("group", { name: "Lifecycle" });
    const all = within(group).getByRole("link", { name: "All" });
    expect(all).toHaveAttribute("aria-current", "true");
    expect(all).toHaveAttribute("href", "/org/acme/clients?search=apex");
    expect(within(group).getByRole("link", { name: "Prospect" })).toHaveAttribute(
      "href",
      "/org/acme/clients?search=apex&lifecycleStatus=PROSPECT"
    );
  });

  it("on the Active view, All writes lifecycleStatus=ALL to lift the view's lifecycle (D-2)", () => {
    render(<ClientLifecycleFilter {...base} activeLifecycle="ACTIVE" viewLifecycle="ACTIVE" />);
    const group = screen.getByRole("group", { name: "Lifecycle" });
    expect(within(group).getByRole("link", { name: "Active" })).toHaveAttribute(
      "aria-current",
      "true"
    );
    expect(within(group).getByRole("link", { name: "All" })).toHaveAttribute(
      "href",
      "/org/acme/clients?search=apex&lifecycleStatus=ALL"
    );
  });

  it("Show incomplete toggles ?showIncomplete=true", () => {
    const { unmount } = render(<ClientLifecycleFilter {...base} />);
    expect(screen.getByTestId("client-show-incomplete")).toHaveAttribute(
      "href",
      "/org/acme/clients?search=apex&showIncomplete=true"
    );
    unmount();
    render(
      <ClientLifecycleFilter
        {...base}
        query={{ showIncomplete: "true", sortBy: "completeness" }}
        showIncomplete
      />
    );
    const toggle = screen.getByTestId("client-show-incomplete");
    expect(toggle).toHaveAttribute("aria-current", "true");
    expect(toggle).toHaveAttribute("href", "/org/acme/clients?sortBy=completeness");
  });

  it("hides the lifecycle pills on a saved view but keeps Show incomplete", () => {
    render(<ClientLifecycleFilter {...base} showLifecycle={false} />);
    expect(screen.queryByTestId("client-lifecycle-filter")).not.toBeInTheDocument();
    expect(screen.getByTestId("client-show-incomplete")).toBeInTheDocument();
  });
});
