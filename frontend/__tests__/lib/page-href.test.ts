import { describe, it, expect } from "vitest";
import { buildPageHref } from "@/lib/page-href";

const BASE = "/org/acme/trust-accounting/transactions";

describe("buildPageHref (ADR-395)", () => {
  it("keeps every non-empty param, in order, and appends page", () => {
    expect(buildPageHref(BASE, { status: "APPROVED", type: "DEPOSIT" }, 1)).toBe(
      `${BASE}?status=APPROVED&type=DEPOSIT&page=1`
    );
  });

  it("replaces an existing page in place", () => {
    expect(buildPageHref(BASE, { status: "APPROVED", page: "1", type: "X" }, 2)).toBe(
      `${BASE}?status=APPROVED&page=2&type=X`
    );
  });

  it("appends a missing param (itemsPage) after the others", () => {
    expect(buildPageHref(BASE, { page: "1" }, 1, "itemsPage")).toBe(`${BASE}?page=1&itemsPage=1`);
  });

  it("sets itemsPage without touching page", () => {
    expect(buildPageHref(BASE, { page: "2", itemsPage: "1" }, 3, "itemsPage")).toBe(
      `${BASE}?page=2&itemsPage=3`
    );
  });

  it("is zero-based: page 0 is written, not omitted", () => {
    expect(buildPageHref(BASE, { page: "3" }, 0)).toBe(`${BASE}?page=0`);
    expect(buildPageHref(BASE, {}, 0)).toBe(`${BASE}?page=0`);
    expect(buildPageHref(BASE, { page: "1", itemsPage: "2" }, 0, "itemsPage")).toBe(
      `${BASE}?page=1&itemsPage=0`
    );
  });

  it("drops empty values (pageUrl parity) and URL-encodes the rest", () => {
    expect(buildPageHref(BASE, { status: "", customerId: "a b&c", dateFrom: "" }, 1)).toBe(
      `${BASE}?customerId=a+b%26c&page=1`
    );
  });

  it("does not mutate the query", () => {
    const query = { status: "APPROVED", page: "1" };
    buildPageHref(BASE, query, 4);
    expect(query).toEqual({ status: "APPROVED", page: "1" });
  });

  it("reproduces the transactions page's pageUrl", () => {
    // `trust-accounting/transactions/page.tsx` `pageUrl`, verbatim in behaviour.
    const filters: Record<string, string> = {
      status: "AWAITING_APPROVAL",
      transactionType: "",
      customerId: "c-1",
      dateFrom: "2026-01-01",
      dateTo: "",
    };
    const pageUrl = (page: number) => {
      const params = new URLSearchParams();
      for (const [key, value] of Object.entries(filters)) {
        if (value) params.set(key, value);
      }
      params.set("page", String(page));
      return `${BASE}?${params.toString()}`;
    };
    for (const page of [0, 1, 7]) {
      expect(buildPageHref(BASE, filters, page)).toBe(pageUrl(page));
    }
  });
});
