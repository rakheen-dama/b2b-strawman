import { describe, it, expect } from "vitest";
import {
  CLIENT_COLUMNS,
  applySavedViewLifecycle,
  buildClientListParams,
  buildClientSortHref,
  defaultClientColumns,
  formatOutstanding,
  migrateLegacyClientColumns,
  parseClientSort,
  resolveClientColumns,
  savedViewLifecycle,
  savedViewShowIncomplete,
  searchClientRows,
  sortClientRows,
} from "@/lib/clients-list";
import {
  ALL_CLIENTS_VIEW_ID,
  CLIENT_VIEW_OPTIONS,
  resolveViewSelection,
  type ViewSelection,
} from "@/lib/constants/system-views";
import { STANDARD_COLUMNS } from "@/components/views/constants";
import { describeCarriedFilter } from "@/components/views/CreateViewDialog";
import { formatCurrency } from "@/lib/format";
import type { ClientListRow, SavedViewResponse } from "@/lib/types";

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
    openMatterCount: 1,
    completenessPercent: 80,
    tags: [],
    customFields: {},
    ...overrides,
  };
}

function savedView(overrides: Partial<SavedViewResponse> = {}): SavedViewResponse {
  return {
    id: "11111111-1111-1111-1111-111111111111",
    entityType: "CUSTOMER",
    name: "My clients",
    filters: {},
    columns: null,
    shared: false,
    createdBy: "m1",
    sortOrder: 0,
    createdAt: "2026-10-01T00:00:00Z",
    updatedAt: "2026-10-01T00:00:00Z",
    ...overrides,
  };
}

const select = (viewId: string | null, saved: SavedViewResponse[] = []): ViewSelection =>
  resolveViewSelection(CLIENT_VIEW_OPTIONS, saved, viewId, ALL_CLIENTS_VIEW_ID);

const ids = (rows: ClientListRow[]) => rows.map((r) => r.id);

describe("client columns", () => {
  it("are the §12.6.4 columns in order", () => {
    expect(CLIENT_COLUMNS.map((c) => c.label)).toEqual([
      "Client",
      "Lifecycle",
      "FICA",
      "Open matters",
      "Outstanding fee notes",
      "Trust balance",
      "Completeness",
      "Created",
      "Contact",
    ]);
  });

  it("feed the Save-view dialog's CUSTOMER column list (single source)", () => {
    expect(STANDARD_COLUMNS.CUSTOMER).toEqual(
      CLIENT_COLUMNS.map((c) => ({ value: c.id, label: c.label }))
    );
  });

  it("default set drops Trust balance when trust is not visible (F-8)", () => {
    expect(defaultClientColumns(true)).toContain("trustBalance");
    expect(defaultClientColumns(false)).not.toContain("trustBalance");
    expect(defaultClientColumns(false)).toHaveLength(CLIENT_COLUMNS.length - 1);
  });
});

describe("parseClientSort (D-5)", () => {
  it("keeps the sortBy/sortDir names, so sortBy=completeness survives", () => {
    expect(parseClientSort("completeness", "desc")).toEqual({ key: "completeness", dir: "desc" });
    expect(parseClientSort("outstanding", "sideways")).toEqual({ key: "outstanding", dir: "asc" });
  });

  it("defaults to name asc; Contact and unknown keys are not sortable", () => {
    expect(parseClientSort(null, null)).toEqual({ key: "name", dir: "asc" });
    expect(parseClientSort("contact", "desc")).toEqual({ key: "name", dir: "asc" });
    expect(parseClientSort("email", "desc")).toEqual({ key: "name", dir: "asc" });
  });
});

