import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MattersDataTable } from "@/components/matters/matters-table";
import { CSV_BOM } from "@/components/data-table/data-table-csv";
import {
  DEFAULT_MATTER_VIEW_ID,
  MATTER_SYSTEM_VIEWS,
  resolveViewSelection,
} from "@/lib/constants/system-views";
import { buildMatterListParams, defaultMatterColumns, type MatterSort } from "@/lib/matters-list";
import type { MatterListRow } from "@/lib/types";

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

function row(overrides: Partial<MatterListRow> = {}): MatterListRow {
  return {
    id: "m1",
    name: "RAF claim — Ndlovu",
    description: null,
    referenceNumber: "LIT-0001",
    status: "ACTIVE",
    workType: "PERSONAL_INJURY",
    priority: null,
    dueDate: null,
    createdAt: "2026-05-01T08:00:00Z",
    clientId: "c1",
    clientName: "Sipho Ndlovu",
    feeEarnerId: "u1",
    feeEarnerName: "Alice Mokoena",
    matterRole: "lead",
    unbilledMinutes: 1260,
    unbilledValue: 42000,
    currency: "ZAR",
    trustBalance: 15000,
    nextDiaryDate: "2026-11-03",
    nextDiaryKind: "COURT_DATE",
    openTaskCount: 2,
    tags: [],
    customFields: {},
    ...overrides,
  };
}

const DEFAULT_SORT: MatterSort = { key: "nextDiaryDate", dir: "asc" };

interface RenderOptions {
  columns?: string[];
  sort?: MatterSort;
  query?: Record<string, string>;
  hideLowPriorityColumns?: boolean;
  footer?: React.ReactNode;
}

/** The serializable props the server page passes (no `empty` / `footer` / `toolbar` nodes). */
function serializableProps(rows: MatterListRow[], opts: RenderOptions = {}) {
  return {
    slug: "acme",
    rows,
    columns: opts.columns ?? defaultMatterColumns(true),
    customFieldNames: { court: "Court" },
    sort: opts.sort ?? DEFAULT_SORT,
    query: opts.query ?? {},
    caption: "Matters — Open matters",
    hideLowPriorityColumns: opts.hideLowPriorityColumns,
  };
}

