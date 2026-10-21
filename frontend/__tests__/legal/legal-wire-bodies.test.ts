import { describe, it, expect, vi, beforeEach } from "vitest";

// Phase 87 S3 Batch 4 (ADR-337): guards the legal/trust/integration callers of the
// 609D-renamed backend (PR #1601). Jackson drops unknown body properties and Spring
// drops unknown query params, so a stale key fails SILENTLY:
//   - POST /api/conflict-checks with customerId/projectId → client link + GAP-L-28 self-match exclusion lost
//   - POST /api/trust-accounts/{id}/transactions/{deposit,payment,refund,transfer} with projectId → no matter link
//   - GET  /api/court-dates?projectId= / /api/prescription-trackers?customerId= → unfiltered lists
//   - GET  /api/legal/disbursements?projectId= → unfiltered list
//   - trust-transaction picker filtering on t.projectId → always empty
// plus the loud ones (400/404) so a regression is caught before the e2e stack:
//   - court-date / prescription / adverse-party-link / disbursement / investment create bodies (@NotNull)
//   - GET /api/projects/{id}/adverse-parties (404), /unbilled?projectId= (400), /xero/import-customers (404)
//   - RetentionElapsedException ProblemDetail key projectId → matterId

vi.mock("server-only", () => ({}));

const { mockGet, mockPost, apiMock, MockApiError } = vi.hoisted(() => {
  const mockGet = vi.fn();
  const mockPost = vi.fn();
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
  const apiMock = {
    get: (...args: unknown[]) => mockGet(...args),
    post: (...args: unknown[]) => mockPost(...args),
    put: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  };
  return { mockGet, mockPost, apiMock, MockApiError };
});

vi.mock("@/lib/api", () => ({ api: apiMock, ApiError: MockApiError }));
vi.mock("@/lib/api/client", () => ({
  api: apiMock,
  ApiError: MockApiError,
  API_BASE: "http://test",
  getAuthFetchOptions: vi.fn(),
  resolveServerReturnTo: vi.fn(),
}));
vi.mock("@/lib/auth/expiry", () => ({ redirectToReLogin: vi.fn() }));
vi.mock("@/lib/auth", () => ({ getAuthToken: vi.fn() }));
vi.mock("@/lib/api/capabilities", () => ({
  fetchMyCapabilities: vi
    .fn()
    .mockResolvedValue({ isAdmin: true, isOwner: true, capabilities: [] }),
}));
vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));

import { performConflictCheck } from "@/app/(app)/org/[slug]/conflict-check/actions";
import {
  createCourtDate,
  createPrescriptionTracker,
  fetchCourtDates,
  fetchPrescriptionTrackers,
} from "@/lib/actions/court-dates";
import {
  fetchProjectAdverseParties,
  linkAdverseParty,
} from "@/app/(app)/org/[slug]/legal/adverse-parties/actions";
import {
  createDisbursementAction,
  fetchApprovedTrustDisbursementPayments,
  fetchDisbursements,
  fetchUnbilledDisbursementsAction,
} from "@/app/(app)/org/[slug]/legal/disbursements/actions";
import {
  recordDeposit,
  recordFeeTransfer,
  recordPayment,
  recordRefund,
  recordTransfer,
} from "@/app/(app)/org/[slug]/trust-accounting/transactions/actions";
import { placeInvestment } from "@/app/(app)/org/[slug]/trust-accounting/investments/actions";
import { fetchClientLedgers } from "@/app/(app)/org/[slug]/trust-accounting/client-ledgers/actions";
import { importXeroCustomers } from "@/lib/api/integrations";
import {
  reopenMatter,
  RetentionElapsedError,
  RETENTION_ELAPSED_PROBLEM_TYPE,
} from "@/lib/api/matter-closure";

const C = "11111111-1111-1111-1111-111111111111";
const C2 = "22222222-2222-2222-2222-222222222222";
const M = "33333333-3333-3333-3333-333333333333";

function lastPost(): [string, Record<string, unknown>] {
  return mockPost.mock.calls.at(-1) as [string, Record<string, unknown>];
}

