import { describe, it, expect } from "vitest";
import {
  NAV_GROUPS,
  NAV_ITEMS,
  UTILITY_ITEMS,
  SETTINGS_ITEMS,
} from "../nav-items";

describe("NAV_GROUPS", () => {
  it("has exactly 5 zones", () => {
    expect(NAV_GROUPS).toHaveLength(5);
  });

  it("zones are in correct order", () => {
    const ids = NAV_GROUPS.map((g) => g.id);
    expect(ids).toEqual(["work", "delivery", "clients", "finance", "team"]);
  });

  it("each group has at least one item", () => {
    for (const group of NAV_GROUPS) {
      expect(group.items.length).toBeGreaterThanOrEqual(1);
    }
  });

  it("total items across all groups equals 18", () => {
    const total = NAV_GROUPS.reduce((sum, g) => sum + g.items.length, 0);
    expect(total).toBe(18);
  });

  it("clients and finance zones default to collapsed", () => {
    const clients = NAV_GROUPS.find((g) => g.id === "clients");
    const finance = NAV_GROUPS.find((g) => g.id === "finance");
    expect(clients?.defaultExpanded).toBe(false);
    expect(finance?.defaultExpanded).toBe(false);
  });

  it("work, delivery, and team zones default to expanded", () => {
    const work = NAV_GROUPS.find((g) => g.id === "work");
    const delivery = NAV_GROUPS.find((g) => g.id === "delivery");
    const team = NAV_GROUPS.find((g) => g.id === "team");
    expect(work?.defaultExpanded).toBe(true);
    expect(delivery?.defaultExpanded).toBe(true);
    expect(team?.defaultExpanded).toBe(true);
  });

  it("every item has a label, href function, and icon", () => {
    for (const group of NAV_GROUPS) {
      for (const item of group.items) {
        expect(item.label).toBeTruthy();
        expect(typeof item.href).toBe("function");
        expect(item.href("test-slug")).toContain("/org/test-slug/");
        expect(item.icon).toBeDefined();
      }
    }
  });
});

describe("UTILITY_ITEMS", () => {
  it("has exactly 2 items (Notifications and Settings)", () => {
    expect(UTILITY_ITEMS).toHaveLength(2);
    const labels = UTILITY_ITEMS.map((i) => i.label);
    expect(labels).toEqual(["Notifications", "Settings"]);
  });

  it("items have valid href functions", () => {
    for (const item of UTILITY_ITEMS) {
      expect(typeof item.href).toBe("function");
      expect(item.href("my-org")).toContain("/org/my-org/");
    }
  });
});

describe("NAV_ITEMS (backward compat)", () => {
  it("is a flat array of all group items plus utility items", () => {
    const expected = [
      ...NAV_GROUPS.flatMap((g) => g.items),
      ...UTILITY_ITEMS,
    ];
    expect(NAV_ITEMS).toEqual(expected);
  });

  it("total count is 20 (18 group items + 2 utility items)", () => {
    expect(NAV_ITEMS).toHaveLength(20);
  });

  it("includes Notifications and Settings from UTILITY_ITEMS", () => {
    const labels = NAV_ITEMS.map((i) => i.label);
    expect(labels).toContain("Notifications");
    expect(labels).toContain("Settings");
  });
});

describe("SETTINGS_ITEMS", () => {
  it("has exactly 24 entries", () => {
    expect(SETTINGS_ITEMS).toHaveLength(24);
  });

  it("all entries have title, description, and href function", () => {
    for (const item of SETTINGS_ITEMS) {
      expect(item.title).toBeTruthy();
      expect(item.description).toBeTruthy();
      expect(typeof item.href).toBe("function");
    }
  });

  it("adminOnly items are flagged correctly", () => {
    const adminItems = SETTINGS_ITEMS.filter((i) => i.adminOnly);
    const adminTitles = adminItems.map((i) => i.title).sort();
    expect(adminTitles).toEqual([
      "Automations",
      "Batch Billing",
      "Email",
      "Roles & Permissions",
    ]);
  });

  it("comingSoon items are flagged correctly", () => {
    const comingSoonItems = SETTINGS_ITEMS.filter((i) => i.comingSoon);
    const titles = comingSoonItems.map((i) => i.title).sort();
    expect(titles).toEqual(["Organization", "Security"]);
  });

  it("all non-comingSoon items have valid href functions returning a path", () => {
    const activeItems = SETTINGS_ITEMS.filter((i) => !i.comingSoon);
    for (const item of activeItems) {
      const href = item.href("test-org");
      expect(href).toContain("/org/test-org/settings/");
    }
  });
});
