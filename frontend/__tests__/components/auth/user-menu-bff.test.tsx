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

  it("renders sign-out button that navigates to gateway logout", async () => {
    // Mock window.location.href
    const originalLocation = window.location;
    const locationMock = {
      ...originalLocation,
      href: "",
    };
    Object.defineProperty(window, "location", {
      writable: true,
      value: locationMock,
    });

    render(<UserMenuBff />);

    // Wait for user data to load
    await waitFor(() => {
      expect(screen.getByText("AS")).toBeInTheDocument();
    });

    // Open dropdown
    fireEvent.click(screen.getByText("AS"));

    // Click sign out
    const signOutButton = screen.getByText("Sign out");
    expect(signOutButton).toBeInTheDocument();
    fireEvent.click(signOutButton);

    expect(window.location.href).toBe("http://localhost:8443/logout");

    // Restore
    Object.defineProperty(window, "location", {
      writable: true,
      value: originalLocation,
    });
  });

  it("fetches from /bff/me with credentials included", async () => {
    render(<UserMenuBff />);

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledWith(
        "http://localhost:8443/bff/me",
        expect.objectContaining({ credentials: "include" }),
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