describe("legal wire bodies (Phase 87 S3 Batch 4)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGet.mockResolvedValue({
      content: [],
      page: { totalElements: 0, totalPages: 0, size: 20, number: 0 },
    });
    mockPost.mockResolvedValue({ id: "new" });
  });

  // ---- silent drops ----

  it("performConflictCheck sends clientId + matterId (never customerId/projectId)", async () => {
    await performConflictCheck({
      checkedName: "Ndlovu Trading",
      checkType: "NEW_MATTER",
      clientId: C,
      matterId: M,
    });
    const [path, body] = lastPost();
    expect(path).toBe("/api/conflict-checks");
    expect(body).toMatchObject({ clientId: C, matterId: M });
    expect(body).not.toHaveProperty("customerId");
    expect(body).not.toHaveProperty("projectId");
  });

  it.each([
    ["deposit", recordDeposit],
    ["payment", recordPayment],
    ["refund", recordRefund],
  ] as const)("record %s maps form data to clientId + matterId", async (kind, fn) => {
    await fn("ta-1", {
      customerId: C,
      projectId: M,
      amount: 1000,
      reference: "REF-1",
      transactionDate: "2026-10-20",
    });
    const [path, body] = lastPost();
    expect(path).toBe(`/api/trust-accounts/ta-1/transactions/${kind}`);
    expect(body).toMatchObject({ clientId: C, matterId: M });
    expect(body).not.toHaveProperty("customerId");
    expect(body).not.toHaveProperty("projectId");
  });

  it("recordTransfer sends sourceClientId/targetClientId/matterId", async () => {
    await recordTransfer("ta-1", {
      sourceCustomerId: C,
      targetCustomerId: C2,
      projectId: M,
      amount: 1000,
      reference: "TRF-1",
      transactionDate: "2026-10-20",
    });
    const [path, body] = lastPost();
    expect(path).toBe("/api/trust-accounts/ta-1/transactions/transfer");
    expect(body).toMatchObject({ sourceClientId: C, targetClientId: C2, matterId: M });
    expect(body).not.toHaveProperty("sourceCustomerId");
    expect(body).not.toHaveProperty("targetCustomerId");
    expect(body).not.toHaveProperty("projectId");
  });

  it("recordFeeTransfer sends clientId", async () => {
    await recordFeeTransfer("ta-1", {
      customerId: C,
      invoiceId: "inv-1",
      amount: 500,
      reference: "FT-1",
    });
    const [, body] = lastPost();
    expect(body.clientId).toBe(C);
    expect(body).not.toHaveProperty("customerId");
  });

  it("fetchCourtDates filters with ?clientId=&matterId=", async () => {
    await fetchCourtDates({ clientId: C, matterId: M });
    const url = mockGet.mock.calls[0][0] as string;
    expect(url).toContain("/api/court-dates?");
    expect(url).toContain(`clientId=${C}`);
    expect(url).toContain(`matterId=${M}`);
    expect(url).not.toContain("customerId=");
    expect(url).not.toContain("projectId=");
  });

  it("fetchPrescriptionTrackers filters with ?clientId=", async () => {
    await fetchPrescriptionTrackers({ clientId: C });
    const url = mockGet.mock.calls[0][0] as string;
    expect(url).toContain("/api/prescription-trackers?");
    expect(url).toContain(`clientId=${C}`);
    expect(url).not.toContain("customerId=");
  });

  it("fetchPrescriptionTrackers filters with ?matterId= (613D matter Court dates tab)", async () => {
    await fetchPrescriptionTrackers({ matterId: M });
    const url = mockGet.mock.calls[0][0] as string;
    expect(url).toContain("/api/prescription-trackers?");
    expect(url).toContain(`matterId=${M}`);
    expect(url).not.toContain("projectId=");
  });

  it("fetchDisbursements filters with ?matterId=", async () => {
    await fetchDisbursements({ matterId: M });
    const url = mockGet.mock.calls[0][0] as string;
    expect(url).toContain("/api/legal/disbursements?");
    expect(url).toContain(`matterId=${M}`);
    expect(url).not.toContain("projectId=");
  });

  it("fetchApprovedTrustDisbursementPayments keeps rows whose matterId matches", async () => {
    mockGet.mockImplementation(async (url: string) => {
      if (url === "/api/trust-accounts") return [{ id: "ta-1" }];
      return {
        content: [
          {
            id: "tx-mine",
            matterId: M,
            status: "APPROVED",
            transactionType: "DISBURSEMENT_PAYMENT",
          },
          {
            id: "tx-other",
            matterId: "other",
            status: "APPROVED",
            transactionType: "DISBURSEMENT_PAYMENT",
          },
        ],
        page: { totalPages: 1 },
      };
    });
    const rows = await fetchApprovedTrustDisbursementPayments(M);
    expect(rows.map((r) => r.id)).toEqual(["tx-mine"]);
  });

  it("fetchClientLedgers search filters on clientName", async () => {
    mockGet.mockResolvedValue({
      content: [
        { id: "l1", clientId: C, clientName: "Acme Corp", balance: 10 },
        { id: "l2", clientId: C2, clientName: "Beta Inc", balance: 20 },
      ],
      page: { totalElements: 2, totalPages: 1, size: 20, number: 0 },
    });
    const page = await fetchClientLedgers("ta-1", { search: "acme" });
    expect(page.content.map((l) => l.id)).toEqual(["l1"]);
  });

  // ---- loud (400/404) ----

  it("createCourtDate sends matterId", async () => {
    await createCourtDate("acme", {
      matterId: M,
      dateType: "HEARING",
      scheduledDate: "2026-11-01",
      courtName: "Johannesburg High Court",
    });
    const [path, body] = lastPost();
    expect(path).toBe("/api/court-dates");
    expect(body.matterId).toBe(M);
    expect(body).not.toHaveProperty("projectId");
  });

  it("createPrescriptionTracker sends matterId", async () => {
    await createPrescriptionTracker("acme", {
      matterId: M,
      causeOfActionDate: "2026-01-01",
      prescriptionType: "GENERAL_3Y",
    });
    const [path, body] = lastPost();
    expect(path).toBe("/api/prescription-trackers");
    expect(body.matterId).toBe(M);
    expect(body).not.toHaveProperty("projectId");
  });

  it("linkAdverseParty sends matterId + clientId", async () => {
    await linkAdverseParty("acme", "ap-1", {
      matterId: M,
      clientId: C,
      relationship: "OPPOSING_PARTY",
    });
    const [path, body] = lastPost();
    expect(path).toBe("/api/adverse-parties/ap-1/links");
    expect(body).toMatchObject({ matterId: M, clientId: C });
    expect(body).not.toHaveProperty("projectId");
    expect(body).not.toHaveProperty("customerId");
  });

  it("fetchProjectAdverseParties uses /api/matters/{id}/adverse-parties", async () => {
    mockGet.mockResolvedValue([]);
    await fetchProjectAdverseParties(M);
    expect(mockGet).toHaveBeenCalledWith(`/api/matters/${M}/adverse-parties`);
  });

  it("createDisbursementAction sends matterId + clientId", async () => {
    await createDisbursementAction("acme", {
      matterId: M,
      clientId: C,
      category: "SHERIFF_FEES",
      description: "Service of summons",
      amount: 500,
      paymentSource: "OFFICE_ACCOUNT",
      incurredDate: "2026-10-01",
      supplierName: "Sheriff Sandton",
    });
    const [path, body] = lastPost();
    expect(path).toBe("/api/legal/disbursements");
    expect(body).toMatchObject({ matterId: M, clientId: C });
    expect(body).not.toHaveProperty("projectId");
    expect(body).not.toHaveProperty("customerId");
  });

  it("fetchUnbilledDisbursementsAction queries /unbilled?matterId=", async () => {
    mockGet.mockResolvedValue({
      matterId: M,
      currency: "ZAR",
      items: [],
      totalAmount: 0,
      totalVat: 0,
    });
    await fetchUnbilledDisbursementsAction(M);
    expect(mockGet).toHaveBeenCalledWith(`/api/legal/disbursements/unbilled?matterId=${M}`);
  });

  it("placeInvestment sends clientId (never customerId) and a decimal rate", async () => {
    await placeInvestment("ta-1", {
      customerId: C,
      institution: "ABSA",
      accountNumber: "INV-1",
      principal: 1000,
      interestRate: 7.5,
      depositDate: "2026-10-01",
      investmentBasis: "CLIENT_INSTRUCTION",
    });
    const [path, body] = lastPost();
    expect(path).toBe("/api/trust-accounts/ta-1/investments");
    expect(body.clientId).toBe(C);
    expect(body).not.toHaveProperty("customerId");
    expect(body.interestRate).toBeCloseTo(0.075);
  });

  it("importXeroCustomers POSTs /api/integrations/xero/import-clients", async () => {
    await importXeroCustomers();
    expect(mockPost).toHaveBeenCalledWith("/api/integrations/xero/import-clients");
  });

  it("reopenMatter reads the retention 409 ProblemDetail matterId", async () => {
    mockPost.mockRejectedValue(
      new MockApiError(409, "retention elapsed", {
        type: RETENTION_ELAPSED_PROBLEM_TYPE,
        retentionEndedOn: "2026-01-31",
        matterId: "from-problem-detail",
      })
    );
    const err = await reopenMatter(M, "notes").catch((e: unknown) => e);
    expect(err).toBeInstanceOf(RetentionElapsedError);
    expect((err as RetentionElapsedError).projectId).toBe("from-problem-detail");
    expect((err as RetentionElapsedError).retentionEndedOn).toBe("2026-01-31");
  });
});
