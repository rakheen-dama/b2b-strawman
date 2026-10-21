import { describe, it, expect } from "vitest";
import {
  MATTER_COLUMNS,
  buildMatterListParams,
  buildSortHref,
  defaultMatterColumns,
  formatCustomFieldCell,
  formatUnbilled,
  humaniseWorkType,
  parseMatterSort,
  resolveMatterColumns,
  searchMatterRows,
  sortMatterRows,
} from "@/lib/matters-list";
import { MATTER_SYSTEM_VIEWS, findSystemView } from "@/lib/constants/system-views";
import type { MatterListRow, SavedViewResponse } from "@/lib/types";

function row(overrides: Partial<MatterListRow> = {}): MatterListRow {
  return {
    id: "m1",
    name: "Matter",
    description: null,
    referenceNumber: null,
    status: "ACTIVE",
    workType: null,
    priority: null,
    dueDate: null,
    createdAt: "2026-01-01T08:00:00Z",
    clientId: null,
    clientName: null,
    feeEarnerId: null,
    feeEarnerName: null,
    matterRole: null,
    unbilledMinutes: 0,
    unbilledValue: null,
    currency: "ZAR",
    trustBalance: null,
    nextDiaryDate: null,
    nextDiaryKind: null,
    openTaskCount: 0,
    tags: [],
    customFields: {},
    ...overrides,
  };
}

const ids = (rows: MatterListRow[]) => rows.map((r) => r.id);

describe("parseMatterSort (R-1)", () => {
  it("defaults to nextDiaryDate asc", () => {
    expect(parseMatterSort(undefined, undefined, false)).toEqual({
      key: "nextDiaryDate",
      dir: "asc",
    });
  });

  it("Dormant defaults to createdAt asc", () => {
    expect(parseMatterSort(null, null, true)).toEqual({ key: "createdAt", dir: "asc" });
  });

  it("honours a valid sort + dir; invalid dir → asc; invalid key → default", () => {
    expect(parseMatterSort("clientName", "desc", false)).toEqual({
      key: "clientName",
      dir: "desc",
    });
    expect(parseMatterSort("unbilled", "sideways", true)).toEqual({ key: "unbilled", dir: "asc" });
    expect(parseMatterSort("description", "desc", false)).toEqual({
      key: "nextDiaryDate",
      dir: "asc",
    });
  });
});

describe("sortMatterRows (R-1)", () => {
  const rows = [
    row({ id: "a", name: "Alpha", nextDiaryDate: null }),
    row({ id: "b", name: "Bravo", nextDiaryDate: "2026-11-02" }),
    row({ id: "c", name: "Charlie", nextDiaryDate: "2026-10-25" }),
    row({ id: "d", name: "Delta", nextDiaryDate: null }),
  ];

  it("nextDiaryDate asc puts nulls last", () => {
    expect(ids(sortMatterRows(rows, "nextDiaryDate", "asc"))).toEqual(["c", "b", "a", "d"]);
  });

  it("desc keeps nulls last too", () => {
    expect(ids(sortMatterRows(rows, "nextDiaryDate", "desc"))).toEqual(["b", "c", "a", "d"]);
  });

  it("does not mutate its input", () => {
    const copy = [...rows];
    sortMatterRows(rows, "name", "desc");
    expect(rows).toEqual(copy);
  });

  it("text sorts are case-insensitive with blanks last; ties → name then id", () => {
    const r = [
      row({ id: "1", name: "Zed", clientName: "beta" }),
      row({ id: "2", name: "Ann", clientName: "" }),
      row({ id: "3", name: "Bob", clientName: "Alpha" }),
      row({ id: "5", name: "Same", clientName: "Beta" }),
      row({ id: "4", name: "Same", clientName: "Beta" }),
    ];
    expect(ids(sortMatterRows(r, "clientName", "asc"))).toEqual(["3", "4", "5", "1", "2"]);
  });

  it("createdAt sorts chronologically", () => {
    const r = [
      row({ id: "new", createdAt: "2026-09-01T00:00:00Z" }),
      row({ id: "old", createdAt: "2025-01-01T00:00:00Z" }),
    ];
    expect(ids(sortMatterRows(r, "createdAt", "asc"))).toEqual(["old", "new"]);
  });

  it("unbilled sorts by minutes (never by the redactable value)", () => {
    const r = [
      row({ id: "x", unbilledMinutes: 60, unbilledValue: 9999 }),
      row({ id: "y", unbilledMinutes: 120, unbilledValue: null }),
      row({ id: "z", unbilledMinutes: 0, unbilledValue: 0 }),
    ];
    expect(ids(sortMatterRows(r, "unbilled", "desc"))).toEqual(["y", "x", "z"]);
  });

  it("trustBalance numeric with nulls last", () => {
    const r = [
      row({ id: "n", trustBalance: null }),
      row({ id: "big", trustBalance: 1000 }),
      row({ id: "small", trustBalance: 5 }),
    ];
    expect(ids(sortMatterRows(r, "trustBalance", "asc"))).toEqual(["small", "big", "n"]);
    expect(ids(sortMatterRows(r, "trustBalance", "desc"))).toEqual(["big", "small", "n"]);
  });

  it("reference numbers sort numerically within a prefix", () => {
    const r = [
      row({ id: "10", referenceNumber: "MAT-10" }),
      row({ id: "9", referenceNumber: "MAT-9" }),
    ];
    expect(ids(sortMatterRows(r, "referenceNumber", "asc"))).toEqual(["9", "10"]);
  });
});

