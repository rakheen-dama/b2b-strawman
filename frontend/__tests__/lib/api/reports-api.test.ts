import { describe, it, expect, vi, beforeEach } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

// Mock server-only
vi.mock("server-only", () => ({}));

const {
  mockGet,
  mockPost,
  mockGetAuthFetchOptions,
  mockRedirectToReLogin,
  mockListMatters,
  mockListClients,
  MockApiError,
} = vi.hoisted(() => {
  class MockApiError extends Error {
    constructor(
      public status: number,
      message: string,
      public detail?: unknown
    ) {
      super(message);
      this.name = "ApiError";
    }
  }
  return {
    mockGet: vi.fn(),
    mockPost: vi.fn(),
    mockGetAuthFetchOptions: vi.fn(),
    mockRedirectToReLogin: vi.fn(() => {
      throw new Error("NEXT_REDIRECT");
    }),
    mockListMatters: vi.fn(),
    mockListClients: vi.fn(),
    MockApiError,
  };
});

vi.mock("@/lib/api/client", () => ({
  API_BASE: "http://gateway.test",
  ApiError: MockApiError,
  api: {
    get: (...args: unknown[]) => mockGet(...args),
    post: (...args: unknown[]) => mockPost(...args),
  },
  getAuthFetchOptions: (...args: unknown[]) => mockGetAuthFetchOptions(...args),
  resolveServerReturnTo: vi.fn().mockResolvedValue("/dashboard"),
}));

// The report actions import the barrel; share the same ApiError class so instanceof holds.
vi.mock("@/lib/api", () => ({
  ApiError: MockApiError,
  api: {
    get: (...args: unknown[]) => mockGet(...args),
    post: (...args: unknown[]) => mockPost(...args),
  },
}));
vi.mock("@/lib/api/matters", () => ({ listMatters: mockListMatters }));
vi.mock("@/lib/api/clients", () => ({ listClients: mockListClients }));
vi.mock("@/lib/auth/expiry", () => ({ redirectToReLogin: mockRedirectToReLogin }));
vi.mock("next/navigation", () => ({
  unstable_rethrow: (error: unknown) => {
    if (error instanceof Error && error.message === "NEXT_REDIRECT") throw error;
  },
}));

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

import {
  getReportDefinitions,
  getReportDefinition,
  executeReport,
  exportReportCsv,
  exportReportPdf,
  exportProfitabilityCsv,
} from "@/lib/api/reports";
import {
  executeReportAction,
  exportReportCsvAction,
  exportReportPdfAction,
  fetchEntityOptionsAction,
} from "@/app/(app)/org/[slug]/reports/[reportSlug]/actions";

const SESSION_AUTH = {
  headers: { cookie: "SESSION=abc" },
  credentials: "include" as RequestCredentials,
};
const EXPECTED_INIT = {
  method: "GET",
  headers: { cookie: "SESSION=abc" },
  credentials: "include",
  redirect: "manual",
};

describe("Reports API client", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGet.mockResolvedValue({});
    mockPost.mockResolvedValue({});
    mockGetAuthFetchOptions.mockResolvedValue(SESSION_AUTH);
  });

  it("getReportDefinitions calls GET /api/report-definitions", async () => {
    await getReportDefinitions();
    expect(mockGet).toHaveBeenCalledWith("/api/report-definitions");
  });

  it("getReportDefinition calls GET /api/report-definitions/{slug}", async () => {
    await getReportDefinition("revenue-summary");
    expect(mockGet).toHaveBeenCalledWith("/api/report-definitions/revenue-summary");
  });

  it("executeReport calls POST /api/report-definitions/{slug}/execute with body", async () => {
    const params = { from: "2026-01-01", to: "2026-01-31" };
    await executeReport("revenue-summary", params, 0, 20);
    expect(mockPost).toHaveBeenCalledWith("/api/report-definitions/revenue-summary/execute", {
      parameters: params,
      page: 0,
      size: 20,
    });
  });
});

