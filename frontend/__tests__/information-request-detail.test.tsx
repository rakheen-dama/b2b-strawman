import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

vi.mock("server-only", () => ({}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: vi.fn(),
    refresh: mockRefresh,
  }),
  useSearchParams: () => new URLSearchParams(),
}));

const mockRefresh = vi.fn();
const mockAcceptItem = vi.fn();
const mockRejectItem = vi.fn();
const mockCancelRequest = vi.fn();
const mockResendNotification = vi.fn();

vi.mock("@/app/(app)/org/[slug]/information-requests/[id]/actions", () => ({
  getRequestAction: vi.fn(),
  acceptItemAction: (...args: unknown[]) => mockAcceptItem(...args),
  rejectItemAction: (...args: unknown[]) => mockRejectItem(...args),
  cancelRequestAction: (...args: unknown[]) => mockCancelRequest(...args),
  resendNotificationAction: (...args: unknown[]) => mockResendNotification(...args),
}));

vi.mock("sonner", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}));

import type {
  InformationRequestResponse,
  InformationRequestItemResponse,
} from "@/lib/api/information-requests";
import { RequestDetailClient } from "@/components/information-requests/request-detail-client";

function makeItem(
  overrides: Partial<InformationRequestItemResponse> = {}
): InformationRequestItemResponse {
  return {
    id: "item-1",
    name: "Tax Certificate",
    description: "Upload your latest tax certificate",
    responseType: "FILE_UPLOAD",
    required: true,
    fileTypeHints: ".pdf,.jpg",
    sortOrder: 0,
    status: "PENDING",
    documentId: null,
    documentFileName: null,
    textResponse: null,
    rejectionReason: null,
    submittedAt: null,
    reviewedAt: null,
    ...overrides,
  };
}

function makeRequest(
  overrides: Partial<InformationRequestResponse> = {}
): InformationRequestResponse {
  return {
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
    totalItems: 3,
    submittedItems: 1,
    acceptedItems: 0,
    rejectedItems: 0,
    items: [makeItem()],
    createdAt: "2026-03-01T09:00:00Z",
    ...overrides,
  };
}

describe("Information Request Detail", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  // Test 1: Detail page renders header info
  it("renders header with request number, customer name, and contact", () => {
    const request = makeRequest();
    render(<RequestDetailClient request={request} slug="test-org" />);

    expect(screen.getByText("REQ-0001")).toBeInTheDocument();
    expect(screen.getByText("Acme Corp")).toBeInTheDocument();
    expect(screen.getByText("John Smith")).toBeInTheDocument();
    expect(screen.getByText("john@acme.com")).toBeInTheDocument();
    expect(screen.getByText("In Progress")).toBeInTheDocument();
    expect(screen.getByText("Annual Audit")).toBeInTheDocument();
  });

  // Test 2: Item list displays items sorted by sortOrder
  it("displays items sorted by sortOrder", () => {
    const items = [
      makeItem({ id: "item-2", name: "Bank Statement", sortOrder: 2 }),
      makeItem({ id: "item-1", name: "Tax Certificate", sortOrder: 0 }),
      makeItem({ id: "item-3", name: "ID Document", sortOrder: 1 }),
    ];
    const request = makeRequest({ items, totalItems: 3 });
    render(<RequestDetailClient request={request} slug="test-org" />);

    const itemNames = screen.getAllByText(/Tax Certificate|ID Document|Bank Statement/);
    expect(itemNames[0]).toHaveTextContent("Tax Certificate");
    expect(itemNames[1]).toHaveTextContent("ID Document");
    expect(itemNames[2]).toHaveTextContent("Bank Statement");
  });

  // Test 3: Accept button shown on SUBMITTED item
  it("shows Accept button on SUBMITTED items", () => {
    const request = makeRequest({
      items: [makeItem({ status: "SUBMITTED" })],
    });
    render(<RequestDetailClient request={request} slug="test-org" />);

    expect(screen.getByText("Accept")).toBeInTheDocument();
    expect(screen.getByText("Reject")).toBeInTheDocument();
  });

  // Test 4: Accept button calls action and refreshes
  it("calls acceptItemAction when Accept is clicked", async () => {
    const user = userEvent.setup();
    mockAcceptItem.mockResolvedValue({ success: true });
    const request = makeRequest({
      items: [makeItem({ status: "SUBMITTED" })],
    });
    render(<RequestDetailClient request={request} slug="test-org" />);

    await user.click(screen.getByText("Accept"));

    expect(mockAcceptItem).toHaveBeenCalledWith("test-org", "req-1", "item-1");
    expect(mockRefresh).toHaveBeenCalled();
  });

  // Test 5: Reject button opens RejectItemDialog
  it("opens reject dialog when Reject is clicked", async () => {
    const user = userEvent.setup();
    const request = makeRequest({
      items: [makeItem({ status: "SUBMITTED", name: "Tax Certificate" })],
    });
    render(<RequestDetailClient request={request} slug="test-org" />);

    await user.click(screen.getByText("Reject"));

    expect(screen.getByText("Reject Item")).toBeInTheDocument();
    expect(screen.getByText(/Provide a reason for rejecting/)).toBeInTheDocument();
  });

  // Test 6: Reject sends reason via action
  it("sends rejection reason via rejectItemAction", async () => {
    const user = userEvent.setup();
    mockRejectItem.mockResolvedValue({ success: true });
    const request = makeRequest({
      items: [makeItem({ status: "SUBMITTED" })],
    });
    render(<RequestDetailClient request={request} slug="test-org" />);

    await user.click(screen.getByText("Reject"));

    const textarea = screen.getByTestId("reject-reason-input");
    await user.type(textarea, "Document is blurry");

    // Click the Reject button inside the dialog (not the row button)
    const dialog = screen.getByRole("alertdialog");
    const rejectButton = within(dialog).getByRole("button", {
      name: "Reject",
    });
    await user.click(rejectButton);

    expect(mockRejectItem).toHaveBeenCalledWith(
      "test-org",
      "req-1",
      "item-1",
      "Document is blurry"
    );
  });

  // Test 7: Accepted item shows document filename
  it("shows document filename on accepted FILE_UPLOAD item", () => {
    const request = makeRequest({
      items: [
        makeItem({
          status: "ACCEPTED",
          responseType: "FILE_UPLOAD",
          documentId: "doc-1",
          documentFileName: "tax-cert-2026.pdf",
        }),
      ],
    });
    render(<RequestDetailClient request={request} slug="test-org" />);

    expect(screen.getByText("tax-cert-2026.pdf")).toBeInTheDocument();
  });

  // Test 8: Rejected item shows rejection reason
  it("shows rejection reason on rejected items", () => {
    const request = makeRequest({
      items: [
        makeItem({
          status: "REJECTED",
          rejectionReason: "Document is expired",
        }),
      ],
    });
    render(<RequestDetailClient request={request} slug="test-org" />);

    expect(screen.getByText("Reason: Document is expired")).toBeInTheDocument();
  });
});