describe("searchMatterRows (F-2)", () => {
  const rows = [
    row({ id: "1", name: "RAF claim", referenceNumber: "LIT-0001", clientName: "Sipho Ndlovu" }),
    row({ id: "2", name: "Estate late", referenceNumber: "EST-0002", clientName: "Moroka Trust" }),
    row({ id: "3", name: "Internal", referenceNumber: null, clientName: null }),
  ];

  it("blank query returns every row", () => {
    expect(ids(searchMatterRows(rows, ""))).toEqual(["1", "2", "3"]);
    expect(ids(searchMatterRows(rows, "   "))).toEqual(["1", "2", "3"]);
    expect(ids(searchMatterRows(rows, undefined))).toEqual(["1", "2", "3"]);
  });

  it("matches name, reference and client case-insensitively", () => {
    expect(ids(searchMatterRows(rows, "raf"))).toEqual(["1"]);
    expect(ids(searchMatterRows(rows, "est-00"))).toEqual(["2"]);
    expect(ids(searchMatterRows(rows, "NDLOVU"))).toEqual(["1"]);
    expect(ids(searchMatterRows(rows, "  moroka "))).toEqual(["2"]);
    expect(ids(searchMatterRows(rows, "nothing"))).toEqual([]);
  });
});

describe("resolveMatterColumns (saved-view columns)", () => {
  const opts = { trustVisible: true, customFieldSlugs: ["court", "case_no"] };

  it("null / empty → default §12.6.4 order", () => {
    expect(resolveMatterColumns(null, opts)).toEqual(MATTER_COLUMNS.map((c) => c.id));
    expect(resolveMatterColumns([], opts)).toEqual(MATTER_COLUMNS.map((c) => c.id));
  });

  it("default drops Trust when it is hidden (F-8)", () => {
    expect(defaultMatterColumns(false)).not.toContain("trustBalance");
    expect(
      resolveMatterColumns(["name", "trustBalance"], { ...opts, trustVisible: false })
    ).toEqual(["name"]);
  });

  it("keeps the view's order, known ids and existing cf: columns; drops legacy/unknown", () => {
    expect(
      resolveMatterColumns(
        ["status", "name", "description", "updatedAt", "cf:court", "cf:gone", "status"],
        opts
      )
    ).toEqual(["status", "name", "cf:court"]);
  });

  it("always includes the Matter column — after Reference, else first", () => {
    expect(resolveMatterColumns(["referenceNumber", "clientName"], opts)).toEqual([
      "referenceNumber",
      "name",
      "clientName",
    ]);
    expect(resolveMatterColumns(["clientName"], opts)).toEqual(["name", "clientName"]);
  });

  it("nothing valid → default", () => {
    expect(resolveMatterColumns(["description", "updatedAt"], opts)).toEqual(
      MATTER_COLUMNS.map((c) => c.id)
    );
  });
});

describe("cell formatting", () => {
  it("formatUnbilled: hours + value; redacted value → hours only; nothing → —", () => {
    const both = formatUnbilled(1260, 42000, "ZAR");
    expect(both.startsWith("21h · ")).toBe(true);
    expect(both).toMatch(/R\s?42/);
    expect(formatUnbilled(90, null, "ZAR")).toBe("1h 30m");
    expect(formatUnbilled(0, 0, "ZAR")).toBe("—");
    expect(formatUnbilled(0, null, "ZAR")).toBe("—");
    expect(formatUnbilled(0, 150, "ZAR")).toMatch(/R\s?150/);
  });

  it("humaniseWorkType", () => {
    expect(humaniseWorkType("PERSONAL_INJURY")).toBe("Personal injury");
    expect(humaniseWorkType("ESTATES")).toBe("Estates");
    expect(humaniseWorkType(null)).toBe("—");
    expect(humaniseWorkType("  ")).toBe("—");
  });

  it("formatCustomFieldCell", () => {
    expect(formatCustomFieldCell(null)).toBe("—");
    expect(formatCustomFieldCell("")).toBe("—");
    expect(formatCustomFieldCell(true)).toBe("Yes");
    expect(formatCustomFieldCell(false)).toBe("No");
    expect(formatCustomFieldCell(["a", "b"])).toBe("a, b");
    expect(formatCustomFieldCell(12)).toBe("12");
  });
});

