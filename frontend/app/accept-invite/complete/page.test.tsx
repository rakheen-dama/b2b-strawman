import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

import AcceptInviteCompletePage from "./page";

describe("AcceptInviteCompletePage", () => {
  const replaceMock = vi.fn();
  let originalLocation: Location;

  beforeEach(() => {
    replaceMock.mockReset();
    originalLocation = window.location;
    // happy-dom: replace `location` with a controllable shim so we can spy on `replace`.
    Object.defineProperty(window, "location", {
      configurable: true,
      writable: true,
      value: { ...originalLocation, replace: replaceMock },
    });
  });

  afterEach(() => {
    Object.defineProperty(window, "location", {
      configurable: true,
      writable: true,
      value: originalLocation,
    });
    cleanup();
  });

  it("renders the loading copy", () => {
    render(<AcceptInviteCompletePage />);
    expect(screen.getByRole("heading", { name: /finishing sign-in/i })).toBeTruthy();
  });

  it("redirects on mount to the gateway OAuth2 kickoff URL", () => {
    render(<AcceptInviteCompletePage />);

    expect(replaceMock).toHaveBeenCalledTimes(1);
    const target = replaceMock.mock.calls[0][0] as string;
    // Target gateway OAuth2 kickoff (default http://localhost:8443 unless
    // NEXT_PUBLIC_GATEWAY_URL is set at build time).
    expect(target).toMatch(/\/oauth2\/authorization\/keycloak$/);
    expect(target).not.toContain("?");
  });
});
