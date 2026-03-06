import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import PortalRequestListPage from "@/app/portal/(authenticated)/requests/page";
import { PortalRequestStatusBadge } from "@/components/portal/portal-request-status-badge";
import { PortalRequestProgressBar } from "@/components/portal/portal-request-progress-bar";
import type { PortalRequestListItem } from "@/lib/api/portal-requests";

// --- Mocks ---

const mockListPortalRequests = vi.fn();
const mockReplace = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: mockReplace,
    refresh: vi.fn(),
    back: vi.fn(),
    forward: vi.fn(),
    prefetch: vi.fn(),
  }),
}));

vi.mock("@/lib/portal-api", () => ({
  portalApi: {
    get: vi.fn(),
    post: vi.fn(),
  },
  isPortalAuthenticated: () => true,
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

vi.mock("@/lib/api/portal-requests", () => ({
  listPortalRequests: (...args: unknown[]) => mockListPortalRequests(...args),
}));

const sampleRequests: PortalRequestListItem[] = [
  {
    id: "req-1",
    requestNumber: "REQ-0001",
    status: "IN_PROGRESS",
    projectId: "proj-1",
    projectName: "Annual Audit",
    totalItems: 8,
    submittedItems: 3,
    acceptedItems: 2,
    rejectedItems: 0,
    sentAt: "2026-03-01T10:00:00Z",
    completedAt: null,
  },
  {
    id: "req-2",
    requestNumber: "REQ-0002",
    status: "SENT",
    projectId: null,
    projectName: null,
    totalItems: 4,
    submittedItems: 0,
    acceptedItems: 0,
    rejectedItems: 0,
    sentAt: "2026-03-05T14:00:00Z",
    completedAt: null,
  },
  {
    id: "req-3",
    requestNumber: "REQ-0003",
    status: "COMPLETED",
    projectId: "proj-2",
    projectName: "Tax Returns",
    totalItems: 3,
    submittedItems: 3,
    acceptedItems: 3,
    rejectedItems: 0,
    sentAt: "2026-02-15T09:00:00Z",
    completedAt: "2026-02-20T16:00:00Z",
  },
];

describe("Portal Requests", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders request cards after loading", async () => {
    mockListPortalRequests.mockResolvedValue(sampleRequests);

    render(<PortalRequestListPage />);

    await waitFor(() => {
      expect(screen.getByText("REQ-0001")).toBeInTheDocument();
    });

    expect(screen.getByText("REQ-0002")).toBeInTheDocument();
    // REQ-0003 is completed, not shown on default "open" tab
    expect(screen.queryByText("REQ-0003")).not.toBeInTheDocument();
  });

  it("renders status badge with correct labels", () => {
    const statuses = [
      { status: "SENT" as const, label: "Sent" },
      { status: "IN_PROGRESS" as const, label: "In Progress" },
      { status: "COMPLETED" as const, label: "Completed" },
      { status: "CANCELLED" as const, label: "Cancelled" },
      { status: "DRAFT" as const, label: "Draft" },
    ];

    for (const { status, label } of statuses) {
      const { unmount } = render(<PortalRequestStatusBadge status={status} />);
      expect(screen.getByText(label)).toBeInTheDocument();
      unmount();
    }
  });

  it("renders progress bar with correct counts", () => {
    render(<PortalRequestProgressBar totalItems={8} acceptedItems={3} />);
    expect(screen.getByText("3/8 accepted")).toBeInTheDocument();
  });

  it("filters between open and completed tabs", async () => {
    mockListPortalRequests.mockResolvedValue(sampleRequests);
    const user = userEvent.setup();

    render(<PortalRequestListPage />);

    await waitFor(() => {
      expect(screen.getByText("REQ-0001")).toBeInTheDocument();
    });

    // Open tab shows SENT and IN_PROGRESS
    expect(screen.getByText("REQ-0002")).toBeInTheDocument();
    expect(screen.queryByText("REQ-0003")).not.toBeInTheDocument();

    // Switch to completed tab
    await user.click(screen.getByText(/Completed/));

    expect(screen.getByText("REQ-0003")).toBeInTheDocument();
    expect(screen.queryByText("REQ-0001")).not.toBeInTheDocument();
    expect(screen.queryByText("REQ-0002")).not.toBeInTheDocument();
  });

  it("shows empty state when no requests", async () => {
    mockListPortalRequests.mockResolvedValue([]);

    render(<PortalRequestListPage />);

    await waitFor(() => {
      expect(screen.getByText("No open requests")).toBeInTheDocument();
    });
  });

  it("shows project name when linked to a project", async () => {
    mockListPortalRequests.mockResolvedValue(sampleRequests);

    render(<PortalRequestListPage />);

    await waitFor(() => {
      expect(screen.getByText("REQ-0001")).toBeInTheDocument();
    });

    expect(screen.getByText("Annual Audit")).toBeInTheDocument();
  });
});
