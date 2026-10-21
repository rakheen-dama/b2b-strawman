import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("server-only", () => ({}));

const mockGet = vi.fn();
const mockPost = vi.fn();
const mockPut = vi.fn();
const mockDelete = vi.fn();

vi.mock("@/lib/api/client", () => ({
  api: {
    get: (...args: unknown[]) => mockGet(...args),
    post: (...args: unknown[]) => mockPost(...args),
    put: (...args: unknown[]) => mockPut(...args),
    delete: (...args: unknown[]) => mockDelete(...args),
  },
}));

import {
  CLIENTS_PATH,
  clientPath,
  listClients,
  listClientRows,
  getClient,
  createClient,
  updateClient,
  archiveClient,
  unarchiveClient,
  transitionClientLifecycle,
  getClientLifecycleHistory,
  getClientLifecycleSummary,
  runClientDormancyCheck,
  getClientReadiness,
  getClientFicaStatus,
  getClientCompletenessSummary,
  getAggregatedClientCompleteness,
  listClientPortalContacts,
  listClientMatters,
  linkClientMatter,
  unlinkClientMatter,
  getClientTags,
  setClientFieldGroups,
  getClientUnbilledTime,
  anonymizeClient,
} from "@/lib/api/clients";

// Phase 87 S4 (610A): `clients.ts` (plural) is the Clients-context module; `client.ts`
// (singular) is the HTTP client it builds on (Open Question #16).
describe("clientPath", () => {
  it("builds collection, item and nested paths", () => {
    expect(CLIENTS_PATH).toBe("/api/clients");
    expect(clientPath()).toBe("/api/clients");
    expect(clientPath("c1")).toBe("/api/clients/c1");
    expect(clientPath("c1", "fica-status")).toBe("/api/clients/c1/fica-status");
  });
});

describe("clients API module URL building", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    for (const m of [mockGet, mockPost, mockPut, mockDelete]) m.mockResolvedValue([]);
  });

  it("listClients: bare collection, or filters with empty values skipped", async () => {
    await listClients();
    expect(mockGet).toHaveBeenLastCalledWith("/api/clients");
    await listClients({ status: "ACTIVE", view: "" });
    expect(mockGet).toHaveBeenLastCalledWith("/api/clients?status=ACTIVE");
    await listClients({ lifecycleStatus: "ONBOARDING" });
    expect(mockGet).toHaveBeenLastCalledWith("/api/clients?lifecycleStatus=ONBOARDING");
  });

  it("listClientRows: same endpoint, carries the 615B list params", async () => {
    await listClientRows({ lifecycleStatus: "ACTIVE", showIncomplete: true });
    expect(mockGet).toHaveBeenLastCalledWith(
      "/api/clients?lifecycleStatus=ACTIVE&showIncomplete=true"
    );
    await listClientRows({
      view: "v1",
      ficaStatus: "OUTSTANDING",
      hasTrustBalance: "true",
      tags: "vip,urgent",
    });
    expect(mockGet).toHaveBeenLastCalledWith(
      "/api/clients?view=v1&ficaStatus=OUTSTANDING&hasTrustBalance=true&tags=vip%2Curgent"
    );
  });

  it("core CRUD + archive/unarchive", async () => {
    const req = { name: "Sipho", email: "s@example.com" };
    await getClient("c1");
    expect(mockGet).toHaveBeenLastCalledWith("/api/clients/c1");
    await createClient(req);
    expect(mockPost).toHaveBeenLastCalledWith("/api/clients", req);
    await updateClient("c1", req);
    expect(mockPut).toHaveBeenLastCalledWith("/api/clients/c1", req);
    await archiveClient("c1");
    expect(mockDelete).toHaveBeenLastCalledWith("/api/clients/c1");
    await unarchiveClient("c1");
    expect(mockPost).toHaveBeenLastCalledWith("/api/clients/c1/unarchive");
  });

  it("lifecycle transition / history / summary / dormancy", async () => {
    await transitionClientLifecycle("c1", "ACTIVE");
    expect(mockPost).toHaveBeenLastCalledWith("/api/clients/c1/transition", {
      targetStatus: "ACTIVE",
      notes: null,
    });
    await getClientLifecycleHistory("c1");
    expect(mockGet).toHaveBeenLastCalledWith("/api/clients/c1/lifecycle");
    await getClientLifecycleSummary();
    expect(mockGet).toHaveBeenLastCalledWith("/api/clients/lifecycle-summary");
    await runClientDormancyCheck();
    expect(mockPost).toHaveBeenLastCalledWith("/api/clients/dormancy-check");
  });

  it("readiness, FICA, completeness, portal contacts", async () => {
    await getClientReadiness("c1");
    expect(mockGet).toHaveBeenLastCalledWith("/api/clients/c1/readiness");
    await getClientFicaStatus("c1");
    expect(mockGet).toHaveBeenLastCalledWith("/api/clients/c1/fica-status");
    await getClientCompletenessSummary(["c1", "c2"]);
    expect(mockGet).toHaveBeenLastCalledWith(
      "/api/clients/completeness-summary?clientIds=c1&clientIds=c2"
    );
    await getAggregatedClientCompleteness();
    expect(mockGet).toHaveBeenLastCalledWith("/api/clients/completeness-summary/aggregated");
    await listClientPortalContacts("c1");
    expect(mockGet).toHaveBeenLastCalledWith("/api/clients/c1/portal-contacts");
  });

  it("linked matters, tags (CUSTOMER key → /api/clients), unbilled time, anonymize", async () => {
    await listClientMatters("c1");
    expect(mockGet).toHaveBeenLastCalledWith("/api/clients/c1/matters");
    await linkClientMatter("c1", "m1");
    expect(mockPost).toHaveBeenLastCalledWith("/api/clients/c1/matters/m1");
    await unlinkClientMatter("c1", "m1");
    expect(mockDelete).toHaveBeenLastCalledWith("/api/clients/c1/matters/m1");
    await getClientTags("c1");
    expect(mockGet).toHaveBeenLastCalledWith("/api/clients/c1/tags");
    await setClientFieldGroups("c1", ["g1"]);
    expect(mockPut).toHaveBeenLastCalledWith("/api/clients/c1/field-groups", {
      appliedFieldGroups: ["g1"],
    });
    await getClientUnbilledTime("c1", { from: "2026-01-01" });
    expect(mockGet).toHaveBeenLastCalledWith("/api/clients/c1/unbilled-time?from=2026-01-01");
    await anonymizeClient("c1", { confirmationName: "Sipho", reason: "POPIA s24" });
    expect(mockPost).toHaveBeenLastCalledWith("/api/clients/c1/anonymize", {
      confirmationName: "Sipho",
      reason: "POPIA s24",
    });
  });
});
