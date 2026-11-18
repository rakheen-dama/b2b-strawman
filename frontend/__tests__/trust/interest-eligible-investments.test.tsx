import type React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import { SWRConfig } from "swr";

vi.mock("server-only", () => ({}));

const { mockGet } = vi.hoisted(() => ({ mockGet: vi.fn() }));
vi.mock("@/lib/api", () => ({ api: { get: (...args: unknown[]) => mockGet(...args) } }));

import { fetchInterestEligibleInvestments } from "@/app/(app)/org/[slug]/trust-accounting/investments/actions";
import { useInterestEligibleInvestments } from "@/hooks/use-interest-eligible-investments";
import type { TrustAccountSection, TrustInvestment } from "@/lib/types";

function inv(id: string, overrides: Partial<TrustInvestment> = {}): TrustInvestment {
  return { id, status: "ACTIVE", awaitingApproval: false, ...overrides } as TrustInvestment;
}

function page(content: TrustInvestment[], number: number, totalPages: number) {
  return { content, page: { totalElements: 0, totalPages, size: 100, number } };
}

const wrapper = ({ children }: { children: React.ReactNode }) => (
  <SWRConfig value={{ provider: () => new Map(), dedupingInterval: 0, shouldRetryOnError: false }}>
    {children}
  </SWRConfig>
);

describe("fetchInterestEligibleInvestments (648B)", () => {
  beforeEach(() => mockGet.mockReset());

  it("reads every page and keeps only ACTIVE, approved investments", async () => {
    mockGet
      .mockResolvedValueOnce(
        page(
          [
            inv("new-withdrawn", { status: "WITHDRAWN" }),
            inv("awaiting", { awaitingApproval: true }),
          ],
          0,
          2
        )
      )
      .mockResolvedValueOnce(page([inv("old-active")], 1, 2));

    const result = await fetchInterestEligibleInvestments("acc-864");

    expect(result.map((i) => i.id)).toEqual(["old-active"]);
    expect(mockGet).toHaveBeenCalledTimes(2);
    expect(mockGet).toHaveBeenNthCalledWith(
      1,
      "/api/trust-accounts/acc-864/investments?page=0&size=100&sort=depositDate,desc&investmentBasis=CLIENT_INSTRUCTION"
    );
    expect(mockGet).toHaveBeenNthCalledWith(
      2,
      "/api/trust-accounts/acc-864/investments?page=1&size=100&sort=depositDate,desc&investmentBasis=CLIENT_INSTRUCTION"
    );
  });

  it("stops after one page when there is one page (or none)", async () => {
    mockGet.mockResolvedValueOnce(page([], 0, 0));
    expect(await fetchInterestEligibleInvestments("acc-864")).toEqual([]);
    expect(mockGet).toHaveBeenCalledTimes(1);
  });
});

describe("useInterestEligibleInvestments (648B)", () => {
  beforeEach(() => mockGet.mockReset());

  it("does not look up investments for an account that is not section 86(4)", async () => {
    const { result } = renderHook(
      ({ section }: { section: TrustAccountSection | undefined }) =>
        useInterestEligibleInvestments("acc-863", section),
      { wrapper, initialProps: { section: "S86_3" } }
    );
    expect(result.current.investments).toEqual([]);
    expect(result.current.error).toBeNull();
    expect(mockGet).not.toHaveBeenCalled();
  });

  it("loads a section 86(4) account's investments", async () => {
    mockGet.mockResolvedValueOnce(page([inv("inv-1")], 0, 1));
    const { result } = renderHook(() => useInterestEligibleInvestments("acc-864", "S86_4"), {
      wrapper,
    });
    await waitFor(() => expect(result.current.investments.map((i) => i.id)).toEqual(["inv-1"]));
    expect(result.current.error).toBeNull();
  });

  it("surfaces a failed lookup and retries it", async () => {
    mockGet
      .mockRejectedValueOnce(new Error("network"))
      .mockResolvedValueOnce(page([inv("inv-1")], 0, 1));
    const { result } = renderHook(() => useInterestEligibleInvestments("acc-864", "S86_4"), {
      wrapper,
    });
    await waitFor(() =>
      expect(result.current.error).toBe("The account's investments could not be loaded.")
    );
    expect(result.current.investments).toEqual([]);

    act(() => result.current.retry());
    await waitFor(() => expect(result.current.investments.map((i) => i.id)).toEqual(["inv-1"]));
    expect(result.current.error).toBeNull();
  });
});
