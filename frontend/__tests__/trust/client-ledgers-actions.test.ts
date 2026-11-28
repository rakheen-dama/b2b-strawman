import { describe, it, expect, vi, beforeEach } from "vitest";

// F-667A-10: the client-ledgers list pages and filters on the server. The action must send the
// filters to the API and pass the server's page metadata through untouched.
vi.mock("server-only", () => ({}));

const mockGet = vi.fn();
vi.mock("@/lib/api", () => ({
  api: { get: (...args: unknown[]) => mockGet(...args), post: vi.fn() },
}));
vi.mock("@/lib/api/reports", () => ({ exportReportPdf: vi.fn() }));

import { fetchClientLedgers } from "@/app/(app)/org/[slug]/trust-accounting/client-ledgers/actions";

function ledger(clientId: string, clientName: string, balance: number) {
  return { id: `l-${clientId}`, clientId, clientName, balance };
}

/** What the API sends for page 1 of 25 ledgers (20 a page). */
function serverPage(content: ReturnType<typeof ledger>[], totalElements: number, number = 0) {
  return {
    content,
    page: { totalElements, totalPages: Math.ceil(totalElements / 20), size: 20, number },
  };
}

function requestedQuery(): URLSearchParams {
  const url = mockGet.mock.calls[0][0] as string;
  expect(url.startsWith("/api/trust-accounts/acc-1/client-ledgers?")).toBe(true);
  return new URLSearchParams(url.split("?")[1]);
}

describe("fetchClientLedgers (F-667A-10)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("keeps the server's totals: 25 ledgers is 2 pages, not the 1 page in hand", async () => {
    const rows = Array.from({ length: 20 }, (_, i) => ledger(`c-${i}`, `Client ${i}`, 100));
    mockGet.mockResolvedValue(serverPage(rows, 25));

    const page = await fetchClientLedgers("acc-1", { page: 0, size: 20 });

    expect(page.totalElements).toBe(25);
    expect(page.totalPages).toBe(2);
    expect(page.pageNumber).toBe(0);
    expect(page.pageSize).toBe(20);
    expect(page.content).toHaveLength(20);
  });

  it("asks the server for page 2 and returns its rows and totals", async () => {
    const rows = Array.from({ length: 5 }, (_, i) => ledger(`c-${20 + i}`, `Client ${20 + i}`, 0));
    mockGet.mockResolvedValue(serverPage(rows, 25, 1));

    const page = await fetchClientLedgers("acc-1", { page: 1, size: 20 });

    expect(requestedQuery().get("page")).toBe("1");
    expect(page.pageNumber).toBe(1);
    expect(page.totalPages).toBe(2);
    // Zero-balance rows from the server are not dropped on the client
    expect(page.content.map((r) => r.clientId)).toEqual(rows.map((r) => r.clientId));
  });

  it("sends nonZeroOnly and search to the server instead of filtering the page", async () => {
    // The server already filtered: its page is the answer, whatever this page's rows look like
    const rows = [ledger("c-zebra", "ZEBRA Holdings", 10)];
    mockGet.mockResolvedValue(serverPage(rows, 1));

    const page = await fetchClientLedgers("acc-1", {
      page: 0,
      size: 20,
      nonZeroOnly: true,
      search: "zebra",
    });

    const q = requestedQuery();
    expect(q.get("nonZeroOnly")).toBe("true");
    expect(q.get("search")).toBe("zebra");
    expect(q.get("size")).toBe("20");
    expect(q.get("sort")).toBe("balance,desc");
    expect(page.totalElements).toBe(1);
    expect(page.content).toEqual(rows);
  });

  it("filtered totals come from the server: 22 non-zero ledgers across 2 pages", async () => {
    const rows = Array.from({ length: 20 }, (_, i) => ledger(`c-${i}`, `Client ${i}`, 100));
    mockGet.mockResolvedValue(serverPage(rows, 22));

    const page = await fetchClientLedgers("acc-1", { nonZeroOnly: true });

    expect(page.totalElements).toBe(22);
    expect(page.totalPages).toBe(2);
  });

  it("omits the filters when they are off", async () => {
    mockGet.mockResolvedValue(serverPage([], 0));

    await fetchClientLedgers("acc-1", { nonZeroOnly: false, search: "" });

    const q = requestedQuery();
    expect(q.has("nonZeroOnly")).toBe(false);
    expect(q.has("search")).toBe(false);
    expect(q.get("page")).toBe("0");
  });
});
