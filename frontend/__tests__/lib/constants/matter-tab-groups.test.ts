import { describe, it, expect } from "vitest";
import { MATTER_TAB_GROUPS, MATTER_TAB_ID_TO_GROUP_MAP } from "@/lib/constants/matter-tab-groups";
import { LEGACY_TAB_IDS, aliasLegacyTabId } from "@/lib/constants/legacy-tab-ids";
import { resolveTabFromUrl } from "@/lib/constants/tab-group-types";

// Phase 87 §12.6.5 (615E): 7 groups / 21 tabs, legal labels, stable group + deep-link ids.

const allTabs = MATTER_TAB_GROUPS.flatMap((g) => g.tabs);

describe("MATTER_TAB_GROUPS", () => {
  it("has 7 groups and 21 tabs", () => {
    expect(MATTER_TAB_GROUPS).toHaveLength(7);
    expect(allTabs).toHaveLength(21);
  });

  it("keeps the group ids stable, in order", () => {
    expect(MATTER_TAB_GROUPS.map((g) => g.id)).toEqual([
      "details",
      "overview",
      "work",
      "finance",
      "client",
      "schedule",
      "activity",
    ]);
  });

  it("labels the groups with the legal words", () => {
    expect(MATTER_TAB_GROUPS.map((g) => g.label)).toEqual([
      "Details",
      "Overview",
      "Work",
      "Fees",
      "Client",
      "Diary",
      "Activity",
    ]);
  });

  it("labels every tab exactly as §12.6.5", () => {
    expect(MATTER_TAB_GROUPS.map((g) => g.tabs.map((t) => [t.id, t.label]))).toEqual([
      [
        ["details", "Details"],
        ["fields", "Custom fields"],
      ],
      [["overview", "Overview"]],
      [
        ["tasks", "Tasks"],
        ["documents", "Documents"],
        ["correspondence", "Correspondence"],
        ["generated", "Generated documents"],
      ],
      [
        ["time", "Time recording"],
        ["disbursements", "Disbursements"],
        ["budget", "Fee estimate"],
        ["rates", "Tariffs"],
        ["financials", "Financials"],
        ["statements", "Statements of account"],
        ["trust", "Trust"],
      ],
      [
        ["clients", "Clients"],
        ["requests", "Information requests"],
        ["customer-comments", "Client comments"],
        ["adverse-parties", "Adverse parties"],
      ],
      [["court-dates", "Court dates"]],
      [
        ["activity", "Activity"],
        ["audit", "Audit trail"],
      ],
    ]);
  });

  it("keeps the deep-linked tab ids in their groups", () => {
    expect(MATTER_TAB_ID_TO_GROUP_MAP["court-dates"]).toBe("schedule");
    expect(MATTER_TAB_ID_TO_GROUP_MAP.disbursements).toBe("finance");
    expect(MATTER_TAB_ID_TO_GROUP_MAP.statements).toBe("finance");
    expect(MATTER_TAB_ID_TO_GROUP_MAP.trust).toBe("finance");
    expect(MATTER_TAB_ID_TO_GROUP_MAP.time).toBe("finance");
    expect(MATTER_TAB_ID_TO_GROUP_MAP.documents).toBe("work");
    expect(MATTER_TAB_ID_TO_GROUP_MAP.clients).toBe("client");
  });

  it("has no expenses, customers or members tab and unique tab ids", () => {
    const ids = allTabs.map((t) => t.id);
    expect(ids).not.toContain("expenses");
    expect(ids).not.toContain("customers");
    expect(ids).not.toContain("members");
    expect(new Set(ids).size).toBe(ids.length);
  });
});

describe("LEGACY_TAB_IDS / aliasLegacyTabId", () => {
  it("maps the two renamed ids and the removed Expenses tab", () => {
    expect(LEGACY_TAB_IDS).toEqual({
      customers: "clients",
      projects: "matters",
      expenses: "disbursements",
    });
  });

  it("aliases legacy ids and passes everything else through", () => {
    expect(aliasLegacyTabId("customers")).toBe("clients");
    expect(aliasLegacyTabId("expenses")).toBe("disbursements");
    expect(aliasLegacyTabId("projects")).toBe("matters");
    expect(aliasLegacyTabId("time")).toBe("time");
    expect(aliasLegacyTabId(null)).toBeNull();
    // Own-property check — prototype keys are not aliases.
    expect(aliasLegacyTabId("constructor")).toBe("constructor");
    expect(aliasLegacyTabId("toString")).toBe("toString");
  });

  it("resolves old matter ?tab= links to the renamed tabs", () => {
    expect(resolveTabFromUrl(aliasLegacyTabId("customers"), MATTER_TAB_GROUPS)).toEqual({
      groupId: "client",
      tabId: "clients",
    });
    expect(resolveTabFromUrl(aliasLegacyTabId("expenses"), MATTER_TAB_GROUPS)).toEqual({
      groupId: "finance",
      tabId: "disbursements",
    });
  });
});
