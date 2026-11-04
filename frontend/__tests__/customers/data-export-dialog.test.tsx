import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { DataExportDialog } from "@/components/customers/data-export-dialog";
import type { StandaloneExportResult } from "@/lib/types/data-protection";

const mockTriggerDataExport = vi.fn();

vi.mock("@/app/(app)/org/[slug]/clients/[id]/data-protection-actions", () => ({
  triggerDataExport: (...args: unknown[]) => mockTriggerDataExport(...args),
  fetchAnonymizationPreview: vi.fn(),
  executeAnonymization: vi.fn(),
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

// Shaped exactly like the 200 body of POST /api/clients/{id}/data-export
// (datarequest/ExportStatusResponse, ADR-365).
const EXPORT_200 = {
  exportId: "exp-1",
  status: "COMPLETED",
  downloadUrl: "https://s3.example.com/download",
  expiresAt: "2026-03-20T10:00:00Z",
  fileCount: 5,
  totalSizeBytes: 1048576,
  s3Key: "org/tenant_x/exports/compliance-c1-exp-1-1.zip",
} satisfies StandaloneExportResult;

function renderDialog() {
  return render(
    <DataExportDialog customerId="c1">
      <button>Open Export Dialog</button>
    </DataExportDialog>
  );
}

describe("DataExportDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("shows confirm step initially", async () => {
    const user = userEvent.setup();
    renderDialog();

    await user.click(screen.getByText("Open Export Dialog"));

    expect(screen.getByText("Download All Data")).toBeInTheDocument();
    expect(screen.getByText("Export Now")).toBeInTheDocument();
    expect(screen.getByText("Cancel")).toBeInTheDocument();
  });

  it("renders 'N files · size' and a live Download Archive link from the 200 body (G88-F43)", async () => {
    mockTriggerDataExport.mockResolvedValue({ success: true, data: EXPORT_200 });
    const user = userEvent.setup();
    renderDialog();

    await user.click(screen.getByText("Open Export Dialog"));
    await user.click(screen.getByText("Export Now"));

    await waitFor(() => {
      expect(screen.getByText("Export Ready")).toBeInTheDocument();
    });

    expect(screen.getByText(/5 files · 1 MB/)).toBeInTheDocument();
    expect(screen.queryByText(/NaN|undefined/)).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Download Archive/ })).toHaveAttribute(
      "href",
      "https://s3.example.com/download"
    );
    expect(screen.getByText(/Link expires in 24 hours/)).toBeInTheDocument();
  });

  it("shows error message when export fails", async () => {
    mockTriggerDataExport.mockResolvedValue({
      success: false,
      error: "Insufficient permissions",
    });
    const user = userEvent.setup();
    renderDialog();

    await user.click(screen.getByText("Open Export Dialog"));
    await user.click(screen.getByText("Export Now"));

    await waitFor(() => {
      expect(screen.getByText("Export Failed")).toBeInTheDocument();
    });

    expect(screen.getByText("Insufficient permissions")).toBeInTheDocument();
  });
});
