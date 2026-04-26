import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";

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

import ProjectsPage from "@/app/(authenticated)/projects/page";

describe("ProjectsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders project cards after loading", async () => {
    mockPortalGet.mockResolvedValue([
      {
        id: "proj-1",
        name: "Website Redesign",
        description: "Modernizing the website",
        documentCount: 3,
        createdAt: "2026-02-01T10:00:00Z",
      },
      {
        id: "proj-2",
        name: "Annual Report",
        description: null,
        documentCount: 0,
        createdAt: "2026-01-15T08:00:00Z",
      },
    ]);

    render(<ProjectsPage />);

    expect(screen.getByText("Your Projects")).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText("Website Redesign")).toBeInTheDocument();
      expect(screen.getByText("Annual Report")).toBeInTheDocument();
    });
  });

  it("shows empty state when no projects exist", async () => {
    mockPortalGet.mockResolvedValue([]);

    render(<ProjectsPage />);

    await waitFor(() => {
      expect(screen.getByText("No projects yet")).toBeInTheDocument();
      expect(
        screen.getByText(
          "Your Test Org team will share projects with you here.",
        ),
      ).toBeInTheDocument();
    });
  });

  it("shows error state on fetch failure", async () => {
    mockPortalGet.mockImplementation((path: string) => {
      if (path === "/portal/projects") {
        return Promise.reject(new Error("Network error"));
      }
      // PendingAcceptancesList also calls portalGet — return empty to keep it quiet
      return Promise.resolve([]);
    });

    render(<ProjectsPage />);

    await waitFor(() => {
      expect(screen.getByText("Network error")).toBeInTheDocument();
    });
  });

  it("filters projects via the All / Active / Past segmented control", async () => {
    mockPortalGet.mockImplementation((path: string) => {
      if (path === "/portal/projects") {
        return Promise.resolve([
          {
            id: "proj-active",
            name: "Active Matter",
            description: null,
            documentCount: 1,
            createdAt: "2026-04-01T00:00:00Z",
            status: "ACTIVE",
          },
          {
            id: "proj-closed",
            name: "Closed Matter",
            description: null,
            documentCount: 2,
            createdAt: "2026-03-01T00:00:00Z",
            status: "CLOSED",
          },
          {
            id: "proj-completed",
            name: "Completed Matter",
            description: null,
            documentCount: 3,
            createdAt: "2026-02-01T00:00:00Z",
            status: "COMPLETED",
          },
        ]);
      }
      return Promise.resolve([]);
    });

    render(<ProjectsPage />);

    // All tab (default) — all 3 projects visible
    await waitFor(() => {
      expect(screen.getByText("Active Matter")).toBeInTheDocument();
      expect(screen.getByText("Closed Matter")).toBeInTheDocument();
      expect(screen.getByText("Completed Matter")).toBeInTheDocument();
    });

    // Active tab — only ACTIVE
    fireEvent.click(screen.getByRole("tab", { name: "Active" }));
    await waitFor(() => {
      expect(screen.getByText("Active Matter")).toBeInTheDocument();
      expect(screen.queryByText("Closed Matter")).not.toBeInTheDocument();
      expect(screen.queryByText("Completed Matter")).not.toBeInTheDocument();
    });

    // Past tab — CLOSED + COMPLETED
    fireEvent.click(screen.getByRole("tab", { name: "Past" }));
    await waitFor(() => {
      expect(screen.queryByText("Active Matter")).not.toBeInTheDocument();
      expect(screen.getByText("Closed Matter")).toBeInTheDocument();
      expect(screen.getByText("Completed Matter")).toBeInTheDocument();
    });
  });
});
