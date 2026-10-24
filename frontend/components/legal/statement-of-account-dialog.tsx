"use client";

import { useEffect, useMemo, useState } from "react";
import useSWR from "swr";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Download, Eye, Loader2, Save } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@b2mash/ui/button";
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
import { Input } from "@b2mash/ui/input";
import { Checkbox } from "@/components/ui/checkbox";
import { ModuleGate } from "@/components/module-gate";
import { A4PreviewWrapper } from "@/components/documents/a4-preview-wrapper";
import {
  generateStatementSchema,
  type GenerateStatementFormData,
} from "@/lib/schemas/statement-of-account";
import {
  generateStatementAction,
  listStatementsAction,
} from "@/app/(app)/org/[slug]/matters/[id]/statement-actions";
import { CLIENT_EMAIL_CAVEAT } from "@/lib/client-notification-copy";
import { downloadGeneratedDocumentAction } from "@/app/(app)/org/[slug]/settings/documents/_tabs/template-generation-actions";
import type { StatementResponse } from "@/lib/api/statement-of-account";

interface StatementOfAccountDialogProps {
  slug: string;
  projectId: string;
  projectName: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onGenerated?: () => void;
}

export function StatementOfAccountDialog(props: StatementOfAccountDialogProps) {
  return (
    <ModuleGate module="disbursements">
      <StatementOfAccountDialogInner {...props} />
    </ModuleGate>
  );
}

