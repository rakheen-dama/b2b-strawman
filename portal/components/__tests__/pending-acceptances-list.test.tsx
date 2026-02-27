import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";

// Mock next/navigation
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
  usePathname: () => "/projects",
}));

// Mock next/link
vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    ...props
  }: {
    children: React.ReactNode;
    href: string;
    [key: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

// Mock api-client
const mockPortalGet = vi.fn();
vi.mock("@/lib/api-client", () => ({
  portalGet: (...args: unknown[]) => mockPortalGet(...args),
}));

// Mock useAuth
vi.mock("@/hooks/use-auth", () => ({
  useAuth: () => ({
    isAuthenticated: true,
    isLoading: false,
    jwt: "test-jwt",
    customer: {
      id: "cust-1",
      name: "Test Corp",
      email: "alice@test.com",
      orgId: "org_abc",
    },
    logout: vi.fn(),
  }),
}));

// Mock useBranding
vi.mock("@/hooks/use-branding", () => ({
  useBranding: () => ({
    orgName: "Test Org",
    logoUrl: null,
    brandColor: "#3B82F6",
    footerText: null,
    isLoading: false,
  }),
}));

import { PendingAcceptancesList } from "@/components/pending-acceptances-list";

describe("PendingAcceptancesList", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders pending acceptances list", async () => {
    mockPortalGet.mockResolvedValue([
      {
        id: "acc-1",
        documentTitle: "Service Agreement 2026",
        requestToken: "token-abc-123",
        sentAt: "2026-02-20T10:00:00Z",
        expiresAt: "2026-03-20T10:00:00Z",
        status: "SENT",
      },
      {
        id: "acc-2",
        documentTitle: "NDA - Project Alpha",
        requestToken: "token-def-456",
        sentAt: "2026-02-22T14:00:00Z",
        expiresAt: "2026-03-22T14:00:00Z",
        status: "VIEWED",
      },
    ]);

    render(<PendingAcceptancesList />);

    await waitFor(() => {
      expect(
        screen.getByText("Service Agreement 2026"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("NDA - Project Alpha"),
      ).toBeInTheDocument();
    });

    expect(screen.getByText("Pending Acceptances")).toBeInTheDocument();
    expect(screen.getAllByText("Review & Accept")).toHaveLength(2);
  });

  it("shows nothing when no pending acceptances", async () => {
    mockPortalGet.mockResolvedValue([]);

    const { container } = render(<PendingAcceptancesList />);

    await waitFor(() => {
      // Component should render nothing when empty
      expect(container.innerHTML).toBe("");
    });
  });

  it("renders error message when fetch fails", async () => {
    mockPortalGet.mockRejectedValue(new Error("Network error"));

    render(<PendingAcceptancesList />);

    await waitFor(() => {
      expect(screen.getByText("Network error")).toBeInTheDocument();
    });
  });

  it("renders fallback error message for non-Error rejection", async () => {
    mockPortalGet.mockRejectedValue("unknown failure");

    render(<PendingAcceptancesList />);

    await waitFor(() => {
      expect(
        screen.getByText("Failed to load pending acceptances"),
      ).toBeInTheDocument();
    });
  });

  it("links to acceptance page with correct token", async () => {
    mockPortalGet.mockResolvedValue([
      {
        id: "acc-1",
        documentTitle: "Service Agreement",
        requestToken: "token-abc-123",
        sentAt: "2026-02-20T10:00:00Z",
        expiresAt: "2026-03-20T10:00:00Z",
        status: "SENT",
      },
    ]);

    render(<PendingAcceptancesList />);

    await waitFor(() => {
      const link = screen.getByText("Review & Accept").closest("a");
      expect(link).toHaveAttribute("href", "/accept/token-abc-123");
    });
  });
});
