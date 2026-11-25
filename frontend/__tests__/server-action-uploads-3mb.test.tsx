import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// FR-3: every file sent through a server action is refused in the browser above 3 MB (the shared
// server-action body limit), with the readable message — DOCX template upload and replace, and the
// disbursement receipt (take-on and bank statements: server-action-body-limit.test.tsx).

const { mockUploadReceipt } = vi.hoisted(() => ({ mockUploadReceipt: vi.fn() }));

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }) }));
vi.mock("@/app/(app)/org/[slug]/settings/documents/_tabs/template-support-actions", () => ({
  uploadDocxTemplateAction: vi.fn(),
}));
vi.mock("@/app/(app)/org/[slug]/legal/disbursements/actions", () => ({
  writeOffDisbursementAction: vi.fn(),
  restoreDisbursementAction: vi.fn(),
  submitForApprovalAction: vi.fn(),
  uploadReceiptAction: (...args: unknown[]) => mockUploadReceipt(...args),
  updateDisbursementAction: vi.fn(),
  approveDisbursementAction: vi.fn(),
  rejectDisbursementAction: vi.fn(),
  fetchUnbilledDisbursementsAction: vi.fn(),
}));
vi.mock("@/app/(app)/org/[slug]/matters/[id]/actions", () => ({ getDownloadUrl: vi.fn() }));

import { UploadDocxDialog } from "@/app/(app)/org/[slug]/settings/documents/_tabs/UploadDocxDialog";
import { ReplaceFileDialog } from "@/components/templates/template-docx-sections";
import { DisbursementDetailClient } from "@/app/(app)/org/[slug]/legal/disbursements/[id]/detail-client";
import type { DisbursementResponse } from "@/lib/api/legal-disbursements";

const DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
const TOO_LARGE = "File exceeds the 3 MB limit (3.1 MB)";

function fileOf(name: string, size: number, type: string) {
  const file = new File(["x"], name, { type });
  Object.defineProperty(file, "size", { value: size });
  return file;
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("DOCX template upload (FR-3)", () => {
  it("states 3 MB and refuses a file over it", async () => {
    const user = userEvent.setup({ applyAccept: false });
    render(
      <UploadDocxDialog slug="acme">
        <button type="button">Upload Word template</button>
      </UploadDocxDialog>
    );
    await user.click(screen.getByRole("button", { name: "Upload Word template" }));
    expect(screen.getByText("Max 3 MB")).toBeInTheDocument();
    await user.upload(screen.getByTestId("docx-file-input"), fileOf("big.docx", 3_000_001, DOCX));
    expect(screen.getByText(TOO_LARGE)).toBeInTheDocument();
  });

  it("accepts a file of exactly 3 MB", async () => {
    const user = userEvent.setup({ applyAccept: false });
    render(
      <UploadDocxDialog slug="acme">
        <button type="button">Upload Word template</button>
      </UploadDocxDialog>
    );
    await user.click(screen.getByRole("button", { name: "Upload Word template" }));
    await user.upload(screen.getByTestId("docx-file-input"), fileOf("ok.docx", 3_000_000, DOCX));
    expect(screen.queryByText(/File exceeds/)).not.toBeInTheDocument();
    expect(screen.getByText("ok.docx")).toBeInTheDocument();
  });
});

describe("DOCX template replace (FR-3)", () => {
  it("states 3 MB and refuses a file over it", async () => {
    const user = userEvent.setup({ applyAccept: false });
    const onReplace = vi.fn();
    render(
      <ReplaceFileDialog
        open
        onOpenChange={vi.fn()}
        onReplace={onReplace}
        isReplacing={false}
        replaceError={null}
      />
    );
    expect(screen.getByText("Max 3 MB")).toBeInTheDocument();
    await user.upload(
      screen.getByTestId("replace-file-input"),
      fileOf("big.docx", 3_000_001, DOCX)
    );
    expect(screen.getByText(TOO_LARGE)).toBeInTheDocument();
    expect(onReplace).not.toHaveBeenCalled();
  });
});

function draftDisbursement(): DisbursementResponse {
  return {
    id: "d1",
    matterId: "p1",
    clientId: "c1",
    category: "SHERIFF_FEES",
    description: "Sheriff fee — service of process",
    amount: 750,
    vatTreatment: "ZERO_RATED_PASS_THROUGH",
    vatAmount: 0,
    paymentSource: "OFFICE_ACCOUNT",
    trustTransactionId: null,
    incurredDate: "2026-10-01",
    supplierName: "Sheriff Johannesburg North",
    supplierReference: null,
    receiptDocumentId: null,
    approvalStatus: "DRAFT",
    approvedBy: null,
    approvedAt: null,
    approvalNotes: null,
    billingStatus: "UNBILLED",
    invoiceLineId: null,
    writeOffReason: null,
    createdBy: "u1",
    createdAt: "2026-10-01T10:00:00Z",
    updatedAt: "2026-10-01T10:00:00Z",
  };
}

describe("disbursement receipt upload (FR-3)", () => {
  async function openUpload() {
    const user = userEvent.setup({ applyAccept: false });
    render(
      <DisbursementDetailClient
        slug="acme"
        disbursement={draftDisbursement()}
        canApprove={false}
        canWriteOff={false}
      />
    );
    await user.click(screen.getByRole("button", { name: /Upload receipt/ }));
    const dialog = await screen.findByTestId("upload-receipt-dialog");
    return { user, dialog };
  }

  it("states 3 MB and refuses a receipt over it without calling the action", async () => {
    const { user, dialog } = await openUpload();
    expect(dialog).toHaveTextContent("up to 3 MB");
    await user.upload(
      dialog.querySelector('input[type="file"]') as HTMLInputElement,
      fileOf("receipt.pdf", 3_000_001, "application/pdf")
    );
    expect(screen.getByText(TOO_LARGE)).toBeInTheDocument();
    expect(mockUploadReceipt).not.toHaveBeenCalled();
  });

  it("sends a receipt of exactly 3 MB", async () => {
    mockUploadReceipt.mockResolvedValue({ success: false, error: "refused" });
    const { user, dialog } = await openUpload();
    await user.upload(
      dialog.querySelector('input[type="file"]') as HTMLInputElement,
      fileOf("receipt.pdf", 3_000_000, "application/pdf")
    );
    expect(mockUploadReceipt).toHaveBeenCalledTimes(1);
  });
});
