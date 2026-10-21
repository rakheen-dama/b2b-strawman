import { describe, it, expect } from "vitest";
import {
  CLIENT_SYSTEM_VIEWS,
  DEFAULT_CLIENT_VIEW_ID,
  DEFAULT_MATTER_VIEW_ID,
  DORMANT_VIEW_ID,
  MATTER_SYSTEM_VIEWS,
  findSystemView,
  isSystemViewId,
  mergeViews,
  resolveRelativeDate,
  resolveViewSelection,
  selectedViewId,
  systemViewToFilters,
} from "@/lib/constants/system-views";
import type { SavedViewResponse } from "@/lib/types";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function savedView(id: string, name: string, sortOrder: number): SavedViewResponse {
  return {
    id,
    entityType: "PROJECT",
    name,
    filters: {},
    columns: null,
    shared: false,
    createdBy: "m1",
    sortOrder,
    createdAt: "2026-10-01T00:00:00Z",
    updatedAt: "2026-10-01T00:00:00Z",
  };
}

const SAVED = [
  savedView("6f1c2b1e-1b2a-4c3d-9e8f-0a1b2c3d4e5f", "Zulu", 0),
  savedView("0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d", "Alpha", 1),
];

describe("system view definitions", () => {
  const all = [...MATTER_SYSTEM_VIEWS, ...CLIENT_SYSTEM_VIEWS];

  it("ids are system-prefixed, never UUIDs, and unique", () => {
    for (const v of all) {
      expect(v.id.startsWith("system:")).toBe(true);
      expect(UUID.test(v.id)).toBe(false);
      expect(UUID.test(v.id.slice("system:".length))).toBe(false);
      expect(v.system).toBe(true);
    }
    expect(new Set(all.map((v) => v.id)).size).toBe(all.length);
  });

  it("entityType strings are storage-plane (ADR-342), never MATTER/CLIENT", () => {
    expect(MATTER_SYSTEM_VIEWS.every((v) => v.entityType === "PROJECT")).toBe(true);
    expect(CLIENT_SYSTEM_VIEWS.every((v) => v.entityType === "CUSTOMER")).toBe(true);
    for (const v of all) expect(["PROJECT", "CUSTOMER"]).toContain(v.entityType);
  });

  it("matter views: fixed order and params", () => {
    expect(MATTER_SYSTEM_VIEWS.map((v) => v.name)).toEqual([
      "My matters",
      "Open matters",
      "Awaiting fee note",
      "Dormant",
    ]);
    expect(MATTER_SYSTEM_VIEWS.map((v) => v.params)).toEqual([
      { mine: "true", status: "ACTIVE" },
      { status: "ACTIVE" },
      { status: "ACTIVE", hasUnbilled: "true" },
      { status: "ACTIVE", inactiveSince: "-90d" },
    ]);
    expect(findSystemView(MATTER_SYSTEM_VIEWS, DORMANT_VIEW_ID)?.name).toBe("Dormant");
  });

  it("client views: fixed order and 615B params", () => {
    expect(CLIENT_SYSTEM_VIEWS.map((v) => v.name)).toEqual([
      "Active",
      "FICA outstanding",
      "With trust balance",
    ]);
    expect(CLIENT_SYSTEM_VIEWS.map((v) => v.params)).toEqual([
      { lifecycleStatus: "ACTIVE" },
      { ficaStatus: "OUTSTANDING" },
      { hasTrustBalance: "true" },
    ]);
  });

  it("default ids name existing views", () => {
    expect(DEFAULT_MATTER_VIEW_ID).toBe("system:open-matters");
    expect(DEFAULT_CLIENT_VIEW_ID).toBe("system:active");
    expect(findSystemView(MATTER_SYSTEM_VIEWS, DEFAULT_MATTER_VIEW_ID)?.name).toBe("Open matters");
    expect(findSystemView(CLIENT_SYSTEM_VIEWS, DEFAULT_CLIENT_VIEW_ID)?.name).toBe("Active");
  });

  it("isSystemViewId distinguishes system ids from saved-view UUIDs", () => {
    expect(isSystemViewId("system:dormant")).toBe(true);
    expect(isSystemViewId(SAVED[0].id)).toBe(false);
    expect(isSystemViewId(null)).toBe(false);
    expect(isSystemViewId(undefined)).toBe(false);
  });
});

