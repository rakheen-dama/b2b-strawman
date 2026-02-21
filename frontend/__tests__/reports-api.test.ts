import { describe, it, expect, vi, beforeEach } from "vitest";

// Mock server-only
vi.mock("server-only", () => ({}));

// Mock the api module to capture calls
const mockGet = vi.fn();
const mockPost = vi.fn();

vi.mock("@/lib/api", () => ({
  api: {
    get: (...args: unknown[]) => mockGet(...args),
    post: (...args: unknown[]) => mockPost(...args),
  },
}));

import {
  getReportDefinitions,
  getReportDefinition,
  executeReport,
} from "@/lib/api/reports";

describe("Reports API client", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGet.mockResolvedValue({});
    mockPost.mockResolvedValue({});
  });

  it("getReportDefinitions calls GET /api/report-definitions", async () => {
    await getReportDefinitions();
    expect(mockGet).toHaveBeenCalledWith("/api/report-definitions");
  });

  it("getReportDefinition calls GET /api/report-definitions/{slug}", async () => {
    await getReportDefinition("revenue-summary");
    expect(mockGet).toHaveBeenCalledWith(
      "/api/report-definitions/revenue-summary",
    );
  });

  it("executeReport calls POST /api/report-definitions/{slug}/execute with body", async () => {
    const params = { from: "2026-01-01", to: "2026-01-31" };
    await executeReport("revenue-summary", params, 0, 20);
    expect(mockPost).toHaveBeenCalledWith(
      "/api/report-definitions/revenue-summary/execute",
      { parameters: params, page: 0, size: 20 },
    );
  });
});