describe("sortClientRows (R-1)", () => {
  it("sorts by completeness with N/A last in both directions", () => {
    const rows = [
      row({ id: "a", name: "A", completenessPercent: null }),
      row({ id: "b", name: "B", completenessPercent: 40 }),
      row({ id: "c", name: "C", completenessPercent: 100 }),
    ];
    expect(ids(sortClientRows(rows, "completeness", "asc"))).toEqual(["b", "c", "a"]);
    expect(ids(sortClientRows(rows, "completeness", "desc"))).toEqual(["c", "b", "a"]);
  });

  it("sorts lifecycle and FICA by their process order, not alphabetically", () => {
    const rows = [
      row({ id: "a", lifecycleStatus: "ACTIVE", ficaStatus: "DONE" }),
      row({ id: "p", lifecycleStatus: "PROSPECT", ficaStatus: "NOT_STARTED" }),
      row({ id: "o", lifecycleStatus: "ONBOARDING", ficaStatus: "IN_PROGRESS" }),
      row({ id: "n", lifecycleStatus: null, ficaStatus: "DONE" }),
    ];
    expect(ids(sortClientRows(rows, "lifecycleStatus", "asc"))).toEqual(["p", "o", "a", "n"]);
    expect(ids(sortClientRows(rows, "ficaStatus", "asc")).slice(0, 2)).toEqual(["p", "o"]);
  });

  it("sorts outstanding by total, or by count when redacted (D-6)", () => {
    const visible = [
      row({ id: "a", outstandingFeeNoteTotal: 500, outstandingFeeNoteCount: 3 }),
      row({ id: "b", outstandingFeeNoteTotal: 18400, outstandingFeeNoteCount: 1 }),
    ];
    expect(ids(sortClientRows(visible, "outstanding", "desc"))).toEqual(["b", "a"]);
    const redacted = visible.map((r) => ({ ...r, outstandingFeeNoteTotal: null }));
    expect(ids(sortClientRows(redacted, "outstanding", "desc"))).toEqual(["a", "b"]);
  });

  it("ties fall back to name, then id; nulls last; input untouched", () => {
    const rows = [
      row({ id: "z", name: "Beta", trustBalance: 10 }),
      row({ id: "y", name: "Alpha", trustBalance: 10 }),
      row({ id: "x", name: "Alpha", trustBalance: 10 }),
      row({ id: "w", name: "Aardvark", trustBalance: null }),
    ];
    const before = ids(rows);
    expect(ids(sortClientRows(rows, "trustBalance", "desc"))).toEqual(["x", "y", "z", "w"]);
    expect(ids(rows)).toEqual(before);
  });

  it("sorts createdAt as instants, name case-insensitively", () => {
    const rows = [
      row({ id: "a", name: "bravo", createdAt: "2026-05-01T08:00:00.5Z" }),
      row({ id: "b", name: "Alpha", createdAt: "2026-05-01T08:00:00.123456Z" }),
    ];
    expect(ids(sortClientRows(rows, "createdAt", "asc"))).toEqual(["b", "a"]);
    expect(ids(sortClientRows(rows, "name", "asc"))).toEqual(["b", "a"]);
  });
});

describe("searchClientRows (D-7)", () => {
  const rows = [
    row({ id: "a", name: "Sipho Ndlovu", email: "sipho@example.com", phone: null }),
    row({ id: "b", name: "Apex Holdings", email: "info@apex.co.za", phone: "011 555 0199" }),
  ];

  it("matches name, email and phone case-insensitively", () => {
    expect(ids(searchClientRows(rows, "NDLOVU"))).toEqual(["a"]);
    expect(ids(searchClientRows(rows, "apex.co"))).toEqual(["b"]);
    expect(ids(searchClientRows(rows, "555 0199"))).toEqual(["b"]);
  });

  it("blank → every row", () => {
    expect(ids(searchClientRows(rows, "  "))).toEqual(["a", "b"]);
    expect(ids(searchClientRows(rows, null))).toEqual(["a", "b"]);
  });
});

describe("resolveClientColumns", () => {
  const opts = { trustVisible: true, customFieldSlugs: ["vat_number"] };

  it("maps legacy CUSTOMER view columns: email/phone → Contact, status dropped", () => {
    expect(resolveClientColumns(["name", "email", "phone", "status", "createdAt"], opts)).toEqual([
      "name",
      "contact",
      "createdAt",
    ]);
  });

  it("keeps known ids and existing cf: columns in view order; Client always first", () => {
    expect(
      resolveClientColumns(["ficaStatus", "cf:vat_number", "cf:gone", "completeness"], opts)
    ).toEqual(["name", "ficaStatus", "cf:vat_number", "completeness"]);
  });

  it("drops Trust balance when hidden; nothing valid → the default", () => {
    expect(
      resolveClientColumns(["name", "trustBalance"], { ...opts, trustVisible: false })
    ).toEqual(["name"]);
    expect(resolveClientColumns(["status", "bogus"], opts)).toEqual(defaultClientColumns(true));
    expect(resolveClientColumns(null, opts)).toEqual(defaultClientColumns(true));
  });
});

