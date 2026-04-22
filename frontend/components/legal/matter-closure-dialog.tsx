"use client";

import { useEffect, useState } from "react";
import useSWR from "swr";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2, ShieldAlert } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
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
import { Textarea } from "@/components/ui/textarea";
import { Checkbox } from "@/components/ui/checkbox";
import { ModuleGate } from "@/components/module-gate";
import { MatterClosureReport } from "@/components/legal/matter-closure-report";
import { useCapabilities } from "@/lib/capabilities";
import { closeMatterSchema, type CloseMatterFormData } from "@/lib/schemas/matter-closure";
import {
  closeMatterAction,
  evaluateClosureAction,
} from "@/app/(app)/org/[slug]/projects/[id]/matter-closure-actions";
import type { ClosureReport } from "@/lib/api/matter-closure";

interface MatterClosureDialogProps {
  slug: string;
  projectId: string;
  projectName: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

type Step = 1 | 2 | 3;

export function MatterClosureDialog(props: MatterClosureDialogProps) {
  return (
    <ModuleGate module="matter_closure">
      <MatterClosureDialogInner {...props} />
    </ModuleGate>
  );
}

function MatterClosureDialogInner({
  slug,
  projectId,
  projectName,
  open,
  onOpenChange,
}: MatterClosureDialogProps) {
  const { hasCapability } = useCapabilities();
  const canOverride = hasCapability("OVERRIDE_MATTER_CLOSURE");

  const [step, setStep] = useState<Step>(1);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const form = useForm<CloseMatterFormData>({
    resolver: zodResolver(closeMatterSchema),
    defaultValues: {
      reason: "CONCLUDED",
      notes: "",
      generateClosureLetter: true,
      override: false,
      overrideJustification: "",
    },
  });

  const overrideValue = form.watch("override");

  // SWR handles: conditional fetching (null key when closed), automatic
  // data clearing on error/refetch, deduplication, and cache freshness.
  // When the server action's gates_failed 409 path returns a fresh report,
  // we update it via `mutate(freshReport, { revalidate: false })` rather
  // than re-evaluating through the gate endpoint.
  const {
    data: report,
    error: reportErrorObj,
    isLoading: isLoadingReport,
    mutate: mutateReport,
  } = useSWR<ClosureReport>(open ? ["matter-closure-report", projectId] : null, async () => {
    const result = await evaluateClosureAction(projectId);
    if (!result.success) {
      throw new Error(result.error);
    }
    return result.report;
  });
  const reportError = reportErrorObj instanceof Error ? reportErrorObj.message : null;

  // Reset step/form/submit state whenever the dialog opens.
  // SWR auto-fetches when the key transitions from null → [projectId].
  useEffect(() => {
    if (!open) return;
    setStep(1);
    setSubmitError(null);
    form.reset({
      reason: "CONCLUDED",
      notes: "",
      generateClosureLetter: true,
      override: false,
      overrideJustification: "",
    });
  }, [open, projectId, form]);

  function handleOpenChange(nextOpen: boolean) {
    onOpenChange(nextOpen);
    if (!nextOpen) {
      setStep(1);
      setSubmitError(null);
      setIsSubmitting(false);
    }
  }

  const anyFailing = report?.gates.some((g) => !g.passed) ?? false;

  async function onSubmit(values: CloseMatterFormData) {
    setSubmitError(null);
    setIsSubmitting(true);
    try {
      const trimmedNotes = values.notes?.trim() ?? "";
      const trimmedJustification = values.overrideJustification?.trim() ?? "";
      const result = await closeMatterAction(slug, projectId, {
        reason: values.reason,
        notes: trimmedNotes || undefined,
        generateClosureLetter: values.generateClosureLetter,
        override: values.override,
        overrideJustification: values.override ? trimmedJustification || undefined : undefined,
      });
      if (result.success) {
        toast.success("Matter closed");
        // The server action calls `revalidatePath` on both the project detail
        // and list routes, so RSC re-renders automatically — no router.refresh().
        handleOpenChange(false);
        return;
      }
      if (result.kind === "gates_failed") {
        // 409 — replace the SWR-cached report with the server-returned
        // fresh report and re-render step 1. No toast.
        await mutateReport(result.report, { revalidate: false });
        setStep(1);
        return;
      }
      if (result.kind === "forbidden") {
        const message = result.error ?? "You do not have permission to close this matter.";
        toast.error(message);
        setSubmitError(message);
        return;
      }
      setSubmitError(result.error);
    } catch {
      setSubmitError("An unexpected error occurred");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-xl" data-testid="matter-closure-dialog">
        <DialogHeader>
          <DialogTitle>Close matter</DialogTitle>
          <DialogDescription>
            Review closure gates and confirm closure of{" "}
            <span className="font-medium text-slate-900 dark:text-slate-100">{projectName}</span>.
          </DialogDescription>
        </DialogHeader>

        {step === 1 && (
          <div className="space-y-4" data-testid="matter-closure-step-1">
            {isLoadingReport && (
              <p className="text-sm text-slate-600 dark:text-slate-400">
                <Loader2 className="mr-1.5 inline-block size-4 animate-spin" />
                Evaluating closure gates...
              </p>
            )}
            {reportError && (
              <p role="alert" className="text-destructive text-sm">
                {reportError}
              </p>
            )}
            {report && (
              <MatterClosureReport gates={report.gates} slug={slug} projectId={projectId} />
            )}
            <DialogFooter>
              <Button type="button" variant="plain" onClick={() => handleOpenChange(false)}>
                Cancel
              </Button>
              <Button
                type="button"
                disabled={isLoadingReport || !report}
                onClick={() => {
                  if (!report) return;
                  if (!anyFailing || canOverride) {
                    setStep(2);
                  } else {
                    setStep(3);
                  }
                }}
                data-testid="matter-closure-next-btn"
              >
                Continue
              </Button>
            </DialogFooter>
          </div>
        )}

        {step === 2 && (
          <Form {...form}>
            <form
              onSubmit={form.handleSubmit(onSubmit)}
              className="space-y-4"
              data-testid="matter-closure-step-2"
            >
              <FormField
                control={form.control}
                name="reason"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Reason</FormLabel>
                    <Select onValueChange={field.onChange} value={field.value}>
                      <FormControl>
                        <SelectTrigger data-testid="matter-closure-reason-select">
                          <SelectValue placeholder="Select a reason" />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        <SelectItem value="CONCLUDED">Concluded</SelectItem>
                        <SelectItem value="CLIENT_TERMINATED">Client terminated</SelectItem>
                        <SelectItem value="REFERRED_OUT">Referred out</SelectItem>
                        <SelectItem value="OTHER">Other</SelectItem>
                      </SelectContent>
                    </Select>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="notes"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>
                      Notes <span className="font-normal text-slate-500">(optional)</span>
                    </FormLabel>
                    <FormControl>
                      <Textarea
                        maxLength={5000}
                        placeholder="Additional context..."
                        data-testid="matter-closure-notes-input"
                        {...field}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="generateClosureLetter"
                render={({ field }) => (
                  <FormItem className="flex flex-row items-start gap-3 space-y-0">
                    <FormControl>
                      <Checkbox
                        checked={field.value}
                        onCheckedChange={(v) => field.onChange(v === true)}
                        data-testid="matter-closure-generate-letter-checkbox"
                      />
                    </FormControl>
                    <div className="space-y-0.5">
                      <FormLabel className="text-sm font-medium">Generate closure letter</FormLabel>
                      <p className="text-xs text-slate-500 dark:text-slate-400">
                        A PDF closure letter will be attached to this matter.
                      </p>
                    </div>
                  </FormItem>
                )}
              />

              {anyFailing && canOverride && (
                <>
                  <FormField
                    control={form.control}
                    name="override"
                    render={({ field }) => (
                      <FormItem className="flex flex-row items-start gap-3 space-y-0 rounded-md border border-slate-300 bg-slate-100 p-3 dark:border-slate-700 dark:bg-slate-900/30">
                        <FormControl>
                          <Checkbox
                            checked={field.value}
                            onCheckedChange={(v) => field.onChange(v === true)}
                            data-testid="matter-closure-override-toggle"
                          />
                        </FormControl>
                        <div className="space-y-0.5">
                          <FormLabel className="flex items-center gap-1 text-sm font-medium">
                            <ShieldAlert className="size-3.5 text-teal-600 dark:text-teal-400" />
                            Override failing gates
                          </FormLabel>
                          <p className="text-xs text-slate-600 dark:text-slate-400">
                            This matter has failing gates. Override requires justification and will
                            be logged.
                          </p>
                        </div>
                      </FormItem>
                    )}
                  />
                  {overrideValue && (
                    <FormField
                      control={form.control}
                      name="overrideJustification"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>
                            Override justification
                            <span className="ml-1 text-xs font-normal text-slate-500">
                              (≥ 20 characters)
                            </span>
                          </FormLabel>
                          <FormControl>
                            <Textarea
                              maxLength={5000}
                              placeholder="Explain why closure should proceed despite failing gates..."
                              data-testid="matter-closure-override-justification-input"
                              {...field}
                            />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                  )}
                </>
              )}

              {submitError && (
                <p role="alert" className="text-destructive text-sm">
                  {submitError}
                </p>
              )}

              <DialogFooter>
                <Button
                  type="button"
                  variant="plain"
                  onClick={() => setStep(1)}
                  disabled={isSubmitting}
                >
                  Back
                </Button>
                <Button
                  type="submit"
                  disabled={isSubmitting}
                  data-testid="matter-closure-confirm-close-btn"
                >
                  {isSubmitting ? (
                    <>
                      <Loader2 className="mr-1.5 size-4 animate-spin" />
                      Closing...
                    </>
                  ) : (
                    "Close matter"
                  )}
                </Button>
              </DialogFooter>
            </form>
          </Form>
        )}

        {step === 3 && (
          <div className="space-y-4" data-testid="matter-closure-step-3">
            <div className="rounded-md border border-slate-300 bg-slate-100 p-4 text-sm dark:border-slate-700 dark:bg-slate-900/30">
              <div className="flex items-start gap-2">
                <ShieldAlert
                  className="mt-0.5 size-4 shrink-0 text-teal-600 dark:text-teal-400"
                  aria-hidden="true"
                />
                <div>
                  <p className="font-medium text-slate-900 dark:text-slate-100">
                    Cannot close — resolve gates
                  </p>
                  <p className="mt-1 text-slate-700 dark:text-slate-300">
                    This matter has failing closure gates and you do not have permission to override
                    them. Resolve the failing items and try again.
                  </p>
                </div>
              </div>
            </div>
            <DialogFooter>
              <Button type="button" variant="plain" onClick={() => handleOpenChange(false)}>
                Close
              </Button>
              <Button
                type="button"
                onClick={() => setStep(1)}
                data-testid="matter-closure-back-to-report-btn"
              >
                Back to report
              </Button>
            </DialogFooter>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
