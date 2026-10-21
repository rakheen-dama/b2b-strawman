import { describe, it, expect } from "vitest";
import {
  resolveTabFromUrl,
  buildTabIdToGroupMap,
  getGroupForTab,
} from "@/lib/constants/tab-group-types";
import { MATTER_TAB_GROUPS } from "@/lib/constants/matter-tab-groups";
import { CLIENT_TAB_GROUPS, CLIENT_TAB_ID_TO_GROUP_MAP } from "@/lib/constants/client-tab-groups";

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("resolveTabFromUrl (shared)", () => {
  it("returns overview for null param with CLIENT_TAB_GROUPS", () => {
    const result = resolveTabFromUrl(null, CLIENT_TAB_GROUPS);
    expect(result).toEqual({ groupId: "overview", tabId: "overview" });
  });

  it("resolves invoices to finance group with CLIENT_TAB_GROUPS", () => {
    const result = resolveTabFromUrl("invoices", CLIENT_TAB_GROUPS);
    expect(result).toEqual({ groupId: "finance", tabId: "invoices" });
  });

  it("resolves group-level alias to first sub-tab (work -> matters)", () => {
    const result = resolveTabFromUrl("work", CLIENT_TAB_GROUPS);
    expect(result).toEqual({ groupId: "work", tabId: "matters" });
  });

  it("returns overview for unknown param with CLIENT_TAB_GROUPS", () => {
    const result = resolveTabFromUrl("unknown", CLIENT_TAB_GROUPS);
    expect(result).toEqual({ groupId: "overview", tabId: "overview" });
  });

  it("still works with MATTER_TAB_GROUPS (backward compat)", () => {
    const result = resolveTabFromUrl("time", MATTER_TAB_GROUPS);
    expect(result).toEqual({ groupId: "finance", tabId: "time" });
  });
});

describe("CLIENT_TAB_ID_TO_GROUP_MAP", () => {
  it("maps all 14 client tab IDs correctly", () => {
    const expectedMappings: Record<string, string> = {
      details: "details",
      fields: "details",
      tags: "details",
      overview: "overview",
      matters: "work",
      documents: "work",
      generated: "work",
      invoices: "finance",
      rates: "finance",
      financials: "finance",
      trust: "finance",
      onboarding: "compliance",
      requests: "compliance",
      audit: "activity",
    };

    expect(Object.keys(CLIENT_TAB_ID_TO_GROUP_MAP)).toHaveLength(14);

    for (const [tabId, groupId] of Object.entries(expectedMappings)) {
      expect(CLIENT_TAB_ID_TO_GROUP_MAP[tabId]).toBe(groupId);
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
    const result = getGroupForTab("invoices", CLIENT_TAB_ID_TO_GROUP_MAP);
    expect(result).toBe("finance");
  });

  it("returns null for an unknown tab", () => {
    const result = getGroupForTab("nonexistent", CLIENT_TAB_ID_TO_GROUP_MAP);
    expect(result).toBeNull();
  });
});