describe("formatOutstanding (D-6)", () => {
  it('renders "R … · n", the count alone when redacted, "—" when nothing is outstanding', () => {
    expect(formatOutstanding(18400, 2, "ZAR")).toBe(`${formatCurrency(18400, "ZAR")} · 2`);
    expect(formatOutstanding(null, 2, "ZAR")).toBe("2");
    expect(formatOutstanding(0, 0, "ZAR")).toBe("—");
    expect(formatOutstanding(null, 0, "ZAR")).toBe("—");
  });
});

describe("buildClientSortHref", () => {
  it("keeps other params, toggles the active column, starts others asc", () => {
    const query = { view: "system:active", search: "apex" };
    const active = { key: "name", dir: "asc" } as const;
    expect(buildClientSortHref("acme", query, "name", active)).toBe(
      "/org/acme/clients?view=system%3Aactive&search=apex&sortBy=name&sortDir=desc"
    );
    expect(buildClientSortHref("acme", {}, "completeness", active)).toBe(
      "/org/acme/clients?sortBy=completeness&sortDir=asc"
    );
  });
});

describe("buildClientListParams", () => {
  it("D-1(B): no view → All clients → no params", () => {
    expect(select(null).view.id).toBe(ALL_CLIENTS_VIEW_ID);
    expect(buildClientListParams(select(null), {})).toEqual({});
    expect(buildClientListParams(select("garbage"), {})).toEqual({});
  });

  it("system views translate to their 615B params; a system id is never sent", () => {
    expect(buildClientListParams(select("system:active"), {})).toEqual({
      lifecycleStatus: "ACTIVE",
    });
    expect(buildClientListParams(select("system:fica-outstanding"), {})).toEqual({
      ficaStatus: "OUTSTANDING",
    });
    expect(buildClientListParams(select("system:with-trust-balance"), {})).toEqual({
      hasTrustBalance: "true",
    });
  });

  it("D-2: URL lifecycleStatus overrides a system view; ALL lifts it and is never sent", () => {
    expect(buildClientListParams(select("system:active"), { lifecycleStatus: "PROSPECT" })).toEqual(
      { lifecycleStatus: "PROSPECT" }
    );
    expect(buildClientListParams(select("system:active"), { lifecycleStatus: "ALL" })).toEqual({});
    expect(buildClientListParams(select(null), { lifecycleStatus: "onboarding" })).toEqual({
      lifecycleStatus: "ONBOARDING",
    });
    expect(buildClientListParams(select(null), { lifecycleStatus: "bogus" })).toEqual({});
  });

  it("showIncomplete=true forwards on both paths; tags on system views only (F-3)", () => {
    const saved = savedView();
    expect(
      buildClientListParams(select(null), { showIncomplete: "true", tags: " vip, ,urgent " })
    ).toEqual({ showIncomplete: "true", tags: "vip,urgent" });
    expect(
      buildClientListParams(select(saved.id, [saved]), {
        showIncomplete: "true",
        tags: "vip",
        lifecycleStatus: "PROSPECT",
      })
    ).toEqual({ view: saved.id, showIncomplete: "true" });
    expect(buildClientListParams(select(null), { showIncomplete: "false" })).toEqual({});
  });

  it("saved view: view=<uuid> plus the filters the backend applies on the view path (F-4)", () => {
    const saved = savedView({
      filters: {
        ficaStatus: "outstanding",
        hasTrustBalance: true,
        showIncomplete: "true",
        lifecycleStatus: "ACTIVE",
        tags: ["vip"],
      },
    });
    expect(buildClientListParams(select(saved.id, [saved]), {})).toEqual({
      view: saved.id,
      ficaStatus: "OUTSTANDING",
      hasTrustBalance: "true",
      showIncomplete: "true",
    });
    const bad = savedView({ filters: { ficaStatus: "MAYBE", hasTrustBalance: "no" } });
    expect(buildClientListParams(select(bad.id, [bad]), {})).toEqual({ view: bad.id });
  });
});

