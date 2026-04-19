"use client";

import { useCallback, useState } from "react";
import Link from "next/link";
import { ArrowLeft, Pencil, Upload } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import {
  approvalStatusBadge,
  billingStatusBadge,
  categoryLabel,
} from "@/components/legal/disbursement-list-view";
import { EditDisbursementDialog } from "@/components/legal/edit-disbursement-dialog";
import { formatCurrency } from "@/lib/format";
import {
  uploadReceiptAction,
} from "@/app/(app)/org/[slug]/legal/disbursements/actions";
import { getDownloadUrl } from "@/app/(app)/org/[slug]/projects/[id]/actions";
import type { DisbursementResponse } from "@/lib/api/legal-disbursements";

interface DisbursementDetailClientProps {
  slug: string;
  disbursement: DisbursementResponse;
}

const VAT_TREATMENT_LABEL: Record<DisbursementResponse["vatTreatment"], string> = {
  STANDARD_15: "Standard (15%)",
  ZERO_RATED_PASS_THROUGH: "Zero-rated pass-through",
  EXEMPT: "Exempt",
};

const PAYMENT_SOURCE_LABEL: Record<DisbursementResponse["paymentSource"], string> = {
  OFFICE_ACCOUNT: "Office Account",
  TRUST_ACCOUNT: "Trust Account",
};

