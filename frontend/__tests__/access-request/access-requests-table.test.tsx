import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AccessRequestsTable } from "@/components/access-request/access-requests-table";
import type { AccessRequest } from "@/app/(app)/platform-admin/access-requests/actions";

const mockApproveAccessRequest = vi.fn();
const mockRejectAccessRequest = vi.fn();
const mockRefresh = vi.fn();

vi.mock("server-only", () => ({}));

vi.mock("@/app/(app)/platform-admin/access-requests/actions", () => ({
  approveAccessRequest: (...args: unknown[]) => mockApproveAccessRequest(...args),
  rejectAccessRequest: (...args: unknown[]) => mockRejectAccessRequest(...args),
  listAccessRequests: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
    refresh: mockRefresh,
    back: vi.fn(),
    forward: vi.fn(),
    prefetch: vi.fn(),
  }),
}));

const pendingRequest: AccessRequest = {
  id: "req-1",
  email: "jane@acme.com",
  fullName: "Jane Smith",
  organizationName: "Acme Corp",
  country: "South Africa",
  industry: "Accounting",
  status: "PENDING",
  otpVerifiedAt: "2026-03-07T10:00:00Z",
  createdAt: "2026-03-07T09:55:00Z",
};

const approvedRequest: AccessRequest = {
  id: "req-2",
  email: "bob@beta.com",
  fullName: "Bob Johnson",
  organizationName: "Beta Inc",
  country: "United States",
  industry: "Legal",
  status: "APPROVED",
  otpVerifiedAt: "2026-03-06T10:00:00Z",
  createdAt: "2026-03-06T08:00:00Z",
};

const rejectedRequest: AccessRequest = {
  id: "req-3",
  email: "carol@gamma.com",
  fullName: "Carol Williams",
  organizationName: "Gamma LLC",
  country: "United Kingdom",
  industry: "Engineering",
  status: "REJECTED",
  otpVerifiedAt: "2026-03-05T10:00:00Z",
  createdAt: "2026-03-05T07:00:00Z",
};

describe("AccessRequestsTable", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders table with pending requests by default", () => {
    render(<AccessRequestsTable requests={[pendingRequest, approvedRequest, rejectedRequest]} />);

    // Default tab is PENDING, so only pending request should show
    expect(screen.getByText("Acme Corp")).toBeInTheDocument();
    expect(screen.getByText("jane@acme.com")).toBeInTheDocument();
    expect(screen.queryByText("Beta Inc")).not.toBeInTheDocument();
    expect(screen.queryByText("Gamma LLC")).not.toBeInTheDocument();
  });

  it("filters by status when clicking tabs", async () => {
    const user = userEvent.setup();

    render(<AccessRequestsTable requests={[pendingRequest, approvedRequest, rejectedRequest]} />);

    // Click "All" tab
    await user.click(screen.getByText("All"));
    expect(screen.getByText("Acme Corp")).toBeInTheDocument();
    expect(screen.getByText("Beta Inc")).toBeInTheDocument();
    expect(screen.getByText("Gamma LLC")).toBeInTheDocument();

    // Click "Approved" tab
    await user.click(screen.getByText("Approved"));
    expect(screen.queryByText("Acme Corp")).not.toBeInTheDocument();
    expect(screen.getByText("Beta Inc")).toBeInTheDocument();
    expect(screen.queryByText("Gamma LLC")).not.toBeInTheDocument();
  });

  it("shows approve dialog confirmation when clicking Approve", async () => {
    const user = userEvent.setup();

    render(<AccessRequestsTable requests={[pendingRequest]} />);

    await user.click(screen.getByText("Approve"));

    expect(screen.getByText("Approve Access Request")).toBeInTheDocument();
    expect(screen.getByText(/provision a tenant schema/)).toBeInTheDocument();
    // Email and org name appear in both table and dialog
    expect(screen.getAllByText(/Acme Corp/).length).toBeGreaterThanOrEqual(2);
    expect(screen.getAllByText(/jane@acme.com/).length).toBeGreaterThanOrEqual(2);
  });

  it("shows reject dialog confirmation when clicking Reject", async () => {
    const user = userEvent.setup();

    render(<AccessRequestsTable requests={[pendingRequest]} />);

    await user.click(screen.getByText("Reject"));

    expect(screen.getByText("Reject Access Request")).toBeInTheDocument();
    expect(screen.getByText(/cannot be undone/)).toBeInTheDocument();
    // Email appears in both table and dialog
    expect(screen.getAllByText(/jane@acme.com/).length).toBeGreaterThanOrEqual(2);
  });

  it("refreshes list after successful approval", async () => {
    mockApproveAccessRequest.mockResolvedValue({ success: true });
    const user = userEvent.setup();

    render(<AccessRequestsTable requests={[pendingRequest]} />);

    await user.click(screen.getByText("Approve"));

    // Click the approve button in the dialog
    const dialogApproveButtons = screen.getAllByText("Approve");
    const confirmButton = dialogApproveButtons[dialogApproveButtons.length - 1];
    await user.click(confirmButton);

    await waitFor(() => {
      expect(mockApproveAccessRequest).toHaveBeenCalledWith("req-1");
    });

    await waitFor(() => {
      expect(mockRefresh).toHaveBeenCalled();
    });
  });

  it("shows empty state when no requests match filter", async () => {
    const user = userEvent.setup();

    render(<AccessRequestsTable requests={[pendingRequest]} />);

    // Click "Rejected" tab — no rejected requests
    await user.click(screen.getByText("Rejected"));

    expect(screen.getByText("No access requests")).toBeInTheDocument();
    expect(screen.getByText(/No rejected access requests/)).toBeInTheDocument();
  });
});