describe("savedViewShowIncomplete", () => {
  it("true only for a saved view whose filters store showIncomplete true", () => {
    const on = savedView({ filters: { showIncomplete: true } });
    const onStr = savedView({ filters: { showIncomplete: "true" } });
    const off = savedView({ filters: { showIncomplete: false } });
    expect(savedViewShowIncomplete(select(on.id, [on]))).toBe(true);
    expect(savedViewShowIncomplete(select(onStr.id, [onStr]))).toBe(true);
    expect(savedViewShowIncomplete(select(off.id, [off]))).toBe(false);
    expect(savedViewShowIncomplete(select(null))).toBe(false);
  });
});

describe("savedViewLifecycle", () => {
  it("returns a saved view's stored lifecycle (the backend does not apply it)", () => {
    const saved = savedView({ filters: { lifecycleStatus: "ACTIVE" } });
    expect(savedViewLifecycle(select(saved.id, [saved]))).toBe("ACTIVE");
    const bad = savedView({ filters: { lifecycleStatus: "ALL" } });
    expect(savedViewLifecycle(select(bad.id, [bad]))).toBeNull();
    expect(savedViewLifecycle(select("system:active"))).toBeNull();
  });
});

describe("applySavedViewLifecycle", () => {
  const rows = [
    row({ id: "a", lifecycleStatus: "ACTIVE" }),
    row({ id: "p", lifecycleStatus: "PROSPECT" }),
    row({ id: "b", lifecycleStatus: "ACTIVE" }),
  ];

  it("keeps only rows in a saved view's stored lifecycle, in order", () => {
    const saved = savedView({ filters: { lifecycleStatus: "ACTIVE" } });
    expect(ids(applySavedViewLifecycle(rows, select(saved.id, [saved])))).toEqual(["a", "b"]);
  });

  it("passes rows through for a saved view without a valid lifecycle and for system views", () => {
    const none = savedView();
    const bad = savedView({ filters: { lifecycleStatus: "ALL" } });
    expect(ids(applySavedViewLifecycle(rows, select(none.id, [none])))).toEqual(["a", "p", "b"]);
    expect(ids(applySavedViewLifecycle(rows, select(bad.id, [bad])))).toEqual(["a", "p", "b"]);
    // A system view's lifecycle is a list param the backend applies; never re-filtered here.
    expect(ids(applySavedViewLifecycle(rows, select("system:active")))).toEqual(["a", "p", "b"]);
  });

  it("returns a new array", () => {
    const result = applySavedViewLifecycle(rows, select(null));
    expect(result).not.toBe(rows);
    expect(result).toEqual(rows);
  });
});

describe("migrateLegacyClientColumns", () => {
  it("maps email/phone to contact once, drops status, keeps order and other ids", () => {
    expect(
      migrateLegacyClientColumns(["name", "email", "phone", "status", "createdAt", "cf:vat"])
    ).toEqual(["name", "contact", "createdAt", "cf:vat"]);
  });

  it("leaves current ids unchanged and removes duplicates", () => {
    expect(migrateLegacyClientColumns(["name", "contact", "email", "name"])).toEqual([
      "name",
      "contact",
    ]);
  });
});

describe("describeCarriedFilter — client system views (615D)", () => {
  it("summarises the client view params in words", () => {
    expect(describeCarriedFilter("lifecycleStatus", "ACTIVE")).toBe("Lifecycle: Active");
    expect(describeCarriedFilter("ficaStatus", "OUTSTANDING")).toBe("FICA outstanding");
    expect(describeCarriedFilter("ficaStatus", "DONE")).toBe("FICA done");
    expect(describeCarriedFilter("hasTrustBalance", "true")).toBe("Has a trust balance");
  });
});
