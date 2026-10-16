import { describe, it, expect } from "vitest";
import {
  resolveTabFromUrl,
  buildTabIdToGroupMap,
  getGroupForTab,
} from "@/lib/constants/tab-group-types";
import { TAB_GROUPS } from "@/lib/constants/project-tab-groups";
import {
  CUSTOMER_TAB_GROUPS,
  CUSTOMER_TAB_ID_TO_GROUP_MAP,
} from "@/lib/constants/customer-tab-groups";

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("resolveTabFromUrl (shared)", () => {
  it("returns overview for null param with CUSTOMER_TAB_GROUPS", () => {
    const result = resolveTabFromUrl(null, CUSTOMER_TAB_GROUPS);
    expect(result).toEqual({ groupId: "overview", tabId: "overview" });
  });

  it("resolves invoices to finance group with CUSTOMER_TAB_GROUPS", () => {
    const result = resolveTabFromUrl("invoices", CUSTOMER_TAB_GROUPS);
    expect(result).toEqual({ groupId: "finance", tabId: "invoices" });
  });

  it("resolves group-level alias to first sub-tab (work -> projects)", () => {
    const result = resolveTabFromUrl("work", CUSTOMER_TAB_GROUPS);
    expect(result).toEqual({ groupId: "work", tabId: "projects" });
  });

  it("returns overview for unknown param with CUSTOMER_TAB_GROUPS", () => {
    const result = resolveTabFromUrl("unknown", CUSTOMER_TAB_GROUPS);
    expect(result).toEqual({ groupId: "overview", tabId: "overview" });
  });

  it("still works with project TAB_GROUPS (backward compat)", () => {
    const result = resolveTabFromUrl("time", TAB_GROUPS);
    expect(result).toEqual({ groupId: "finance", tabId: "time" });
  });
});

describe("CUSTOMER_TAB_ID_TO_GROUP_MAP", () => {
  it("maps all 15 customer tab IDs correctly", () => {
    const expectedMappings: Record<string, string> = {
      details: "details",
      fields: "details",
      tags: "details",
      overview: "overview",
      projects: "work",
      documents: "work",
      generated: "work",
      invoices: "finance",
      rates: "finance",
      retainer: "finance",
      financials: "finance",
      trust: "finance",
      onboarding: "compliance",
      requests: "compliance",
      audit: "activity",
    };

    expect(Object.keys(CUSTOMER_TAB_ID_TO_GROUP_MAP)).toHaveLength(15);

    for (const [tabId, groupId] of Object.entries(expectedMappings)) {
      expect(CUSTOMER_TAB_ID_TO_GROUP_MAP[tabId]).toBe(groupId);
    }
  });
});

describe("buildTabIdToGroupMap", () => {
  it("handles empty groups array", () => {
    const result = buildTabIdToGroupMap([]);
    expect(result).toEqual({});
  });
});

describe("getGroupForTab", () => {
  it("returns the correct group for a known tab", () => {
    const result = getGroupForTab("invoices", CUSTOMER_TAB_ID_TO_GROUP_MAP);
    expect(result).toBe("finance");
  });

  it("returns null for an unknown tab", () => {
    const result = getGroupForTab("nonexistent", CUSTOMER_TAB_ID_TO_GROUP_MAP);
    expect(result).toBeNull();
  });
});