describe("Report exports (auth-mode-aware base)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetAuthFetchOptions.mockResolvedValue(SESSION_AUTH);
  });

  it("exportReportCsv fetches the CSV through API_BASE with the session auth only", async () => {
    mockFetch.mockResolvedValue(new Response("Member,Hours\r\nAlice,8\r\n", { status: 200 }));

    const csv = await exportReportCsv("timesheet", {
      dateFrom: "2026-10-01",
      dateTo: "2026-10-31",
      groupBy: "member",
      projectId: "",
      memberId: null,
    });

    expect(mockGetAuthFetchOptions).toHaveBeenCalledWith("GET");
    expect(mockFetch).toHaveBeenCalledTimes(1);
    const [url, init] = mockFetch.mock.calls[0];
    expect(url).toBe(
      "http://gateway.test/api/report-definitions/timesheet/export/csv?dateFrom=2026-10-01&dateTo=2026-10-31&groupBy=member"
    );
    // Exactly the auth headers — no Authorization, Accept or Content-Type added.
    expect(init).toEqual(EXPECTED_INIT);
    expect(csv).toBe("Member,Hours\r\nAlice,8\r\n");
  });

  it("exportReportPdf fetches the PDF through API_BASE and returns base64", async () => {
    const bytes = new Uint8Array([0x25, 0x50, 0x44, 0x46]);
    mockFetch.mockResolvedValue(new Response(bytes, { status: 200 }));

    const base64 = await exportReportPdf("timesheet", { dateFrom: "2026-10-01" });

    const [url, init] = mockFetch.mock.calls[0];
    expect(url).toBe(
      "http://gateway.test/api/report-definitions/timesheet/export/pdf?dateFrom=2026-10-01"
    );
    expect(init).toEqual(EXPECTED_INIT);
    expect(base64).toBe(Buffer.from(bytes).toString("base64"));
    expect(Buffer.from(base64, "base64").toString("latin1")).toBe("%PDF");
  });

  it("exportReportPdf encodes the slug", async () => {
    mockFetch.mockResolvedValue(new Response(new Uint8Array([1]), { status: 200 }));
    await exportReportPdf("client ledger/statement", {});
    expect(mockFetch.mock.calls[0][0]).toBe(
      "http://gateway.test/api/report-definitions/client%20ledger%2Fstatement/export/pdf?"
    );
  });

  it("exportProfitabilityCsv sends from/to to the profitability export endpoint", async () => {
    mockFetch.mockResolvedValue(new Response("Matter,Client\r\n", { status: 200 }));

    const csv = await exportProfitabilityCsv("2026-10-01", "2026-10-31");

    const [url, init] = mockFetch.mock.calls[0];
    expect(url).toBe(
      "http://gateway.test/api/reports/profitability/export/csv?from=2026-10-01&to=2026-10-31"
    );
    expect(init).toEqual(EXPECTED_INIT);
    expect(csv).toBe("Matter,Client\r\n");
  });

  it("exportProfitabilityCsv omits empty or missing params (no trailing ?)", async () => {
    mockFetch.mockImplementation(() =>
      Promise.resolve(new Response("Matter\r\n", { status: 200 }))
    );

    await exportProfitabilityCsv();
    await exportProfitabilityCsv("", "");
    await exportProfitabilityCsv("2026-10-01");

    expect(mockFetch.mock.calls[0][0]).toBe(
      "http://gateway.test/api/reports/profitability/export/csv"
    );
    expect(mockFetch.mock.calls[1][0]).toBe(
      "http://gateway.test/api/reports/profitability/export/csv"
    );
    expect(mockFetch.mock.calls[2][0]).toBe(
      "http://gateway.test/api/reports/profitability/export/csv?from=2026-10-01"
    );
    for (const call of mockFetch.mock.calls) {
      expect(call[1]).toEqual(EXPECTED_INIT);
    }
  });

  it("maps a problem+json error to ApiError with the backend detail", async () => {
    mockFetch.mockResolvedValue(
      new Response(JSON.stringify({ status: 403, detail: "Insufficient permissions" }), {
        status: 403,
        statusText: "Forbidden",
        headers: { "Content-Type": "application/problem+json" },
      })
    );

    await expect(exportProfitabilityCsv("2026-10-01", "2026-10-31")).rejects.toMatchObject({
      status: 403,
      message: "Insufficient permissions",
      detail: { status: 403, detail: "Insufficient permissions" },
    });
  });

  it("maps a non-JSON error body to 'Export failed: <statusText>'", async () => {
    mockFetch.mockResolvedValue(
      new Response("<html>boom</html>", { status: 500, statusText: "Internal Server Error" })
    );

    await expect(exportReportCsv("timesheet", {})).rejects.toMatchObject({
      status: 500,
      message: "Export failed: Internal Server Error",
    });
  });

  it("maps a redirect response to a 401 ApiError (never parses a login page as the export)", async () => {
    mockFetch.mockResolvedValue(
      new Response(null, { status: 302, statusText: "Found", headers: { Location: "/login" } })
    );

    await expect(exportReportPdf("timesheet", {})).rejects.toMatchObject({
      status: 401,
      message: "Authentication session expired",
    });
  });

  it("funnels to re-login without fetching when there is no usable session", async () => {
    mockGetAuthFetchOptions.mockRejectedValue(new Error("no session"));

    await expect(exportReportCsv("timesheet", {})).rejects.toThrow("NEXT_REDIRECT");
    await expect(exportReportPdf("timesheet", {})).rejects.toThrow("NEXT_REDIRECT");
    await expect(exportProfitabilityCsv("2026-10-01", "2026-10-31")).rejects.toThrow(
      "NEXT_REDIRECT"
    );
    expect(mockRedirectToReLogin).toHaveBeenCalledWith("/dashboard", "expired");
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it("the module no longer uses getAuthToken or a module-local BACKEND_URL", () => {
    const source = readFileSync(resolve(__dirname, "../../../lib/api/reports.ts"), "utf8");
    expect(source).not.toMatch(/getAuthToken/);
    expect(source).not.toMatch(/BACKEND_URL/);
    expect(source).not.toMatch(/Bearer/);
    expect(source).not.toMatch(/Accept/);
  });
});

