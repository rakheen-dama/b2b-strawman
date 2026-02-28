"use client";

import { useCallback, useRef, useState, type ReactNode } from "react";
import { Loader2, Paperclip, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  createExpense,
  updateExpense,
} from "@/app/(app)/org/[slug]/projects/[id]/expense-actions";
import {
  initiateUpload,
  confirmUpload,
  cancelUpload,
} from "@/app/(app)/org/[slug]/projects/[id]/actions";
import type { ExpenseResponse, ExpenseCategory, UpdateExpenseRequest } from "@/lib/types";

const EXPENSE_CATEGORIES: { value: ExpenseCategory; label: string }[] = [
  { value: "FILING_FEE", label: "Filing Fee" },
  { value: "TRAVEL", label: "Travel" },
  { value: "COURIER", label: "Courier" },
  { value: "SOFTWARE", label: "Software" },
  { value: "SUBCONTRACTOR", label: "Subcontractor" },
  { value: "PRINTING", label: "Printing" },
  { value: "COMMUNICATION", label: "Communication" },
  { value: "OTHER", label: "Other" },
];

interface LogExpenseDialogProps {
  slug: string;
  projectId: string;
  tasks: { id: string; title: string }[];
  children: ReactNode;
  expenseToEdit?: ExpenseResponse;
}

