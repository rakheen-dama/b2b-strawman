import { describe, it, expect } from "vitest";
import { NAV_GROUPS, NAV_ITEMS, UTILITY_ITEMS, SETTINGS_ITEMS } from "../nav-items";

describe("NAV_GROUPS", () => {
  it("has exactly 6 zones", () => {
    expect(NAV_GROUPS).toHaveLength(6);
  });

  it("zones are in correct order", () => {
    const ids = NAV_GROUPS.map((g) => g.id);
    expect(ids).toEqual(["work", "projects", "clients", "finance", "team", "ai"]);
  });

  it("each group has at least one item", () => {
    for (const group of NAV_GROUPS) {
      expect(group.items.length).toBeGreaterThanOrEqual(1);
    }
  });

  it("total items across all groups equals 30", () => {
    const total = NAV_GROUPS.reduce((sum, g) => sum + g.items.length, 0);
    expect(total).toBe(30);
  });

  it("clients, finance, and ai zones default to collapsed", () => {
    const clients = NAV_GROUPS.find((g) => g.id === "clients");
    const finance = NAV_GROUPS.find((g) => g.id === "finance");
    const ai = NAV_GROUPS.find((g) => g.id === "ai");
    expect(clients?.defaultExpanded).toBe(false);
    expect(finance?.defaultExpanded).toBe(false);
    expect(ai?.defaultExpanded).toBe(false);
  });

  it("work, projects, and team zones default to expanded", () => {
    const work = NAV_GROUPS.find((g) => g.id === "work");
    const projects = NAV_GROUPS.find((g) => g.id === "projects");
    const team = NAV_GROUPS.find((g) => g.id === "team");
    expect(work?.defaultExpanded).toBe(true);
    expect(projects?.defaultExpanded).toBe(true);
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

/**
 * AIVERIFY-011 — members with AI_EXECUTE must get an AI sidebar entry.
 *
 * The sidebar (`nav-zone.tsx`) filters a group's items by `requiredCapability`
 * and renders nothing when no item passes (`if (visibleItems.length === 0) return null`).
 * Before the fix the `ai` group held a SINGLE item gated on `AI_REVIEW`, so a plain
 * member (who has `AI_EXECUTE` but not `AI_REVIEW`/`AI_MANAGE`) saw the whole AI group
 * collapse to empty — no AI entry point at all. These tests guard that:
 *   (a) the AI group exposes at least one `AI_EXECUTE`-gated item (member visibility), and
 *   (b) the `AI_REVIEW` "AI Reviews" item is retained (owner/admin gate-review, unchanged).
 */
describe("AIVERIFY-011 — AI group capability gating", () => {
  // Mirrors nav-zone.tsx's plain-member filter: a member is not admin/owner,
  // so hasCapability(cap) === capSet.has(cap). (No module gating on the ai group.)
  function visibleForMember(memberCaps: string[]) {
    const ai = NAV_GROUPS.find((g) => g.id === "ai");
    const capSet = new Set(memberCaps);
    return (ai?.items ?? []).filter(
      (item) => !item.requiredCapability || capSet.has(item.requiredCapability)
    );
  }

  it("exposes at least one AI_EXECUTE-gated item so members see the AI group", () => {
    const ai = NAV_GROUPS.find((g) => g.id === "ai");
    const executeItems = (ai?.items ?? []).filter(
      (item) => item.requiredCapability === "AI_EXECUTE"
    );
    expect(executeItems.length).toBeGreaterThanOrEqual(1);
  });

  it("retains the AI_REVIEW 'AI Reviews' item (owner/admin gate review unchanged)", () => {
    const ai = NAV_GROUPS.find((g) => g.id === "ai");
    const reviewItem = (ai?.items ?? []).find((item) => item.label === "AI Reviews");
    expect(reviewItem).toBeDefined();
    expect(reviewItem?.requiredCapability).toBe("AI_REVIEW");
  });

  it("a member with only AI_EXECUTE sees the AI group (>=1 visible item)", () => {
    expect(visibleForMember(["AI_EXECUTE"]).length).toBeGreaterThanOrEqual(1);
  });

  it("a member with only AI_EXECUTE does NOT see the AI_REVIEW item", () => {
    const visible = visibleForMember(["AI_EXECUTE"]);
    expect(visible.some((i) => i.requiredCapability === "AI_REVIEW")).toBe(false);
  });

  it("a user with no AI capabilities sees no AI items (group would collapse)", () => {
    expect(visibleForMember([]).length).toBe(0);
  });
});

describe("UTILITY_ITEMS", () => {
  it("has exactly 3 items (Notifications, Settings, Help)", () => {
    expect(UTILITY_ITEMS).toHaveLength(3);
    const labels = UTILITY_ITEMS.map((i) => i.label);
    expect(labels).toEqual(["Notifications", "Settings", "Help"]);
  });

  it("items have valid href functions", () => {
    for (const item of UTILITY_ITEMS) {
      expect(typeof item.href).toBe("function");
      if (!item.external) {
        expect(item.href("my-org")).toContain("/org/my-org/");
      }
    }
  });
});

describe("NAV_ITEMS (backward compat)", () => {
  it("is a flat array of all group items plus utility items", () => {
    const expected = [...NAV_GROUPS.flatMap((g) => g.items), ...UTILITY_ITEMS];
    expect(NAV_ITEMS).toEqual(expected);
  });

  it("total count is 33 (30 group items + 3 utility items)", () => {
    expect(NAV_ITEMS).toHaveLength(33);
  });

  it("includes Notifications and Settings from UTILITY_ITEMS", () => {
    const labels = NAV_ITEMS.map((i) => i.label);
    expect(labels).toContain("Notifications");
    expect(labels).toContain("Settings");
  });
});

describe("SETTINGS_ITEMS", () => {
  it("has exactly 29 entries", () => {
    expect(SETTINGS_ITEMS).toHaveLength(29);
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
      "AI Configuration",
      "AI Review Queue",
      "Audit log",
      "Automations",
      "Batch Billing",
      "Email",
      "Features",
      "Roles & Permissions",
      "Trust Accounting",
    ]);
  });

  it("comingSoon items are flagged correctly", () => {
    const comingSoonItems = SETTINGS_ITEMS.filter((i) => i.comingSoon);
    const titles = comingSoonItems.map((i) => i.title).sort();
    expect(titles).toEqual(["Security"]);
  });

  it("all non-comingSoon items have valid href functions returning a path", () => {
    const activeItems = SETTINGS_ITEMS.filter((i) => !i.comingSoon);
    for (const item of activeItems) {
      const href = item.href("test-org");
      expect(href).toContain("/org/test-org/settings/");
    }
  });
});
