import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, cleanup, waitFor, fireEvent } from "@testing-library/react";

// Stub env before imports
vi.stubEnv("NEXT_PUBLIC_GATEWAY_URL", "http://localhost:8443");

// Mock global fetch
const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

import { UserMenuBff, getChangePasswordUrl } from "@/components/auth/user-menu-bff";

const defaultBffResponse = {
  authenticated: true,
  userId: "kc-user-123",
  email: "alice@example.com",
  name: "Alice Smith",
  picture: null,
  orgId: "kc-org-456",
  orgSlug: "acme-corp",
  orgRole: "owner",
};

describe("UserMenuBff", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(defaultBffResponse),
    });
  });

  afterEach(() => {
    cleanup();
  });

  it("renders user name and avatar initials after loading", async () => {
    render(<UserMenuBff />);

    // Wait for the fetch to resolve and render user data
    await waitFor(() => {
      expect(screen.getByText("AS")).toBeInTheDocument();
    });

    // Click to open dropdown
    fireEvent.click(screen.getByText("AS"));

    expect(screen.getByText("Alice Smith")).toBeInTheDocument();
    expect(screen.getByText("alice@example.com")).toBeInTheDocument();
  });

  it("renders sign-out button that fetches CSRF and submits form POST", async () => {
    // Mock fetch: first call returns /bff/me, second returns /bff/csrf
    mockFetch
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(defaultBffResponse),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            token: "test-csrf-token",
            parameterName: "_csrf",
            headerName: "X-XSRF-TOKEN",
          }),
      });

    render(<UserMenuBff />);

    await waitFor(() => {
      expect(screen.getByText("AS")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText("AS"));

    const signOutButton = screen.getByText("Sign out");
    expect(signOutButton).toBeInTheDocument();
    fireEvent.click(signOutButton);

    // Verify CSRF token was fetched from gateway
    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledWith(
        "http://localhost:8443/bff/csrf",
        expect.objectContaining({ credentials: "include" })
      );
    });

    // Verify the hidden form was appended to the body
    await waitFor(() => {
      const form = document.body.querySelector('form[action="http://localhost:8443/logout"]');
      expect(form).not.toBeNull();
      expect(form?.getAttribute("method")).toBe("POST");
      // Verify CSRF token input
      const csrfInput = form?.querySelector('input[name="_csrf"]');
      expect(csrfInput).not.toBeNull();
      expect(csrfInput?.getAttribute("value")).toBe("test-csrf-token");
    });
  });

  it("fetches from /bff/me with credentials included", async () => {
    render(<UserMenuBff />);

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledWith(
        "http://localhost:8443/bff/me",
        expect.objectContaining({ credentials: "include" })
      );
    });
  });

  it("shows fallback initials when fetch fails", async () => {
    mockFetch.mockRejectedValue(new Error("Network error"));

    render(<UserMenuBff />);

    // Should show "?" as fallback
    await waitFor(() => {
      expect(screen.getByText("?")).toBeInTheDocument();
    });
  });

  // Epic 571B — real, CI-runnable regression coverage for the "Account & Security" menu item and
  // the change-password initiation URL. The e2e mock arm cannot DOM-interact (mock auth renders
  // MockUserButton, NOT UserMenuBff — see auth-header-controls.tsx), so this vitest test is the
  // genuine CI gate for the menu item + URL helper (review Finding 2).
  it("getChangePasswordUrl targets the gateway keycloak initiation path with the bff_action sentinel", () => {
    const url = new URL(getChangePasswordUrl());
    expect(url.origin).toBe("http://localhost:8443");
    expect(url.pathname).toBe("/oauth2/authorization/keycloak");
    expect(url.searchParams.get("bff_action")).toBe("change_password");
    // The gateway-private sentinel must NOT be the raw KC param (review Finding 1).
    expect(url.searchParams.get("kc_action")).toBeNull();
  });

  it("renders an 'Account & Security' item that navigates to the change-password URL on click", async () => {
    // Capture the top-level navigation the handler performs (window.location.href assignment).
    const hrefSetter = vi.fn();
    const originalLocation = window.location;
    Object.defineProperty(window, "location", {
      configurable: true,
      value: {
        ...originalLocation,
        get href() {
          return originalLocation.href;
        },
        set href(value: string) {
          hrefSetter(value);
        },
      },
    });

    try {
      render(<UserMenuBff />);

      await waitFor(() => {
        expect(screen.getByText("AS")).toBeInTheDocument();
      });

      fireEvent.click(screen.getByText("AS"));

      const item = screen.getByText("Account & Security");
      expect(item).toBeInTheDocument();

      fireEvent.click(item);

      expect(hrefSetter).toHaveBeenCalledWith(
        "http://localhost:8443/oauth2/authorization/keycloak?bff_action=change_password"
      );
    } finally {
      Object.defineProperty(window, "location", {
        configurable: true,
        value: originalLocation,
      });
    }
  });
});
