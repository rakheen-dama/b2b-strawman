import { describe, it, expect, vi, beforeEach } from "vitest";

// D-131: the trust dashboard's "Active Clients" figure is the number of clients holding money in
// trust on the account — a general balance or s86(4) invested money (the D-126 rule) — across every
// ledger, not the first page. The server's `nonZeroOnly` filter implements that rule in its count.
vi.mock("server-only", () => ({}));

const mockGet = vi.fn();
vi.mock("@/lib/api", () => ({
  api: { get: (...args: unknown[]) => mockGet(...args), post: vi.fn() },
}));

import { fetchDashboardData } from "@/app/(app)/org/[slug]/trust-accounting/actions";

interface FakeLedger {
  clientId: string;
  clientName: string;
  balance: number;
  investedBalance: number;
}

function ledger(clientId: string, balance: number, investedBalance = 0): FakeLedger {
  return { clientId, clientName: `Client ${clientId}`, balance, investedBalance };
}

/**
 * A fake of the backend: `/client-ledgers` pages and filters every ledger of the account the way
 * `ClientLedgerController` does since #1899 (nonZeroOnly = general ≠ 0 OR invested ≠ 0), and
 * reports the filtered total in `page.totalElements`.
 */
function serveAccount(ledgers: FakeLedger[]) {
  mockGet.mockImplementation(async (url: string) => {
    const [path, qs] = url.split("?");
    const q = new URLSearchParams(qs ?? "");
    if (path.endsWith("/cashbook-balance")) return { balance: 31000 };
    if (path.endsWith("/pending-approvals")) return [];
    if (path.endsWith("/transactions")) {
      return { content: [], page: { totalElements: 0, totalPages: 0, size: 10, number: 0 } };
    }
    if (path.endsWith("/client-ledgers")) {
      const rows =
        q.get("nonZeroOnly") === "true"
          ? ledgers.filter((l) => l.balance !== 0 || l.investedBalance !== 0)
          : ledgers;
      const size = Number(q.get("size") ?? "20");
      const number = Number(q.get("page") ?? "0");
      return {
        content: rows.slice(number * size, (number + 1) * size),
        page: {
          totalElements: rows.length,
          totalPages: Math.ceil(rows.length / size),
          size,
          number,
        },
      };
    }
    throw new Error(`unexpected GET ${url}`);
  });
}

function clientLedgerRequests(): URLSearchParams[] {
  return mockGet.mock.calls
    .map((c) => c[0] as string)
    .filter((url) => url.startsWith("/api/trust-accounts/acc-1/client-ledgers"))
    .map((url) => new URLSearchParams(url.split("?")[1] ?? ""));
}

describe("fetchDashboardData — Active Clients (D-131)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("counts a client whose whole balance is placed in s86(4) (D-126)", async () => {
    serveAccount([
      ledger("plain", 1000),
      ledger("holder", 0, 30000), // general R0, R30 000 invested in s86(4)
      ledger("settled", 0), // paid out to R0, nothing invested
    ]);

    const data = await fetchDashboardData("acc-1");

    expect(data.activeClients).toBe(2);
  });

  it("counts every active client, not only the first 200 ledgers", async () => {
    const active = Array.from({ length: 250 }, (_, i) => ledger(`a-${i}`, 100 + i));
    const settled = Array.from({ length: 10 }, (_, i) => ledger(`z-${i}`, 0));
    serveAccount([...active, ...settled]);

    const data = await fetchDashboardData("acc-1");

    expect(data.activeClients).toBe(250);
  });

  it("asks the server for the non-zero count instead of filtering a page of rows", async () => {
    serveAccount([ledger("plain", 1000)]);

    await fetchDashboardData("acc-1");

    const requests = clientLedgerRequests();
    expect(requests).toHaveLength(1);
    expect(requests[0].get("nonZeroOnly")).toBe("true");
    expect(requests[0].get("size")).toBe("1");
  });

  it("reads 0 when no client holds money in trust", async () => {
    serveAccount([ledger("settled", 0)]);

    const data = await fetchDashboardData("acc-1");

    expect(data.activeClients).toBe(0);
  });

  it("keeps the other dashboard figures", async () => {
    serveAccount([ledger("plain", 1000)]);

    const data = await fetchDashboardData("acc-1");

    expect(data.totalBalance).toBe(31000);
    expect(data.pendingApprovals).toBe(0);
    expect(data.recentTransactions).toEqual([]);
    expect(data.alerts).toEqual([]);
  });
});