describe("mergeViews", () => {
  it("system views first in fixed order, then saved views in API order (not re-sorted)", () => {
    const merged = mergeViews(MATTER_SYSTEM_VIEWS, SAVED);
    expect(merged.map((v) => v.name)).toEqual([
      "My matters",
      "Open matters",
      "Awaiting fee note",
      "Dormant",
      "Zulu",
      "Alpha",
    ]);
  });

  it("no saved views → system views only", () => {
    expect(mergeViews(CLIENT_SYSTEM_VIEWS, [])).toHaveLength(3);
  });
});

describe("default selection (resolveViewSelection)", () => {
  it("no view param → the default system view", () => {
    const sel = resolveViewSelection(MATTER_SYSTEM_VIEWS, SAVED, null, DEFAULT_MATTER_VIEW_ID);
    expect(sel).toEqual({ kind: "system", view: MATTER_SYSTEM_VIEWS[1] });
    expect(
      resolveViewSelection(CLIENT_SYSTEM_VIEWS, [], undefined, DEFAULT_CLIENT_VIEW_ID).view.id
    ).toBe("system:active");
  });

  it("a known system id selects that system view", () => {
    const sel = resolveViewSelection(
      MATTER_SYSTEM_VIEWS,
      SAVED,
      "system:dormant",
      DEFAULT_MATTER_VIEW_ID
    );
    expect(sel.kind).toBe("system");
    expect(sel.view.id).toBe("system:dormant");
  });

  it("a known saved-view id selects that saved view", () => {
    const sel = resolveViewSelection(
      MATTER_SYSTEM_VIEWS,
      SAVED,
      SAVED[1].id,
      DEFAULT_MATTER_VIEW_ID
    );
    expect(sel).toEqual({ kind: "saved", view: SAVED[1] });
  });

  it("an unknown system id or garbage falls back to the default", () => {
    for (const raw of ["system:nope", "not-a-uuid", "", "6f1c2b1e-0000-4c3d-9e8f-0a1b2c3d4e5f"]) {
      expect(selectedViewId(MATTER_SYSTEM_VIEWS, SAVED, raw, DEFAULT_MATTER_VIEW_ID)).toBe(
        DEFAULT_MATTER_VIEW_ID
      );
    }
  });

  it("an unknown default id falls back to the first system view", () => {
    expect(selectedViewId(MATTER_SYSTEM_VIEWS, [], null, "system:missing")).toBe(
      "system:my-matters"
    );
  });
});

describe("resolveRelativeDate (R-3)", () => {
  const today = new Date(2026, 9, 21); // 21 Oct 2026, local

  it("-90d → 90 calendar days before today", () => {
    expect(resolveRelativeDate("-90d", today)).toBe("2026-07-23");
  });

  it("crosses month and year boundaries", () => {
    expect(resolveRelativeDate("-1d", new Date(2026, 0, 1))).toBe("2025-12-31");
    expect(resolveRelativeDate("-0d", today)).toBe("2026-10-21");
    expect(resolveRelativeDate("-365d", new Date(2024, 2, 1))).toBe("2023-03-02");
  });

  it("valid ISO dates pass through", () => {
    expect(resolveRelativeDate("2026-02-28", today)).toBe("2026-02-28");
  });

  it("invalid tokens → null (the param is dropped, never sent raw)", () => {
    for (const bad of ["90d", "-90", "-90w", "+90d", "2026-02-31", "2026-13-01", "abc", ""]) {
      expect(resolveRelativeDate(bad, today)).toBeNull();
    }
    expect(resolveRelativeDate(null, today)).toBeNull();
    expect(resolveRelativeDate(undefined, today)).toBeNull();
  });
});

describe("systemViewToFilters (F-4 Save-as-view pre-fill)", () => {
  it("status becomes a list; other params ride along raw (-90d stays relative)", () => {
    expect(systemViewToFilters(findSystemView(MATTER_SYSTEM_VIEWS, "system:dormant")!)).toEqual({
      status: ["ACTIVE"],
      inactiveSince: "-90d",
    });
    expect(systemViewToFilters(MATTER_SYSTEM_VIEWS[0])).toEqual({
      status: ["ACTIVE"],
      mine: "true",
    });
    expect(systemViewToFilters(MATTER_SYSTEM_VIEWS[2])).toEqual({
      status: ["ACTIVE"],
      hasUnbilled: "true",
    });
  });

  it("views without a status carry no status key", () => {
    expect(systemViewToFilters(CLIENT_SYSTEM_VIEWS[1])).toEqual({ ficaStatus: "OUTSTANDING" });
  });
});
