import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, fireEvent } from "@testing-library/react";

vi.mock("server-only", () => ({}));
vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));

const mockApiGet = vi.fn();
vi.mock("@/lib/api", () => ({
  api: { get: (...a: unknown[]) => mockApiGet(...a), post: vi.fn() },
  ApiError: class ApiError extends Error {},
}));

const mockPush = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush, refresh: vi.fn() }),
}));

import { fetchTransactions } from "@/app/(app)/org/[slug]/trust-accounting/transactions/actions";
import { TransactionFilters } from "@/components/trust/transaction-filters";

describe("F-646E-2 transaction filters reach the request", () => {
  beforeEach(() => {
    mockApiGet.mockReset();
    mockPush.mockReset();
    mockApiGet.mockResolvedValue({
      content: [],
      page: { totalElements: 0, totalPages: 0, size: 20, number: 0 },
    });
  });
  afterEach(() => cleanup());

  it("sends dateFrom, dateTo, clientId and matterId to the list endpoint", async () => {
    await fetchTransactions("acc-1", {
      dateFrom: "2026-04-01",
      dateTo: "2026-04-30",
      customerId: "c-1",
      projectId: "m-1",
    });
    const url = new URL(mockApiGet.mock.calls[0][0] as string, "http://x");
    expect(url.pathname).toBe("/api/trust-accounts/acc-1/transactions");
    expect(url.searchParams.get("dateFrom")).toBe("2026-04-01");
    expect(url.searchParams.get("dateTo")).toBe("2026-04-30");
    expect(url.searchParams.get("clientId")).toBe("c-1");
    expect(url.searchParams.get("matterId")).toBe("m-1");
  });

  it("omits a cleared filter from the request", async () => {
    await fetchTransactions("acc-1", { dateFrom: undefined, customerId: "" });
    const url = new URL(mockApiGet.mock.calls[0][0] as string, "http://x");
    expect(url.searchParams.has("dateFrom")).toBe(false);
    expect(url.searchParams.has("clientId")).toBe(false);
  });

  it("clearing a date drops it from the page URL and keeps the others", () => {
    render(
      <TransactionFilters
        slug="acme"
        search={{ dateFrom: "2026-04-01", dateTo: "2026-04-30", status: "RECORDED" }}
      />
    );
    fireEvent.change(document.getElementById("filter-dateFrom")!, { target: { value: "" } });
    const pushed = mockPush.mock.calls[0][0] as string;
    expect(pushed).not.toContain("dateFrom");
    expect(pushed).toContain("dateTo=2026-04-30");
    expect(pushed).toContain("status=RECORDED");
  });
});
