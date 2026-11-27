import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
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
  industry: "Legal Services",
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
    // A page-level tab panel's empty state (669B): default variant with an h2 title.
    const rejectedPanel = screen.getByRole("tabpanel");
    expect(
      within(rejectedPanel).getByText("No access requests").closest('[data-slot="empty-state"]')
    ).toHaveAttribute("data-variant", "default");
    expect(
      within(rejectedPanel).getByRole("heading", { level: 2, name: "No access requests" })
    ).toBeInTheDocument();
    expect(within(rejectedPanel).queryByRole("table")).toBeNull();
  });

  it("renders each tab as a DataTable with today's headers and row testids (669B)", () => {
    render(<AccessRequestsTable requests={[pendingRequest, approvedRequest]} />);

    const panel = screen.getByRole("tabpanel");
    expect(
      within(panel)
        .getAllByRole("columnheader")
        .map((h) => h.textContent)
    ).toEqual([
      "Org Name",
      "Email",
      "Name",
      "Country",
      "Industry",
      "Submitted",
      "Status",
      "Actions",
    ]);
    const row = screen.getByTestId("request-row-Acme Corp");
    expect(row.tagName).toBe("TR");
    expect(within(row).getByTestId("approve-btn")).toHaveTextContent("Approve");
    expect(within(row).getByRole("button", { name: "Reject" })).toBeInTheDocument();
    expect(within(row).getByText("PENDING")).toBeInTheDocument();
    expect(screen.getByTestId("pending-tab")).toHaveAttribute("data-state", "active");
    expect(screen.getByTestId("access-requests-page")).toBeInTheDocument();
  });

  it("lists the All tab oldest first, with actions only on pending rows", async () => {
    const user = userEvent.setup();
    render(<AccessRequestsTable requests={[pendingRequest, approvedRequest, rejectedRequest]} />);

    await user.click(screen.getByText("All"));

    const panel = screen.getByRole("tabpanel");
    const bodyRows = within(panel).getAllByRole("row").slice(1);
    expect(bodyRows.map((r) => r.getAttribute("data-testid"))).toEqual([
      "request-row-Gamma LLC",
      "request-row-Beta Inc",
      "request-row-Acme Corp",
    ]);
    expect(within(panel).getAllByTestId("approve-btn")).toHaveLength(1);
    expect(within(bodyRows[0]).queryByRole("button")).toBeNull();
  });

  it("lets the prose columns wrap so Actions stays in the frame at 1024 (F-669B-B-8)", () => {
    render(<AccessRequestsTable requests={[pendingRequest]} />);

    const cells = within(screen.getByTestId("request-row-Acme Corp")).getAllByRole("cell");
    const [org, email, name, country, industry, submitted, status, actions] = cells;
    for (const cell of [org, email, name, country, industry]) {
      expect(cell).toHaveClass("whitespace-normal");
    }
    // The email breaks anywhere, above a floor; the org name keeps a floor too.
    expect(email).toHaveClass("wrap-anywhere");
    expect(email.style.minWidth).toBe("12rem");
    expect(org.style.minWidth).toBe("8rem");
    // Submitted, Status and Actions are not opted into wrapping (they keep the kit default).
    for (const cell of [submitted, status, actions]) {
      expect(cell).not.toHaveClass("whitespace-normal");
    }
  });
});
