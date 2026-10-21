import { describe, it, expect, vi, beforeEach } from "vitest";

// Phase 87 610C: characterization guard for the callers whose inline /api/matters|/api/clients
// literals were routed through lib/api/{matters,clients,paths}.ts. Written green on main BEFORE the
// refactor; must stay green after it — same verb, URL and body at every call site.

vi.mock("server-only", () => ({}));

const { mockGet, mockPost, mockPut, mockDelete, apiMock, MockApiError } = vi.hoisted(() => {
  const mockGet = vi.fn();
  const mockPost = vi.fn();
  const mockPut = vi.fn();
  const mockDelete = vi.fn();
  class MockApiError extends Error {
    constructor(
      public status: number,
      message: string,
      public detail?: Record<string, unknown>
    ) {
      super(message);
      this.name = "ApiError";
    }
  }
  const apiMock = {
    get: (...args: unknown[]) => mockGet(...args),
    post: (...args: unknown[]) => mockPost(...args),
    put: (...args: unknown[]) => mockPut(...args),
    patch: vi.fn(),
    delete: (...args: unknown[]) => mockDelete(...args),
  };
  return { mockGet, mockPost, mockPut, mockDelete, apiMock, MockApiError };
});

vi.mock("@/lib/api", () => ({ api: apiMock, ApiError: MockApiError }));
vi.mock("@/lib/api/client", () => ({ api: apiMock, ApiError: MockApiError }));
vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));

import {
  fetchComments,
  fetchProjectComments,
  createProjectComment,
  createComment,
  updateComment,
  deleteComment,
} from "@/lib/actions/comments";
import { fetchProjectActivity } from "@/lib/actions/activity";
import { fetchPortalContacts } from "@/lib/actions/acceptance-actions";
// 614B: the dashboard wrapper fetchAggregatedCompleteness was deleted with the admin stats
// column; the call site it wrapped keeps its characterization.
import { getAggregatedClientCompleteness } from "@/lib/api/clients";
import { getOnboardingPipelineData } from "@/lib/compliance-api";
import { getCustomerChecklists, instantiateChecklist } from "@/lib/checklist-api";
import { getProjectCorrespondence, getCustomerCorrespondence } from "@/lib/api/correspondence";
import { getCustomerRequests, getProjectRequests } from "@/lib/api/information-requests";
import { setCollectionsExemption } from "@/lib/api/collections";
import {
  evaluateClosure,
  closeMatter,
  reopenMatter,
  listClosureLog,
} from "@/lib/api/matter-closure";
import { generateStatement, listStatements, getStatement } from "@/lib/api/statement-of-account";

beforeEach(() => {
  vi.clearAllMocks();
  for (const m of [mockGet, mockPost, mockPut, mockDelete]) m.mockResolvedValue([]);
});

describe("lib/actions callers", () => {
  it("comments: list / create / update / delete", async () => {
    await fetchComments("m1", "TASK", "t1");
    expect(mockGet).toHaveBeenLastCalledWith(
      "/api/matters/m1/comments?entityType=TASK&entityId=t1"
    );
    await fetchProjectComments("m1");
    expect(mockGet).toHaveBeenLastCalledWith("/api/matters/m1/comments?entityType=PROJECT&size=50");
    await createProjectComment("acme", "m1", " hi ");
    expect(mockPost).toHaveBeenLastCalledWith("/api/matters/m1/comments", {
      entityType: "PROJECT",
      entityId: "m1",
      body: "hi",
      visibility: "SHARED",
    });
    await createComment("acme", "m1", "TASK", "t1", "x");
    expect(mockPost).toHaveBeenLastCalledWith("/api/matters/m1/comments", {
      entityType: "TASK",
      entityId: "t1",
      body: "x",
    });
    await updateComment("acme", "m1", "c1", "y", "INTERNAL");
    expect(mockPut).toHaveBeenLastCalledWith("/api/matters/m1/comments/c1", {
      body: "y",
      visibility: "INTERNAL",
    });
    await deleteComment("acme", "m1", "c1");
    expect(mockDelete).toHaveBeenLastCalledWith("/api/matters/m1/comments/c1");
  });

  it("activity: default size 20, optional entityType/page", async () => {
    await fetchProjectActivity("m1");
    expect(mockGet).toHaveBeenLastCalledWith("/api/matters/m1/activity?size=20");
    await fetchProjectActivity("m1", "TASK", 0, 10);
    expect(mockGet).toHaveBeenLastCalledWith(
      "/api/matters/m1/activity?entityType=TASK&page=0&size=10"
    );
  });

  it("portal contacts, aggregated completeness, onboarding pipeline", async () => {
    await fetchPortalContacts("c1");
    expect(mockGet).toHaveBeenLastCalledWith("/api/clients/c1/portal-contacts");
    mockGet.mockResolvedValueOnce({});
    await getAggregatedClientCompleteness();
    expect(mockGet).toHaveBeenLastCalledWith("/api/clients/completeness-summary/aggregated");
    await getOnboardingPipelineData();
    expect(mockGet).toHaveBeenLastCalledWith("/api/clients?lifecycleStatus=ONBOARDING");
  });

  it("checklists", async () => {
    await getCustomerChecklists("c1");
    expect(mockGet).toHaveBeenLastCalledWith("/api/clients/c1/checklists");
    await instantiateChecklist("c1", "tpl1");
    expect(mockPost).toHaveBeenLastCalledWith("/api/clients/c1/checklists", { templateId: "tpl1" });
  });
});

describe("lib/api sibling modules", () => {
  it("correspondence, information requests, collections exemption", async () => {
    await getProjectCorrespondence("m1", { page: 0, size: 5 });
    expect(mockGet).toHaveBeenLastCalledWith("/api/matters/m1/correspondence?page=0&size=5");
    await getCustomerCorrespondence("c1");
    expect(mockGet).toHaveBeenLastCalledWith("/api/clients/c1/correspondence");
    await getCustomerRequests("c1");
    expect(mockGet).toHaveBeenLastCalledWith("/api/clients/c1/information-requests");
    await getProjectRequests("m1");
    expect(mockGet).toHaveBeenLastCalledWith("/api/matters/m1/information-requests");
    await setCollectionsExemption("c1", true);
    expect(mockPut).toHaveBeenLastCalledWith("/api/clients/c1/collections-exemption", {
      collectionsExempt: true,
    });
  });

  it("matter closure and statements", async () => {
    await evaluateClosure("m1");
    expect(mockGet).toHaveBeenLastCalledWith("/api/matters/m1/closure/evaluate");
    const req = { reason: "CONCLUDED" } as unknown as Parameters<typeof closeMatter>[1];
    await closeMatter("m1", req);
    expect(mockPost).toHaveBeenLastCalledWith("/api/matters/m1/closure/close", req);
    await reopenMatter("m1", "n");
    expect(mockPost).toHaveBeenLastCalledWith("/api/matters/m1/closure/reopen", { notes: "n" });
    await listClosureLog("m1");
    expect(mockGet).toHaveBeenLastCalledWith("/api/matters/m1/closure/log");
    const sreq = {} as Parameters<typeof generateStatement>[1];
    await generateStatement("m1", sreq);
    expect(mockPost).toHaveBeenLastCalledWith("/api/matters/m1/statements", sreq);
    await listStatements("m1");
    expect(mockGet).toHaveBeenLastCalledWith("/api/matters/m1/statements?page=0&size=20");
    await getStatement("m1", "s1");
    expect(mockGet).toHaveBeenLastCalledWith("/api/matters/m1/statements/s1");
  });
});
