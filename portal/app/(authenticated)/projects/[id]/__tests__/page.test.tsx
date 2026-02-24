import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";

// Mock next/navigation
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
  usePathname: () => "/projects/proj-1",
  useParams: () => ({ id: "proj-1" }),
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
const mockPortalPost = vi.fn();
vi.mock("@/lib/api-client", () => ({
  portalGet: (...args: unknown[]) => mockPortalGet(...args),
  portalPost: (...args: unknown[]) => mockPortalPost(...args),
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

import ProjectDetailPage from "@/app/(authenticated)/projects/[id]/page";

const mockProject = {
  id: "proj-1",
  name: "Website Redesign",
  status: "ACTIVE",
  description: "Modernizing the company website",
  documentCount: 2,
  commentCount: 1,
  createdAt: "2026-02-01T10:00:00Z",
};

const mockTasks = [
  {
    id: "t-1",
    name: "Design mockups",
    status: "DONE",
    assigneeName: "Alice",
    sortOrder: 1,
  },
];

const mockDocuments = [
  {
    id: "doc-1",
    fileName: "proposal.pdf",
    contentType: "application/pdf",
    size: 2048000,
    scope: "PROJECT",
    status: "ACTIVE",
    createdAt: "2026-02-01T10:00:00Z",
  },
];

const mockComments = [
  {
    id: "c-1",
    authorName: "Alice",
    content: "Looks good!",
    createdAt: "2026-02-20T14:00:00Z",
  },
];

const mockSummary = {
  projectId: "proj-1",
  totalHours: 24.5,
  billableHours: 20.0,
  lastActivityAt: "2026-02-20T14:00:00Z",
};

function setupMocks() {
  mockPortalGet.mockImplementation((path: string) => {
    if (path.endsWith("/tasks")) return Promise.resolve(mockTasks);
    if (path.endsWith("/documents")) return Promise.resolve(mockDocuments);
    if (path.endsWith("/comments")) return Promise.resolve(mockComments);
    if (path.endsWith("/summary")) return Promise.resolve(mockSummary);
    return Promise.resolve(mockProject);
  });
}

describe("ProjectDetailPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders all sections after loading", async () => {
    setupMocks();

    render(<ProjectDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("Website Redesign")).toBeInTheDocument();
    });

    // Status badge
    expect(screen.getByText("ACTIVE")).toBeInTheDocument();
    // Description
    expect(
      screen.getByText("Modernizing the company website"),
    ).toBeInTheDocument();
    // Task
    expect(screen.getByText("Design mockups")).toBeInTheDocument();
    // Document (renders in both mobile card and desktop table layouts)
    expect(screen.getAllByText("proposal.pdf").length).toBeGreaterThanOrEqual(1);
    // Comment
    expect(screen.getByText("Looks good!")).toBeInTheDocument();
    // Summary
    expect(screen.getByText("24.5h total")).toBeInTheDocument();
    expect(screen.getByText("20.0h billable")).toBeInTheDocument();
  });

  it("shows error state on fetch failure", async () => {
    mockPortalGet.mockRejectedValue(new Error("Network error"));

    render(<ProjectDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("Network error")).toBeInTheDocument();
    });
  });

  it("hides summary card when totalHours is 0", async () => {
    mockPortalGet.mockImplementation((path: string) => {
      if (path.endsWith("/tasks")) return Promise.resolve(mockTasks);
      if (path.endsWith("/documents")) return Promise.resolve(mockDocuments);
      if (path.endsWith("/comments")) return Promise.resolve(mockComments);
      if (path.endsWith("/summary"))
        return Promise.resolve({
          ...mockSummary,
          totalHours: 0,
          billableHours: 0,
        });
      return Promise.resolve(mockProject);
    });

    render(<ProjectDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("Website Redesign")).toBeInTheDocument();
    });

    expect(screen.queryByText("Project Summary")).not.toBeInTheDocument();
  });
});