function renderTable(rows: MatterListRow[], opts: RenderOptions = {}) {
  return render(
    <MattersDataTable
      {...serializableProps(rows, opts)}
      empty={<p>No fixture matters.</p>}
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

beforeEach(() => vi.clearAllMocks());
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

/**
 * The first body row's cell under the column headed `label`. Located via the header index, so
 * reordering columns does not break the assertions.
 */
function cellUnder(label: string): HTMLElement {
  const headers = screen
    .getAllByRole("columnheader")
    .map((h) => h.textContent?.replace(/[↑↓]/g, "").trim());
  const index = headers.indexOf(label);
  if (index === -1) throw new Error(`No "${label}" column; headers: ${headers.join(", ")}`);
  return within(screen.getAllByTestId("matter-row")[0]).getAllByRole("cell")[index];
}

const cellText = (label: string) => cellUnder(label).textContent?.trim();

describe("MattersDataTable — columns", () => {
  it("renders a visually hidden caption and scope=col headers in §12.6.4 order", () => {
    renderTable([row()]);
    const table = screen.getByRole("table");
    const caption = table.querySelector("caption");
    expect(caption).toHaveTextContent("Matters — Open matters");
    expect(caption).toHaveClass("sr-only");

    const headers = within(table).getAllByRole("columnheader");
    expect(headers.map((h) => h.textContent?.replace(/[↑↓]/g, "").trim())).toEqual([
      "Reference",
      "Matter",
      "Client",
      "Work type",
      "Fee earner",
      "Status",
      "Opened",
      "Unbilled",
      "Trust",
      "Next in diary",
      "Actions",
    ]);
    for (const h of headers) expect(h).toHaveAttribute("scope", "col");
  });

  it("renders one body row per matter with links in Reference → Matter → Client order", () => {
    renderTable([row(), row({ id: "m2", name: "Second matter", referenceNumber: "LIT-0002" })]);
    const bodyRows = screen.getAllByTestId("matter-row");
    expect(bodyRows).toHaveLength(2);

    const links = within(bodyRows[0]).getAllByRole("link");
    expect(links.map((l) => l.getAttribute("href"))).toEqual([
      "/org/acme/matters/m1",
      "/org/acme/matters/m1",
      "/org/acme/clients/c1",
    ]);
    expect(links[0]).toHaveTextContent("LIT-0001");
    expect(links[0]).toHaveClass("font-mono");
    expect(links[1]).toHaveAttribute("data-testid", "matter-link");
    expect(links[1]).toHaveTextContent("RAF claim — Ndlovu");
    expect(links[2]).toHaveTextContent("Sipho Ndlovu");
  });

  it("formats work type, fee earner, status, unbilled, trust and next diary", () => {
    renderTable([row()]);
    const r = screen.getByTestId("matter-row");
    expect(within(r).getByText("Personal injury")).toBeInTheDocument();
    expect(within(r).getByText("Alice Mokoena")).toBeInTheDocument();
    expect(within(r).getByText("AM")).toBeInTheDocument();
    expect(within(r).getByTestId("matter-status-badge")).toHaveTextContent("Active");
    expect(r.textContent).toMatch(/21h · R\s?42/);
    expect(r.textContent).toMatch(/R\s?15/);
    // Kind marker is never colour-only: visually hidden label beside the icon
    expect(within(r).getByText(/Court date/)).toHaveClass("sr-only");
  });

  it("renders only the saved view's columns, including custom-field columns", () => {
    renderTable([row({ customFields: { court: "Durban High Court" } })], {
      columns: ["name", "status", "cf:court"],
    });
    const headers = screen.getAllByRole("columnheader").map((h) => h.textContent?.trim());
    expect(headers).toEqual(["Matter", "Status", "Court", "Actions"]);
    expect(screen.getByText("Durban High Court")).toBeInTheDocument();
    expect(screen.queryByText("LIT-0001")).not.toBeInTheDocument();
  });
});

describe("MattersDataTable — fitting the container", () => {
  const LOW_PRIORITY = ["Work type", "Fee earner", "Opened"];

  function headerFor(label: string): HTMLElement {
    const header = screen
      .getAllByRole("columnheader")
      .find((h) => h.textContent?.replace(/[↑↓]/g, "").trim() === label);
    if (!header) throw new Error(`No "${label}" header`);
    return header;
  }

  it("is its own size container, so column queries measure the table's box", () => {
    renderTable([row()]);
    expect(screen.getByTestId("matters-table").closest(".\\@container")).not.toBeNull();
  });

  it("hides Work type, Fee earner and Opened (header and cells) in narrow containers", () => {
    renderTable([row()]);
    expect(headerFor("Fee earner")).toHaveClass("hidden", "@min-[67rem]:table-cell");
    expect(cellUnder("Fee earner")).toHaveClass("hidden", "@min-[67rem]:table-cell");
    for (const label of ["Work type", "Opened"]) {
      expect(headerFor(label), label).toHaveClass("hidden", "@7xl:table-cell");
      expect(cellUnder(label), label).toHaveClass("hidden", "@7xl:table-cell");
    }
    for (const label of ["Reference", "Matter", "Client", "Status", "Unbilled", "Trust"]) {
      expect(headerFor(label), label).not.toHaveClass("hidden");
      expect(cellUnder(label), label).not.toHaveClass("hidden");
    }
    expect(headerFor("Actions")).not.toHaveClass("hidden");
  });

  it("never hides the active sort column (Dormant sorts by Opened)", () => {
    renderTable([row()], { sort: { key: "createdAt", dir: "asc" } });
    expect(headerFor("Opened")).not.toHaveClass("hidden");
    expect(cellUnder("Opened")).not.toHaveClass("hidden");
    expect(headerFor("Work type")).toHaveClass("hidden");
  });

  it("keeps every column of a saved view's own column choice", () => {
    renderTable([row()], { hideLowPriorityColumns: false });
    for (const label of LOW_PRIORITY) {
      expect(headerFor(label), label).not.toHaveClass("hidden");
      expect(cellUnder(label), label).not.toHaveClass("hidden");
    }
  });

  it("caps long matter, client and fee-earner names to one truncated line with the full text as a title", () => {
    const longName = "Khumalo Property Holdings — Erf 1234 Sandton Transfer mvihx3e91274";
    renderTable([
      row({
        name: longName,
        clientName: "Khumalo Property Holdings (Pty) Ltd",
        feeEarnerName: "Nomvula Dlamini-Mokoena",
      }),
    ]);
    const matterLink = screen.getByTestId("matter-link");
    expect(matterLink).toHaveClass("truncate");
    expect(matterLink).toHaveAttribute("title", longName);
    expect(matterLink.parentElement).toHaveClass("max-w-[14rem]");

    const clientLink = within(cellUnder("Client")).getByRole("link");
    expect(clientLink).toHaveClass("truncate", "max-w-[10rem]");
    expect(clientLink).toHaveAttribute("title", "Khumalo Property Holdings (Pty) Ltd");

    const feeEarner = within(cellUnder("Fee earner")).getByText("Nomvula Dlamini-Mokoena");
    expect(feeEarner).toHaveClass("truncate", "max-w-[8rem]");
    expect(feeEarner).toHaveAttribute("title", "Nomvula Dlamini-Mokoena");
  });
});

describe("MattersDataTable — redaction and nulls", () => {
  it("value redacted → hours only; null trust / client / fee earner / diary / reference → —", () => {
    renderTable([
      row({
        unbilledMinutes: 90,
        unbilledValue: null,
        trustBalance: null,
        clientId: null,
        clientName: null,
        feeEarnerId: null,
        feeEarnerName: null,
        nextDiaryDate: null,
        nextDiaryKind: null,
        referenceNumber: null,
        workType: null,
      }),
    ]);
    for (const label of [
      "Reference",
      "Client",
      "Work type",
      "Fee earner",
      "Trust",
      "Next in diary",
    ]) {
      expect(cellText(label), label).toBe("—");
    }
    expect(cellText("Unbilled")).toBe("1h 30m");
    // Only the matter link remains
    expect(within(screen.getByTestId("matter-row")).getAllByRole("link")).toHaveLength(1);
  });

  it("value redacted → hours only under a reordered saved-view column set too", () => {
    renderTable([row({ unbilledMinutes: 90, unbilledValue: null, trustBalance: null })], {
      columns: ["unbilled", "trustBalance", "name"],
    });
    expect(cellText("Unbilled")).toBe("1h 30m");
    expect(cellText("Trust")).toBe("—");
  });

  it("nothing unbilled → —", () => {
    renderTable([row({ unbilledMinutes: 0, unbilledValue: 0 })]);
    expect(cellText("Unbilled")).toBe("—");
  });

  it("an unknown diary kind renders the date without a marker", () => {
    renderTable([row({ nextDiaryKind: "SOMETHING_NEW" })]);
    const diary = cellUnder("Next in diary");
    expect(diary.querySelector("svg")).toBeNull();
    expect(diary.textContent).toMatch(/2026/);
  });
});

describe("MattersDataTable — sorting headers", () => {
  it("sortable headers are links that set sort/dir and keep other params; aria-sort on th", () => {
    renderTable([row()], {
      sort: { key: "nextDiaryDate", dir: "asc" },
      query: { view: "system:dormant", search: "raf" },
    });
    const clientHeader = screen.getByRole("columnheader", { name: "Client" });
    expect(clientHeader).toHaveAttribute("aria-sort", "none");
    const href = within(clientHeader).getByRole("link").getAttribute("href")!;
    const url = new URL(href, "http://x");
    expect(url.pathname).toBe("/org/acme/matters");
    expect(Object.fromEntries(url.searchParams)).toEqual({
      view: "system:dormant",
      search: "raf",
      sort: "clientName",
      dir: "asc",
    });

    const diaryHeader = screen.getByRole("columnheader", { name: /Next in diary/ });
    expect(diaryHeader).toHaveAttribute("aria-sort", "ascending");
    expect(within(diaryHeader).getByRole("link").getAttribute("href")).toContain("dir=desc");
  });
});

describe("MattersDataTable — row click and row actions", () => {
  it("clicking a plain cell opens the matter; clicking a link does not double-navigate", () => {
    renderTable([row()]);
    const r = screen.getByTestId("matter-row");
    fireEvent.click(within(r).getByText("Personal injury"));
    expect(mockPush).toHaveBeenCalledWith("/org/acme/matters/m1");

    mockPush.mockClear();
    fireEvent.click(within(r).getByText("Sipho Ndlovu"));
    expect(mockPush).not.toHaveBeenCalled();
  });

  it("row menu is navigation-only: Open matter + Open client (F-1)", async () => {
    const user = userEvent.setup();
    renderTable([row()]);
    await user.click(screen.getByRole("button", { name: "Matter actions" }));
    const openMatter = await screen.findByRole("menuitem", { name: "Open matter" });
    expect(openMatter).toHaveAttribute("href", "/org/acme/matters/m1");
    expect(screen.getByRole("menuitem", { name: "Open client" })).toHaveAttribute(
      "href",
      "/org/acme/clients/c1"
    );
    expect(screen.getAllByRole("menuitem")).toHaveLength(2);
    // Opening the menu does not trigger the row click
    expect(mockPush).not.toHaveBeenCalled();
  });

  it("row menu omits Open client when the matter has no client", async () => {
    const user = userEvent.setup();
    renderTable([row({ clientId: null, clientName: null })]);
    await user.click(screen.getByTestId("matter-row-actions-trigger"));
    await screen.findByRole("menuitem", { name: "Open matter" });
    expect(screen.queryByRole("menuitem", { name: "Open client" })).not.toBeInTheDocument();
  });
});

describe("MattersDataTable — custom-field columns", () => {
  it("custom-field headers are plain text: no sort link, no aria-sort", () => {
    renderTable([row({ customFields: { court: "Durban High Court" } })], {
      columns: ["name", "cf:court"],
    });
    const court = screen.getByRole("columnheader", { name: "Court" });
    expect(within(court).queryByRole("link")).toBeNull();
    expect(court).not.toHaveAttribute("aria-sort");
    const matter = screen.getByRole("columnheader", { name: "Matter" });
    expect(within(matter).getByRole("link")).toBeInTheDocument();
  });

  it("a custom field with no known name is headed by its slug", () => {
    renderTable([row()], { columns: ["name", "cf:unknown_slug"] });
    const headers = screen.getAllByRole("columnheader").map((h) => h.textContent?.trim());
    expect(headers).toEqual(["Matter", "unknown_slug", "Actions"]);
  });
});

describe("MattersDataTable — table controls", () => {
  it("the Columns menu lists every hideable column (not Matter, not Actions) and hides one", async () => {
    const user = userEvent.setup();
    renderTable([row()]);
    await user.click(screen.getByRole("button", { name: "Columns" }));
    const client = await screen.findByRole("menuitemcheckbox", { name: "Client" });
    expect(screen.getAllByRole("menuitemcheckbox").map((item) => item.textContent)).toEqual([
      "Reference",
      "Client",
      "Work type",
      "Fee earner",
      "Status",
      "Opened",
      "Unbilled",
      "Trust",
      "Next in diary",
    ]);
    await user.click(client);
    await user.keyboard("{Escape}");
    expect(screen.queryByRole("columnheader", { name: "Client" })).toBeNull();
    expect(screen.queryByText("Sipho Ndlovu")).toBeNull();
    expect(mockPush).not.toHaveBeenCalled();
  });

  it("renders every row with no pagination control", () => {
    const rows = Array.from({ length: 30 }, (_, i) =>
      row({ id: `m${i + 1}`, name: `Matter ${i + 1}`, referenceNumber: `LIT-${i + 1}` })
    );
    renderTable(rows);
    expect(screen.getAllByTestId("matter-row")).toHaveLength(30);
    expect(screen.queryByText("Rows per page")).toBeNull();
    expect(screen.queryByRole("button", { name: /Next|Previous/ })).toBeNull();
  });

  it("no rows → the empty node instead of the table, footer and controls", () => {
    renderTable([], { footer: <p data-testid="matters-footer">Showing 0 matters</p> });
    expect(screen.getByText("No fixture matters.")).toBeInTheDocument();
    expect(screen.queryByRole("table")).toBeNull();
    expect(screen.queryByTestId("matters-footer")).toBeNull();
    expect(screen.queryByRole("button", { name: "Columns" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Export CSV" })).toBeNull();
  });

  it("renders the footer after the table when rows render", () => {
    renderTable([row(), row({ id: "m2", name: "Second matter" })], {
      footer: <p data-testid="matters-footer">Showing 2 matters</p>,
    });
    const footer = screen.getByTestId("matters-footer");
    expect(footer).toHaveTextContent("Showing 2 matters");
    expect(
      screen.getByTestId("matters-table").compareDocumentPosition(footer) &
        Node.DOCUMENT_POSITION_FOLLOWING
    ).toBeTruthy();
  });

  it("takes only serializable props from the server page (RSC boundary, OQ #19)", () => {
    const props = serializableProps(
      [
        row({
          tags: [
            {
              id: "t1",
              name: "Urgent",
              slug: "urgent",
              color: "#0d9488",
              createdAt: "2026-05-01T08:00:00Z",
              updatedAt: "2026-05-01T08:00:00Z",
            },
          ],
        }),
      ],
      {
        query: { view: "system:dormant" },
      }
    );
    const containsFunction = (value: unknown): boolean =>
      typeof value === "function" ||
      (typeof value === "object" && value !== null && Object.values(value).some(containsFunction));
    expect(containsFunction(props)).toBe(false);
    expect(JSON.parse(JSON.stringify(props))).toEqual(props);
  });
});

describe("MattersDataTable — CSV export", () => {
  it("exports the visible columns × rendered rows as matters.csv; a hidden column drops out", async () => {
    const user = userEvent.setup();
    const stub = stubDownload();
    try {
      renderTable([
        row(),
        row({
          id: "m2",
          name: "Redacted matter",
          referenceNumber: "LIT-0002",
          unbilledMinutes: 90,
          unbilledValue: null,
          trustBalance: null,
        }),
      ]);
      await user.click(screen.getByRole("button", { name: "Export CSV" }));
      expect(stub.captured.downloads).toEqual(["matters.csv"]);

      const text = await blobText(stub.captured.blobs[0]);
      expect(text.startsWith(CSV_BOM)).toBe(true);
      const [header, first, second] = text.slice(CSV_BOM.length).split("\r\n");
      expect(header).toBe(
        "Reference,Matter,Client,Work type,Fee earner,Status,Opened,Unbilled,Trust,Next in diary"
      );
      // Money uses non-breaking group separators, hence \s?.
      expect(first).toMatch(
        /^LIT-0001,RAF claim — Ndlovu,Sipho Ndlovu,Personal injury,Alice Mokoena,Active,1 May 2026,"21h · R\s?42\s?000,00",15000,03 Nov 2026$/
      );
      // Redacted value → hours only; redacted trust → empty field.
      expect(second).toMatch(/,1h 30m,,03 Nov 2026$/);

      await user.click(screen.getByRole("button", { name: "Columns" }));
      await user.click(await screen.findByRole("menuitemcheckbox", { name: "Client" }));
      await user.keyboard("{Escape}");
      await user.click(screen.getByRole("button", { name: "Export CSV" }));
      const [hiddenHeader] = (await blobText(stub.captured.blobs[1]))
        .slice(CSV_BOM.length)
        .split("\r\n");
      expect(hiddenHeader).toBe(
        "Reference,Matter,Work type,Fee earner,Status,Opened,Unbilled,Trust,Next in diary"
      );
      expect(mockPush).not.toHaveBeenCalled();
    } finally {
      await stub.restore();
    }
  });
});

describe("view switch → list params", () => {
  const today = new Date(2026, 9, 21);
  const paramsFor = (view: string | null, url: { status?: string } = {}) =>
    buildMatterListParams(
      resolveViewSelection(MATTER_SYSTEM_VIEWS, [], view, DEFAULT_MATTER_VIEW_ID),
      url,
      today
    );

  it("each system view sends its own params; system ids never reach the backend", () => {
    expect(paramsFor(null)).toEqual({ status: "ACTIVE" });
    expect(paramsFor("system:my-matters")).toEqual({ mine: "true", status: "ACTIVE" });
    expect(paramsFor("system:awaiting-fee-note")).toEqual({
      status: "ACTIVE",
      hasUnbilled: "true",
    });
    expect(paramsFor("system:dormant")).toEqual({ status: "ACTIVE", inactiveSince: "2026-07-23" });
    for (const v of MATTER_SYSTEM_VIEWS) expect(paramsFor(v.id).view).toBeUndefined();
  });

  it("?status=CLOSED lists closed matters under the default view", () => {
    expect(paramsFor(null, { status: "CLOSED" })).toEqual({ status: "CLOSED" });
  });
});
