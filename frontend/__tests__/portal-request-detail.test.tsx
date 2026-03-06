import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import PortalRequestDetailPage from "@/app/portal/(authenticated)/requests/[id]/page";
import type {
  PortalRequestDetail,
  PortalRequestItemDetail,
} from "@/lib/api/portal-requests";

// --- Mocks ---

const mockGetPortalRequest = vi.fn();
const mockInitiateUpload = vi.fn();
const mockSubmitItem = vi.fn();
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
  useParams: () => ({ id: "req-detail-1" }),
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
  getPortalRequest: (...args: unknown[]) => mockGetPortalRequest(...args),
  initiateUpload: (...args: unknown[]) => mockInitiateUpload(...args),
  submitItem: (...args: unknown[]) => mockSubmitItem(...args),
}));

// --- Test Data ---

function makeItem(
  overrides: Partial<PortalRequestItemDetail> & { id: string; name: string },
): PortalRequestItemDetail {
  return {
    description: null,
    responseType: "FILE_UPLOAD",
    required: false,
    fileTypeHints: null,
    sortOrder: 0,
    status: "PENDING",
    rejectionReason: null,
    documentId: null,
    textResponse: null,
    ...overrides,
  };
}

const sampleRequest: PortalRequestDetail = {
  id: "req-detail-1",
  requestNumber: "REQ-0042",
  status: "IN_PROGRESS",
  projectId: "proj-1",
  projectName: "Annual Audit",
  totalItems: 4,
  submittedItems: 1,
  acceptedItems: 1,
  rejectedItems: 1,
  sentAt: "2026-03-01T10:00:00Z",
  completedAt: null,
  items: [
    makeItem({
      id: "item-1",
      name: "Tax Certificate",
      description: "Upload your latest tax certificate",
      responseType: "FILE_UPLOAD",
      sortOrder: 1,
      status: "PENDING",
    }),
    makeItem({
      id: "item-2",
      name: "Company Description",
      responseType: "TEXT_RESPONSE",
      sortOrder: 2,
      status: "PENDING",
    }),
    makeItem({
      id: "item-3",
      name: "ID Document",
      responseType: "FILE_UPLOAD",
      sortOrder: 3,
      status: "ACCEPTED",
      documentId: "doc-123",
    }),
    makeItem({
      id: "item-4",
      name: "Bank Statement",
      description: "Last 3 months",
      responseType: "FILE_UPLOAD",
      sortOrder: 4,
      status: "REJECTED",
      rejectionReason: "Document is expired, please upload a recent one",
    }),
  ],
};

