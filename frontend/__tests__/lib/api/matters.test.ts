import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("server-only", () => ({}));

const mockGet = vi.fn();
const mockPost = vi.fn();
const mockPut = vi.fn();
const mockPatch = vi.fn();
const mockDelete = vi.fn();

vi.mock("@/lib/api/client", () => ({
  api: {
    get: (...args: unknown[]) => mockGet(...args),
    post: (...args: unknown[]) => mockPost(...args),
    put: (...args: unknown[]) => mockPut(...args),
    patch: (...args: unknown[]) => mockPatch(...args),
    delete: (...args: unknown[]) => mockDelete(...args),
  },
}));

import {
  MATTERS_PATH,
  matterPath,
  listMatters,
  getMatter,
  createMatter,
  updateMatter,
  deleteMatter,
  completeMatter,
  archiveMatter,
  reactivateMatter,
  getMatterTags,
  setMatterTags,
  setMatterFieldGroups,
  getMatterSetupStatus,
  getMatterUnbilledSummary,
  getMatterUpcomingDeadlines,
  getMatterMemberHours,
  getMatterActivity,
  getMatterTimeSummary,
  listMatterTasks,
  createMatterTask,
  listMatterDocuments,
  initMatterDocumentUpload,
  addMatterMember,
  removeMatterMember,
  transferMatterLead,
  linkMatterClient,
  unlinkMatterClient,
  getMatterBudgetStatus,
  upsertMatterBudget,
  listMatterComments,
  listMatterExpenses,
  writeOffMatterExpense,
  listMatterAdverseParties,
} from "@/lib/api/matters";

// Phase 87 S4 (610A): the matters module is the single place `/api/matters` is spelled.
describe("matterPath", () => {
  it("builds collection, item and nested paths", () => {
    expect(MATTERS_PATH).toBe("/api/matters");
    expect(matterPath()).toBe("/api/matters");
    expect(matterPath("m1")).toBe("/api/matters/m1");
    expect(matterPath("m1", "closure/evaluate")).toBe("/api/matters/m1/closure/evaluate");
  });
});

