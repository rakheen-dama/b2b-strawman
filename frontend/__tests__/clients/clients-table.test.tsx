import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ClientsTable } from "@/components/clients/clients-table";
import { ClientLifecycleFilter } from "@/components/clients/client-lifecycle-filter";
import { defaultClientColumns, type ClientSort } from "@/lib/clients-list";
import { formatCurrency, formatDate } from "@/lib/format";
import type { ClientListRow } from "@/lib/types";

const mockPush = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush, replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => "/org/acme/clients",
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
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
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

function renderTable(
  rows: ClientListRow[],
  opts: {
    columns?: string[];
    sort?: ClientSort;
    query?: Record<string, string>;
    hideLowPriorityColumns?: boolean;
  } = {}
) {
  return render(
    <ClientsTable
      slug="acme"
      rows={rows}
      columns={opts.columns ?? defaultClientColumns(true)}
      customFieldNames={{ vat_number: "VAT number" }}
      sort={opts.sort ?? DEFAULT_SORT}
      query={opts.query ?? {}}
      caption="Clients — All clients"
      hideLowPriorityColumns={opts.hideLowPriorityColumns}
    />
  );
}

beforeEach(() => vi.clearAllMocks());
afterEach(() => cleanup());

const headerText = (h: HTMLElement) => h.textContent?.replace(/[↑↓]/g, "").trim();

/** The first body row's cell under the column headed `label`. */
function cellUnder(label: string, rowIndex = 0): HTMLElement {
  const headers = screen.getAllByRole("columnheader").map(headerText);
  const index = headers.indexOf(label);
  if (index === -1) throw new Error(`No "${label}" column; headers: ${headers.join(", ")}`);
  return within(screen.getAllByTestId("client-row")[rowIndex]).getAllByRole("cell")[index];
}

const cellText = (label: string, rowIndex = 0) => cellUnder(label, rowIndex).textContent?.trim();
const headerCell = (label: string) =>
  screen.getAllByRole("columnheader").find((h) => headerText(h) === label);

describe("ClientsTable — columns", () => {
  it("renders a visually hidden caption and scope=col headers in §12.6.4 order", () => {
    renderTable([row()]);
    const table = screen.getByRole("table");
    const caption = table.querySelector("caption");
    expect(caption).toHaveTextContent("Clients — All clients");
    expect(caption).toHaveClass("sr-only");

    const headers = within(table).getAllByRole("columnheader");
    expect(headers.map(headerText)).toEqual([
      "Client",
      "Lifecycle",
      "FICA",
      "Open matters",
      "Outstanding fee notes",
      "Trust balance",
      "Completeness",
      "Created",
      "Contact",
      "Actions",
    ]);
    for (const h of headers) expect(h).toHaveAttribute("scope", "col");
  });

  it("omits the Trust balance column when trust accounting is off (F-8)", () => {
    renderTable([row()], { columns: defaultClientColumns(false) });
    expect(headerCell("Trust balance")).toBeUndefined();
  });

  it("renders one body row per client from ClientListRow fields", () => {
    renderTable([row(), row({ id: "c2", name: "Apex Holdings", clientType: "COMPANY" })]);
    expect(screen.getAllByTestId("client-row")).toHaveLength(2);
    expect(cellText("Lifecycle")).toBe("Active");
    expect(cellText("Open matters")).toBe("3");
    expect(cellText("Trust balance")).toBe(formatCurrency(5000, "ZAR"));
    expect(cellText("Completeness")).toBe("80%");
    expect(cellText("Created")).toBe(formatDate("2026-05-01T08:00:00Z"));
    expect(cellText("Contact")).toBe("sipho@example.com · +27 82 555 0101");
  });
});

describe("ClientsTable — Client cell (D-9)", () => {
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

describe("ClientsTable — FICA, fee notes, financial redaction", () => {
  it("renders the three FICA variants", () => {
    renderTable([
      row({ id: "a", ficaStatus: "DONE", ficaLastVerifiedAt: "2026-10-03T08:00:00Z" }),
      row({ id: "b", ficaStatus: "IN_PROGRESS", ficaLastVerifiedAt: null }),
      row({ id: "c", ficaStatus: "NOT_STARTED", ficaLastVerifiedAt: null }),
    ]);
    const [done, inProgress, notStarted] = screen.getAllByTestId("client-fica-status");
    expect(done).toHaveTextContent("Done");
    expect(done).toHaveAttribute("data-variant", "default");
    expect(cellText("FICA", 0)).toContain(formatDate("2026-10-03T08:00:00Z"));
    expect(inProgress).toHaveTextContent("In progress");
    expect(inProgress).toHaveAttribute("data-variant", "outline");
    expect(notStarted).toHaveTextContent("Not started");
    expect(notStarted.tagName).toBe("SPAN");
    expect(notStarted).toHaveClass("text-red-600");
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
});

describe("ClientsTable — sorting", () => {
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

  it("marks the active column and toggles its direction", () => {
    renderTable([row()], { sort: { key: "completeness", dir: "asc" } });
    expect(headerCell("Completeness")).toHaveAttribute("aria-sort", "ascending");
    expect(headerCell("Client")).toHaveAttribute("aria-sort", "none");
    expect(within(headerCell("Completeness")!).getByRole("link")).toHaveAttribute(
      "href",
      "/org/acme/clients?sortBy=completeness&sortDir=desc"
    );
  });
});

describe("ClientsTable — responsive columns (D-8)", () => {
  it("hides low-priority columns in narrow containers", () => {
    renderTable([row()]);
    expect(headerCell("Open matters")).toHaveClass("hidden", "@4xl:table-cell");
    expect(headerCell("Created")).toHaveClass("hidden", "@5xl:table-cell");
    expect(headerCell("Contact")).toHaveClass("hidden", "@6xl:table-cell");
    for (const label of ["Client", "Lifecycle", "FICA", "Outstanding fee notes", "Completeness"]) {
      expect(headerCell(label)).not.toHaveClass("hidden");
    }
    expect(cellUnder("Created")).toHaveClass("hidden");
  });

  it("never hides the active sort column", () => {
    renderTable([row()], { sort: { key: "createdAt", dir: "desc" } });
    expect(headerCell("Created")).not.toHaveClass("hidden");
    expect(cellUnder("Created")).not.toHaveClass("hidden");
  });

  it("never hides columns a saved view chose", () => {
    renderTable([row()], {
      columns: ["name", "openMatterCount", "contact", "cf:vat_number"],
      hideLowPriorityColumns: false,
    });
    expect(headerCell("Open matters")).not.toHaveClass("hidden");
    expect(headerCell("Contact")).not.toHaveClass("hidden");
    expect(headerCell("VAT number")).toBeDefined();
  });
});

describe("ClientsTable — row interaction", () => {
  it("a row click opens the client; clicks on links and the menu do not", async () => {
    renderTable([row()]);
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