export function DisbursementDetailClient({
  slug,
  disbursement: initial,
}: DisbursementDetailClientProps) {
  const [disbursement, setDisbursement] = useState(initial);
  const [editOpen, setEditOpen] = useState(false);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [isUploading, setIsUploading] = useState(false);

  const editable =
    disbursement.approvalStatus === "DRAFT" ||
    disbursement.approvalStatus === "PENDING_APPROVAL";

  const handleReceiptUpload = useCallback(
    async (file: File) => {
      setUploadError(null);
      setIsUploading(true);
      try {
        const result = await uploadReceiptAction(slug, disbursement.id, file);
        if (result.success && result.data) {
          setDisbursement(result.data);
          setUploadOpen(false);
        } else {
          setUploadError(result.error ?? "Failed to upload receipt.");
        }
      } catch {
        setUploadError("An unexpected error occurred during upload.");
      } finally {
        setIsUploading(false);
      }
    },
    [slug, disbursement.id]
  );

  const handleDownloadReceipt = useCallback(async () => {
    if (!disbursement.receiptDocumentId) return;
    const result = await getDownloadUrl(disbursement.receiptDocumentId);
    if (result.success && result.presignedUrl) {
      window.open(result.presignedUrl, "_blank", "noopener");
    }
  }, [disbursement.receiptDocumentId]);

  const inclVat = disbursement.amount + disbursement.vatAmount;

  return (
    <div className="space-y-6" data-testid="disbursement-detail">
      <div>
        <Link
          href={`/org/${slug}/legal/disbursements`}
          className="inline-flex items-center text-sm text-slate-600 transition-colors hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ArrowLeft className="mr-1.5 size-4" />
          Back to Disbursements
        </Link>
      </div>

      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="flex items-center gap-3">
            <h1 className="font-display text-2xl text-slate-950 dark:text-slate-50">
              {categoryLabel(disbursement.category)}
            </h1>
            {approvalStatusBadge(disbursement.approvalStatus)}
            {billingStatusBadge(disbursement.billingStatus)}
          </div>
          <p className="mt-2 text-sm text-slate-600 dark:text-slate-400">
            Supplier: <span className="font-medium">{disbursement.supplierName}</span>
            {disbursement.supplierReference && (
              <>
                {" "}
                &middot; Ref{" "}
                <code className="rounded bg-slate-100 px-1.5 py-0.5 font-mono text-xs text-slate-700 dark:bg-slate-800 dark:text-slate-300">
                  {disbursement.supplierReference}
                </code>
              </>
            )}
          </p>
          <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
            Incurred {disbursement.incurredDate}
          </p>
        </div>

        <div className="flex shrink-0 gap-2">
          {editable && (
            <>
              <Button variant="outline" size="sm" onClick={() => setUploadOpen(true)}>
                <Upload className="mr-1.5 size-4" />
                Upload receipt
              </Button>
              <Button variant="outline" size="sm" onClick={() => setEditOpen(true)}>
                <Pencil className="mr-1.5 size-4" />
                Edit
              </Button>
            </>
          )}
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="text-sm font-medium">Financial summary</CardTitle>
          </CardHeader>
          <CardContent className="space-y-2 text-sm">
            <Row label="Amount (excl VAT)" value={formatCurrency(disbursement.amount, "ZAR")} />
            <Row
              label="VAT"
              value={`${formatCurrency(disbursement.vatAmount, "ZAR")} — ${VAT_TREATMENT_LABEL[disbursement.vatTreatment]}`}
            />
            <Row
              label="Total (incl VAT)"
              value={formatCurrency(inclVat, "ZAR")}
              emphasize
            />
            <Row
              label="Payment Source"
              value={PAYMENT_SOURCE_LABEL[disbursement.paymentSource]}
            />
            {disbursement.trustTransactionId && (
              // TODO: wire trust transaction detail route when available. No detail page exists
              // for trust transactions yet, so we display the id as read-only text.
              <Row
                label="Trust Transaction"
                value={
                  <code className="font-mono text-xs text-slate-600 dark:text-slate-400">
                    {disbursement.trustTransactionId}
                  </code>
                }
              />
            )}
            {disbursement.invoiceLineId && (
              // TODO: wire invoice id when 488B updates the backend DTO to expose invoiceId
              // (current DTO only has invoiceLineId which is NOT a valid /invoices/:id path).
              <Row
                label="Invoiced"
                value={<span className="text-slate-600 dark:text-slate-400">Yes</span>}
              />
            )}
            {disbursement.writeOffReason && (
              <Row label="Write-off reason" value={disbursement.writeOffReason} />
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-sm font-medium">Description & receipt</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3 text-sm">
            <div>
              <p className="text-xs text-slate-500 uppercase dark:text-slate-400">Description</p>
              <p className="mt-1 whitespace-pre-wrap text-slate-700 dark:text-slate-300">
                {disbursement.description}
              </p>
            </div>
            <div>
              <p className="text-xs text-slate-500 uppercase dark:text-slate-400">Receipt</p>
              {disbursement.receiptDocumentId ? (
                <button
                  type="button"
                  onClick={handleDownloadReceipt}
                  className="mt-1 text-teal-600 hover:text-teal-700 hover:underline dark:text-teal-400 dark:hover:text-teal-300"
                  data-testid="download-receipt"
                >
                  Download receipt
                </button>
              ) : (
                <p className="mt-1 text-slate-400 italic dark:text-slate-600">
                  No receipt uploaded
                </p>
              )}
            </div>
          </CardContent>
        </Card>
      </div>

      {disbursement.approvalStatus === "PENDING_APPROVAL" && (
        <Card
          data-testid="approval-panel-placeholder"
          className="border-dashed border-slate-300 dark:border-slate-700"
        >
          <CardContent className="py-6 text-center text-sm text-slate-500 dark:text-slate-400">
            Approval panel placeholder (slice 488B)
          </CardContent>
        </Card>
      )}

      {disbursement.approvalNotes && (
        <Card>
          <CardHeader>
            <CardTitle className="text-sm font-medium">Approval Notes</CardTitle>
          </CardHeader>
          <CardContent className="text-sm whitespace-pre-wrap text-slate-700 dark:text-slate-300">
            {disbursement.approvalNotes}
          </CardContent>
        </Card>
      )}

      <EditDisbursementDialog
        slug={slug}
        disbursement={disbursement}
        open={editOpen}
        onOpenChange={setEditOpen}
        onSuccess={(updated) => setDisbursement(updated)}
      />

      <Dialog open={uploadOpen} onOpenChange={setUploadOpen}>
        <DialogContent data-testid="upload-receipt-dialog">
          <DialogHeader>
            <DialogTitle>Upload receipt</DialogTitle>
            <DialogDescription>
              Attach a PDF or image of the supplier receipt.
            </DialogDescription>
          </DialogHeader>
          <Input
            type="file"
            accept="application/pdf,image/*"
            disabled={isUploading}
            onChange={(e) => {
              const file = e.target.files?.[0];
              if (file) handleReceiptUpload(file);
            }}
          />
          {isUploading && (
            <p className="text-xs text-slate-500">Uploading receipt...</p>
          )}
          {uploadError && <p className="text-sm text-red-600">{uploadError}</p>}
          <DialogFooter>
            <Button
              type="button"
              variant="plain"
              onClick={() => setUploadOpen(false)}
              disabled={isUploading}
            >
              Close
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function Row({
  label,
  value,
  emphasize,
}: {
  label: string;
  value: React.ReactNode;
  emphasize?: boolean;
}) {
  return (
    <div className="flex items-start justify-between gap-3">
      <span className="text-slate-500 dark:text-slate-400">{label}</span>
      <span
        className={
          emphasize
            ? "font-mono tabular-nums text-slate-950 dark:text-slate-50 font-semibold"
            : "text-right text-slate-700 dark:text-slate-300 font-mono tabular-nums"
        }
      >
        {value}
      </span>
    </div>
  );
}
