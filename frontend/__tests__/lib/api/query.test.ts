import { describe, it, expect } from "vitest";
import { toQuery } from "@/lib/api/query";

describe("toQuery", () => {
  it("returns an empty string when params are omitted", () => {
    expect(toQuery()).toBe("");
    expect(toQuery(undefined)).toBe("");
  });

  it("returns an empty string for an empty object", () => {
    expect(toQuery({})).toBe("");
  });

  it("returns an empty string when every value is unset", () => {
    expect(toQuery({ a: undefined, b: null, c: "" })).toBe("");
  });

  it("skips undefined, null and empty-string values but keeps the rest", () => {
    expect(toQuery({ a: undefined, b: "x", c: null, d: "", e: "y" })).toBe("?b=x&e=y");
  });

  it("keeps insertion order", () => {
    expect(toQuery({ z: "1", a: "2", m: "3" })).toBe("?z=1&a=2&m=3");
  });

  it("stringifies numbers and booleans, keeping falsy 0 and false", () => {
    expect(toQuery({ size: 0, page: 2, archived: false, active: true })).toBe(
      "?size=0&page=2&archived=false&active=true"
    );
  });

  it("form-encodes keys and values (spaces as +, reserved characters escaped)", () => {
    expect(toQuery({ q: "a b&c=d", "cf[x]": "é/?" })).toBe("?q=a+b%26c%3Dd&cf%5Bx%5D=%C3%A9%2F%3F");
  });

  it("keeps a whitespace-only value (only the exact empty string is skipped)", () => {
    expect(toQuery({ q: " " })).toBe("?q=+");
  });
});