describe("matters API module URL building", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    for (const m of [mockGet, mockPost, mockPut, mockPatch, mockDelete]) m.mockResolvedValue([]);
  });

  it("listMatters: no params → bare collection; params skip empty values, keep order", async () => {
    await listMatters();
    expect(mockGet).toHaveBeenLastCalledWith("/api/matters");
    await listMatters({ view: "v1", status: "ACTIVE", customerId: undefined, size: 200 });
    expect(mockGet).toHaveBeenLastCalledWith("/api/matters?view=v1&status=ACTIVE&size=200");
  });

  it("core CRUD hits /api/matters[/{id}] with the right verbs", async () => {
    const req = { name: "Estate of X" };
    await getMatter("m1");
    expect(mockGet).toHaveBeenLastCalledWith("/api/matters/m1");
    await createMatter(req);
    expect(mockPost).toHaveBeenLastCalledWith("/api/matters", req);
    await updateMatter("m1", req);
    expect(mockPut).toHaveBeenLastCalledWith("/api/matters/m1", req);
    await deleteMatter("m1");
    expect(mockDelete).toHaveBeenLastCalledWith("/api/matters/m1");
  });

  it("lifecycle PATCHes: complete (optional ack body), archive, reopen", async () => {
    await completeMatter("m1");
    expect(mockPatch).toHaveBeenLastCalledWith("/api/matters/m1/complete", undefined);
    await completeMatter("m1", { acknowledgeUnbilledTime: true });
    expect(mockPatch).toHaveBeenLastCalledWith("/api/matters/m1/complete", {
      acknowledgeUnbilledTime: true,
    });
    await archiveMatter("m1");
    expect(mockPatch).toHaveBeenLastCalledWith("/api/matters/m1/archive");
    await reactivateMatter("m1");
    expect(mockPatch).toHaveBeenLastCalledWith("/api/matters/m1/reopen");
  });

  it("tags and field groups delegate to the PROJECT storage key → /api/matters", async () => {
    await getMatterTags("m1");
    expect(mockGet).toHaveBeenLastCalledWith("/api/matters/m1/tags");
    await setMatterTags("m1", { tagIds: ["t1"] });
    expect(mockPost).toHaveBeenLastCalledWith("/api/matters/m1/tags", { tagIds: ["t1"] });
    await setMatterFieldGroups("m1", ["g1"]);
    expect(mockPut).toHaveBeenLastCalledWith("/api/matters/m1/field-groups", {
      appliedFieldGroups: ["g1"],
    });
  });

  it("read models", async () => {
    await getMatterSetupStatus("m1");
    expect(mockGet).toHaveBeenLastCalledWith("/api/matters/m1/setup-status");
    await getMatterUnbilledSummary("m1");
    expect(mockGet).toHaveBeenLastCalledWith("/api/matters/m1/unbilled-summary");
    await getMatterUpcomingDeadlines("m1");
    expect(mockGet).toHaveBeenLastCalledWith("/api/matters/m1/upcoming-deadlines");
    await getMatterMemberHours("m1", { from: "2026-01-01", to: "2026-01-31" });
    expect(mockGet).toHaveBeenLastCalledWith(
      "/api/matters/m1/member-hours?from=2026-01-01&to=2026-01-31"
    );
    await getMatterActivity("m1", { entityType: "TASK", size: 20 });
    expect(mockGet).toHaveBeenLastCalledWith("/api/matters/m1/activity?entityType=TASK&size=20");
    await getMatterTimeSummary("m1");
    expect(mockGet).toHaveBeenLastCalledWith("/api/matters/m1/time-summary");
  });

  it("nested helpers: tasks, documents, members, clients, budget, comments, expenses, adverse parties", async () => {
    await listMatterTasks("m1", { status: "OPEN", recurring: true });
    expect(mockGet).toHaveBeenLastCalledWith("/api/matters/m1/tasks?status=OPEN&recurring=true");
    await createMatterTask("m1", { title: "Draft will" });
    expect(mockPost).toHaveBeenLastCalledWith("/api/matters/m1/tasks", { title: "Draft will" });
    await listMatterDocuments("m1");
    expect(mockGet).toHaveBeenLastCalledWith("/api/matters/m1/documents");
    const upload = { fileName: "a.pdf", contentType: "application/pdf", size: 10 };
    await initMatterDocumentUpload("m1", upload);
    expect(mockPost).toHaveBeenLastCalledWith("/api/matters/m1/documents/upload-init", upload);
    await addMatterMember("m1", "u1");
    expect(mockPost).toHaveBeenLastCalledWith("/api/matters/m1/members", { memberId: "u1" });
    await removeMatterMember("m1", "u1");
    expect(mockDelete).toHaveBeenLastCalledWith("/api/matters/m1/members/u1");
    await transferMatterLead("m1", "u1");
    expect(mockPut).toHaveBeenLastCalledWith("/api/matters/m1/members/u1/role", { role: "lead" });
    await linkMatterClient("m1", "c1");
    expect(mockPost).toHaveBeenLastCalledWith("/api/matters/m1/clients/c1");
    await unlinkMatterClient("m1", "c1");
    expect(mockDelete).toHaveBeenLastCalledWith("/api/matters/m1/clients/c1");
    await getMatterBudgetStatus("m1");
    expect(mockGet).toHaveBeenLastCalledWith("/api/matters/m1/budget/status");
    await upsertMatterBudget("m1", { budgetHours: 10 });
    expect(mockPut).toHaveBeenLastCalledWith("/api/matters/m1/budget", { budgetHours: 10 });
    await listMatterComments("m1", { entityType: "PROJECT", size: 50 });
    expect(mockGet).toHaveBeenLastCalledWith("/api/matters/m1/comments?entityType=PROJECT&size=50");
    await listMatterExpenses("m1", { page: 0, sort: "date,desc" });
    expect(mockGet).toHaveBeenLastCalledWith("/api/matters/m1/expenses?page=0&sort=date%2Cdesc");
    await writeOffMatterExpense("m1", "e1");
    expect(mockPatch).toHaveBeenLastCalledWith("/api/matters/m1/expenses/e1/write-off", {});
    await listMatterAdverseParties("m1");
    expect(mockGet).toHaveBeenLastCalledWith("/api/matters/m1/adverse-parties");
  });
});
