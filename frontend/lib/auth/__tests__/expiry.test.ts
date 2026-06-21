import { describe, it, expect, vi, beforeEach } from "vitest";

// Mock next/navigation's redirect() to throw a recognisable error so we can
// assert the URL it was called with (redirect() is typed `never`).
vi.mock("next/navigation", () => ({
  redirect: vi.fn((url: string) => {
    throw new Error(`REDIRECT:${url}`);
  }),
}));

import { redirect } from "next/navigation";
import { isSessionExpired, redirectToReLogin, clientRedirectToReLogin } from "../expiry";

describe("isSessionExpired", () => {
  it("returns true for a 401 response", () => {
    expect(isSessionExpired({ status: 401 })).toBe(true);
  });

  it("returns false for a raw 3xx (3xx→401 mapping is client.ts's job, not the detector's)", () => {
    // client.ts maps a manual-redirect 3xx to ApiError(401) BEFORE the detector
    // sees it. isSessionExpired itself only treats a literal 401 as expired, so
    // a raw 302 must NOT be classified as expired here.
    expect(isSessionExpired({ status: 302 })).toBe(false);
  });

  it("returns true for an authenticated:false /bff/me body", () => {
    expect(isSessionExpired({ authenticated: false })).toBe(true);
  });

  it("returns true for a null (failed) response", () => {
    expect(isSessionExpired(null)).toBe(true);
  });

  it("returns false for a 200 response", () => {
    expect(isSessionExpired({ status: 200 })).toBe(false);
  });

  it("returns false for an authenticated:true /bff/me body", () => {
    expect(isSessionExpired({ authenticated: true })).toBe(false);
  });
});

describe("redirectToReLogin (server)", () => {
  beforeEach(() => {
    vi.mocked(redirect).mockClear();
  });

  it("builds /sign-in?reason=expired&returnTo=<safe> for an allowlisted path", () => {
    expect(() => redirectToReLogin("/org/acme/matters")).toThrow(
      "REDIRECT:/sign-in?reason=expired&returnTo=%2Forg%2Facme%2Fmatters"
    );
    expect(redirect).toHaveBeenCalledWith(
      "/sign-in?reason=expired&returnTo=%2Forg%2Facme%2Fmatters"
    );
  });

  it("falls back returnTo to /dashboard for a non-allowlisted path", () => {
    expect(() => redirectToReLogin("/settings/secret")).toThrow(
      "REDIRECT:/sign-in?reason=expired&returnTo=%2Fdashboard"
    );
  });

  it("rejects an open-redirect returnTo, falling back to /dashboard", () => {
    expect(() => redirectToReLogin("//evil.com")).toThrow(
      "REDIRECT:/sign-in?reason=expired&returnTo=%2Fdashboard"
    );
  });
});

describe("clientRedirectToReLogin (client)", () => {
  it("hard-navigates to /sign-in?reason=expired with a validated returnTo", () => {
    const assign = vi.fn();
    const removeItem = vi.fn();
    vi.stubGlobal("window", {
      location: { assign },
      sessionStorage: { removeItem },
    });

    clientRedirectToReLogin("/dashboard");

    expect(removeItem).toHaveBeenCalledWith("kazi.returnTo");
    expect(assign).toHaveBeenCalledWith("/sign-in?reason=expired&returnTo=%2Fdashboard");

    vi.unstubAllGlobals();
  });
});
