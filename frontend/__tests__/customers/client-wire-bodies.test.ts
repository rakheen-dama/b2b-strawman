import { describe, it, expect, vi, beforeEach } from "vitest";

// Phase 87 S2 (ADR-337): guards the Batch 2 request bodies whose old keys the
// backend would silently drop (Jackson ignores unknown properties):
//   - POST /api/clients with `customerType` → client created with a null type (PR #1594 R3)
//   - POST /api/information-requests with `projectId` → request created without a matter link
//   - PUT  /api/information-requests/{id} with `projectId` → matter link CLEARED (PR #1595)

vi.mock("server-only", () => ({}));

const { mockPost, mockPut, apiMock } = vi.hoisted(() => {
  const mockPost = vi.fn();
  const mockPut = vi.fn();
  const apiMock = {
    get: vi.fn(),
    post: (...args: unknown[]) => mockPost(...args),
    put: (...args: unknown[]) => mockPut(...args),
    delete: vi.fn(),
  };
  return { mockPost, mockPut, apiMock };
});

vi.mock("@/lib/api", () => ({
  api: apiMock,
  ApiError: class ApiError extends Error {
    constructor(
      public status: number,
      message: string
    ) {
      super(message);
      this.name = "ApiError";
    }
  },
}));
vi.mock("@/lib/api/client", () => ({ api: apiMock }));
vi.mock("@/lib/api/capabilities", () => ({
  fetchMyCapabilities: vi
    .fn()
    .mockResolvedValue({ isAdmin: true, isOwner: true, capabilities: [] }),
}));
vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));

import { createCustomer, updateCustomer } from "@/app/(app)/org/[slug]/clients/actions";
import { createRequest, updateRequest } from "@/lib/api/information-requests";

describe("client wire bodies (Phase 87 S2)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockPost.mockResolvedValue({ id: "c-new" });
    mockPut.mockResolvedValue({ id: "c-1" });
  });

  it("createCustomer POSTs /api/clients with clientType (never customerType)", async () => {
    const result = await createCustomer("acme", {
      name: "Mathebula Trust",
      email: "trust@example.com",
      customerType: "TRUST",
    });

    expect(result).toEqual({ success: true, customerId: "c-new" });
    expect(mockPost).toHaveBeenCalledTimes(1);
    const [path, body] = mockPost.mock.calls[0] as [string, Record<string, unknown>];
    expect(path).toBe("/api/clients");
    expect(body.clientType).toBe("TRUST");
    expect(body).not.toHaveProperty("customerType");
  });

  it("updateCustomer PUTs /api/clients/{clientId}", async () => {
    await updateCustomer("acme", "c-1", { name: "Mathebula Trust", email: "trust@example.com" });
    expect(mockPut).toHaveBeenCalledWith("/api/clients/c-1", expect.any(Object));
  });

  it("createRequest sends clientId + matterId (never customerId/projectId)", async () => {
    await createRequest({
      clientId: "c-1",
      matterId: "m-1",
      portalContactId: "pc-1",
      reminderIntervalDays: 5,
    });
    const [path, body] = mockPost.mock.calls[0] as [string, Record<string, unknown>];
    expect(path).toBe("/api/information-requests");
    expect(body).toMatchObject({ clientId: "c-1", matterId: "m-1" });
    expect(body).not.toHaveProperty("customerId");
    expect(body).not.toHaveProperty("projectId");
  });

  it("updateRequest keeps the matter link under the matterId key", async () => {
    await updateRequest("ir-1", { matterId: "m-1", reminderIntervalDays: 7 });
    const [path, body] = mockPut.mock.calls[0] as [string, Record<string, unknown>];
    expect(path).toBe("/api/information-requests/ir-1");
    expect(body.matterId).toBe("m-1");
    expect(body).not.toHaveProperty("projectId");
  });
});
