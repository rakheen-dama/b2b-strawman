import { describe, it, expect } from "vitest";
import { CLIENT_TAB_GROUPS, CLIENT_TAB_ID_TO_GROUP_MAP } from "@/lib/constants/client-tab-groups";
import { aliasLegacyTabId } from "@/lib/constants/legacy-tab-ids";
import { resolveTabFromUrl } from "@/lib/constants/tab-group-types";

// Phase 87 §12.6.5 (615E): 6 groups / 14 tabs, legal labels, stable group ids.

const allTabs = CLIENT_TAB_GROUPS.flatMap((g) => g.tabs);

describe("CLIENT_TAB_GROUPS", () => {
  it("has 6 groups and 14 tabs", () => {
    expect(CLIENT_TAB_GROUPS).toHaveLength(6);
    expect(allTabs).toHaveLength(14);
  });

  it("keeps the group ids stable, in order", () => {
    expect(CLIENT_TAB_GROUPS.map((g) => g.id)).toEqual([
      "details",
      "overview",
      "work",
      "finance",
      "compliance",
      "activity",
    ]);
  });

  it("labels the groups with the legal words", () => {
    expect(CLIENT_TAB_GROUPS.map((g) => g.label)).toEqual([
      "Details",
      "Overview",
      "Matters",
      "Fees",
      "FICA & compliance",
      "Activity",
    ]);
  });

  it("labels every tab exactly as §12.6.5", () => {
    expect(CLIENT_TAB_GROUPS.map((g) => g.tabs.map((t) => [t.id, t.label]))).toEqual([
      [
        ["details", "Details"],
        ["fields", "Custom fields"],
        ["tags", "Tags"],
      ],
      [["overview", "Overview"]],
      [
        ["matters", "Matters"],
        ["documents", "Documents"],
        ["generated", "Generated documents"],
      ],
      [
        ["invoices", "Fee notes"],
        ["rates", "Tariffs"],
        ["financials", "Financials"],
        ["trust", "Trust"],
      ],
      [
        ["onboarding", "FICA onboarding"],
        ["requests", "Information requests"],
      ],
      [["audit", "Audit trail"]],
    ]);
  });

  it("puts matters in the work group, keeps trust/documents, has no projects tab", () => {
    expect(CLIENT_TAB_ID_TO_GROUP_MAP.matters).toBe("work");
    expect(CLIENT_TAB_ID_TO_GROUP_MAP.trust).toBe("finance");
    expect(CLIENT_TAB_ID_TO_GROUP_MAP.documents).toBe("work");
    const ids = allTabs.map((t) => t.id);
    expect(ids).not.toContain("projects");
    expect(new Set(ids).size).toBe(ids.length);
  });

  it("resolves the old ?tab=projects link to the Matters tab", () => {
    expect(resolveTabFromUrl(aliasLegacyTabId("projects"), CLIENT_TAB_GROUPS)).toEqual({
      groupId: "work",
      tabId: "matters",
    });
  });
});
