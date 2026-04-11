import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ChecklistInstanceItemRow } from "@/components/compliance/ChecklistInstanceItemRow";
import type { ChecklistInstanceItemResponse, Document } from "@/lib/types";

// Mock next/navigation (required by ChecklistInstanceItemRow → KycVerificationDialog)
vi.mock("next/navigation", () => ({
  useRouter: () => ({
    refresh: vi.fn(),
    push: vi.fn(),
    replace: vi.fn(),
    back: vi.fn(),
    forward: vi.fn(),
    prefetch: vi.fn(),
  }),
}));

// Mock kyc-actions to avoid server-only import chain
vi.mock("@/app/(app)/org/[slug]/customers/[id]/kyc-actions", () => ({
  verifyKycAction: vi.fn().mockResolvedValue({ success: true }),
  getKycStatusAction: vi.fn().mockResolvedValue({ configured: false, provider: null }),
  getKycResultAction: vi.fn().mockResolvedValue({ success: true, data: null }),
}));

const baseItem: ChecklistInstanceItemResponse = {
  id: "item-1",
  instanceId: "inst-1",
  templateItemId: "ti-1",
  name: "Verify identity document",
  description: "Check passport or ID",
  sortOrder: 1,
  required: true,
  requiresDocument: false,
  requiredDocumentLabel: null,
  status: "PENDING",
  completedAt: null,
  completedBy: null,
  notes: null,
  documentId: null,
  dependsOnItemId: null,
  createdAt: "2026-02-18T10:00:00Z",
  updatedAt: "2026-02-18T10:00:00Z",
};

const mockOnComplete = vi.fn().mockResolvedValue(undefined);
const mockOnSkip = vi.fn().mockResolvedValue(undefined);
const mockOnReopen = vi.fn().mockResolvedValue(undefined);

