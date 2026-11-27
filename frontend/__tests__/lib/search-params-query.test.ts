import { describe, it, expect } from "vitest";
import { searchParamsToQuery } from "@/lib/search-params-query";

describe("searchParamsToQuery (DA-8)", () => {
  it("keeps every non-empty string param in the URL's order", () => {
    const query = searchParamsToQuery({ status: "APPROVED", dateFrom: "2026-11-01", page: "2" });
    expect(Object.entries(query)).toEqual([
      ["status", "APPROVED"],
      ["dateFrom", "2026-11-01"],
      ["page", "2"],
    ]);
  });

  it("drops empty, undefined and repeated params", () => {
    expect(
      searchParamsToQuery({ status: "", type: undefined, customerId: ["a", "b"], search: "Moroka" })
    ).toEqual({ search: "Moroka" });
  });

  it("returns an empty query for no params", () => {
    expect(searchParamsToQuery({})).toEqual({});
  });
});
