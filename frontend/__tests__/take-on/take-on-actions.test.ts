import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

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
  getAuthFetchOptions: vi.fn(async () => ({ headers: {}, credentials: "include" })),
  getFieldDefinitions: vi.fn(),
}));

import {
  downloadTakeOnResult,
  downloadTakeOnTemplate,
  fetchTakeOnBankingAccounts,
} from "@/app/(app)/org/[slug]/settings/matters/take-on-actions";

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

// One helper (downloadTakeOnCsv) serves both CSV downloads: the path, the file name from
// Content-Disposition (else the fallback), and the backend's detail on a refusal.
describe("take-on CSV downloads", () => {
  const fetchMock = vi.fn();
  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("reads the result report with the file name the backend gives", async () => {
    fetchMock.mockResolvedValue(
      new Response("Row,Status\r\n", {
        status: 200,
        headers: { "content-disposition": 'attachment; filename="take-on-result-b1.csv"' },
      })
    );
    const result = await downloadTakeOnResult("b1");
    expect(fetchMock).toHaveBeenCalledWith(
      "http://backend/api/take-on/batches/b1/result.csv",
      expect.objectContaining({ method: "GET", cache: "no-store" })
    );
    expect(result).toEqual({
      success: true,
      data: { fileName: "take-on-result-b1.csv", content: "Row,Status\r\n" },
    });
  });

  it("falls back to the dataset's file name for a template without Content-Disposition", async () => {
    fetchMock.mockResolvedValue(new Response("Client name\r\n", { status: 200 }));
    const result = await downloadTakeOnTemplate("TRUST_POSITION", "CLIENT_LEDGER");
    expect(fetchMock).toHaveBeenCalledWith(
      "http://backend/api/take-on/templates/TRUST_POSITION?section=CLIENT_LEDGER",
      expect.anything()
    );
    expect(result.data?.fileName).toBe("take-on-trust-position.csv");
  });

  it("shows the backend's detail on a refusal, else the generic text", async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          detail: "The result report is available once the take-on has been committed.",
        }),
        {
          status: 409,
        }
      )
    );
    expect(await downloadTakeOnResult("b1")).toEqual({
      success: false,
      error: "The result report is available once the take-on has been committed.",
    });
    fetchMock.mockResolvedValueOnce(new Response("not json", { status: 500 }));
    expect(await downloadTakeOnTemplate("CLIENTS")).toEqual({
      success: false,
      error: "The template could not be downloaded. Try again.",
    });
    fetchMock.mockRejectedValueOnce(new Error("offline"));
    expect(await downloadTakeOnResult("b1")).toEqual({
      success: false,
      error: "The result report could not be downloaded. Try again.",
    });
  });
});