describe("Report actions", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetAuthFetchOptions.mockResolvedValue(SESSION_AUTH);
  });

  it("exportReportCsvAction / exportReportPdfAction let the re-login redirect propagate", async () => {
    mockGetAuthFetchOptions.mockRejectedValue(new Error("no session"));

    await expect(exportReportCsvAction("timesheet", {})).rejects.toThrow("NEXT_REDIRECT");
    await expect(exportReportPdfAction("timesheet", {})).rejects.toThrow("NEXT_REDIRECT");
  });

  it("executeReportAction / fetchEntityOptionsAction let the re-login redirect propagate", async () => {
    mockPost.mockRejectedValue(new Error("NEXT_REDIRECT"));
    mockListMatters.mockRejectedValue(new Error("NEXT_REDIRECT"));
    mockGet.mockRejectedValue(new Error("NEXT_REDIRECT"));
    mockListClients.mockRejectedValue(new Error("NEXT_REDIRECT"));

    await expect(executeReportAction("timesheet", {}, 0, 20)).rejects.toThrow("NEXT_REDIRECT");
    await expect(fetchEntityOptionsAction("project")).rejects.toThrow("NEXT_REDIRECT");
    await expect(fetchEntityOptionsAction("member")).rejects.toThrow("NEXT_REDIRECT");
    await expect(fetchEntityOptionsAction("customer")).rejects.toThrow("NEXT_REDIRECT");
  });

  it("export actions return the data on success", async () => {
    mockFetch.mockResolvedValueOnce(new Response("a,b\r\n", { status: 200 }));
    await expect(exportReportCsvAction("timesheet", {})).resolves.toEqual({ data: "a,b\r\n" });

    const bytes = new Uint8Array([0x25, 0x50, 0x44, 0x46]);
    mockFetch.mockResolvedValueOnce(new Response(bytes, { status: 200 }));
    await expect(exportReportPdfAction("timesheet", {})).resolves.toEqual({
      data: Buffer.from(bytes).toString("base64"),
    });
  });

  it("export actions map an ApiError to its message", async () => {
    const forbidden = () =>
      Promise.resolve(
        new Response(JSON.stringify({ detail: "Insufficient permissions" }), {
          status: 403,
          statusText: "Forbidden",
          headers: { "Content-Type": "application/problem+json" },
        })
      );
    mockFetch.mockImplementation(forbidden);

    await expect(exportReportCsvAction("timesheet", {})).resolves.toEqual({
      data: null,
      error: "Insufficient permissions",
    });
    await expect(exportReportPdfAction("timesheet", {})).resolves.toEqual({
      data: null,
      error: "Insufficient permissions",
    });
  });

  it("export actions map any other error to the generic message", async () => {
    mockFetch.mockRejectedValue(new Error("boom"));

    await expect(exportReportCsvAction("timesheet", {})).resolves.toEqual({
      data: null,
      error: "Failed to export CSV.",
    });
    await expect(exportReportPdfAction("timesheet", {})).resolves.toEqual({
      data: null,
      error: "Failed to export PDF.",
    });
  });

  it("executeReportAction / fetchEntityOptionsAction keep their ApiError and generic mapping", async () => {
    mockPost.mockRejectedValueOnce(new MockApiError(400, "Bad parameters"));
    await expect(executeReportAction("timesheet", {}, 0, 20)).resolves.toEqual({
      data: null,
      error: "Bad parameters",
    });
    mockPost.mockRejectedValueOnce(new Error("boom"));
    await expect(executeReportAction("timesheet", {}, 0, 20)).resolves.toEqual({
      data: null,
      error: "An unexpected error occurred.",
    });

    mockListMatters.mockRejectedValueOnce(new MockApiError(403, "Insufficient permissions"));
    await expect(fetchEntityOptionsAction("project")).resolves.toEqual({
      data: null,
      error: "Insufficient permissions",
    });
    mockListClients.mockRejectedValueOnce(new Error("boom"));
    await expect(fetchEntityOptionsAction("customer")).resolves.toEqual({
      data: null,
      error: "Failed to fetch entities.",
    });
  });
});
