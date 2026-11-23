import { describe, it, expect, vi, beforeEach } from "vitest";

// Mock server-only so the "use server" module loads in the test environment.
vi.mock("server-only", () => ({}));

const { mockGet, MockApiError } = vi.hoisted(() => {
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
  return { mockGet: vi.fn(), MockApiError };
});

vi.mock("@/lib/api", () => ({
  api: { get: mockGet },
  API_BASE: "http://backend",
  ApiError: MockApiError,
  getAuthFetchOptions: vi.fn(),
  getFieldDefinitions: vi.fn(),
}));

import { fetchTakeOnBankingAccounts } from "@/app/(app)/org/[slug]/settings/matters/take-on-actions";

const PERMISSION =
  "A trust take-on needs permission to view and manage trust accounting. Ask an owner or admin.";
const MODULE_OFF = "Turn on trust accounting and add a trust account first.";

describe("fetchTakeOnBankingAccounts — refusals", () => {
  beforeEach(() => {
    mockGet.mockReset();
  });

  it("asks for permission on a 403 that is not a module refusal", async () => {
    mockGet.mockRejectedValue(
      new MockApiError(403, "Forbidden", { status: 403, title: "Forbidden" })
    );

    const result = await fetchTakeOnBankingAccounts();

    expect(result).toEqual({ success: false, data: [], error: PERMISSION });
  });

  it("asks to turn on trust accounting on a module-off 403", async () => {
    mockGet.mockRejectedValue(
      new MockApiError(403, "Module not enabled", {
        status: 403,
        title: "Module not enabled",
        moduleId: "trust_accounting",
      })
    );

    const result = await fetchTakeOnBankingAccounts();

    expect(result).toEqual({ success: false, data: [], error: MODULE_OFF });
  });

  it("lists only open, non-investment accounts", async () => {
    mockGet.mockResolvedValue([
      { id: "a", accountName: "Main", accountType: "GENERAL", status: "ACTIVE" },
      { id: "b", accountName: "Invest", accountType: "INVESTMENT", status: "ACTIVE" },
      { id: "c", accountName: "Old", accountType: "GENERAL", status: "CLOSED" },
    ]);

    const result = await fetchTakeOnBankingAccounts();

    expect(result).toEqual({ success: true, data: [{ id: "a", accountName: "Main" }] });
  });
});
