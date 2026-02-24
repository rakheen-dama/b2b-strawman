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

import LoginPage from "@/app/login/page";

describe("LoginPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSearchParams = new URLSearchParams();
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
    const user = userEvent.setup();
    mockPublicFetch.mockResolvedValue(
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
});