describe("buildSortHref", () => {
  const active = { key: "nextDiaryDate", dir: "asc" } as const;

  it("another column → asc, keeping every other param", () => {
    const href = buildSortHref("acme", { view: "system:dormant", search: "raf" }, "name", active);
    const url = new URL(href, "http://x");
    expect(url.pathname).toBe("/org/acme/matters");
    expect(url.searchParams.get("view")).toBe("system:dormant");
    expect(url.searchParams.get("search")).toBe("raf");
    expect(url.searchParams.get("sort")).toBe("name");
    expect(url.searchParams.get("dir")).toBe("asc");
  });

  it("the active column toggles asc ↔ desc", () => {
    expect(buildSortHref("acme", {}, "nextDiaryDate", active)).toContain("dir=desc");
    expect(
      buildSortHref("acme", { sort: "name", dir: "desc" }, "name", { key: "name", dir: "desc" })
    ).toContain("dir=asc");
  });
});

describe("buildMatterListParams", () => {
  const today = new Date(2026, 9, 21);
  const system = (id: string) => ({
    kind: "system" as const,
    view: findSystemView(MATTER_SYSTEM_VIEWS, id)!,
  });

  it("system views translate to list params; -90d is resolved (R-3)", () => {
    expect(buildMatterListParams(system("system:open-matters"), {}, today)).toEqual({
      status: "ACTIVE",
    });
    expect(buildMatterListParams(system("system:my-matters"), {}, today)).toEqual({
      mine: "true",
      status: "ACTIVE",
    });
    expect(buildMatterListParams(system("system:awaiting-fee-note"), {}, today)).toEqual({
      status: "ACTIVE",
      hasUnbilled: "true",
    });
    expect(buildMatterListParams(system("system:dormant"), {}, today)).toEqual({
      status: "ACTIVE",
      inactiveSince: "2026-07-23",
    });
  });

  it("the URL status overrides the view's status (ALL accepted, junk ignored)", () => {
    expect(
      buildMatterListParams(system("system:open-matters"), { status: "CLOSED" }, today).status
    ).toBe("CLOSED");
    expect(buildMatterListParams(system("system:my-matters"), { status: "ALL" }, today)).toEqual({
      mine: "true",
      status: "ALL",
    });
    expect(
      buildMatterListParams(system("system:open-matters"), { status: "BOGUS" }, today).status
    ).toBe("ACTIVE");
  });

  it("URL tags apply to system views (trimmed CSV)", () => {
    expect(
      buildMatterListParams(system("system:open-matters"), { tags: " vip, ,urgent " }, today).tags
    ).toBe("vip,urgent");
  });

  it("saved view: view=<uuid> plus forwarded mine/hasUnbilled/inactiveSince (F-4); tags ignored", () => {
    const view: SavedViewResponse = {
      id: "6f1c2b1e-1b2a-4c3d-9e8f-0a1b2c3d4e5f",
      entityType: "PROJECT",
      name: "Saved dormant",
      filters: { status: ["ACTIVE"], mine: "true", hasUnbilled: true, inactiveSince: "-90d" },
      columns: null,
      shared: false,
      createdBy: "m1",
      sortOrder: 0,
      createdAt: "2026-10-01T00:00:00Z",
      updatedAt: "2026-10-01T00:00:00Z",
    };
    expect(buildMatterListParams({ kind: "saved", view }, { tags: "vip" }, today)).toEqual({
      view: view.id,
      mine: "true",
      hasUnbilled: "true",
      inactiveSince: "2026-07-23",
    });
  });

  it("saved view with an invalid inactiveSince drops it; plain saved views send view only", () => {
    const base: SavedViewResponse = {
      id: "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d",
      entityType: "PROJECT",
      name: "Plain",
      filters: { tags: ["vip"] },
      columns: null,
      shared: false,
      createdBy: "m1",
      sortOrder: 0,
      createdAt: "2026-10-01T00:00:00Z",
      updatedAt: "2026-10-01T00:00:00Z",
    };
    expect(buildMatterListParams({ kind: "saved", view: base }, {}, today)).toEqual({
      view: base.id,
    });
    expect(
      buildMatterListParams(
        { kind: "saved", view: { ...base, filters: { inactiveSince: "soon", mine: "false" } } },
        {},
        today
      )
    ).toEqual({ view: base.id });
  });
});
