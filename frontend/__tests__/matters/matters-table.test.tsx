import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MattersTable } from "@/components/matters/matters-table";
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

function renderTable(
  rows: MatterListRow[],
  opts: { columns?: string[]; sort?: MatterSort; query?: Record<string, string> } = {}
) {
  return render(
    <MattersTable
      slug="acme"
      rows={rows}
      columns={opts.columns ?? defaultMatterColumns(true)}
      customFieldNames={{ court: "Court" }}
      sort={opts.sort ?? DEFAULT_SORT}
      query={opts.query ?? {}}
      caption="Matters — Open matters"
    />
  );
}

beforeEach(() => vi.clearAllMocks());
afterEach(() => cleanup());

describe("MattersTable — columns", () => {
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

describe("MattersTable — redaction and nulls", () => {
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
    const cells = within(screen.getByTestId("matter-row")).getAllByRole("cell");
    const text = cells.map((c) => c.textContent?.trim());
    // Reference, Client, Work type, Fee earner, Trust, Next in diary → "—"
    expect(text[0]).toBe("—");
    expect(text[2]).toBe("—");
    expect(text[3]).toBe("—");
    expect(text[4]).toBe("—");
    expect(text[7]).toBe("1h 30m");
    expect(text[8]).toBe("—");
    expect(text[9]).toBe("—");
    // Only the matter link remains
    expect(within(screen.getByTestId("matter-row")).getAllByRole("link")).toHaveLength(1);
  });

  it("nothing unbilled → —", () => {
    renderTable([row({ unbilledMinutes: 0, unbilledValue: 0 })]);
    const cells = within(screen.getByTestId("matter-row")).getAllByRole("cell");
    expect(cells[7].textContent?.trim()).toBe("—");
  });

  it("an unknown diary kind renders the date without a marker", () => {
    renderTable([row({ nextDiaryKind: "SOMETHING_NEW" })]);
    const cells = within(screen.getByTestId("matter-row")).getAllByRole("cell");
    expect(cells[9].querySelector("svg")).toBeNull();
    expect(cells[9].textContent).toMatch(/2026/);
  });
});

describe("MattersTable — sorting headers", () => {
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

describe("MattersTable — row click and row actions", () => {
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
