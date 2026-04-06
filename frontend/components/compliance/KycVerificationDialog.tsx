"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Checkbox } from "@/components/ui/checkbox";
import { Alert, AlertDescription } from "@/components/ui/alert";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Loader2, CheckCircle2, XCircle, AlertTriangle, Info } from "lucide-react";
import { kycVerifySchema, type KycVerifyFormData } from "@/lib/schemas/kyc";
import { verifyKycAction } from "@/app/(app)/org/[slug]/customers/[id]/kyc-actions";
import type { KycVerifyResponse } from "@/lib/types";

interface KycVerificationDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  slug: string;
  customerId: string;
  checklistInstanceItemId: string;
  customerName: string;
  providerName?: string;
  prefillIdNumber?: string;
  prefillFullName?: string;
}

function getProviderDescription(providerName?: string): string | null {
  if (providerName === "verifynow") {
    return "VerifyNow — verified against Home Affairs HANIS";
  }
  if (providerName === "checkid") {
    return "Check ID SA — format validation only (manual review required)";
  }
  return null;
}

export function KycVerificationDialog({
  open,
  onOpenChange,
  slug,
  customerId,
  checklistInstanceItemId,
  customerName,
  providerName,
  prefillIdNumber,
  prefillFullName,
}: KycVerificationDialogProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [result, setResult] = useState<KycVerifyResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  const form = useForm<KycVerifyFormData>({
    resolver: zodResolver(kycVerifySchema),
    defaultValues: {
      customerId,
      checklistInstanceItemId,
      idNumber: prefillIdNumber ?? "",
      fullName: prefillFullName ?? "",
      idDocumentType: undefined,
      consentAcknowledged: false,
    },
  });

  function resetForm() {
    setResult(null);
    setError(null);
    form.reset({
      customerId,
      checklistInstanceItemId,
      idNumber: prefillIdNumber ?? "",
      fullName: prefillFullName ?? "",
      idDocumentType: undefined,
      consentAcknowledged: false,
    });
  }

  function handleOpenChange(nextOpen: boolean) {
    if (!nextOpen) {
      resetForm();
    }
    onOpenChange(nextOpen);
  }

  async function handleSubmit(values: KycVerifyFormData) {
    setIsSubmitting(true);
    setError(null);

    try {
      const actionResult = await verifyKycAction(slug, {
        customerId: values.customerId,
        checklistInstanceItemId: values.checklistInstanceItemId,
        idNumber: values.idNumber,
        fullName: values.fullName,
        idDocumentType: values.idDocumentType,
        consentAcknowledged: values.consentAcknowledged,
      });

      if (actionResult.success && actionResult.data) {
        setResult(actionResult.data);
      } else {
        setError(actionResult.error ?? "Verification failed.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  }

  const providerDescription = getProviderDescription(providerName);

  // Result display
  if (result) {
    return (
      <Dialog open={open} onOpenChange={handleOpenChange}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>Verification Result</DialogTitle>
            <DialogDescription>
              Identity verification for {customerName}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4 py-2">
            {result.status === "VERIFIED" && (
              <Alert className="border-emerald-200 bg-emerald-50 text-emerald-800 dark:border-emerald-800 dark:bg-emerald-950 dark:text-emerald-200">
                <CheckCircle2 className="size-4" />
                <AlertDescription>
                  <p className="font-medium">Identity verified</p>
                  {result.providerReference && (
                    <p className="mt-1 text-sm">
                      Reference: {result.providerReference}
                    </p>
                  )}
                  {result.verifiedAt && (
                    <p className="mt-1 text-sm">
                      Verified at: {new Date(result.verifiedAt).toLocaleString()}
                    </p>
                  )}
                </AlertDescription>
              </Alert>
            )}

            {result.status === "NOT_VERIFIED" && (
              <Alert variant="destructive">
                <XCircle className="size-4" />
                <AlertDescription>
                  <p className="font-medium">Identity not verified</p>
                  {result.reasonDescription && (
                    <p className="mt-1 text-sm">{result.reasonDescription}</p>
                  )}
                </AlertDescription>
              </Alert>
            )}

            {result.status === "NEEDS_REVIEW" && (
              <Alert variant="warning">
                <AlertTriangle className="size-4" />
                <AlertDescription>
                  <p className="font-medium">Manual review required</p>
                  <p className="mt-1 text-sm">
                    {result.providerName} validates format only. Please verify
                    the physical ID document.
                  </p>
                </AlertDescription>
              </Alert>
            )}

            {result.status === "ERROR" && (
              <Alert>
                <Info className="size-4" />
                <AlertDescription>
                  <p className="font-medium">Verification error</p>
                  <p className="mt-1 text-sm">
                    {result.reasonDescription ??
                      "Please try again or contact support."}
                  </p>
                </AlertDescription>
              </Alert>
            )}
          </div>

          <DialogFooter>
            {(result.status === "NOT_VERIFIED" || result.status === "ERROR") && (
              <Button
                type="button"
                variant="outline"
                onClick={resetForm}
              >
                Retry
              </Button>
            )}
            <Button type="button" onClick={() => handleOpenChange(false)}>
              Close
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    );
  }

  // Form display
  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>KYC Verification</DialogTitle>
          <DialogDescription>
            Verify identity for {customerName}
          </DialogDescription>
        </DialogHeader>

        {providerDescription && (
          <p className="text-sm text-slate-500 dark:text-slate-400">
            {providerDescription}
          </p>
        )}

        <Form {...form}>
          <form
            onSubmit={form.handleSubmit(handleSubmit)}
            className="space-y-4"
          >
            <FormField
              control={form.control}
              name="idNumber"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>ID Number</FormLabel>
                  <FormControl>
                    <Input
                      placeholder="Enter ID number"
                      disabled={isSubmitting}
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="fullName"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Full Name</FormLabel>
                  <FormControl>
                    <Input
                      placeholder="Enter full name"
                      disabled={isSubmitting}
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="idDocumentType"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>ID Document Type (optional)</FormLabel>
                  <Select
                    value={field.value ?? ""}
                    onValueChange={(value) =>
                      field.onChange(value || undefined)
                    }
                    disabled={isSubmitting}
                  >
                    <FormControl>
                      <SelectTrigger>
                        <SelectValue placeholder="Select document type" />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      <SelectItem value="SA_ID">SA ID Card</SelectItem>
                      <SelectItem value="SMART_ID">Smart ID Card</SelectItem>
                      <SelectItem value="PASSPORT">Passport</SelectItem>
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="consentAcknowledged"
              render={({ field }) => (
                <FormItem className="flex flex-row items-start space-x-3 space-y-0 rounded-md border border-slate-200 p-4 dark:border-slate-800">
                  <FormControl>
                    <Checkbox
                      checked={field.value === true}
                      onCheckedChange={(checked) =>
                        field.onChange(checked === true)
                      }
                      disabled={isSubmitting}
                    />
                  </FormControl>
                  <div className="space-y-1 leading-none">
                    <FormLabel className="text-sm font-normal">
                      By proceeding, you confirm that {customerName} has given
                      explicit written consent for identity verification against
                      government databases, as required by POPIA and FICA.
                    </FormLabel>
                    <FormMessage />
                  </div>
                </FormItem>
              )}
            />

            {error && (
              <p className="text-sm text-destructive" role="alert">
                {error}
              </p>
            )}

            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => handleOpenChange(false)}
                disabled={isSubmitting}
              >
                Cancel
              </Button>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? (
                  <>
                    <Loader2 className="mr-1.5 size-4 animate-spin" />
                    Verifying...
                  </>
                ) : (
                  "Verify"
                )}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
