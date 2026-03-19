import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Mock next/navigation
const mockRefresh = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ refresh: mockRefresh, push: vi.fn() }),
  usePathname: () => "/org/acme/settings/data-protection/requests",
}));

// Mock server actions
const mockUpdateDsarStatus = vi.fn();
const mockCreateDsarRequest = vi.fn();
vi.mock(
  "@/app/(app)/org/[slug]/settings/data-protection/requests/actions",
  () => ({
    updateDsarStatus: (...args: unknown[]) => mockUpdateDsarStatus(...args),
    createDsarRequest: (...args: unknown[]) => mockCreateDsarRequest(...args),
    fetchDsarRequests: vi.fn().mockResolvedValue([]),
  }),
);

import { DsarRequestsTable } from "@/components/data-protection/dsar-requests-table";
import { LogDsarRequestDialog } from "@/components/data-protection/log-dsar-dialog";
import type { DsarRequest } from "@/lib/types/data-protection";

// --- Test data helpers ---
const today = new Date().toLocaleDateString("en-CA"); // "YYYY-MM-DD"
const [y, m, d] = today.split("-").map(Number);
const pastDate = `${y - 1}-${String(m).padStart(2, "0")}-${String(d).padStart(2, "0")}`;
const soonDate = new Date(Date.now() + 3 * 24 * 60 * 60 * 1000)
  .toLocaleDateString("en-CA");
const futureDate = `${y + 1}-${String(m).padStart(2, "0")}-${String(d).padStart(2, "0")}`;

const baseRequest: DsarRequest = {
  id: "req-1",
  customerId: "cust-1",
  customerName: "Acme Corp",
  requestType: "ACCESS",
  status: "RECEIVED",
  subjectName: "Jane Doe",
  subjectEmail: "jane@example.com",
  description: "Please provide all my data.",
  resolutionNotes: null,
  deadline: futureDate,
  deadlineStatus: "ON_TRACK",
  requestedAt: "2026-03-01T10:00:00Z",
  requestedBy: "member-1",
  completedAt: null,
  jurisdiction: "ZA",
  notes: null,
  createdAt: "2026-03-01T10:00:00Z",
};

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("DsarRequestsTable", () => {
  it("renders DSAR table with deadline badge for on-track request", () => {
    const requests: DsarRequest[] = [baseRequest];
    render(<DsarRequestsTable requests={requests} slug="acme" />);
    expect(screen.getByText("Jane Doe")).toBeInTheDocument();
    expect(screen.getByText("Access")).toBeInTheDocument();
    // "Received" appears as both column header and status badge — check badge via data-variant
    const receivedBadge = screen
      .getAllByText("Received")
      .find((el) => el.getAttribute("data-variant") === "neutral");
    expect(receivedBadge).toBeDefined();
  });

  it("shows destructive badge for OVERDUE requests", () => {
    const overdueRequest: DsarRequest = {
      ...baseRequest,
      id: "req-overdue",
      status: "IN_PROGRESS",
      deadline: pastDate,
      deadlineStatus: "OVERDUE",
    };
    render(<DsarRequestsTable requests={[overdueRequest]} slug="acme" />);
    // "Overdue" appears in both the DeadlineCell span (text-red-600) and the Badge
    // Find the DeadlineCell span by its class
    const overdueElements = screen.getAllByText("Overdue");
    const overdueSpan = overdueElements.find((el) =>
      el.classList.contains("text-red-600"),
    );
    expect(overdueSpan).toBeDefined();
    // Also verify the destructive badge exists
    const overdueBadge = overdueElements.find(
      (el) => el.getAttribute("data-variant") === "destructive",
    );
    expect(overdueBadge).toBeDefined();
  });

  it("shows warning badge for DUE_SOON requests", () => {
    const dueSoonRequest: DsarRequest = {
      ...baseRequest,
      id: "req-soon",
      status: "IN_PROGRESS",
      deadline: soonDate,
      deadlineStatus: "DUE_SOON",
    };
    render(<DsarRequestsTable requests={[dueSoonRequest]} slug="acme" />);
    // DUE_SOON badge rendered
    const dueSoonBadge = screen.getByText("Due Soon");
    expect(dueSoonBadge).toBeInTheDocument();
    expect(dueSoonBadge).toHaveAttribute("data-variant", "warning");
  });

  it("calls updateDsarStatus with START_PROCESSING when Mark Processing is clicked", async () => {
    const user = userEvent.setup();
    mockUpdateDsarStatus.mockResolvedValue({ success: true });
    render(<DsarRequestsTable requests={[baseRequest]} slug="acme" />);
    await user.click(
      screen.getByRole("button", { name: /mark as processing/i }),
    );
    await waitFor(() => {
      expect(mockUpdateDsarStatus).toHaveBeenCalledWith(
        "acme",
        "req-1",
        "START_PROCESSING",
      );
    });
  });

  it("calls updateDsarStatus COMPLETE with resolution notes on confirm", async () => {
    const user = userEvent.setup();
    mockUpdateDsarStatus.mockResolvedValue({ success: true });
    const inProgressRequest: DsarRequest = {
      ...baseRequest,
      id: "req-ip",
      status: "IN_PROGRESS",
    };
    render(
      <DsarRequestsTable requests={[inProgressRequest]} slug="acme" />,
    );

    // Click Complete button — opens resolution dialog
    await user.click(
      screen.getByRole("button", { name: /complete request/i }),
    );
    expect(screen.getByText("Complete Request")).toBeInTheDocument();

    // Fill in resolution notes
    const textarea = screen.getByPlaceholderText(/resolution notes/i);
    await user.type(textarea, "Request fulfilled successfully.");
    await user.click(screen.getByRole("button", { name: "Confirm" }));

    await waitFor(() => {
      expect(mockUpdateDsarStatus).toHaveBeenCalledWith(
        "acme",
        "req-ip",
        "COMPLETE",
        "Request fulfilled successfully.",
      );
    });
  });
});

describe("LogDsarRequestDialog", () => {
  it("submits form with correct data when filled and submitted", async () => {
    const user = userEvent.setup();
    mockCreateDsarRequest.mockResolvedValue({ success: true });

    render(<LogDsarRequestDialog slug="acme" />);

    // Open dialog
    await user.click(
      screen.getByRole("button", { name: /log new request/i }),
    );
    expect(screen.getByText("Log DSAR Request")).toBeInTheDocument();

    // Fill form
    await user.clear(
      screen.getByPlaceholderText(/full name of the data subject/i),
    );
    await user.type(
      screen.getByPlaceholderText(/full name of the data subject/i),
      "John Smith",
    );

    await user.clear(screen.getByPlaceholderText(/subject@example.com/i));
    await user.type(
      screen.getByPlaceholderText(/subject@example.com/i),
      "john@example.com",
    );

    // Select request type — change to DELETION
    await user.selectOptions(screen.getByRole("combobox"), "Deletion");

    // Enter customer ID
    await user.clear(screen.getByPlaceholderText(/customer uuid/i));
    await user.type(
      screen.getByPlaceholderText(/customer uuid/i),
      "550e8400-e29b-41d4-a716-446655440000",
    );

    // Submit
    await user.click(screen.getByRole("button", { name: /log request/i }));

    await waitFor(() => {
      expect(mockCreateDsarRequest).toHaveBeenCalledWith(
        "acme",
        expect.objectContaining({
          customerId: "550e8400-e29b-41d4-a716-446655440000",
          requestType: "DELETION",
        }),
      );
    });
  });
});
