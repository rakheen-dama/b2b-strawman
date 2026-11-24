import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

// Mock server-only so the "use server" module loads in the test environment.
vi.mock("server-only", () => ({}));

const { mockGet, mockPost, mockDelete, MockApiError } = vi.hoisted(() => {
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
  return { mockGet: vi.fn(), mockPost: vi.fn(), mockDelete: vi.fn(), MockApiError };
});

vi.mock("@/lib/api", () => ({
  api: { get: mockGet, post: mockPost, delete: mockDelete },
  API_BASE: "http://backend",
  ApiError: MockApiError,
  getAuthFetchOptions: vi.fn(async () => ({ headers: {}, credentials: "include" })),
  getFieldDefinitions: vi.fn(),
}));

import {
  downloadTakeOnReport,
  downloadTakeOnResult,
  downloadTakeOnTemplate,
  fetchTakeOnBankingAccounts,
  fetchTakeOnRows,
  getSignOffQueue,
  getSignOffReview,
  getTakeOnReport,
  signOff,
  withdrawMySignOff,
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

describe("fetchTakeOnRows — query", () => {
  beforeEach(() => {
    mockGet.mockReset();
    mockGet.mockResolvedValue({ content: [], page: {} });
  });

  it("asks for the rows that match an existing record with duplicatesOnly (OBS-655A-1)", async () => {
    await fetchTakeOnRows("b1", { status: "VALIDATED", duplicatesOnly: true, page: 0, size: 50 });
    expect(mockGet).toHaveBeenCalledWith(
      "/api/take-on/batches/b1/rows?status=VALIDATED&duplicatesOnly=true&page=0&size=50"
    );
  });

  it("sends no duplicatesOnly when the filter is off", async () => {
    await fetchTakeOnRows("b1", { status: "VALIDATED", page: 0, size: 50 });
    expect(mockGet).toHaveBeenCalledWith(
      "/api/take-on/batches/b1/rows?status=VALIDATED&page=0&size=50"
    );
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

  // 657D: a server action's arguments are untrusted — only pdf / csv reach the backend URL
  it("refuses a report file format other than pdf or csv, without calling the backend", async () => {
    const result = await downloadTakeOnReport("r1", "../batches/b1/result.csv" as "pdf");
    expect(result).toEqual({
      success: false,
      error: "The report file could not be downloaded. Try again.",
    });
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

// 657C: the signer's endpoints (SIGN_OFF_TRUST_TAKE_ON + VIEW_TRUST) and the report JSON.
describe("trust sign-off and report actions", () => {
  beforeEach(() => {
    mockGet.mockReset();
    mockPost.mockReset();
    mockDelete.mockReset();
  });

  it("reads the queue, a review page and the report from the signer's paths", async () => {
    mockGet.mockResolvedValue([]);
    await getSignOffQueue();
    await getSignOffReview("b 1", 2);
    await getTakeOnReport("r/1");
    expect(mockGet.mock.calls.map((c) => c[0])).toEqual([
      "/api/take-on/sign-off-queue",
      "/api/take-on/batches/b%201/sign-off-review?page=2&size=50",
      "/api/take-on/reports/r%2F1",
    ]);
  });

  it("signs off with the request body and withdraws with DELETE …/trust-sign-off", async () => {
    mockPost.mockResolvedValue({ batchId: "b1" });
    mockDelete.mockResolvedValue({ batchId: "b1" });
    const request = {
      validationHash: "h",
      statementVersion: "TAKEON-SIGNOFF-v1",
      declarationAccepted: true,
    };
    expect(await signOff("b1", request)).toEqual({ success: true, data: { batchId: "b1" } });
    expect(mockPost).toHaveBeenCalledWith("/api/take-on/batches/b1/sign-off", request);
    await withdrawMySignOff("b1");
    expect(mockDelete).toHaveBeenCalledWith("/api/take-on/batches/b1/trust-sign-off");
  });

  it("returns the backend's detail and code on a refusal (a non-signer's 403)", async () => {
    mockDelete.mockRejectedValue(
      new MockApiError(403, "Forbidden", {
        title: "Access denied",
        detail: "Only the member who signed off can withdraw this sign-off.",
      })
    );
    expect(await withdrawMySignOff("b1")).toEqual({
      success: false,
      error: "Only the member who signed off can withdraw this sign-off.",
    });
    mockPost.mockRejectedValue(
      new MockApiError(409, "Conflict", {
        code: "TAKE_ON_ROWS_REDACTED",
        detail: "A client named in this trust take-on was anonymised after it was validated.",
      })
    );
    expect(
      await signOff("b1", { validationHash: "h", statementVersion: "v", declarationAccepted: true })
    ).toEqual({
      success: false,
      error: "A client named in this trust take-on was anonymised after it was validated.",
      code: "TAKE_ON_ROWS_REDACTED",
    });
  });
});
