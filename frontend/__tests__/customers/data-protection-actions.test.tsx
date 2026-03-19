import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { DataExportDialog } from "@/components/customers/data-export-dialog";
import { AnonymizeCustomerDialog } from "@/components/customers/anonymize-customer-dialog";

// Mock server actions
const mockTriggerDataExport = vi.fn();
const mockFetchAnonymizationPreview = vi.fn();
const mockExecuteAnonymization = vi.fn();

vi.mock("@/app/(app)/org/[slug]/customers/[id]/data-protection-actions", () => ({
  triggerDataExport: (...args: unknown[]) => mockTriggerDataExport(...args),
  fetchAnonymizationPreview: (...args: unknown[]) => mockFetchAnonymizationPreview(...args),
  executeAnonymization: (...args: unknown[]) => mockExecuteAnonymization(...args),
}));

const mockRefresh = vi.fn();

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

describe("DataExportDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("shows confirm step initially", async () => {
    const user = userEvent.setup();

    render(
      <DataExportDialog customerId="c1">
        <button>Export Trigger</button>
      </DataExportDialog>,
    );

    await user.click(screen.getByText("Export Trigger"));

    expect(screen.getByText("Download All Data")).toBeInTheDocument();
    expect(screen.getByText("Export Now")).toBeInTheDocument();
    expect(screen.getByText("Cancel")).toBeInTheDocument();
  });

  it("shows download link after successful export", async () => {
    mockTriggerDataExport.mockResolvedValue({
      success: true,
      data: {
        exportId: "exp-1",
        status: "COMPLETED",
        downloadUrl: "https://s3.example.com/download",
        expiresAt: "2026-03-20T10:00:00Z",
        fileCount: 5,
        totalSizeBytes: 1048576,
      },
    });
    const user = userEvent.setup();

    render(
      <DataExportDialog customerId="c1">
        <button>Export Trigger</button>
      </DataExportDialog>,
    );

    await user.click(screen.getByText("Export Trigger"));
    await user.click(screen.getByText("Export Now"));

    await waitFor(() => {
      expect(screen.getByText("Export Ready")).toBeInTheDocument();
    });

    expect(screen.getByText("Download Archive")).toBeInTheDocument();
    expect(screen.getByText(/5 files/)).toBeInTheDocument();
    expect(screen.getByText(/Link expires in 24 hours/)).toBeInTheDocument();
  });

  it("shows error message when export fails", async () => {
    mockTriggerDataExport.mockResolvedValue({
      success: false,
      error: "Insufficient permissions",
    });
    const user = userEvent.setup();

    render(
      <DataExportDialog customerId="c1">
        <button>Export Trigger</button>
      </DataExportDialog>,
    );

    await user.click(screen.getByText("Export Trigger"));
    await user.click(screen.getByText("Export Now"));

    await waitFor(() => {
      expect(screen.getByText("Export Failed")).toBeInTheDocument();
    });

    expect(screen.getByText("Insufficient permissions")).toBeInTheDocument();
  });
});

describe("AnonymizeCustomerDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Default preview mock — will be used when SWR fetches
    mockFetchAnonymizationPreview.mockResolvedValue({
      success: true,
      data: {
        customerId: "c1",
        customerName: "Acme Corp",
        affectedEntities: {
          portalContacts: 2,
          projects: 3,
          documents: 12,
          timeEntries: 45,
          invoices: 8,
          comments: 23,
          customFieldValues: 6,
        },
        financialRecordsRetained: 8,
        financialRetentionExpiresAt: "2031-03-19",
      },
    });
  });

  afterEach(() => {
    cleanup();
  });

  it("confirm button disabled when input is empty", async () => {
    const user = userEvent.setup();

    render(
      <AnonymizeCustomerDialog slug="acme" customerId="c1" customerName="Acme Corp">
        <button>Anonymize Trigger</button>
      </AnonymizeCustomerDialog>,
    );

    await user.click(screen.getByText("Anonymize Trigger"));

    // Wait for preview to load, then move to confirm step
    await waitFor(() => {
      expect(screen.getByText("Continue")).toBeInTheDocument();
    });
    await user.click(screen.getByText("Continue"));

    const confirmButton = screen.getByRole("button", { name: /execute anonymization/i });
    expect(confirmButton).toBeDisabled();
  });

  it("confirm button disabled when input does not match customer name", async () => {
    const user = userEvent.setup();

    render(
      <AnonymizeCustomerDialog slug="acme" customerId="c1" customerName="Acme Corp">
        <button>Anonymize Trigger</button>
      </AnonymizeCustomerDialog>,
    );

    await user.click(screen.getByText("Anonymize Trigger"));

    await waitFor(() => {
      expect(screen.getByText("Continue")).toBeInTheDocument();
    });
    await user.click(screen.getByText("Continue"));

    const input = screen.getByPlaceholderText("Acme Corp");
    await user.type(input, "Wrong Name");

    const confirmButton = screen.getByRole("button", { name: /execute anonymization/i });
    expect(confirmButton).toBeDisabled();
  });

  it("executes anonymization successfully when name matches and reason is provided", async () => {
    mockExecuteAnonymization.mockResolvedValue({
      success: true,
      data: {
        status: "COMPLETED",
        referenceId: "REF-abc123",
        preExportKey: "org/tenant/customer-exports/uuid/pre-anonymization.zip",
        summary: {
          customerAnonymized: true,
          documentsDeleted: 12,
          commentsRedacted: 23,
          portalContactsAnonymized: 2,
          invoicesPreserved: 8,
          customFieldsCleared: 6,
        },
      },
    });
    const user = userEvent.setup();

    render(
      <AnonymizeCustomerDialog slug="acme" customerId="c1" customerName="Acme Corp">
        <button>Anonymize Trigger</button>
      </AnonymizeCustomerDialog>,
    );

    await user.click(screen.getByText("Anonymize Trigger"));

    // Wait for preview to load, then move to confirm step
    await waitFor(() => {
      expect(screen.getByText("Continue")).toBeInTheDocument();
    });
    await user.click(screen.getByText("Continue"));

    // Type the correct customer name
    const input = screen.getByPlaceholderText("Acme Corp");
    await user.type(input, "Acme Corp");

    // Button should now be enabled (reason has a default value)
    const confirmButton = screen.getByRole("button", { name: /execute anonymization/i });
    expect(confirmButton).not.toBeDisabled();

    // Click execute
    await user.click(confirmButton);

    // Verify success step is shown
    await waitFor(() => {
      expect(screen.getByText("Anonymization Complete")).toBeInTheDocument();
    });

    expect(screen.getByText(/Documents deleted: 12/)).toBeInTheDocument();
    expect(screen.getByText(/Comments redacted: 23/)).toBeInTheDocument();
    expect(screen.getByText(/Invoices preserved: 8/)).toBeInTheDocument();

    // Verify the server action was called with correct args (including default reason)
    expect(mockExecuteAnonymization).toHaveBeenCalledWith(
      "acme",
      "c1",
      "Acme Corp",
      "Data subject request",
    );
  });
});
