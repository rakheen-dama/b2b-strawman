import { describe, it, expect, vi, beforeEach } from "vitest";

// Mock server-only so the "use server" module loads in the test environment.
vi.mock("server-only", () => ({}));

// Mock the api module so we can drive each call to api.get with explicit
// response shapes — this is the whole point of these tests.
const mockGet = vi.fn();

vi.mock("@/lib/api", () => ({
  api: {
    get: (...args: unknown[]) => mockGet(...args),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
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

// next/cache is referenced via revalidatePath in actions.ts; stub it so the
// "use server" module can load without pulling Next.js runtime internals.
vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));

import {
  fetchCustomers,
  fetchProjects,
} from "@/app/(app)/org/[slug]/conflict-check/actions";

describe("conflict-check actions — fetchCustomers / fetchProjects defensive parse", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // -- fetchCustomers --

  it("fetchCustomers handles raw array shape (current backend contract)", async () => {
    mockGet.mockResolvedValueOnce([
      { id: "c1", name: "Sipho Dlamini" },
      { id: "c2", name: "Nontando Zulu" },
    ]);
    await expect(fetchCustomers()).resolves.toEqual([
      { id: "c1", name: "Sipho Dlamini" },
      { id: "c2", name: "Nontando Zulu" },
    ]);
    expect(mockGet).toHaveBeenCalledWith("/api/customers?size=200");
  });

  it("fetchCustomers handles paginated wrapper shape (forward-compat)", async () => {
    mockGet.mockResolvedValueOnce({
      content: [{ id: "c2", name: "Nontando Zulu" }],
      page: { totalElements: 1, totalPages: 1, size: 200, number: 0 },
    });
    await expect(fetchCustomers()).resolves.toEqual([
      { id: "c2", name: "Nontando Zulu" },
    ]);
  });

  it("fetchCustomers returns [] on null/undefined response", async () => {
    mockGet.mockResolvedValueOnce(null);
    await expect(fetchCustomers()).resolves.toEqual([]);
  });

  it("fetchCustomers returns [] on empty paginated wrapper missing content", async () => {
    mockGet.mockResolvedValueOnce({ page: { totalElements: 0 } });
    await expect(fetchCustomers()).resolves.toEqual([]);
  });

  // -- fetchProjects --

  it("fetchProjects handles raw array shape (current backend contract)", async () => {
    mockGet.mockResolvedValueOnce([
      { id: "p1", name: "Mathebula v Mthembu" },
      { id: "p2", name: "Estate of Late Khumalo" },
    ]);
    await expect(fetchProjects()).resolves.toEqual([
      { id: "p1", name: "Mathebula v Mthembu" },
      { id: "p2", name: "Estate of Late Khumalo" },
    ]);
    expect(mockGet).toHaveBeenCalledWith("/api/projects?size=200");
  });

  it("fetchProjects handles paginated wrapper shape (forward-compat)", async () => {
    mockGet.mockResolvedValueOnce({
      content: [{ id: "p1", name: "Mathebula v Mthembu" }],
      page: { totalElements: 1, totalPages: 1, size: 200, number: 0 },
    });
    await expect(fetchProjects()).resolves.toEqual([
      { id: "p1", name: "Mathebula v Mthembu" },
    ]);
  });

  it("fetchProjects returns [] on null/undefined response", async () => {
    mockGet.mockResolvedValueOnce(null);
    await expect(fetchProjects()).resolves.toEqual([]);
  });

  it("fetchProjects returns [] on empty paginated wrapper missing content", async () => {
    mockGet.mockResolvedValueOnce({ page: { totalElements: 0 } });
    await expect(fetchProjects()).resolves.toEqual([]);
  });
});
