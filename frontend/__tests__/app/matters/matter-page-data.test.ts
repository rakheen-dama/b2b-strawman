import { describe, it, expect, vi, beforeEach } from "vitest";

// Phase 90 641B — the matter budget is a tab-panel read (Budget tab + Overview fee-estimate tile):
// loadMatterPageData reads it exactly once; a 404 (no budget set) or any failure → null.
// Every other read is non-fatal and irrelevant here, so its mock rejects (the "backend down" path).

vi.mock("server-only", () => ({}));

const { mockBudget, down } = vi.hoisted(() => ({
  mockBudget: vi.fn(),
  down: () => vi.fn().mockRejectedValue(new Error("backend down")),
}));

vi.mock("@/lib/auth", () => ({ getCurrentUserEmail: down() }));
vi.mock("@/lib/api", () => ({
  api: { get: down() },
  getFieldDefinitions: down(),
  getFieldGroups: down(),
  getGroupMembers: down(),
  getTags: down(),
  getTemplates: down(),
  getViews: down(),
}));
vi.mock("@/lib/api/matters", () => ({
  getMatterBudget: mockBudget,
  getMatterProfitability: down(),
  getMatterSetupStatus: down(),
  getMatterTags: down(),
  getMatterTimeSummary: down(),
  getMatterTimeSummaryByMember: down(),
  getMatterTimeSummaryByTask: down(),
  listMatterClients: down(),
  listMatterDocuments: down(),
  listMatterMembers: down(),
  listMatterTasks: down(),
}));
vi.mock("@/lib/api/clients", () => ({ getClientFicaStatus: down() }));
vi.mock("@/lib/api/correspondence", () => ({ getProjectCorrespondence: down() }));
vi.mock("@/lib/api/information-requests", () => ({ getProjectRequests: down() }));
vi.mock("@/lib/api/ai", () => ({ getAiProfile: down() }));

import { loadMatterPageData } from "@/app/(app)/org/[slug]/matters/[id]/matter-page-data";
import type { Matter } from "@/lib/types";

const MATTER = "m-1";
const BUDGET = { matterId: MATTER, hoursConsumedPct: 80 };
const args = {
  id: MATTER,
  project: { id: MATTER, customFields: {} } as unknown as Matter,
  caps: { capabilities: [], role: "member", isAdmin: false, isOwner: false, memberId: "x" },
  canManage: false,
};

describe("loadMatterPageData budget (641B)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("reads the budget exactly once and returns it", async () => {
    mockBudget.mockResolvedValue(BUDGET);

    expect((await loadMatterPageData(args)).budget).toEqual(BUDGET);
    expect(mockBudget).toHaveBeenCalledTimes(1);
    expect(mockBudget).toHaveBeenCalledWith(MATTER);
  });

  it("404 (no budget set) or any failure → null, never throws", async () => {
    mockBudget.mockRejectedValue(Object.assign(new Error("Not found"), { status: 404 }));

    expect((await loadMatterPageData(args)).budget).toBeNull();
    expect(mockBudget).toHaveBeenCalledTimes(1);
  });
});
