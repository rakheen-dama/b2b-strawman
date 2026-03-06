import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { RequestStatusBadge } from "@/components/information-requests/request-status-badge";
import { ItemStatusBadge } from "@/components/information-requests/item-status-badge";
import { RequestProgressBar } from "@/components/information-requests/request-progress-bar";
import { RequestList } from "@/components/information-requests/request-list";
import type { InformationRequestResponse } from "@/lib/api/information-requests";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock(
  "@/app/(app)/org/[slug]/customers/[id]/request-actions",
  () => ({
    createRequestAction: vi.fn(),
    sendRequestAction: vi.fn(),
    fetchActiveTemplatesAction: vi.fn().mockResolvedValue({ success: true, data: [] }),
    fetchPortalContactsAction: vi.fn().mockResolvedValue({ success: true, data: [] }),
  }),
);

const mockRequest: InformationRequestResponse = {
  id: "req-1",
  requestNumber: "REQ-0001",
  customerId: "cust-1",
  customerName: "Acme Corp",
  projectId: "proj-1",
  projectName: "Annual Audit",
  portalContactId: "contact-1",
  portalContactName: "John Smith",
  portalContactEmail: "john@acme.com",
  status: "IN_PROGRESS",
  reminderIntervalDays: 5,
  sentAt: "2026-03-01T10:00:00Z",
  completedAt: null,
  totalItems: 8,
  submittedItems: 3,
  acceptedItems: 2,
  rejectedItems: 0,
  items: [],
  createdAt: "2026-03-01T09:00:00Z",
};

describe("Information Request Components", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  // Test 1: RequestStatusBadge renders correct labels
  it("renders RequestStatusBadge with correct label for each status", () => {
    const statuses = [
      { status: "DRAFT" as const, label: "Draft" },
      { status: "SENT" as const, label: "Sent" },
      { status: "IN_PROGRESS" as const, label: "In Progress" },
      { status: "COMPLETED" as const, label: "Completed" },
      { status: "CANCELLED" as const, label: "Cancelled" },
    ];

    for (const { status, label } of statuses) {
      const { unmount } = render(<RequestStatusBadge status={status} />);
      expect(screen.getByText(label)).toBeInTheDocument();
      unmount();
    }
  });

  // Test 2: ItemStatusBadge renders correct labels
  it("renders ItemStatusBadge with correct label for each status", () => {
    const statuses = [
      { status: "PENDING" as const, label: "Pending" },
      { status: "SUBMITTED" as const, label: "Submitted" },
      { status: "ACCEPTED" as const, label: "Accepted" },
      { status: "REJECTED" as const, label: "Rejected" },
    ];

    for (const { status, label } of statuses) {
      const { unmount } = render(<ItemStatusBadge status={status} />);
      expect(screen.getByText(label)).toBeInTheDocument();
      unmount();
    }
  });

  // Test 3: RequestProgressBar displays correct count
  it("renders RequestProgressBar with correct accepted/total text", () => {
    render(<RequestProgressBar totalItems={8} acceptedItems={3} />);
    expect(screen.getByText("3/8 accepted")).toBeInTheDocument();
  });

  // Test 4: RequestProgressBar with zero items
  it("renders RequestProgressBar with zero items gracefully", () => {
    render(<RequestProgressBar totalItems={0} acceptedItems={0} />);
    expect(screen.getByText("0/0 accepted")).toBeInTheDocument();
  });

  // Test 5: RequestList empty state
  it("renders RequestList empty state when no requests", () => {
    render(<RequestList requests={[]} slug="test-org" />);
    expect(screen.getByText("No information requests yet")).toBeInTheDocument();
  });

  // Test 6: RequestList shows request data
  it("renders RequestList with request data", () => {
    render(
      <RequestList
        requests={[mockRequest]}
        slug="test-org"
        showCustomer={true}
      />,
    );
    expect(screen.getByText("REQ-0001")).toBeInTheDocument();
    expect(screen.getByText("Acme Corp")).toBeInTheDocument();
    expect(screen.getByText("John Smith")).toBeInTheDocument();
    expect(screen.getByText("In Progress")).toBeInTheDocument();
    expect(screen.getByText("2/8 accepted")).toBeInTheDocument();
  });

  // Test 7: RequestList hides customer column when showCustomer=false
  it("hides customer column when showCustomer is false", () => {
    render(
      <RequestList
        requests={[mockRequest]}
        slug="test-org"
        showCustomer={false}
      />,
    );
    expect(screen.getByText("REQ-0001")).toBeInTheDocument();
    // Customer column header should not be rendered
    const headers = screen.getAllByRole("columnheader");
    const headerTexts = headers.map((h) => h.textContent);
    expect(headerTexts).not.toContain("Customer");
  });

  // Test 8: RequestList shows project name as subtitle
  it("shows project name under request number", () => {
    render(
      <RequestList
        requests={[mockRequest]}
        slug="test-org"
        showCustomer={false}
      />,
    );
    expect(screen.getByText("Annual Audit")).toBeInTheDocument();
  });
});
