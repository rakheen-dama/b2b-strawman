import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

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

// Mock auth helpers — GAP-L-66
const mockGetLastOrgId = vi.fn<() => string | null>();
vi.mock("@/lib/auth", () => ({
  getLastOrgId: () => mockGetLastOrgId(),
}));

import LoginPage from "@/app/login/page";

describe("LoginPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSearchParams = new URLSearchParams();
    mockGetLastOrgId.mockReturnValue(null);
    if (typeof sessionStorage !== "undefined") {
      sessionStorage.clear();
    }
  });

  afterEach(() => {
    cleanup();
  });

  it("renders email input and submit button", () => {
    render(<LoginPage />);

    expect(screen.getByLabelText("Email address")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Send Magic Link" })).toBeInTheDocument();
  });

  it("shows success message after submitting email", async () => {
    mockSearchParams = new URLSearchParams("orgId=org_abc");
    const user = userEvent.setup();
    // First call: branding fetch. Second call: request-link.
    mockPublicFetch
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({ orgName: "Test Corp", logoUrl: null, brandColor: null, footerText: null }),
          { status: 200 },
        ),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({ message: "If an account exists, a link has been sent.", magicLink: null }),
          { status: 200 },
        ),
      );

    render(<LoginPage />);

    await user.type(screen.getByLabelText("Email address"), "test@example.com");
    await user.click(screen.getByRole("button", { name: "Send Magic Link" }));

    await waitFor(() => {
      expect(screen.getByText("Check your email for a login link.")).toBeInTheDocument();
    });
  });

  it("displays branding when orgId is present", async () => {
    mockSearchParams = new URLSearchParams("orgId=org_abc");
    mockPublicFetch.mockResolvedValue(
      new Response(
        JSON.stringify({
          orgName: "Smith & Associates",
          logoUrl: null,
          brandColor: null,
          footerText: null,
        }),
        { status: 200 },
      ),
    );

    render(<LoginPage />);

    await waitFor(() => {
      expect(screen.getByText("Smith & Associates")).toBeInTheDocument();
    });
  });

  // GAP-L-66
  it("uses last-known orgId from localStorage when query param missing", async () => {
    mockSearchParams = new URLSearchParams();
    mockGetLastOrgId.mockReturnValue("mathebula-partners");
    mockPublicFetch
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({ orgName: "Mathebula & Partners", logoUrl: null, brandColor: null, footerText: null }),
          { status: 200 },
        ),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ message: "Sent." }), { status: 200 }),
      );

    const user = userEvent.setup();
    render(<LoginPage />);

    // Branding fetch is triggered after the effect populates orgId from
    // localStorage — confirm tenant name renders.
    await waitFor(() => {
      expect(screen.getByText("Mathebula & Partners")).toBeInTheDocument();
    });

    await user.type(screen.getByLabelText("Email address"), "sipho@example.com");
    await user.click(screen.getByRole("button", { name: "Send Magic Link" }));

    // Confirm orgId from localStorage was threaded into the request body.
    await waitFor(() => {
      const requestLinkCall = mockPublicFetch.mock.calls.find(
        (c) => c[0] === "/portal/auth/request-link",
      );
      expect(requestLinkCall).toBeDefined();
      const body = JSON.parse(requestLinkCall![1].body as string);
      expect(body.orgId).toBe("mathebula-partners");
    });
  });

  // GAP-L-66
  it("renders inline guard when orgId is unresolvable", async () => {
    mockSearchParams = new URLSearchParams();
    mockGetLastOrgId.mockReturnValue(null);

    const user = userEvent.setup();
    render(<LoginPage />);

    await user.type(screen.getByLabelText("Email address"), "first@example.com");
    await user.click(screen.getByRole("button", { name: "Send Magic Link" }));

    await waitFor(() => {
      expect(
        screen.getByText(/couldn't determine which organization/i),
      ).toBeInTheDocument();
    });
    // Form must NOT POST when orgId can't be resolved (avoids guaranteed 400).
    expect(mockPublicFetch).not.toHaveBeenCalled();
  });

  // GAP-L-66
  it("persists redirectTo to sessionStorage when present in query", () => {
    mockSearchParams = new URLSearchParams(
      "orgId=mathebula-partners&redirectTo=%2Finvoices%2F123",
    );
    mockPublicFetch.mockResolvedValue(
      new Response(
        JSON.stringify({ orgName: "Mathebula & Partners", logoUrl: null, brandColor: null, footerText: null }),
        { status: 200 },
      ),
    );

    render(<LoginPage />);

    expect(sessionStorage.getItem("portal_post_login_redirect")).toBe(
      "/invoices/123",
    );
  });
});
