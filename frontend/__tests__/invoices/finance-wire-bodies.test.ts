import { describe, it, expect, vi, beforeEach } from "vitest";

// Phase 87 S3 (ADR-337): guards the Batch 3 finance callers whose old keys the
// backend would silently ignore (Jackson drops unknown body properties; Spring
// drops unknown query params) — PR #1598 "Silent-drop hazards":
//   - POST /api/billing-rates with projectId/customerId → override becomes a member-default rate
//   - POST /api/billing-runs/{id}/preview with customerIds → preview runs for ALL clients
//   - GET  /api/invoices?customerId= → unfiltered invoice list
// plus the loud ones (400/404) so a regression is caught before the e2e stack:
//   - POST /api/invoices/validate-generation with customerId → 400 (@NotNull clientId)
//   - GET  /api/billing-rates/resolve?projectId= → 400 (required matterId)
//   - /api/projects/{id}/{expenses,time-summary,profitability}, /api/customers/{id}/profitability → 404

vi.mock("server-only", () => ({}));

const { mockGet, mockPost, mockPut, apiMock } = vi.hoisted(() => {
  const mockGet = vi.fn();
  const mockPost = vi.fn();
  const mockPut = vi.fn();
  const apiMock = {
    get: (...args: unknown[]) => mockGet(...args),
    post: (...args: unknown[]) => mockPost(...args),
    put: (...args: unknown[]) => mockPut(...args),
    patch: vi.fn(),
    delete: vi.fn(),
  };
  return { mockGet, mockPost, mockPut, apiMock };
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

import { createProjectBillingRate } from "@/app/(app)/org/[slug]/matters/[id]/rate-actions";
import { createCustomerBillingRate } from "@/app/(app)/org/[slug]/clients/[id]/rate-actions";
import { validateInvoiceGeneration } from "@/app/(app)/org/[slug]/clients/[id]/invoice-actions";
import { resolveRate } from "@/app/(app)/org/[slug]/matters/[id]/time-entry-actions";
import { fetchInvoices } from "@/app/(app)/org/[slug]/invoices/invoice-crud-actions";
import {
  getOrgProfitability,
  getProjectProfitability,
  getCustomerProfitability,
} from "@/app/(app)/org/[slug]/profitability/actions";
import { fetchProjectTimeSummary } from "@/app/(app)/org/[slug]/matters/[id]/time-summary-actions";
import { deleteExpense } from "@/app/(app)/org/[slug]/matters/[id]/expense-actions";
import { loadPreview } from "@/lib/api/billing-runs";

const RATE = {
  memberId: "mem-1",
  currency: "ZAR",
  hourlyRate: 1500,
  effectiveFrom: "2026-10-01",
};

describe("finance wire bodies (Phase 87 S3)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGet.mockResolvedValue({});
    mockPost.mockResolvedValue({ id: "new" });
    mockPut.mockResolvedValue({ id: "upd" });
  });

  it("createProjectBillingRate sends matterId (never projectId)", async () => {
    await createProjectBillingRate("acme", "m-1", RATE);
    const [path, body] = mockPost.mock.calls[0] as [string, Record<string, unknown>];
    expect(path).toBe("/api/billing-rates");
    expect(body.matterId).toBe("m-1");
    expect(body).not.toHaveProperty("projectId");
  });

  it("createCustomerBillingRate sends clientId (never customerId)", async () => {
    await createCustomerBillingRate("acme", "c-1", RATE);
    const [path, body] = mockPost.mock.calls[0] as [string, Record<string, unknown>];
    expect(path).toBe("/api/billing-rates");
    expect(body.clientId).toBe("c-1");
    expect(body).not.toHaveProperty("customerId");
  });

  it("validateInvoiceGeneration sends clientId (never customerId)", async () => {
    mockPost.mockResolvedValue([]);
    await validateInvoiceGeneration("c-1", ["te-1"]);
    const [path, body] = mockPost.mock.calls[0] as [string, Record<string, unknown>];
    expect(path).toBe("/api/invoices/validate-generation");
    expect(body.clientId).toBe("c-1");
    expect(body).not.toHaveProperty("customerId");
  });

  it("loadPreview sends clientIds (never customerIds)", async () => {
    await loadPreview("br-1", ["c-1", "c-2"]);
    const [path, body] = mockPost.mock.calls[0] as [string, Record<string, unknown>];
    expect(path).toBe("/api/billing-runs/br-1/preview");
    expect(body.clientIds).toEqual(["c-1", "c-2"]);
    expect(body).not.toHaveProperty("customerIds");
  });

  it("resolveRate queries ?matterId= (never ?projectId=)", async () => {
    await resolveRate("mem-1", "m-1", "2026-10-20");
    const url = mockGet.mock.calls[0][0] as string;
    expect(url).toContain("/api/billing-rates/resolve?");
    expect(url).toContain("matterId=m-1");
    expect(url).not.toContain("projectId=");
  });

  it("fetchInvoices filters with ?clientId= (never ?customerId=)", async () => {
    mockGet.mockResolvedValue([]);
    await fetchInvoices("SENT", "c-1");
    const url = mockGet.mock.calls[0][0] as string;
    expect(url).toBe("/api/invoices?status=SENT&clientId=c-1");
  });

  it("getProjectProfitability uses /api/matters/{id}/profitability", async () => {
    await getProjectProfitability("m-1");
    expect(mockGet).toHaveBeenCalledWith("/api/matters/m-1/profitability");
  });

  it("getCustomerProfitability uses /api/clients/{id}/profitability", async () => {
    await getCustomerProfitability("c-1");
    expect(mockGet).toHaveBeenCalledWith("/api/clients/c-1/profitability");
  });

  it("fetchProjectTimeSummary uses /api/matters/{id}/time-summary", async () => {
    await fetchProjectTimeSummary("m-1");
    expect(mockGet).toHaveBeenCalledWith("/api/matters/m-1/time-summary");
  });

  it("deleteExpense uses /api/matters/{id}/expenses/{expenseId}", async () => {
    await deleteExpense("acme", "m-1", "e-1");
    expect(apiMock.delete).toHaveBeenCalledWith("/api/matters/m-1/expenses/e-1");
  });

  // D4 (Phase 87): the backend param on /api/reports/profitability is still
  // customerId (ReportController:66). A mechanical rename to clientId would be
  // silently dropped by Spring and return org-wide profitability — keep this red
  // until the backend moves.
  it("getOrgProfitability still filters with ?customerId= (D4 keep)", async () => {
    await getOrgProfitability("2026-10-01", "2026-10-31", "c-1");
    expect(mockGet).toHaveBeenCalledWith(
      "/api/reports/profitability?from=2026-10-01&to=2026-10-31&customerId=c-1"
    );
  });
});
