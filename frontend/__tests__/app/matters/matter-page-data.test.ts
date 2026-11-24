import { describe, it, expect, vi, beforeEach } from "vitest";

// Phase 90 641B — the matter budget is a tab-panel read (Budget tab + Overview fee-estimate tile):
// loadMatterPageData reads it exactly once; a 404 (no budget set) or any failure → null.
// Every other read is non-fatal and irrelevant here, so its mock rejects (the "backend down" path).

vi.mock("server-only", () => ({}));
// The real settle() for the FICA read: its deps are the API client's ApiError and the
// expired-session funnel.
const { MockApiError } = vi.hoisted(() => ({
  MockApiError: class extends Error {
    constructor(
      public status: number,
      message: string
    ) {
      super(message);
      this.name = "ApiError";
    }
  },
}));
vi.mock("@/lib/api/client", () => ({ ApiError: MockApiError }));
vi.mock("@/lib/auth/expiry-server", () => ({ rethrowOnExpiredSession: vi.fn() }));

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
  listMatterClients: mockMatterClients,
  listMatterDocuments: down(),
  listMatterMembers: down(),
  listMatterTasks: down(),
}));
const { mockFica, mockMatterClients } = vi.hoisted(() => ({
  mockFica: vi.fn(),
  mockMatterClients: vi.fn(),
}));
vi.mock("@/lib/api/clients", () => ({ getClientFicaStatus: mockFica }));
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
    mockMatterClients.mockRejectedValue(new Error("backend down"));
    mockFica.mockRejectedValue(new Error("backend down"));
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

describe("loadMatterPageData FICA status (655B, D-152: fica-status needs CUSTOMER_MANAGEMENT)", () => {
  const FICA = {
    clientId: "c-1",
    status: "DONE",
    lastVerifiedAt: "2026-05-01T08:00:00Z",
    requestId: null,
    checklistInstanceId: "chk-1",
    reviewReasons: [],
    attestationId: null,
  };
  const withCaps = (capabilities: string[], isAdmin = false) => ({
    ...args,
    caps: { ...args.caps, capabilities, isAdmin },
  });

  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(console, "error").mockImplementation(() => {});
    mockBudget.mockResolvedValue(null);
    mockMatterClients.mockResolvedValue([{ id: "c-1", name: "Acme" }]);
    mockFica.mockResolvedValue(FICA);
  });

  it("without CUSTOMER_MANAGEMENT: absent (card hidden), fica-status never read", async () => {
    expect((await loadMatterPageData(args)).ficaStatus).toEqual({ state: "absent" });
    expect(mockFica).not.toHaveBeenCalled();
  });

  it.each([
    ["admin", withCaps([], true)],
    ["CUSTOMER_MANAGEMENT", withCaps(["CUSTOMER_MANAGEMENT"])],
  ])("%s: reads the primary client's status once", async (_, input) => {
    expect((await loadMatterPageData(input)).ficaStatus).toEqual({ state: "ok", data: FICA });
    expect(mockFica).toHaveBeenCalledTimes(1);
    expect(mockFica).toHaveBeenCalledWith("c-1");
  });

  it("a 403 is absent (card hidden), never an error page", async () => {
    mockFica.mockRejectedValue(new MockApiError(403, "Forbidden"));
    expect((await loadMatterPageData(withCaps(["CUSTOMER_MANAGEMENT"]))).ficaStatus).toEqual({
      state: "absent",
    });
  });

  it("any other failure is an error (the card's 'Status unavailable')", async () => {
    mockFica.mockRejectedValue(new Error("boom"));
    expect((await loadMatterPageData(withCaps([], true))).ficaStatus).toEqual({ state: "error" });
  });

  it("no client on the matter: absent, never read", async () => {
    mockMatterClients.mockResolvedValue([]);
    expect((await loadMatterPageData(withCaps([], true))).ficaStatus).toEqual({ state: "absent" });
    expect(mockFica).not.toHaveBeenCalled();
  });
});