function todayIso(): string {
  const d = new Date();
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function firstOfCurrentMonth(): string {
  const d = new Date();
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  return `${y}-${m}-01`;
}

function addDaysIso(isoDate: string, days: number): string {
  // Parse YYYY-MM-DD using local-date arithmetic (avoid UTC drift)
  const [y, m, d] = isoDate.split("-").map(Number);
  if (!y || !m || !d) return isoDate;
  const dt = new Date(y, m - 1, d);
  dt.setDate(dt.getDate() + days);
  const yy = dt.getFullYear();
  const mm = String(dt.getMonth() + 1).padStart(2, "0");
  const dd = String(dt.getDate()).padStart(2, "0");
  return `${yy}-${mm}-${dd}`;
}

/** ADR-350: what the next "Preview & Save" click does, stated exactly. */
function intentSummary(share: boolean, notify: boolean): string {
  if (notify) return "Save, share on the client portal and request an email to the client.";
  if (share)
    return "Save and share on the client portal — the client can see it there. No email is sent now.";
  return "Save as internal draft — the client will not see it.";
}

function intentToastDescription(share: boolean, notify: boolean): string {
  if (notify) return "Shared on the client portal — email requested.";
  if (share) return "Shared on the client portal.";
  return "Saved as internal draft — the client will not see it.";
}

function StatementOfAccountDialogInner({
  slug,
  projectId,
  projectName,
  open,
  onOpenChange,
  onGenerated,
}: StatementOfAccountDialogProps) {
  const [previewHtml, setPreviewHtml] = useState<string | null>(null);
  const [lastGenerated, setLastGenerated] = useState<StatementResponse | null>(null);
  const [isGenerating, setIsGenerating] = useState(false);
  const [isDownloading, setIsDownloading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // SWR: fetch prior statements when dialog opens so we can default `periodStart`.
  const { data: priorStatements } = useSWR(
    open ? ["statements-prior", projectId] : null,
    async () => {
      const result = await listStatementsAction(projectId);
      if (!result.success) {
        return null;
      }
      return result.data;
    }
  );

  // Derive the latest `generatedAt` explicitly — order-independent — and
  // stabilise with useMemo so the open/defaultPeriodStart effect doesn't loop
  // when `priorStatements` arrives late and changes identity.
  const defaultPeriodStart = useMemo(() => {
    const latestGeneratedAt = priorStatements?.content
      ?.map((s) => s.generatedAt)
      .filter((v): v is string => Boolean(v))
      .sort((a, b) => new Date(b).getTime() - new Date(a).getTime())[0];
    if (latestGeneratedAt) return addDaysIso(latestGeneratedAt.slice(0, 10), 1);
    return firstOfCurrentMonth();
  }, [priorStatements]);

  const form = useForm<GenerateStatementFormData>({
    resolver: zodResolver(generateStatementSchema),
    defaultValues: {
      periodStart: defaultPeriodStart,
      periodEnd: todayIso(),
      templateId: "",
      shareWithClient: false,
      notifyClient: false,
    },
  });

  const notifyValue = useWatch({ control: form.control, name: "notifyClient" });
  const shareValue = useWatch({ control: form.control, name: "shareWithClient" }) || notifyValue;

  useEffect(() => {
    if (!open) {
      /* eslint-disable react-hooks/set-state-in-effect -- Reset dialog state on close; one-shot, no render loop. */
      setPreviewHtml(null);
      setLastGenerated(null);
      setError(null);
      setIsGenerating(false);
      setIsDownloading(false);
      /* eslint-enable react-hooks/set-state-in-effect */
      // ADR-350 demo safety: a reopened dialog never carries a previous "Email the client" tick.
      form.resetField("shareWithClient");
      form.resetField("notifyClient");
      return;
    }
    // When the dialog (re-)opens, recompute defaults from prior statements.
    // Guard with !isDirty so a late-arriving SWR resolve doesn't clobber
    // edits the user has already made to either date input.
    if (!form.formState.isDirty) {
      form.reset({
        periodStart: defaultPeriodStart,
        periodEnd: todayIso(),
        templateId: "",
        shareWithClient: false,
        notifyClient: false,
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, defaultPeriodStart]);

  function handleOpenChange(next: boolean) {
    onOpenChange(next);
    if (!next) {
      setPreviewHtml(null);
      setLastGenerated(null);
      setError(null);
    }
  }

  async function callGenerate(values: GenerateStatementFormData) {
    const req = {
      periodStart: values.periodStart,
      periodEnd: values.periodEnd,
      templateId: values.templateId && values.templateId.length > 0 ? values.templateId : undefined,
      // notifyClient implies share (backend rule too); send the honest pair.
      shareWithClient: values.shareWithClient || values.notifyClient,
      notifyClient: values.notifyClient,
    };
    return await generateStatementAction(slug, projectId, req);
  }

  async function onPreview(values: GenerateStatementFormData) {
    setError(null);
    setIsGenerating(true);
    try {
      const result = await callGenerate(values);
      if (result.success) {
        setLastGenerated(result.data);
        setPreviewHtml(result.data.htmlPreview ?? null);
        toast.success("Statement generated", {
          description: intentToastDescription(
            values.shareWithClient || values.notifyClient,
            values.notifyClient
          ),
        });
        onGenerated?.();
      } else {
        setError(result.error);
        toast.error(result.error);
      }
    } catch {
      setError("An unexpected error occurred while generating the statement.");
    } finally {
      setIsGenerating(false);
    }
  }

  async function onDownload() {
    if (!lastGenerated) return;
    setIsDownloading(true);
    setError(null);
    try {
      const result = await downloadGeneratedDocumentAction(lastGenerated.id);
      if (result.success && result.pdfBase64) {
        const byteCharacters = atob(result.pdfBase64);
        const byteNumbers = new Array(byteCharacters.length);
        for (let i = 0; i < byteCharacters.length; i++) {
          byteNumbers[i] = byteCharacters.charCodeAt(i);
        }
        const blob = new Blob([new Uint8Array(byteNumbers)], {
          type: "application/pdf",
        });
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download =
          result.fileName ?? `statement-of-account-${projectName.replace(/\s+/g, "-")}.pdf`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
      } else {
        const msg = result.error ?? "Failed to download PDF.";
        setError(msg);
        toast.error(msg);
      }
    } catch {
      setError("An unexpected error occurred while downloading the PDF.");
    } finally {
      setIsDownloading(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent
        className="max-h-[90vh] max-w-3xl overflow-y-auto"
        data-testid="statement-of-account-dialog"
      >
        <DialogHeader>
          <DialogTitle>Generate Statement of Account</DialogTitle>
          <DialogDescription>
            Generate a statement of account for{" "}
            <span className="font-medium text-slate-900 dark:text-slate-100">{projectName}</span>.
            The PDF will be saved to this matter&apos;s documents.
          </DialogDescription>
        </DialogHeader>

        <Form {...form}>
          <form onSubmit={form.handleSubmit(onPreview)} className="space-y-4">
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <FormField
                control={form.control}
                name="periodStart"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Period start</FormLabel>
                    <FormControl>
                      <Input type="date" data-testid="statement-period-start-input" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="periodEnd"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Period end</FormLabel>
                    <FormControl>
                      <Input type="date" data-testid="statement-period-end-input" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            <div className="space-y-3" data-testid="statement-intent">
              <FormField
                control={form.control}
                name="shareWithClient"
                render={({ field }) => (
                  <FormItem className="flex flex-row items-start gap-3 space-y-0">
                    <FormControl>
                      <Checkbox
                        checked={field.value || notifyValue}
                        disabled={notifyValue}
                        onCheckedChange={(v) => field.onChange(v === true)}
                        data-testid="statement-share-checkbox"
                      />
                    </FormControl>
                    <div className="space-y-0.5">
                      <FormLabel className="text-sm font-medium">
                        Share on the client portal
                      </FormLabel>
                      <p className="text-xs text-slate-500 dark:text-slate-400">
                        The client can see this statement when they sign in to the portal.
                      </p>
                    </div>
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="notifyClient"
                render={({ field }) => (
                  <FormItem className="flex flex-row items-start gap-3 space-y-0">
                    <FormControl>
                      <Checkbox
                        checked={field.value}
                        onCheckedChange={(v) => {
                          const next = v === true;
                          field.onChange(next);
                          // Email implies share: tick it; unticking email leaves share ticked.
                          if (next) form.setValue("shareWithClient", true, { shouldDirty: true });
                        }}
                        data-testid="statement-notify-checkbox"
                      />
                    </FormControl>
                    <div className="space-y-0.5">
                      <FormLabel className="text-sm font-medium">Email the client now</FormLabel>
                      <p className="text-xs text-slate-500 dark:text-slate-400">
                        Also shares it, and requests an email with a link to the client&apos;s
                        portal contact.
                      </p>
                      <p className="text-xs text-slate-500 dark:text-slate-400">
                        {CLIENT_EMAIL_CAVEAT}
                      </p>
                    </div>
                  </FormItem>
                )}
              />
              <p
                className="text-sm font-medium text-slate-700 dark:text-slate-300"
                data-testid="statement-intent-summary"
              >
                {intentSummary(shareValue, notifyValue)}
              </p>
            </div>

            <div className="max-h-[50vh] overflow-y-auto" data-testid="statement-preview-container">
              {isGenerating && (
                <div className="flex h-[240px] items-center justify-center rounded-lg border border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900">
                  <p className="text-sm text-slate-500">
                    <Loader2 className="mr-1.5 inline-block size-4 animate-spin" />
                    Generating statement&hellip;
                  </p>
                </div>
              )}
              {!isGenerating && previewHtml && <A4PreviewWrapper html={previewHtml} />}
              {!isGenerating && !previewHtml && !error && (
                <p className="text-xs text-slate-500 italic">
                  Choose a period and click Preview &amp; Save to generate the statement. It will be
                  saved to this matter&apos;s documents.
                </p>
              )}
              {error && (
                <p role="alert" className="text-destructive text-sm">
                  {error}
                </p>
              )}
            </div>

            <DialogFooter>
              <Button
                type="button"
                variant="plain"
                onClick={() => handleOpenChange(false)}
                disabled={isGenerating || isDownloading}
              >
                Close
              </Button>
              {lastGenerated && (
                <Button
                  type="button"
                  variant="outline"
                  onClick={onDownload}
                  disabled={isDownloading || isGenerating}
                  data-testid="statement-download-btn"
                >
                  <Download className="mr-1.5 size-4" />
                  {isDownloading ? "Downloading…" : "Download PDF"}
                </Button>
              )}
              <Button
                type="submit"
                variant="accent"
                disabled={isGenerating}
                data-testid="statement-generate-btn"
              >
                {lastGenerated ? (
                  <>
                    <Save className="mr-1.5 size-4" />
                    {isGenerating ? "Generating…" : "Regenerate"}
                  </>
                ) : (
                  <>
                    <Eye className="mr-1.5 size-4" />
                    {isGenerating ? "Generating…" : "Preview & Save"}
                  </>
                )}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
