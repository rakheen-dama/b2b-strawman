"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Plus } from "lucide-react";
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
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import {
  createDisbursementSchema,
  type CreateDisbursementFormData,
} from "@/lib/schemas/legal";
import {
  DISBURSEMENT_CATEGORY_OPTIONS,
  VAT_TREATMENT_OPTIONS,
  defaultVatTreatmentForCategory,
} from "@/lib/legal/disbursement-defaults";
import {
  createDisbursementAction,
  fetchCustomers,
  fetchProjects,
} from "@/app/(app)/org/[slug]/legal/disbursements/actions";
import {
  cancelUpload,
  confirmUpload,
  initiateUpload,
} from "@/app/(app)/org/[slug]/projects/[id]/actions";

interface CreateDisbursementDialogProps {
  slug: string;
  defaultProjectId?: string;
  defaultCustomerId?: string;
  onSuccess?: () => void;
}

export function CreateDisbursementDialog({
  slug,
  defaultProjectId,
  defaultCustomerId,
  onSuccess,
}: CreateDisbursementDialogProps) {
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [projects, setProjects] = useState<{ id: string; name: string }[]>([]);
  const [customers, setCustomers] = useState<{ id: string; name: string }[]>([]);
  const [projectsLoading, setProjectsLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  // User-override tracking: if user manually picks a VAT treatment, stop
  // auto-seeding from category changes.
  const vatTreatmentUserTouched = useRef(false);

  // Receipt upload state (optional)
  const [receiptDocumentId, setReceiptDocumentId] = useState<string | null>(null);
  const [receiptFileName, setReceiptFileName] = useState<string | null>(null);
  const [uploadProgress, setUploadProgress] = useState<number>(0);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);

  const form = useForm<CreateDisbursementFormData>({
    resolver: zodResolver(createDisbursementSchema),
    defaultValues: {
      projectId: defaultProjectId ?? "",
      customerId: defaultCustomerId ?? "",
      category: "OTHER",
      description: "",
      amount: 0,
      vatTreatment: defaultVatTreatmentForCategory("OTHER"),
      paymentSource: "OFFICE_ACCOUNT",
      trustTransactionId: "",
      incurredDate: "",
      supplierName: "",
      supplierReference: "",
      receiptDocumentId: "",
    },
  });

  const selectedCategory = form.watch("category");
  const selectedPaymentSource = form.watch("paymentSource");
  const selectedProjectId = form.watch("projectId");

  // Auto-seed VAT treatment from category when user hasn't manually overridden
  useEffect(() => {
    if (!vatTreatmentUserTouched.current) {
      form.setValue("vatTreatment", defaultVatTreatmentForCategory(selectedCategory));
    }
  }, [selectedCategory, form]);

  // Load projects + customers when dialog opens
  useEffect(() => {
    if (!open) return;
    setProjectsLoading(true);
    setLoadError(null);
    Promise.all([fetchProjects(), fetchCustomers()])
      .then(([allProjects, allCustomers]) => {
        setProjects(allProjects ?? []);
        setCustomers(allCustomers ?? []);
      })
      .catch((err) => {
        console.error("Failed to load projects/customers:", err);
        setLoadError("Failed to load matters. Please try again.");
      })
      .finally(() => setProjectsLoading(false));
  }, [open]);

  const handleFileUpload = useCallback(
    async (file: File) => {
      if (!selectedProjectId) {
        setUploadError("Select a matter before uploading a receipt.");
        return;
      }
      setIsUploading(true);
      setUploadError(null);
      setUploadProgress(0);
      try {
        const initResult = await initiateUpload(
          slug,
          selectedProjectId,
          file.name,
          file.type,
          file.size
        );
        if (!initResult.success || !initResult.presignedUrl || !initResult.documentId) {
          setUploadError(initResult.error ?? "Failed to initiate upload.");
          setIsUploading(false);
          return;
        }

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
            if (xhr.status >= 200 && xhr.status < 300) resolve();
            else reject(new Error("Upload failed"));
          };
          xhr.onerror = () => reject(new Error("Upload failed"));
          xhr.send(file);
        });

        const confirmResult = await confirmUpload(
          slug,
          selectedProjectId,
          initResult.documentId
        );
        if (!confirmResult.success) {
          await cancelUpload(initResult.documentId);
          setUploadError(confirmResult.error ?? "Failed to confirm upload.");
          setIsUploading(false);
          return;
        }

        setReceiptDocumentId(initResult.documentId);
        setReceiptFileName(file.name);
        form.setValue("receiptDocumentId", initResult.documentId);
      } catch {
        setUploadError("Receipt upload failed. You can still submit without a receipt.");
      } finally {
        setIsUploading(false);
      }
    },
    [slug, selectedProjectId, form]
  );

  async function onSubmit(values: CreateDisbursementFormData) {
    setError(null);
    setIsSubmitting(true);
    try {
      const result = await createDisbursementAction(slug, {
        projectId: values.projectId,
        customerId: values.customerId,
        category: values.category,
        description: values.description,
        amount: values.amount,
        vatTreatment: values.vatTreatment,
        paymentSource: values.paymentSource,
        trustTransactionId:
          values.trustTransactionId && values.trustTransactionId !== ""
            ? values.trustTransactionId
            : null,
        incurredDate: values.incurredDate,
        supplierName: values.supplierName,
        supplierReference:
          values.supplierReference && values.supplierReference !== ""
            ? values.supplierReference
            : null,
        receiptDocumentId: receiptDocumentId ?? null,
      });
      if (result.success) {
        form.reset();
        vatTreatmentUserTouched.current = false;
        setReceiptDocumentId(null);
        setReceiptFileName(null);
        setOpen(false);
        onSuccess?.();
      } else {
        setError(result.error ?? "Failed to create disbursement");
      }
    } catch {
      setError("An unexpected error occurred");
    } finally {
      setIsSubmitting(false);
    }
  }

  function handleOpenChange(newOpen: boolean) {
    if (newOpen) {
      setError(null);
      setUploadError(null);
      vatTreatmentUserTouched.current = false;
      form.reset({
        projectId: defaultProjectId ?? "",
        customerId: defaultCustomerId ?? "",
        category: "OTHER",
        description: "",
        amount: 0,
        vatTreatment: defaultVatTreatmentForCategory("OTHER"),
        paymentSource: "OFFICE_ACCOUNT",
        trustTransactionId: "",
        incurredDate: "",
        supplierName: "",
        supplierReference: "",
        receiptDocumentId: "",
      });
      setReceiptDocumentId(null);
      setReceiptFileName(null);
    }
    setOpen(newOpen);
  }

  const selectClass = useMemo(
    () =>
      "flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-slate-500 focus-visible:ring-1 focus-visible:ring-slate-950 focus-visible:outline-none disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-800 dark:placeholder:text-slate-400 dark:focus-visible:ring-slate-300",
    []
  );

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>
        <Button size="sm" data-testid="create-disbursement-trigger">
          <Plus className="mr-1.5 size-4" />
          New Disbursement
        </Button>
      </DialogTrigger>
      <DialogContent
        data-testid="create-disbursement-dialog"
        className="max-h-[90vh] overflow-y-auto sm:max-w-2xl"
      >
        <DialogHeader>
          <DialogTitle>New Disbursement</DialogTitle>
          <DialogDescription>
            Record a client disbursement paid by the firm on the matter&apos;s behalf.
          </DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <FormField
                control={form.control}
                name="projectId"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Matter</FormLabel>
                    <FormControl>
                      <select
                        value={field.value}
                        onChange={field.onChange}
                        disabled={projectsLoading || !!defaultProjectId}
                        className={selectClass}
                      >
                        <option value="">
                          {projectsLoading ? "Loading matters..." : "-- Select matter --"}
                        </option>
                        {projects.map((p) => (
                          <option key={p.id} value={p.id}>
                            {p.name}
                          </option>
                        ))}
                      </select>
                    </FormControl>
                    {loadError && <p className="text-sm text-red-600">{loadError}</p>}
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="customerId"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Customer</FormLabel>
                    <FormControl>
                      <select
                        value={field.value}
                        onChange={field.onChange}
                        disabled={projectsLoading || !!defaultCustomerId}
                        className={selectClass}
                      >
                        <option value="">
                          {projectsLoading ? "Loading customers..." : "-- Select customer --"}
                        </option>
                        {customers.map((c) => (
                          <option key={c.id} value={c.id}>
                            {c.name}
                          </option>
                        ))}
                      </select>
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            <FormField
              control={form.control}
              name="category"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Category</FormLabel>
                  <FormControl>
                    <select value={field.value} onChange={field.onChange} className={selectClass}>
                      {DISBURSEMENT_CATEGORY_OPTIONS.map((o) => (
                        <option key={o.value} value={o.value}>
                          {o.label}
                        </option>
                      ))}
                    </select>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="description"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Description</FormLabel>
                  <FormControl>
                    <Textarea
                      placeholder="What was paid for..."
                      maxLength={5000}
                      rows={3}
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <div className="grid grid-cols-2 gap-4">
              <FormField
                control={form.control}
                name="amount"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Amount (ZAR, excl VAT)</FormLabel>
                    <FormControl>
                      <Input
                        type="number"
                        min={0.01}
                        step={0.01}
                        value={Number.isFinite(field.value) ? field.value : 0}
                        onChange={(e) =>
                          field.onChange(e.target.value === "" ? 0 : Number(e.target.value))
                        }
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="vatTreatment"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>VAT Treatment</FormLabel>
                    <FormControl>
                      <select
                        value={field.value}
                        onChange={(e) => {
                          vatTreatmentUserTouched.current = true;
                          field.onChange(e);
                        }}
                        className={selectClass}
                      >
                        {VAT_TREATMENT_OPTIONS.map((o) => (
                          <option key={o.value} value={o.value}>
                            {o.label}
                          </option>
                        ))}
                      </select>
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            <FormField
              control={form.control}
              name="paymentSource"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Payment Source</FormLabel>
                  <FormControl>
                    <RadioGroup
                      value={field.value}
                      onValueChange={field.onChange}
                      className="flex gap-6"
                    >
                      <label className="flex items-center gap-2 text-sm">
                        <RadioGroupItem value="OFFICE_ACCOUNT" id="ps-office" />
                        <span>Office Account</span>
                      </label>
                      <label className="flex items-center gap-2 text-sm">
                        <RadioGroupItem value="TRUST_ACCOUNT" id="ps-trust" />
                        <span>Trust Account</span>
                      </label>
                    </RadioGroup>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            {selectedPaymentSource === "TRUST_ACCOUNT" && (
              <div
                data-testid="trust-link-slot"
                className="rounded-md border border-dashed border-amber-300 bg-amber-50 p-3 text-sm dark:border-amber-800 dark:bg-amber-950"
              >
                <p className="font-medium text-amber-800 dark:text-amber-200">
                  Link a trust transaction (required)
                </p>
                <p className="mt-1 text-xs text-amber-700 dark:text-amber-300">
                  The trust transaction picker arrives in slice 488B. Submission will fail with a
                  validation error from the server until a transaction is linked.
                </p>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled
                  className="mt-2"
                  data-testid="trust-link-button-stub"
                >
                  Link trust transaction (488B)
                </Button>
              </div>
            )}

            <div className="grid grid-cols-2 gap-4">
              <FormField
                control={form.control}
                name="incurredDate"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Incurred Date</FormLabel>
                    <FormControl>
                      <Input type="date" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="supplierName"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Supplier</FormLabel>
                    <FormControl>
                      <Input placeholder="e.g. Sheriff Sandton" maxLength={200} {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            <FormField
              control={form.control}
              name="supplierReference"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    Supplier Reference{" "}
                    <span className="font-normal text-slate-500">(optional)</span>
                  </FormLabel>
                  <FormControl>
                    <Input placeholder="Invoice or reference number" maxLength={100} {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            {/* Receipt upload (optional). Requires a selected matter */}
            <div className="space-y-2">
              <FormLabel>
                Receipt <span className="font-normal text-slate-500">(optional)</span>
              </FormLabel>
              <Input
                type="file"
                accept="application/pdf,image/*"
                data-testid="receipt-file-input"
                disabled={isUploading || !selectedProjectId}
                onChange={(e) => {
                  const f = e.target.files?.[0];
                  if (f) handleFileUpload(f);
                }}
              />
              {!selectedProjectId && (
                <p className="text-xs text-slate-500">
                  Select a matter first to enable receipt upload.
                </p>
              )}
              {isUploading && (
                <p className="text-xs text-slate-600 dark:text-slate-400">
                  Uploading {uploadProgress}%...
                </p>
              )}
              {receiptFileName && !isUploading && (
                <p className="text-xs text-teal-600 dark:text-teal-400">
                  Uploaded: {receiptFileName}
                </p>
              )}
              {uploadError && <p className="text-xs text-red-600">{uploadError}</p>}
            </div>

            {error && (
              <p className="text-sm text-red-600" data-testid="submit-error">
                {error}
              </p>
            )}

            <DialogFooter>
              <Button
                type="button"
                variant="plain"
                onClick={() => setOpen(false)}
                disabled={isSubmitting}
              >
                Cancel
              </Button>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? "Creating..." : "Create Disbursement"}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
