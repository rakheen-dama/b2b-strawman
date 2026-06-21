import { describe, it, expect } from "vitest";
import { safeReturnTo, captureReturnTo } from "../return-to";

describe("safeReturnTo — open-redirect allowlist guard", () => {
  it("rejects protocol-relative //evil → /dashboard", () => {
    expect(safeReturnTo("//evil.com")).toBe("/dashboard");
  });

  it("rejects http:// absolute URL → /dashboard", () => {
    expect(safeReturnTo("http://evil.com/dashboard")).toBe("/dashboard");
  });

  it("rejects https:// absolute URL → /dashboard", () => {
    expect(safeReturnTo("https://evil.com/dashboard")).toBe("/dashboard");
  });

  it("rejects backslash-trick /\\evil → /dashboard", () => {
    expect(safeReturnTo("/\\evil.com")).toBe("/dashboard");
  });

  it("rejects javascript: scheme → /dashboard", () => {
    expect(safeReturnTo("javascript:alert(1)")).toBe("/dashboard");
  });

  it("rejects data: scheme → /dashboard", () => {
    expect(safeReturnTo("data:text/html,<script>alert(1)</script>")).toBe("/dashboard");
  });

  it("rejects empty string and null → /dashboard", () => {
    expect(safeReturnTo("")).toBe("/dashboard");
    expect(safeReturnTo(null)).toBe("/dashboard");
    expect(safeReturnTo(undefined)).toBe("/dashboard");
  });

  it("rejects non-allowlisted same-origin path → /dashboard", () => {
    expect(safeReturnTo("/settings/secret")).toBe("/dashboard");
    expect(safeReturnTo("/api/webhooks")).toBe("/dashboard");
  });

  it("accepts allowlisted /dashboard", () => {
    expect(safeReturnTo("/dashboard")).toBe("/dashboard");
  });

  it("accepts allowlisted /org/x/matters with query", () => {
    expect(safeReturnTo("/org/acme/matters?tab=open")).toBe("/org/acme/matters?tab=open");
  });

  it("accepts allowlisted /platform-admin and /create-org", () => {
    expect(safeReturnTo("/platform-admin/access-requests")).toBe("/platform-admin/access-requests");
    expect(safeReturnTo("/create-org")).toBe("/create-org");
  });
});

describe("captureReturnTo", () => {
  it("extracts pathname + search from a nextUrl request", () => {
    expect(
      captureReturnTo({ nextUrl: new URL("http://localhost:3000/org/acme/matters?x=1") })
    ).toBe("/org/acme/matters?x=1");
  });

  it("extracts pathname + search from a location-like object", () => {
    expect(captureReturnTo({ pathname: "/dashboard", search: "?ref=a" })).toBe("/dashboard?ref=a");
  });
});