export function LogExpenseDialog({
  slug,
  projectId,
  tasks,
  children,
  expenseToEdit,
}: LogExpenseDialogProps) {
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const formRef = useRef<HTMLFormElement>(null);

  const [billable, setBillable] = useState(expenseToEdit?.billable ?? true);
  const [receiptDocumentId, setReceiptDocumentId] = useState<string | null>(
    expenseToEdit?.receiptDocumentId ?? null,
  );
  const [isUploading, setIsUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [receiptFileName, setReceiptFileName] = useState<string | null>(
    expenseToEdit?.receiptDocumentId ? "Receipt attached" : null,
  );

  const today = new Date().toLocaleDateString("en-CA");
  const isEditMode = !!expenseToEdit;

  const handleFileUpload = useCallback(
    async (file: File) => {
      setIsUploading(true);
      setUploadError(null);
      setUploadProgress(0);

      try {
        const initResult = await initiateUpload(
          slug,
          projectId,
          file.name,
          file.type,
          file.size,
        );

        if (!initResult.success || !initResult.presignedUrl || !initResult.documentId) {
          setUploadError(initResult.error ?? "Failed to initiate upload.");
          setIsUploading(false);
          return;
        }

        // Upload to S3 via XHR
        await new Promise<void>((resolve, reject) => {
          const xhr = new XMLHttpRequest();
          xhr.open("PUT", initResult.presignedUrl!);
          xhr.setRequestHeader("Content-Type", file.type);

          xhr.upload.onprogress = (e) => {
            if (e.lengthComputable) {
              setUploadProgress(Math.round((e.loaded / e.total) * 100));
            }
          };

          xhr.onload = () => {
            if (xhr.status >= 200 && xhr.status < 300) {
              resolve();
            } else {
              reject(new Error("Upload failed"));
            }
          };

          xhr.onerror = () => reject(new Error("Upload failed"));
          xhr.send(file);
        });

        // Confirm upload
        const confirmResult = await confirmUpload(
          slug,
          projectId,
          initResult.documentId,
        );

        if (!confirmResult.success) {
          await cancelUpload(initResult.documentId);
          setUploadError(confirmResult.error ?? "Failed to confirm upload.");
          setIsUploading(false);
          return;
        }

        setReceiptDocumentId(initResult.documentId);
        setReceiptFileName(file.name);
        setIsUploading(false);
      } catch {
        setUploadError("Receipt upload failed. You can still submit without a receipt.");
        setIsUploading(false);
      }
    },
    [slug, projectId],
  );

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (file) {
      handleFileUpload(file);
    }
  }

  function clearReceipt() {
    setReceiptDocumentId(null);
    setReceiptFileName(null);
    setUploadError(null);
  }

  async function handleSubmit(formData: FormData) {
    setError(null);
    setIsSubmitting(true);

    try {
      if (isEditMode && expenseToEdit) {
        const data: UpdateExpenseRequest = {
          date: formData.get("date")?.toString().trim() || undefined,
          description:
            formData.get("description")?.toString().trim() || undefined,
          amount: parseFloat(
            formData.get("amount")?.toString().trim() ?? "0",
          ),
          currency:
            formData.get("currency")?.toString().trim() || undefined,
          category:
            (formData.get("category")?.toString() as ExpenseCategory) ||
            undefined,
          taskId: formData.get("taskId")?.toString().trim() || null,
          receiptDocumentId: receiptDocumentId,
          markupPercent: formData.get("markupPercent")?.toString().trim()
            ? parseFloat(
                formData.get("markupPercent")?.toString().trim() ?? "0",
              )
            : null,
          billable,
          notes: formData.get("notes")?.toString().trim() || null,
        };

        const result = await updateExpense(
          slug,
          projectId,
          expenseToEdit.id,
          data,
        );

        if (result.success) {
          setOpen(false);
        } else {
          setError(result.error ?? "Failed to update expense.");
        }
      } else {
        // Add receiptDocumentId to formData
        if (receiptDocumentId) {
          formData.set("receiptDocumentId", receiptDocumentId);
        }
        // Add billable state
        if (billable) {
          formData.set("billable", "true");
        } else {
          formData.delete("billable");
        }

        const result = await createExpense(slug, projectId, formData);

        if (result.success) {
          formRef.current?.reset();
          setBillable(true);
          setReceiptDocumentId(null);
          setReceiptFileName(null);
          setOpen(false);
        } else {
          setError(result.error ?? "Failed to log expense.");
        }
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  }

  function handleOpenChange(newOpen: boolean) {
    if (!newOpen) {
      formRef.current?.reset();
      setError(null);
      setUploadError(null);
      setBillable(expenseToEdit?.billable ?? true);
      setReceiptDocumentId(expenseToEdit?.receiptDocumentId ?? null);
      setReceiptFileName(
        expenseToEdit?.receiptDocumentId ? "Receipt attached" : null,
      );
    }
    setOpen(newOpen);
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            {isEditMode ? "Edit Expense" : "Log Expense"}
          </DialogTitle>
          <DialogDescription>
            {isEditMode
              ? "Update the details of this expense."
              : "Record a disbursement against this project."}
          </DialogDescription>
        </DialogHeader>
        <form ref={formRef} action={handleSubmit} className="space-y-4">
          {/* Date */}
          <div className="space-y-1.5">
            <Label htmlFor="expense-date">Date</Label>
            <Input
              id="expense-date"
              name="date"
              type="date"
              defaultValue={expenseToEdit?.date ?? today}
              required
            />
          </div>

          {/* Description */}
          <div className="space-y-1.5">
            <Label htmlFor="expense-description">Description</Label>
            <Input
              id="expense-description"
              name="description"
              placeholder="e.g. Court filing fee"
              maxLength={500}
              defaultValue={expenseToEdit?.description ?? ""}
              required
            />
          </div>

          {/* Amount + Currency row */}
          <div className="grid grid-cols-3 gap-3">
            <div className="col-span-2 space-y-1.5">
              <Label htmlFor="expense-amount">Amount</Label>
              <Input
                id="expense-amount"
                name="amount"
                type="number"
                min="0.01"
                step="0.01"
                defaultValue={expenseToEdit?.amount ?? ""}
                required
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="expense-currency">Currency</Label>
              <Input
                id="expense-currency"
                name="currency"
                placeholder="ZAR"
                maxLength={3}
                defaultValue={expenseToEdit?.currency ?? "ZAR"}
              />
            </div>
          </div>

          {/* Category */}
          <div className="space-y-1.5">
            <Label htmlFor="expense-category">Category</Label>
            <select
              id="expense-category"
              name="category"
              defaultValue={expenseToEdit?.category ?? "OTHER"}
              className="flex h-9 w-full rounded-md border border-slate-200 bg-white px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-teal-500 dark:border-slate-800 dark:bg-slate-950 dark:text-slate-50"
            >
              {EXPENSE_CATEGORIES.map((cat) => (
                <option key={cat.value} value={cat.value}>
                  {cat.label}
                </option>
              ))}
            </select>
          </div>

          {/* Task (optional) */}
          {tasks.length > 0 && (
            <div className="space-y-1.5">
              <Label htmlFor="expense-task">Task (optional)</Label>
              <select
                id="expense-task"
                name="taskId"
                defaultValue={expenseToEdit?.taskId ?? ""}
                className="flex h-9 w-full rounded-md border border-slate-200 bg-white px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-teal-500 dark:border-slate-800 dark:bg-slate-950 dark:text-slate-50"
              >
                <option value="">No task</option>
                {tasks.map((task) => (
                  <option key={task.id} value={task.id}>
                    {task.title}
                  </option>
                ))}
              </select>
            </div>
          )}

          {/* Markup % */}
          <div className="space-y-1.5">
            <Label htmlFor="expense-markup">Markup %</Label>
            <Input
              id="expense-markup"
              name="markupPercent"
              type="number"
              min="0"
              step="0.01"
              placeholder="0.00"
              defaultValue={expenseToEdit?.markupPercent ?? ""}
            />
          </div>

          {/* Billable checkbox */}
          <div className="flex items-center gap-2">
            <input
              id="expense-billable"
              name="billable"
              type="checkbox"
              checked={billable}
              onChange={(e) => setBillable(e.target.checked)}
              className="size-4 rounded border-slate-300 text-teal-600 focus:ring-teal-500 dark:border-slate-700"
            />
            <Label htmlFor="expense-billable" className="font-normal">
              Billable
            </Label>
          </div>

          {/* Notes */}
          <div className="space-y-1.5">
            <Label htmlFor="expense-notes">Notes (optional)</Label>
            <Textarea
              id="expense-notes"
              name="notes"
              maxLength={1000}
              defaultValue={expenseToEdit?.notes ?? ""}
              rows={2}
            />
          </div>

          {/* Receipt upload */}
          <div className="space-y-1.5">
            <Label>Receipt (optional)</Label>
            {receiptFileName ? (
              <div className="flex items-center gap-2 rounded-md border border-slate-200 px-3 py-2 dark:border-slate-800">
                <Paperclip className="size-4 text-slate-500" />
                <span className="flex-1 truncate text-sm text-slate-700 dark:text-slate-300">
                  {receiptFileName}
                </span>
                <Button
                  type="button"
                  variant="ghost"
                  size="xs"
                  onClick={clearReceipt}
                >
                  <X className="size-3" />
                </Button>
              </div>
            ) : isUploading ? (
              <div className="flex items-center gap-2 rounded-md border border-slate-200 px-3 py-2 dark:border-slate-800">
                <Loader2 className="size-4 animate-spin text-teal-500" />
                <span className="text-sm text-slate-600 dark:text-slate-400">
                  Uploading... {uploadProgress}%
                </span>
              </div>
            ) : (
              <Input
                type="file"
                onChange={handleFileChange}
                accept="image/*,.pdf,.doc,.docx"
                className="text-sm"
              />
            )}
            {uploadError && (
              <p className="text-xs text-red-600 dark:text-red-400">
                {uploadError}
              </p>
            )}
          </div>

          {error && <p className="text-sm text-destructive">{error}</p>}
          <DialogFooter>
            <Button
              type="button"
              variant="plain"
              onClick={() => setOpen(false)}
              disabled={isSubmitting}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={isSubmitting || isUploading}>
              {isSubmitting
                ? "Saving..."
                : isEditMode
                  ? "Update Expense"
                  : "Log Expense"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