describe("Portal Request Detail", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders detail page with item names after loading", async () => {
    mockGetPortalRequest.mockResolvedValue(sampleRequest);

    render(<PortalRequestDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("REQ-0042")).toBeInTheDocument();
    });

    expect(screen.getByText("Tax Certificate")).toBeInTheDocument();
    expect(screen.getByText("Company Description")).toBeInTheDocument();
    expect(screen.getByText("ID Document")).toBeInTheDocument();
    expect(screen.getByText("Bank Statement")).toBeInTheDocument();
    expect(screen.getByText("Annual Audit")).toBeInTheDocument();
  });

  it("shows dropzone for FILE_UPLOAD item in PENDING state", async () => {
    mockGetPortalRequest.mockResolvedValue(sampleRequest);

    render(<PortalRequestDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("Tax Certificate")).toBeInTheDocument();
    });

    // Multiple dropzones may exist (PENDING + REJECTED items)
    const dropzones = screen.getAllByText("Drop a file here, or click to browse");
    expect(dropzones.length).toBeGreaterThanOrEqual(1);
  });

  it("shows textarea for TEXT_RESPONSE item in PENDING state", async () => {
    mockGetPortalRequest.mockResolvedValue(sampleRequest);

    render(<PortalRequestDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("Company Description")).toBeInTheDocument();
    });

    expect(
      screen.getByPlaceholderText("Enter your response..."),
    ).toBeInTheDocument();
    expect(screen.getByText("Submit Response")).toBeInTheDocument();
  });

  it("shows accepted item with green check indicator", async () => {
    mockGetPortalRequest.mockResolvedValue(sampleRequest);

    render(<PortalRequestDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("ID Document")).toBeInTheDocument();
    });

    // The accepted item should show "Accepted" text in the status area
    const acceptedTexts = screen.getAllByText("Accepted");
    expect(acceptedTexts.length).toBeGreaterThanOrEqual(1);
  });

  it("shows rejection reason and re-submit controls for REJECTED item", async () => {
    mockGetPortalRequest.mockResolvedValue(sampleRequest);

    render(<PortalRequestDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("Bank Statement")).toBeInTheDocument();
    });

    expect(
      screen.getByText(/Document is expired, please upload a recent one/),
    ).toBeInTheDocument();

    // Rejected FILE_UPLOAD items should still show the dropzone for re-upload
    // There should be at least 2 dropzones (PENDING item-1 and REJECTED item-4)
    const dropzones = screen.getAllByText("Drop a file here, or click to browse");
    expect(dropzones.length).toBe(2);
  });

  it("displays progress summary with correct counts", async () => {
    mockGetPortalRequest.mockResolvedValue(sampleRequest);

    render(<PortalRequestDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("REQ-0042")).toBeInTheDocument();
    });

    expect(screen.getByText("1/4 accepted")).toBeInTheDocument();
    expect(screen.getByText("1 submitted")).toBeInTheDocument();
    expect(screen.getByText("1 accepted")).toBeInTheDocument();
    expect(screen.getByText("1 rejected")).toBeInTheDocument();
  });

  it("submits text response when Submit Response is clicked", async () => {
    mockGetPortalRequest.mockResolvedValue(sampleRequest);
    mockSubmitItem.mockResolvedValue(undefined);

    const user = userEvent.setup();
    render(<PortalRequestDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("Company Description")).toBeInTheDocument();
    });

    const textarea = screen.getByPlaceholderText("Enter your response...");
    await user.type(textarea, "Our company builds software.");

    // After fetchRequest is called again post-submit, return the same data
    mockGetPortalRequest.mockResolvedValue(sampleRequest);

    const submitButton = screen.getByText("Submit Response");
    await user.click(submitButton);

    await waitFor(() => {
      expect(mockSubmitItem).toHaveBeenCalledWith("req-detail-1", "item-2", {
        textResponse: "Our company builds software.",
      });
    });
  });

  it("shows validation error when an invalid file is dropped", async () => {
    // Use a request with only a FILE_UPLOAD PENDING item
    const requestWithFileItem: PortalRequestDetail = {
      ...sampleRequest,
      items: [
        makeItem({
          id: "item-1",
          name: "Tax Certificate",
          responseType: "FILE_UPLOAD",
          sortOrder: 1,
          status: "PENDING",
        }),
      ],
    };
    mockGetPortalRequest.mockResolvedValue(requestWithFileItem);

    render(<PortalRequestDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("Tax Certificate")).toBeInTheDocument();
    });

    // Create a file with an unsupported type (e.g. .exe)
    const badFile = new File(["content"], "malware.exe", {
      type: "application/x-msdownload",
    });

    // Find the dropzone and simulate a drop
    const dropzone = screen.getByText("Drop a file here, or click to browse")
      .closest("[role='button']") as HTMLElement;

    const dataTransfer = {
      files: [badFile],
      items: [{ kind: "file", type: badFile.type, getAsFile: () => badFile }],
      types: ["Files"],
    };

    // Fire drop event
    const dropEvent = new Event("drop", { bubbles: true });
    Object.assign(dropEvent, {
      dataTransfer,
      preventDefault: vi.fn(),
      stopPropagation: vi.fn(),
    });
    dropzone.dispatchEvent(dropEvent);

    // The validation error should appear
    await waitFor(() => {
      expect(screen.getByText(/not supported/)).toBeInTheDocument();
    });

    // Should NOT have called initiateUpload
    expect(mockInitiateUpload).not.toHaveBeenCalled();
  });
});
