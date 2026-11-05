import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

/**
 * Phase 89 632C (G88-F32): the fee-note Payment History receipt cell — a "Receipt INV-0001/R1" link
 * when the receipt document exists, "Generate receipt" for INVOICING holders when it does not, and
 * nothing for a member without INVOICING or for a non-COMPLETED payment.
 */

const mockGenerate = vi.fn();
const mockDownload = vi.fn();
vi.mock("@/app/(app)/org/[slug]/invoices/invoice-payment-actions", () => ({
  generatePaymentReceiptAction: (...args: unknown[]) => mockGenerate(...args),
  downloadPaymentReceiptAction: (...args: unknown[]) => mockDownload(...args),
}));

import { PaymentEventHistory } from "@/components/invoices/PaymentEventHistory";
import { CapabilityProvider } from "@/lib/capabilities";
import type { PaymentEvent } from "@/lib/types";

function makeEvent(overrides: Partial<PaymentEvent> = {}): PaymentEvent {
  return {
    id: "pe-1",
    providerSlug: "manual",
    sessionId: null,
    paymentReference: "EFT-1",
    status: "COMPLETED",
    amount: 1000,
    currency: "ZAR",
    paymentDestination: "OPERATING",
    paymentMethod: "EFT",
    createdAt: "2026-11-05T09:12:00Z",
    updatedAt: "2026-11-05T09:12:00Z",
    receiptGeneratedDocumentId: null,
    receiptNumber: null,
    ...overrides,
  };
}

function renderHistory(events: PaymentEvent[], caps: string[] = ["INVOICING"]) {
  return render(
    <CapabilityProvider capabilities={caps} role="Member" isAdmin={false} isOwner={false}>
      <PaymentEventHistory events={events} invoiceId="inv-1" slug="acme" />
    </CapabilityProvider>
  );
}

describe("PaymentEventHistory — payment receipts", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("shows a Receipt column", () => {
    renderHistory([makeEvent()]);
    expect(screen.getByRole("columnheader", { name: "Receipt" })).toBeInTheDocument();
  });

  it("links the receipt by its number when the receipt document exists", async () => {
    const user = userEvent.setup();
    mockDownload.mockResolvedValue({ success: true, pdfBase64: btoa("%PDF-1.4") });
    const originalCreate = URL.createObjectURL;
    const originalRevoke = URL.revokeObjectURL;
    URL.createObjectURL = vi.fn(() => "blob:fake");
    URL.revokeObjectURL = vi.fn();
    try {
      renderHistory([
        makeEvent({ receiptGeneratedDocumentId: "doc-1", receiptNumber: "INV-0001/R1" }),
      ]);

      const link = screen.getByRole("button", { name: "Receipt INV-0001/R1" });
      expect(screen.queryByRole("button", { name: "Generate receipt" })).not.toBeInTheDocument();

      await user.click(link);

      await waitFor(() => expect(mockDownload).toHaveBeenCalledWith("doc-1"));
      await waitFor(() => expect(URL.createObjectURL).toHaveBeenCalledTimes(1));
      expect(mockGenerate).not.toHaveBeenCalled();
    } finally {
      URL.createObjectURL = originalCreate;
      URL.revokeObjectURL = originalRevoke;
    }
  });

  it("shows the download error inline when the receipt cannot be fetched", async () => {
    const user = userEvent.setup();
    mockDownload.mockResolvedValue({ success: false, error: "Document not found" });
    renderHistory([
      makeEvent({ receiptGeneratedDocumentId: "doc-1", receiptNumber: "INV-0001/R1" }),
    ]);

    await user.click(screen.getByRole("button", { name: "Receipt INV-0001/R1" }));

    expect(await screen.findByText("Document not found")).toBeInTheDocument();
  });

  it("offers Generate receipt to an INVOICING holder when no receipt exists", async () => {
    const user = userEvent.setup();
    mockGenerate.mockResolvedValue({ success: true, generatedDocumentId: "doc-9" });
    renderHistory([makeEvent()]);

    expect(screen.queryByRole("button", { name: /^Receipt / })).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Generate receipt" }));

    await waitFor(() => expect(mockGenerate).toHaveBeenCalledWith("acme", "inv-1", "pe-1"));
    expect(mockDownload).not.toHaveBeenCalled();
  });

  it("offers Generate receipt when the number is kept but the document was deleted", () => {
    // receipt_generated_document_id is ON DELETE SET NULL; receipt_number stays set.
    renderHistory([makeEvent({ receiptGeneratedDocumentId: null, receiptNumber: "INV-0001/R1" })]);

    expect(screen.getByRole("button", { name: "Generate receipt" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Receipt INV-0001/R1" })).not.toBeInTheDocument();
  });

  it("shows the generate error inline", async () => {
    const user = userEvent.setup();
    mockGenerate.mockResolvedValue({ success: false, error: "Fee note not found" });
    renderHistory([makeEvent()]);

    await user.click(screen.getByRole("button", { name: "Generate receipt" }));

    expect(await screen.findByText("Fee note not found")).toBeInTheDocument();
  });

  it("shows neither control to a member without INVOICING when no receipt exists", () => {
    renderHistory([makeEvent()], []);

    expect(screen.queryByRole("button", { name: "Generate receipt" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^Receipt/ })).not.toBeInTheDocument();
  });

  it("still links an existing receipt for a member without INVOICING", () => {
    renderHistory(
      [makeEvent({ receiptGeneratedDocumentId: "doc-1", receiptNumber: "INV-0001/R1" })],
      []
    );

    expect(screen.getByRole("button", { name: "Receipt INV-0001/R1" })).toBeInTheDocument();
  });

  it.each(["PENDING", "FAILED", "CREATED"] as const)(
    "shows neither control for a %s payment",
    (status) => {
      renderHistory([
        makeEvent({ status, receiptGeneratedDocumentId: "doc-1", receiptNumber: "INV-0001/R1" }),
        makeEvent({ id: "pe-2", status }),
      ]);

      expect(screen.queryByRole("button", { name: "Generate receipt" })).not.toBeInTheDocument();
      expect(screen.queryByRole("button", { name: /^Receipt/ })).not.toBeInTheDocument();
    }
  );
});
