import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import PortalDocumentsPage from "@/app/portal/(authenticated)/documents/page";
import type { PortalDocument } from "@/lib/types";

// --- Mocks ---

const mockGet = vi.fn();
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
    get: (...args: unknown[]) => mockGet(...args),
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

const sampleDocuments: PortalDocument[] = [
  {
    id: "d1",
    fileName: "report.pdf",
    contentType: "application/pdf",
    size: 1024000,
    scope: "PROJECT",
    projectId: "p1",
    projectName: "Website Redesign",
    uploadedAt: "2024-06-01T00:00:00Z",
    createdAt: "2024-06-01T00:00:00Z",
  },
  {
    id: "d2",
    fileName: "brand-guide.png",
    contentType: "image/png",
    size: 512000,
    scope: "ORG",
    projectId: null,
    projectName: null,
    uploadedAt: "2024-05-15T00:00:00Z",
    createdAt: "2024-05-15T00:00:00Z",
  },
];

describe("PortalDocumentsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders document list with file names and sizes", async () => {
    mockGet.mockResolvedValue(sampleDocuments);

    render(<PortalDocumentsPage />);

    await waitFor(() => {
      expect(screen.getByText("report.pdf")).toBeInTheDocument();
    });

    expect(screen.getByText("brand-guide.png")).toBeInTheDocument();
  });

  it("renders download buttons for each document", async () => {
    mockGet.mockResolvedValue(sampleDocuments);

    render(<PortalDocumentsPage />);

    await waitFor(() => {
      expect(screen.getByText("report.pdf")).toBeInTheDocument();
    });

    const downloadButtons = screen.getAllByRole("button", { name: /Download/i });
    expect(downloadButtons).toHaveLength(2);
  });

  it("triggers presigned download on button click", async () => {
    // Use mockImplementation to handle multiple calls correctly:
    // First call fetches the document list, subsequent calls return presigned URL
    mockGet.mockImplementation((endpoint: string) => {
      if (endpoint === "/portal/documents") {
        return Promise.resolve(sampleDocuments);
      }
      if (endpoint.includes("/presign-download")) {
        return Promise.resolve({
          presignedUrl: "https://s3.example.com/download?signed=abc",
          expiresInSeconds: 900,
        });
      }
      return Promise.resolve([]);
    });

    const user = userEvent.setup();
    render(<PortalDocumentsPage />);

    await waitFor(() => {
      expect(screen.getByText("report.pdf")).toBeInTheDocument();
    });

    const downloadButtons = screen.getAllByRole("button", { name: /Download/i });
    await user.click(downloadButtons[0]);

    await waitFor(() => {
      expect(mockGet).toHaveBeenCalledWith("/portal/documents/d1/presign-download");
    });
  });

  it("renders empty state when no documents", async () => {
    mockGet.mockResolvedValue([]);

    render(<PortalDocumentsPage />);

    await waitFor(() => {
      expect(screen.getByText("No documents")).toBeInTheDocument();
    });
  });
});
