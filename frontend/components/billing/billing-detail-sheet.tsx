"use client";

import { useState } from "react";
import { toast } from "sonner";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetDescription,
  SheetFooter,
} from "@/components/ui/sheet";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Label } from "@/components/ui/label";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { StatusBadge } from "@/components/billing/status-badge";
import { MethodBadge } from "@/components/billing/method-badge";
import {
  overrideBilling,
  extendTrial,
} from "@/app/(app)/platform-admin/billing/actions";
import type { AdminTenantBilling } from "@/app/(app)/platform-admin/billing/actions";

const STATUS_OPTIONS = [
  { value: "TRIALING", label: "Trialing" },
  { value: "ACTIVE", label: "Active" },
  { value: "GRACE_PERIOD", label: "Grace Period" },
  { value: "LOCKED", label: "Locked" },
] as const;

const METHOD_OPTIONS = [
  { value: "PAYFAST", label: "PayFast" },
  { value: "DEBIT_ORDER", label: "Debit Order" },
  { value: "PILOT", label: "Pilot" },
  { value: "COMPLIMENTARY", label: "Complimentary" },
  { value: "MANUAL", label: "Manual" },
] as const;

interface BillingDetailSheetProps {
  tenant: AdminTenantBilling | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

function formatDate(dateStr: string | null): string {
  if (!dateStr) return "N/A";
  return new Date(dateStr).toLocaleDateString("en-ZA", {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

export function BillingDetailSheet({
  tenant,
  open,
  onOpenChange,
  onSuccess,
}: BillingDetailSheetProps) {
  const [status, setStatus] = useState("");
  const [billingMethod, setBillingMethod] = useState("");
  const [trialDays, setTrialDays] = useState("");
  const [adminNote, setAdminNote] = useState("");
  const [noteError, setNoteError] = useState("");
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState("");

  // Reset form when tenant changes
  const [prevTenantId, setPrevTenantId] = useState<string | null>(null);
  if (tenant && tenant.organizationId !== prevTenantId) {
    setPrevTenantId(tenant.organizationId);
    setStatus(tenant.subscriptionStatus);
    setBillingMethod(tenant.billingMethod);
    setTrialDays("");
    setAdminNote(tenant.adminNote ?? "");
    setNoteError("");
    setError("");
  }

  if (!tenant) return null;

  async function handleSave() {
    if (!tenant) return;

    // Validate admin note
    if (!adminNote.trim()) {
      setNoteError("Admin note is required");
      return;
    }
    setNoteError("");
    setError("");
    setIsSaving(true);

    try {
      // Extend trial if days specified
      const days = parseInt(trialDays, 10);
      if (trialDays && days > 0) {
        const trialResult = await extendTrial(tenant.organizationId, days);
        if (!trialResult.success) {
          setError(trialResult.error ?? "Failed to extend trial");
          setIsSaving(false);
          return;
        }
      }

      // Override billing status/method
      const overrideResult = await overrideBilling(tenant.organizationId, {
        status: status !== tenant.subscriptionStatus ? status : null,
        billingMethod:
          billingMethod !== tenant.billingMethod ? billingMethod : null,
        adminNote: adminNote.trim(),
      });

      if (!overrideResult.success) {
        setError(overrideResult.error ?? "Failed to update billing");
        setIsSaving(false);
        return;
      }

      toast.success("Billing updated successfully");
      onSuccess();
      onOpenChange(false);
    } catch {
      setError("An unexpected error occurred");
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="w-full sm:max-w-lg overflow-y-auto">
        <SheetHeader>
          <SheetTitle data-testid="sheet-org-name">
            {tenant.organizationName}
          </SheetTitle>
          <SheetDescription>Manage billing for this tenant</SheetDescription>
          <div className="flex items-center gap-2 pt-1">
            <Badge variant="neutral">{tenant.verticalProfile}</Badge>
            {tenant.isDemoTenant && (
              <Badge variant="warning">Demo</Badge>
            )}
          </div>
        </SheetHeader>

        <div className="flex-1 space-y-6 px-4">
          {/* Key Dates */}
          <div>
            <h3 className="text-sm font-medium text-slate-900 dark:text-slate-100">
              Key Dates
            </h3>
            <dl className="mt-2 grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
              <div>
                <dt className="text-slate-500 dark:text-slate-400">
                  Trial Ends
                </dt>
                <dd
                  className="font-medium text-slate-900 dark:text-slate-100"
                  data-testid="trial-ends"
                >
                  {formatDate(tenant.trialEndsAt)}
                </dd>
              </div>
              <div>
                <dt className="text-slate-500 dark:text-slate-400">
                  Period End
                </dt>
                <dd
                  className="font-medium text-slate-900 dark:text-slate-100"
                  data-testid="period-end"
                >
                  {formatDate(tenant.currentPeriodEnd)}
                </dd>
              </div>
              <div>
                <dt className="text-slate-500 dark:text-slate-400">
                  Grace Ends
                </dt>
                <dd className="font-medium text-slate-900 dark:text-slate-100">
                  {formatDate(tenant.graceEndsAt)}
                </dd>
              </div>
              <div>
                <dt className="text-slate-500 dark:text-slate-400">Created</dt>
                <dd className="font-medium text-slate-900 dark:text-slate-100">
                  {formatDate(tenant.createdAt)}
                </dd>
              </div>
            </dl>
          </div>

          {/* Stats */}
          <div>
            <h3 className="text-sm font-medium text-slate-900 dark:text-slate-100">
              Stats
            </h3>
            <div className="mt-2 flex items-center gap-4 text-sm">
              <span className="text-slate-500 dark:text-slate-400">
                Members:{" "}
                <span className="font-medium text-slate-900 dark:text-slate-100">
                  {tenant.memberCount}
                </span>
              </span>
            </div>
          </div>

          {/* Current Status */}
          <div>
            <h3 className="text-sm font-medium text-slate-900 dark:text-slate-100">
              Current Status
            </h3>
            <div className="mt-2 flex items-center gap-3">
              <StatusBadge status={tenant.subscriptionStatus} />
              <MethodBadge method={tenant.billingMethod} />
            </div>
          </div>

          {/* Admin Note (current) */}
          {tenant.adminNote && (
            <div>
              <h3 className="text-sm font-medium text-slate-900 dark:text-slate-100">
                Current Admin Note
              </h3>
              <p className="mt-1 text-sm text-slate-600 dark:text-slate-400 rounded-md bg-slate-50 dark:bg-slate-800 p-3">
                {tenant.adminNote}
              </p>
            </div>
          )}

          {/* Override Form */}
          <div className="space-y-4 border-t border-slate-200 dark:border-slate-700 pt-4">
            <h3 className="text-sm font-medium text-slate-900 dark:text-slate-100">
              Override Settings
            </h3>

            <div className="space-y-2">
              <Label htmlFor="billing-status">Status</Label>
              <Select value={status} onValueChange={setStatus}>
                <SelectTrigger id="billing-status" className="w-full">
                  <SelectValue placeholder="Select status..." />
                </SelectTrigger>
                <SelectContent>
                  {STATUS_OPTIONS.map((opt) => (
                    <SelectItem key={opt.value} value={opt.value}>
                      {opt.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label htmlFor="billing-method">Billing Method</Label>
              <Select value={billingMethod} onValueChange={setBillingMethod}>
                <SelectTrigger id="billing-method" className="w-full">
                  <SelectValue placeholder="Select method..." />
                </SelectTrigger>
                <SelectContent>
                  {METHOD_OPTIONS.map((opt) => (
                    <SelectItem key={opt.value} value={opt.value}>
                      {opt.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label htmlFor="trial-days">Extend Trial (days)</Label>
              <Input
                id="trial-days"
                type="number"
                min={1}
                placeholder="e.g. 14"
                value={trialDays}
                onChange={(e) => setTrialDays(e.target.value)}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="admin-note">Admin Note</Label>
              <Textarea
                id="admin-note"
                placeholder="Reason for change (required)..."
                value={adminNote}
                onChange={(e) => {
                  setAdminNote(e.target.value);
                  if (e.target.value.trim()) setNoteError("");
                }}
                rows={3}
              />
              {noteError && (
                <p className="text-sm text-red-600" data-testid="note-error">
                  {noteError}
                </p>
              )}
            </div>
          </div>

          {error && (
            <div className="rounded-md bg-red-50 p-3 text-sm text-red-700 dark:bg-red-900/20 dark:text-red-400">
              {error}
            </div>
          )}
        </div>

        <SheetFooter>
          <Button
            onClick={handleSave}
            disabled={isSaving}
            className="w-full"
          >
            {isSaving ? "Saving..." : "Save Changes"}
          </Button>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  );
}
