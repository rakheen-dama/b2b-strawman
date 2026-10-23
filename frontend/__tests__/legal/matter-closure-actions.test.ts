import { describe, it, expect, vi, beforeEach } from "vitest";

// Mock server-only so the "use server" module loads in the test environment.
vi.mock("server-only", () => ({}));

const mockCaps = vi.fn();
vi.mock("@/lib/api/capabilities", () => ({
  fetchMyCapabilities: () => mockCaps(),
}));

const mockCloseMatter = vi.fn();
vi.mock("@/lib/api/matter-closure", () => ({
  closeMatter: (...args: unknown[]) => mockCloseMatter(...args),
  evaluateClosure: vi.fn(),
  ClosureGatesFailedError: class ClosureGatesFailedError extends Error {},
}));

vi.mock("@/lib/api", () => ({
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

vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));

import { closeMatterAction } from "@/app/(app)/org/[slug]/matters/[id]/matter-closure-actions";

// Backend OrgRoleService.resolveCapabilities: admin = ALL_NAMES − OWNER_ONLY, so an admin's
// real capability set carries CLOSE_MATTER but never OVERRIDE_MATTER_CLOSURE.
const ADMIN_CAPS = {
  capabilities: ["CLOSE_MATTER", "INVOICING", "VIEW_TRUST", "PROJECT_MANAGEMENT"],
  role: "Admin",
  isAdmin: true,
  isOwner: false,
};
// Empty set on purpose: the owner path must hold via isOwner, not the capability list.
const OWNER_CAPS = {
  capabilities: [] as string[],
  role: "Owner",
  isAdmin: false,
  isOwner: true,
};

const baseReq = {
  reason: "CONCLUDED" as const,
  generateClosureLetter: false,
  generateStatementOfAccount: false,
  override: false,
};
const overrideReq = {
  ...baseReq,
  override: true,
  overrideJustification: "Owner override justification text",
};

describe("closeMatterAction — OVERRIDE_MATTER_CLOSURE is owner-only (ADR-355, G-D7)", () => {
  beforeEach(() => {
    mockCaps.mockReset();
    mockCloseMatter.mockReset();
    mockCloseMatter.mockResolvedValue({ projectId: "p1", status: "CLOSED" });
  });

  it("admin + override → forbidden, closeMatter not called", async () => {
    mockCaps.mockResolvedValue(ADMIN_CAPS);

    const result = await closeMatterAction("acme", "p1", overrideReq);

    expect(result).toEqual({
      success: false,
      kind: "forbidden",
      error: "You do not have permission to override closure gates.",
    });
    expect(mockCloseMatter).not.toHaveBeenCalled();
  });

  it("owner + override → closeMatter called", async () => {
    mockCaps.mockResolvedValue(OWNER_CAPS);

    const result = await closeMatterAction("acme", "p1", overrideReq);

    expect(result.success).toBe(true);
    expect(mockCloseMatter).toHaveBeenCalledTimes(1);
    expect(mockCloseMatter).toHaveBeenCalledWith("p1", overrideReq);
  });

  it("member explicitly granted OVERRIDE_MATTER_CLOSURE + override → closeMatter called", async () => {
    mockCaps.mockResolvedValue({
      capabilities: ["CLOSE_MATTER", "OVERRIDE_MATTER_CLOSURE"],
      role: "Senior Associate",
      isAdmin: false,
      isOwner: false,
    });

    const result = await closeMatterAction("acme", "p1", overrideReq);

    expect(result.success).toBe(true);
    expect(mockCloseMatter).toHaveBeenCalledWith("p1", overrideReq);
  });

  it("admin without override → closeMatter called (CLOSE_MATTER is not owner-only)", async () => {
    mockCaps.mockResolvedValue(ADMIN_CAPS);

    const result = await closeMatterAction("acme", "p1", baseReq);

    expect(result.success).toBe(true);
    expect(mockCloseMatter).toHaveBeenCalledWith("p1", baseReq);
  });
});
