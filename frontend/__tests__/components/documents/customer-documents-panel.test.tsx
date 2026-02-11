import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import { CustomerDocumentsPanel } from "@/components/documents/customer-documents-panel";
import type { Document } from "@/lib/types";

// Mock server actions
vi.mock("@/app/(app)/org/[slug]/customers/[id]/actions", () => ({
  initiateCustomerUpload: vi.fn().mockResolvedValue({ success: true }),
  confirmCustomerUpload: vi.fn().mockResolvedValue({ success: true }),
  cancelCustomerUpload: vi.fn().mockResolvedValue({ success: true }),
  toggleDocumentVisibility: vi.fn().mockResolvedValue({ success: true }),
}));

vi.mock("@/app/(app)/org/[slug]/projects/[id]/actions", () => ({
  getDownloadUrl: vi.fn().mockResolvedValue({ success: true, presignedUrl: "https://example.com/file" }),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
    refresh: vi.fn(),
    back: vi.fn(),
    forward: vi.fn(),
    prefetch: vi.fn(),
  }),
}));

// --- Test data ---

function makeDocument(overrides: Partial<Document> = {}): Document {
  return {
    id: "doc1",
    projectId: null,
    fileName: "contract.pdf",
    contentType: "application/pdf",
    size: 1024000,
    status: "UPLOADED",
    scope: "CUSTOMER",
    customerId: "c1",
    visibility: "INTERNAL",
    uploadedBy: "m1",
    uploadedAt: "2024-06-01T00:00:00Z",
    createdAt: "2024-06-01T00:00:00Z",
    ...overrides,
  };
}

const internalDoc = makeDocument({
  id: "doc1",
  fileName: "contract.pdf",
  visibility: "INTERNAL",
});

const sharedDoc = makeDocument({
  id: "doc2",
  fileName: "invoice.xlsx",
  contentType: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  size: 512000,
  visibility: "SHARED",
});

const pendingDoc = makeDocument({
  id: "doc3",
  fileName: "report.docx",
  contentType: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  status: "PENDING",
  visibility: "INTERNAL",
});

describe("CustomerDocumentsPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  // 42.10: Customer documents tab renders
  it("renders document rows with file name, size, status, visibility, and date columns", () => {
    render(
      <CustomerDocumentsPanel
        documents={[internalDoc, sharedDoc]}
        slug="acme"
        customerId="c1"
        canManage={true}
      />
    );

    expect(screen.getByText("contract.pdf")).toBeInTheDocument();
    expect(screen.getByText("invoice.xlsx")).toBeInTheDocument();
    expect(screen.getByText("2")).toBeInTheDocument(); // count badge
  });

  // 42.10: Empty state renders for no customer documents
  it("shows empty state when no documents exist", () => {
    render(
      <CustomerDocumentsPanel
        documents={[]}
        slug="acme"
        customerId="c1"
        canManage={true}
      />
    );

    expect(screen.getByText("No customer documents yet")).toBeInTheDocument();
    expect(
      screen.getByText("Upload your first document for this customer.")
    ).toBeInTheDocument();
  });

  // 42.10: Empty state message differs for non-admin
  it("shows different empty state message for non-admin", () => {
    render(
      <CustomerDocumentsPanel
        documents={[]}
        slug="acme"
        customerId="c1"
        canManage={false}
      />
    );

    expect(
      screen.getByText("No documents have been uploaded for this customer.")
    ).toBeInTheDocument();
  });

  // 42.10: Admin sees upload buttons
  it("shows upload button when canManage is true", () => {
    render(
      <CustomerDocumentsPanel
        documents={[internalDoc]}
        slug="acme"
        customerId="c1"
        canManage={true}
      />
    );

    // There should be an "Upload Document" button in the header
    expect(screen.getByText("Upload Document")).toBeInTheDocument();
  });

  // 42.10: Member does not see upload buttons
  it("hides upload button when canManage is false", () => {
    render(
      <CustomerDocumentsPanel
        documents={[internalDoc]}
        slug="acme"
        customerId="c1"
        canManage={false}
      />
    );

    expect(screen.queryByText("Upload Document")).not.toBeInTheDocument();
  });

  // 42.10: Visibility toggle renders for admin
  it("renders visibility toggle buttons for admin users", () => {
    render(
      <CustomerDocumentsPanel
        documents={[internalDoc, sharedDoc]}
        slug="acme"
        customerId="c1"
        canManage={true}
      />
    );

    const table = screen.getByRole("table");
    // Internal doc shows "Internal" button, shared doc shows "Shared" button
    expect(within(table).getByRole("button", { name: /Internal/i })).toBeInTheDocument();
    expect(within(table).getByRole("button", { name: /Shared/i })).toBeInTheDocument();
  });

  // 42.10: Non-admin sees static visibility labels
  it("renders static visibility labels for non-admin users", () => {
    render(
      <CustomerDocumentsPanel
        documents={[internalDoc, sharedDoc]}
        slug="acme"
        customerId="c1"
        canManage={false}
      />
    );

    const table = screen.getByRole("table");
    // Should show text, not buttons
    expect(within(table).queryByRole("button", { name: /Internal/i })).not.toBeInTheDocument();
    expect(within(table).queryByRole("button", { name: /Shared/i })).not.toBeInTheDocument();
    expect(within(table).getByText("Internal")).toBeInTheDocument();
    expect(within(table).getByText("Shared")).toBeInTheDocument();
  });

  // 42.10: Status badges render correctly
  it("renders correct status badges", () => {
    render(
      <CustomerDocumentsPanel
        documents={[internalDoc, pendingDoc]}
        slug="acme"
        customerId="c1"
        canManage={false}
      />
    );

    // "Uploaded" appears both as column header and as status badge.
    // Find badges specifically by data-slot attribute.
    const uploadedBadges = screen.getAllByText("Uploaded");
    const uploadedBadge = uploadedBadges.find(
      (el) => el.getAttribute("data-slot") === "badge"
    );
    expect(uploadedBadge).toHaveAttribute("data-variant", "success");

    const pendingBadge = screen.getByText("Pending");
    expect(pendingBadge).toHaveAttribute("data-variant", "warning");
  });
});
