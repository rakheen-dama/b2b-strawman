import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";

// Mock next/navigation
const mockPush = vi.fn();
let mockSearchParams = new URLSearchParams();

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: mockPush,
    replace: vi.fn(),
    refresh: vi.fn(),
    back: vi.fn(),
    forward: vi.fn(),
    prefetch: vi.fn(),
  }),
  useSearchParams: () => mockSearchParams,
}));

// Mock api-client
const mockPublicFetch = vi.fn();
vi.mock("@/lib/api-client", () => ({
  publicFetch: (...args: unknown[]) => mockPublicFetch(...args),
}));

// Mock auth
const mockStoreAuth = vi.fn();
vi.mock("@/lib/auth", () => ({
  storeAuth: (...args: unknown[]) => mockStoreAuth(...args),
}));

import ExchangePage from "@/app/auth/exchange/page";

describe("ExchangePage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSearchParams = new URLSearchParams("token=abc123&orgId=org_xyz");
  });

  afterEach(() => {
    cleanup();
  });

  it("redirects to /projects on successful exchange", async () => {
    mockPublicFetch.mockResolvedValue(
      new Response(
        JSON.stringify({
          token: "jwt-token-here",
          email: "alice@testcorp.com",
          customerId: "cust-1",
          customerName: "Test Corp",
        }),
        { status: 200 },
      ),
    );

    render(<ExchangePage />);

    await waitFor(() => {
      expect(mockStoreAuth).toHaveBeenCalledWith("jwt-token-here", {
        id: "cust-1",
        name: "Test Corp",
        email: "alice@testcorp.com",
        orgId: "org_xyz",
      });
      expect(mockPush).toHaveBeenCalledWith("/projects");
    });
  });

  it("shows error message on failed exchange", async () => {
    mockPublicFetch.mockResolvedValue(
      new Response(JSON.stringify({ detail: "Invalid magic link token" }), {
        status: 401,
      }),
    );

    render(<ExchangePage />);

    await waitFor(() => {
      expect(screen.getByText(/Link expired or invalid/)).toBeInTheDocument();
    });

    expect(screen.getByRole("link", { name: "Back to Login" })).toBeInTheDocument();
  });

  it("shows loading state while exchanging", () => {
    // Never resolve the fetch to keep loading state
    mockPublicFetch.mockReturnValue(new Promise(() => {}));

    render(<ExchangePage />);

    expect(screen.getByText("Verifying your link...")).toBeInTheDocument();
  });

  it("shows error when token param is missing", () => {
    mockSearchParams = new URLSearchParams("orgId=org_xyz");

    render(<ExchangePage />);

    expect(screen.getByText(/Invalid login link/)).toBeInTheDocument();
    expect(mockPublicFetch).not.toHaveBeenCalled();
  });

  it("shows error when orgId param is missing", () => {
    mockSearchParams = new URLSearchParams("token=abc123");

    render(<ExchangePage />);

    expect(screen.getByText(/Invalid login link/)).toBeInTheDocument();
    expect(mockPublicFetch).not.toHaveBeenCalled();
  });
});
