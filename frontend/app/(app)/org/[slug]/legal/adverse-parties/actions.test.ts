import { describe, it, expect, vi, beforeEach } from "vitest";

// Mock server-only so the "use server" module loads in the test environment.
vi.mock("server-only", () => ({}));

// Drive api.get with explicit response shapes. The Link to Matter crash (Phase 88 G-C2) came from
// fetchProjects reading `.content` off the flat array GET /api/matters really returns.
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

// fetchProjects goes through lib/api/matters, which calls `api` from "@/lib/api/client" — mock it
// with the same spy so the URL assertions hold.
vi.mock("@/lib/api/client", () => ({
  api: {
    get: (...args: unknown[]) => mockGet(...args),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  },
}));

vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));

import { fetchProjects } from "@/app/(app)/org/[slug]/legal/adverse-parties/actions";

describe("adverse-parties actions — fetchProjects", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("returns the flat matter array GET /api/matters sends", async () => {
    mockGet.mockResolvedValueOnce([
      { id: "m-1", name: "Mthembu v Road Accident Fund" },
      { id: "m-2", name: "Estate of Late Khumalo" },
    ]);
    await expect(fetchProjects()).resolves.toEqual([
      { id: "m-1", name: "Mthembu v Road Accident Fund" },
      { id: "m-2", name: "Estate of Late Khumalo" },
    ]);
    expect(mockGet).toHaveBeenCalledWith("/api/matters?size=200");
  });

  it("returns content for a paginated wrapper", async () => {
    mockGet.mockResolvedValueOnce({
      content: [{ id: "m-1", name: "Mthembu v Road Accident Fund" }],
      page: { totalElements: 1, totalPages: 1, size: 200, number: 0 },
    });
    await expect(fetchProjects()).resolves.toEqual([
      { id: "m-1", name: "Mthembu v Road Accident Fund" },
    ]);
  });

  it("returns [] on a null response", async () => {
    mockGet.mockResolvedValueOnce(null);
    await expect(fetchProjects()).resolves.toEqual([]);
  });

  it("returns [] for a wrapper without content", async () => {
    mockGet.mockResolvedValueOnce({ page: { totalElements: 0 } });
    await expect(fetchProjects()).resolves.toEqual([]);
  });
});