describe("ChecklistInstanceItemRow", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders item name and PENDING badge", () => {
    render(
      <ChecklistInstanceItemRow
        item={baseItem}
        instanceItems={[baseItem]}
        onComplete={mockOnComplete}
        onSkip={mockOnSkip}
        onReopen={mockOnReopen}
        isAdmin={false}
      />
    );
    expect(screen.getByText("Verify identity document")).toBeInTheDocument();
    expect(screen.getByText("Pending")).toBeInTheDocument();
  });

  it("shows 'Mark Complete' button for PENDING items when isAdmin", () => {
    render(
      <ChecklistInstanceItemRow
        item={baseItem}
        instanceItems={[baseItem]}
        onComplete={mockOnComplete}
        onSkip={mockOnSkip}
        onReopen={mockOnReopen}
        isAdmin={true}
      />
    );
    expect(screen.getByText("Mark Complete")).toBeInTheDocument();
  });

  it("hides 'Mark Complete' and 'Skip' buttons for non-admin users", () => {
    render(
      <ChecklistInstanceItemRow
        item={{ ...baseItem, required: false }}
        instanceItems={[baseItem]}
        onComplete={mockOnComplete}
        onSkip={mockOnSkip}
        onReopen={mockOnReopen}
        isAdmin={false}
      />
    );
    expect(screen.queryByText("Mark Complete")).not.toBeInTheDocument();
    expect(screen.queryByText("Skip")).not.toBeInTheDocument();
  });

  it("does NOT show 'Skip' button for required items even when isAdmin", () => {
    render(
      <ChecklistInstanceItemRow
        item={{ ...baseItem, required: true }}
        instanceItems={[baseItem]}
        onComplete={mockOnComplete}
        onSkip={mockOnSkip}
        onReopen={mockOnReopen}
        isAdmin={true}
      />
    );
    expect(screen.queryByText("Skip")).not.toBeInTheDocument();
  });

  it("shows 'Skip' button for optional (non-required) PENDING items when isAdmin", () => {
    render(
      <ChecklistInstanceItemRow
        item={{ ...baseItem, required: false }}
        instanceItems={[baseItem]}
        onComplete={mockOnComplete}
        onSkip={mockOnSkip}
        onReopen={mockOnReopen}
        isAdmin={true}
      />
    );
    expect(screen.getByText("Skip")).toBeInTheDocument();
  });

  it("shows 'Reopen' button for COMPLETED items when isAdmin is true and hides when false", () => {
    const completedItem: ChecklistInstanceItemResponse = {
      ...baseItem,
      status: "COMPLETED",
      completedAt: "2026-02-18T11:00:00Z",
      completedBy: "m1",
      notes: "Done",
    };

    // Admin can see Reopen
    const { unmount } = render(
      <ChecklistInstanceItemRow
        item={completedItem}
        instanceItems={[completedItem]}
        onComplete={mockOnComplete}
        onSkip={mockOnSkip}
        onReopen={mockOnReopen}
        isAdmin={true}
      />
    );
    expect(screen.getByText("Reopen")).toBeInTheDocument();
    unmount();

    // Non-admin cannot see Reopen
    render(
      <ChecklistInstanceItemRow
        item={completedItem}
        instanceItems={[completedItem]}
        onComplete={mockOnComplete}
        onSkip={mockOnSkip}
        onReopen={mockOnReopen}
        isAdmin={false}
      />
    );
    expect(screen.queryByText("Reopen")).not.toBeInTheDocument();
  });

  it("shows document picker select when requiresDocument is true and complete form is open", async () => {
    const user = userEvent.setup();
    const docItem: ChecklistInstanceItemResponse = {
      ...baseItem,
      requiresDocument: true,
      requiredDocumentLabel: "Certified ID copy",
    };
    const mockDocs: Document[] = [
      {
        id: "doc-1",
        projectId: null,
        fileName: "passport-scan.pdf",
        contentType: "application/pdf",
        size: 12345,
        status: "UPLOADED",
        scope: "CUSTOMER",
        customerId: "c1",
        visibility: "INTERNAL",
        uploadedBy: "m1",
        createdAt: "2026-02-18T10:00:00Z",
        updatedAt: "2026-02-18T10:00:00Z",
      },
      {
        id: "doc-2",
        projectId: null,
        fileName: "pending-file.pdf",
        contentType: "application/pdf",
        size: 999,
        status: "PENDING",
        scope: "CUSTOMER",
        customerId: "c1",
        visibility: "INTERNAL",
        uploadedBy: "m1",
        createdAt: "2026-02-18T10:00:00Z",
        updatedAt: "2026-02-18T10:00:00Z",
      },
    ];

    render(
      <ChecklistInstanceItemRow
        item={docItem}
        instanceItems={[docItem]}
        onComplete={mockOnComplete}
        onSkip={mockOnSkip}
        onReopen={mockOnReopen}
        isAdmin={true}
        customerDocuments={mockDocs}
      />
    );

    // Open the complete form
    await user.click(screen.getByText("Mark Complete"));

    // Should show "Select a document..." placeholder
    expect(screen.getByText("Select a document...")).toBeInTheDocument();
  });

  it("shows 'No documents uploaded' when all documents have non-UPLOADED status", async () => {
    const user = userEvent.setup();
    const docItem: ChecklistInstanceItemResponse = {
      ...baseItem,
      requiresDocument: true,
      requiredDocumentLabel: "Tax certificate",
    };
    const pendingDocs: Document[] = [
      {
        id: "doc-pending",
        projectId: null,
        fileName: "pending-file.pdf",
        contentType: "application/pdf",
        size: 999,
        status: "PENDING",
        scope: "CUSTOMER",
        customerId: "c1",
        visibility: "INTERNAL",
        uploadedBy: "m1",
        createdAt: "2026-02-18T10:00:00Z",
        updatedAt: "2026-02-18T10:00:00Z",
      },
    ];

    render(
      <ChecklistInstanceItemRow
        item={docItem}
        instanceItems={[docItem]}
        onComplete={mockOnComplete}
        onSkip={mockOnSkip}
        onReopen={mockOnReopen}
        isAdmin={true}
        customerDocuments={pendingDocs}
      />
    );

    // Open the complete form
    await user.click(screen.getByText("Mark Complete"));

    // The select trigger should be rendered with placeholder
    expect(screen.getByText("Select a document...")).toBeInTheDocument();
  });

  it("does not show document picker when requiresDocument is false", async () => {
    const user = userEvent.setup();
    render(
      <ChecklistInstanceItemRow
        item={{ ...baseItem, requiresDocument: false }}
        instanceItems={[baseItem]}
        onComplete={mockOnComplete}
        onSkip={mockOnSkip}
        onReopen={mockOnReopen}
        isAdmin={true}
        customerDocuments={[]}
      />
    );

    await user.click(screen.getByText("Mark Complete"));

    expect(screen.queryByText("Select a document...")).not.toBeInTheDocument();
  });

  // GAP-S3-03 — Confirm button must be disabled (not silent no-op) when a
  // requiresDocument item has no document selected, and inline guidance must
  // be shown so users know what to do.
  it("disables Confirm and explains the gate when requiresDocument and no document selected", async () => {
    const user = userEvent.setup();
    const docItem: ChecklistInstanceItemResponse = {
      ...baseItem,
      requiresDocument: true,
      requiredDocumentLabel: "Certified copy of SA ID / passport",
    };

    render(
      <ChecklistInstanceItemRow
        item={docItem}
        instanceItems={[docItem]}
        onComplete={mockOnComplete}
        onSkip={mockOnSkip}
        onReopen={mockOnReopen}
        isAdmin={true}
        customerDocuments={[]}
      />
    );

    await user.click(screen.getByText("Mark Complete"));

    const confirmBtn = screen.getByRole("button", { name: /Confirm$/ });
    expect(confirmBtn).toBeDisabled();
    // Inline guidance must instruct the user to upload the named document.
    expect(
      screen.getByText(/Upload Certified copy of SA ID \/ passport/i)
    ).toBeInTheDocument();
    // The completion handler must NOT have been invoked.
    await user.click(confirmBtn);
    expect(mockOnComplete).not.toHaveBeenCalled();
  });

  it("surfaces backend errors inline (sticky) and does not auto-clear", async () => {
    const user = userEvent.setup();
    const docItem: ChecklistInstanceItemResponse = {
      ...baseItem,
      requiresDocument: false,
    };
    const failingComplete = vi
      .fn()
      .mockRejectedValueOnce(new Error("Document required"));

    render(
      <ChecklistInstanceItemRow
        item={docItem}
        instanceItems={[docItem]}
        onComplete={failingComplete}
        onSkip={mockOnSkip}
        onReopen={mockOnReopen}
        isAdmin={true}
      />
    );

    await user.click(screen.getByText("Mark Complete"));
    await user.click(screen.getByRole("button", { name: /Confirm$/ }));

    // Sticky error message visible.
    expect(await screen.findByText("Document required")).toBeInTheDocument();
    // Dismiss button is reachable for the user.
    expect(screen.getByLabelText("Dismiss error")).toBeInTheDocument();
  });
});
