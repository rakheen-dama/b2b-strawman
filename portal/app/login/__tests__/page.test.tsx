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
    // clearAllMocks keeps queued mockResolvedValueOnce values; reset so an
    // unconsumed response cannot leak into the next test.
    mockPublicFetch.mockReset();
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
    expect(
      screen.getByRole("button", { name: "Send Magic Link" }),
    ).toBeInTheDocument();
  });

  it("shows success message after submitting email", async () => {
    mockSearchParams = new URLSearchParams("orgId=org_abc");
    const user = userEvent.setup();
    // First call: branding fetch. Second call: request-link.
    mockPublicFetch
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            orgName: "Test Corp",
            logoUrl: null,
            brandColor: null,
            footerText: null,
          }),
          { status: 200 },
        ),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            message: "If an account exists, a link has been sent.",
            magicLink: null,
          }),
          { status: 200 },
        ),
      );

    render(<LoginPage />);

    await user.type(screen.getByLabelText("Email address"), "test@example.com");
    await user.click(screen.getByRole("button", { name: "Send Magic Link" }));

    await waitFor(() => {
      expect(screen.getByText("Check your email.")).toBeInTheDocument();
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

  // Phase 88 U11 (ADR-351) — (a) bare /login: no query, no hint.
  it("bare /login submits email-only and shows the truthful success copy", async () => {
    mockSearchParams = new URLSearchParams();
    mockGetLastOrgId.mockReturnValue(null);
    mockPublicFetch.mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          message: "If an account exists, a link has been sent.",
          magicLink: null,
        }),
        { status: 200 },
      ),
    );

    const user = userEvent.setup();
    render(<LoginPage />);

    // Generic branding, no branding fetch when no orgId is known.
    expect(screen.getByText("Kazi")).toBeInTheDocument();
    expect(mockPublicFetch).not.toHaveBeenCalled();

    await user.type(
      screen.getByLabelText("Email address"),
      "first@example.com",
    );
    await user.click(screen.getByRole("button", { name: "Send Magic Link" }));

    await waitFor(() => {
      expect(screen.getByText("Check your email.")).toBeInTheDocument();
    });
    expect(screen.getByText(/sent a sign-in link/i)).toBeInTheDocument();
    expect(
      screen.queryByText(/couldn't determine which organization/i),
    ).toBeNull();

    expect(mockPublicFetch).toHaveBeenCalledTimes(1);
    const [url, init] = mockPublicFetch.mock.calls[0];
    expect(url).toBe("/portal/auth/request-link");
    expect(JSON.parse(init.body as string)).toEqual({
      email: "first@example.com",
    });
  });

  // Phase 88 U11 — (b) ?orgId= from the link is the fast path.
  it("sends the query orgId as the fast-path hint", async () => {
    mockSearchParams = new URLSearchParams("orgId=org_abc");
    mockPublicFetch
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            orgName: "Test Corp",
            logoUrl: null,
            brandColor: null,
            footerText: null,
          }),
          { status: 200 },
        ),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ message: "Sent." }), { status: 200 }),
      );

    const user = userEvent.setup();
    render(<LoginPage />);

    await waitFor(() => {
      expect(screen.getByText("Test Corp")).toBeInTheDocument();
    });
    expect(mockPublicFetch.mock.calls[0][0]).toBe(
      "/portal/branding?orgId=org_abc",
    );

    await user.type(screen.getByLabelText("Email address"), "a@example.com");
    await user.click(screen.getByRole("button", { name: "Send Magic Link" }));

    await waitFor(() => {
      const requestLinkCall = mockPublicFetch.mock.calls.find(
        (c) => c[0] === "/portal/auth/request-link",
      );
      expect(requestLinkCall).toBeDefined();
      expect(JSON.parse(requestLinkCall![1].body as string)).toEqual({
        email: "a@example.com",
        orgId: "org_abc",
      });
    });
  });

  // Phase 88 U11 — (c) the localStorage hint brands the page only.
  it("uses the last-known orgId hint for branding but never sends it", async () => {
    mockSearchParams = new URLSearchParams();
    mockGetLastOrgId.mockReturnValue("mathebula-partners");
    mockPublicFetch
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            orgName: "Mathebula & Partners",
            logoUrl: null,
            brandColor: null,
            footerText: null,
          }),
          { status: 200 },
        ),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ message: "Sent." }), { status: 200 }),
      );

    const user = userEvent.setup();
    render(<LoginPage />);

    await waitFor(() => {
      expect(screen.getByText("Mathebula & Partners")).toBeInTheDocument();
    });

    await user.type(
      screen.getByLabelText("Email address"),
      "sipho@example.com",
    );
    await user.click(screen.getByRole("button", { name: "Send Magic Link" }));

    await waitFor(() => {
      const requestLinkCall = mockPublicFetch.mock.calls.find(
        (c) => c[0] === "/portal/auth/request-link",
      );
      expect(requestLinkCall).toBeDefined();
      expect(JSON.parse(requestLinkCall![1].body as string)).toEqual({
        email: "sipho@example.com",
      });
    });
  });

  // Phase 88 U11 — (d) per-email cap.
  it("shows the rate-limit message on 429", async () => {
    mockSearchParams = new URLSearchParams();
    mockPublicFetch.mockResolvedValueOnce(
      new Response(
        JSON.stringify({ status: 429, title: "Too many login attempts" }),
        { status: 429 },
      ),
    );

    const user = userEvent.setup();
    render(<LoginPage />);

    await user.type(screen.getByLabelText("Email address"), "a@example.com");
    await user.click(screen.getByRole("button", { name: "Send Magic Link" }));

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent(
        "Too many login attempts. Please try again later.",
      );
    });
    expect(screen.queryByText("Check your email.")).toBeNull();
  });

  // (f) dev-profile echo still renders.
  it("renders the dev-mode magic link when the backend echoes one", async () => {
    mockSearchParams = new URLSearchParams();
    mockPublicFetch.mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          message: "If an account exists, a link has been sent.",
          magicLink: "/auth/exchange?token=t&orgId=acme",
        }),
        { status: 200 },
      ),
    );

    const user = userEvent.setup();
    render(<LoginPage />);

    await user.type(screen.getByLabelText("Email address"), "a@example.com");
    await user.click(screen.getByRole("button", { name: "Send Magic Link" }));

    await waitFor(() => {
      expect(screen.getByText(/Dev mode/)).toBeInTheDocument();
    });
    expect(
      screen.getByRole("link", { name: "/auth/exchange?token=t&orgId=acme" }),
    ).toHaveAttribute("href", "/auth/exchange?token=t&orgId=acme");
  });

  // GAP-L-66
  it("persists redirectTo to sessionStorage when present in query", () => {
    mockSearchParams = new URLSearchParams(
      "orgId=mathebula-partners&redirectTo=%2Finvoices%2F123",
    );
    mockPublicFetch.mockResolvedValue(
      new Response(
        JSON.stringify({
          orgName: "Mathebula & Partners",
          logoUrl: null,
          brandColor: null,
          footerText: null,
        }),
        { status: 200 },
      ),
    );

    render(<LoginPage />);

    expect(sessionStorage.getItem("portal_post_login_redirect")).toBe(
      "/invoices/123",
    );
  });
});
