import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";

// Mock next/navigation
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
  usePathname: () => "/profile",
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

import ProfilePage from "@/app/(authenticated)/profile/page";

describe("ProfilePage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders contact info after loading", async () => {
    mockPortalGet.mockResolvedValue({
      contactId: "contact-1",
      customerId: "cust-1",
      customerName: "Acme Corp",
      email: "alice@acme.com",
      displayName: "Alice Smith",
      role: "PRIMARY",
    });

    render(<ProfilePage />);

    await waitFor(() => {
      expect(screen.getByText("Alice Smith")).toBeInTheDocument();
    });

    expect(screen.getByText("alice@acme.com")).toBeInTheDocument();
  });

  it("shows customer name", async () => {
    mockPortalGet.mockResolvedValue({
      contactId: "contact-1",
      customerId: "cust-1",
      customerName: "Acme Corp",
      email: "alice@acme.com",
      displayName: "Alice Smith",
      role: "BILLING",
    });

    render(<ProfilePage />);

    await waitFor(() => {
      expect(screen.getByText("Acme Corp")).toBeInTheDocument();
    });
  });

  it("displays role badge", async () => {
    mockPortalGet.mockResolvedValue({
      contactId: "contact-1",
      customerId: "cust-1",
      customerName: "Acme Corp",
      email: "alice@acme.com",
      displayName: "Alice Smith",
      role: "GENERAL",
    });

    render(<ProfilePage />);

    await waitFor(() => {
      expect(screen.getByText("General")).toBeInTheDocument();
    });
  });

  it("shows error state on fetch failure", async () => {
    mockPortalGet.mockRejectedValue(new Error("Network error"));

    render(<ProfilePage />);

    await waitFor(() => {
      expect(screen.getByText("Network error")).toBeInTheDocument();
    });
  });
});
