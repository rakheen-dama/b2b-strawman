import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("server-only", () => ({}));

const mockGet = vi.fn();
const mockPost = vi.fn();
const mockPut = vi.fn();

vi.mock("@/lib/api/client", () => ({
  api: {
    get: (...args: unknown[]) => mockGet(...args),
    post: (...args: unknown[]) => mockPost(...args),
    put: (...args: unknown[]) => mockPut(...args),
  },
}));

import { entityApiPrefix, getEntityTags, setEntityTags } from "@/lib/api/tags";
import { setEntityFieldGroups } from "@/lib/api/fields";

// Phase 87 S1 (ADR-337): matters moved to /api/matters/**, but the EntityType
// storage key stays "PROJECT" (ADR-342). Phase 87 S2: clients moved to
// /api/clients/** ("CUSTOMER" storage key unchanged).
describe("entityApiPrefix", () => {
  it("maps the PROJECT storage key to the matters collection", () => {
    expect(entityApiPrefix("PROJECT")).toBe("matters");
  });

  it("maps the CUSTOMER storage key to the clients collection", () => {
    expect(entityApiPrefix("CUSTOMER")).toBe("clients");
  });

  it("keeps the pluralised lowercase segment for the other entity types", () => {
    expect(entityApiPrefix("TASK")).toBe("tasks");
    expect(entityApiPrefix("INVOICE")).toBe("invoices");
  });
});

describe("entity tag / field-group endpoints use entityApiPrefix", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGet.mockResolvedValue([]);
    mockPost.mockResolvedValue([]);
    mockPut.mockResolvedValue([]);
  });

  it("getEntityTags(PROJECT) calls GET /api/matters/{id}/tags", async () => {
    await getEntityTags("PROJECT", "m1");
    expect(mockGet).toHaveBeenCalledWith("/api/matters/m1/tags");
  });

  it("setEntityTags(PROJECT) calls POST /api/matters/{id}/tags", async () => {
    await setEntityTags("PROJECT", "m1", { tagIds: ["t1"] });
    expect(mockPost).toHaveBeenCalledWith("/api/matters/m1/tags", { tagIds: ["t1"] });
  });

  it("setEntityFieldGroups(PROJECT) calls PUT /api/matters/{id}/field-groups", async () => {
    await setEntityFieldGroups("PROJECT", "m1", ["g1"]);
    expect(mockPut).toHaveBeenCalledWith("/api/matters/m1/field-groups", {
      appliedFieldGroups: ["g1"],
    });
  });

  it("routes CUSTOMER to /api/clients and leaves TASK unchanged", async () => {
    await getEntityTags("CUSTOMER", "c1");
    await setEntityFieldGroups("TASK", "t1", []);
    expect(mockGet).toHaveBeenCalledWith("/api/clients/c1/tags");
    expect(mockPut).toHaveBeenCalledWith("/api/tasks/t1/field-groups", {
      appliedFieldGroups: [],
    });
  });
});
