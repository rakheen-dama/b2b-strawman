import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import PortalLoginPage from "@/app/portal/page";

// --- Mocks ---

const mockPush = vi.fn();
const mockReplace = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: mockPush,
    replace: mockReplace,
    refresh: vi.fn(),
    back: vi.fn(),
    forward: vi.fn(),
    prefetch: vi.fn(),
  }),
}));

const mockPost = vi.fn();

vi.mock("@/lib/portal-api", () => ({
  portalApi: {
    get: vi.fn(),
    post: (...args: unknown[]) => mockPost(...args),
  },
  setPortalToken: vi.fn(),
  setPortalCustomerName: vi.fn(),
  clearPortalAuth: vi.fn(),
  PortalApiError: class PortalApiError extends Error {
    status: number;
    constructor(status: number, message: string) {
      super(message);
      this.status = status;
      this.name = "PortalApiError";
    }
  },
}));

describe("PortalLoginPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders the login form with email and organization fields", () => {
    render(<PortalLoginPage />);

    expect(screen.getByText("DocTeams Portal")).toBeInTheDocument();
    expect(screen.getByLabelText("Email address")).toBeInTheDocument();
    expect(screen.getByLabelText("Organization")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Send Magic Link/i })).toBeInTheDocument();
  });

  it("shows success message and magic link on successful request", async () => {
    mockPost.mockResolvedValue({
      message: "Magic link generated",
      magicLink: "http://localhost:8080/portal/auth/verify?token=abc123",
    });

    const user = userEvent.setup();
    render(<PortalLoginPage />);

    await user.type(screen.getByLabelText("Email address"), "customer@example.com");
    await user.type(screen.getByLabelText("Organization"), "acme");
    await user.click(screen.getByRole("button", { name: /Send Magic Link/i }));

    await waitFor(() => {
      expect(mockPost).toHaveBeenCalledWith("/portal/auth/request-link", {
        email: "customer@example.com",
        orgSlug: "acme",
      });
    });

    await waitFor(() => {
      expect(screen.getByText("Magic link generated")).toBeInTheDocument();
    });
  });

  it("shows error message on failed magic link request", async () => {
    const { PortalApiError } = await import("@/lib/portal-api");
    mockPost.mockRejectedValue(new PortalApiError(404, "Customer not found"));

    const user = userEvent.setup();
    render(<PortalLoginPage />);

    await user.type(screen.getByLabelText("Email address"), "unknown@example.com");
    await user.type(screen.getByLabelText("Organization"), "acme");
    await user.click(screen.getByRole("button", { name: /Send Magic Link/i }));

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("Customer not found");
    });
  });
});
