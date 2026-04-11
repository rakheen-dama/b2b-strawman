import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, cleanup, waitFor, fireEvent } from "@testing-library/react";

// Stub env before imports
vi.stubEnv("NEXT_PUBLIC_GATEWAY_URL", "http://localhost:8443");

// Mock global fetch
const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

import { UserMenuBff } from "@/components/auth/user-menu-bff";

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
});
