"use client";

import React, { useCallback, useEffect, useState } from "react";
import {
  CheckCircle2,
  AlertCircle,
  Loader2,
  Clock,
  FileText,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  getAcceptancePageData,
  getAcceptancePdf,
  acceptDocument,
} from "@/lib/api/portal-acceptance";
import type {
  AcceptancePageData,
  AcceptResponse,
} from "@/lib/api/portal-acceptance";

type PageState =
  | { kind: "loading" }
  | { kind: "not_found" }
  | { kind: "expired"; data: AcceptancePageData }
  | { kind: "revoked"; data: AcceptancePageData }
  | { kind: "ready"; data: AcceptancePageData; pdfUrl: string | null }
  | {
      kind: "accepted";
      data: AcceptancePageData;
      acceptResponse?: AcceptResponse;
    };

function formatDate(iso: string | null): string {
  if (!iso) return "";
  return new Date(iso).toLocaleDateString(undefined, {
    year: "numeric",
    month: "long",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

/** Inner component that takes a resolved token string. Exported for testing. */
export function AcceptancePageContent({ token }: { token: string }) {
  const [state, setState] = useState<PageState>({ kind: "loading" });
  const [name, setName] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      try {
        const data = await getAcceptancePageData(token);
        if (cancelled) return;

        if (data.status === "EXPIRED") {
          setState({ kind: "expired", data });
          return;
        }
        if (data.status === "REVOKED") {
          setState({ kind: "revoked", data });
          return;
        }
        if (data.status === "ACCEPTED") {
          setState({ kind: "accepted", data });
          return;
        }

        // SENT or VIEWED — load PDF
        let pdfUrl: string | null = null;
        try {
          const blob = await getAcceptancePdf(token);
          if (!cancelled) {
            pdfUrl = URL.createObjectURL(blob);
          }
        } catch {
          // PDF load failed — still show page without preview
        }

        if (!cancelled) {
          setState({ kind: "ready", data, pdfUrl });
        }
      } catch (err) {
        if (cancelled) return;
        if (err instanceof Error && err.message === "not_found") {
          setState({ kind: "not_found" });
        } else {
          setState({ kind: "not_found" });
        }
      }
    }

    load();

    return () => {
      cancelled = true;
    };
  }, [token]);

  const handleSubmit = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();
      if (name.trim().length < 2 || submitting) return;

      setSubmitting(true);
      setSubmitError(null);

      try {
        const response = await acceptDocument(token, name.trim());
        setState((prev) => {
          if (prev.kind === "ready") {
            // Revoke the PDF blob URL since we no longer need it
            if (prev.pdfUrl) {
              URL.revokeObjectURL(prev.pdfUrl);
            }
            return {
              kind: "accepted",
              data: { ...prev.data, status: "ACCEPTED" },
              acceptResponse: response,
            };
          }
          return prev;
        });
      } catch (err) {
        setSubmitError(
          err instanceof Error ? err.message : "Failed to accept",
        );
      } finally {
        setSubmitting(false);
      }
    },
    [token, name, submitting],
  );

  // Extract branding from any state that has data
  const data =
    state.kind !== "loading" && state.kind !== "not_found"
      ? state.data
      : null;

  return (
    <div className="min-h-screen bg-slate-50 dark:bg-slate-950">
      <div className="mx-auto max-w-3xl px-4 py-8">
        {/* Header with branding */}
        <div className="mb-8 text-center">
          {data?.orgLogo && (
            <img
              src={data.orgLogo}
              alt={data.orgName ?? "Organization logo"}
              className="mx-auto mb-4 h-12"
            />
          )}
          {data?.orgName && (
            <h1 className="text-2xl font-semibold text-slate-900 dark:text-slate-100">
              {data.orgName}
            </h1>
          )}
        </div>

        {/* Loading state */}
        {state.kind === "loading" && (
          <div className="flex min-h-[400px] items-center justify-center">
            <Loader2 className="h-8 w-8 animate-spin text-slate-400" />
          </div>
        )}

        {/* Not found state */}
        {state.kind === "not_found" && (
          <div className="rounded-lg border border-slate-200 bg-white p-8 text-center shadow-sm dark:border-slate-800 dark:bg-slate-900">
            <AlertCircle className="mx-auto mb-4 h-12 w-12 text-slate-400" />
            <h2 className="mb-2 text-lg font-semibold text-slate-900 dark:text-slate-100">
              Link Not Valid
            </h2>
            <p className="text-slate-600 dark:text-slate-400">
              This link is not valid. It may have already been used or the URL
              may be incorrect.
            </p>
          </div>
        )}

        {/* Expired state */}
        {state.kind === "expired" && (
          <div className="rounded-lg border border-slate-200 bg-white p-8 text-center shadow-sm dark:border-slate-800 dark:bg-slate-900">
            <Clock className="mx-auto mb-4 h-12 w-12 text-amber-500" />
            <h2 className="mb-2 text-lg font-semibold text-slate-900 dark:text-slate-100">
              Request Expired
            </h2>
            <p className="text-slate-600 dark:text-slate-400">
              This acceptance request expired on{" "}
              {formatDate(state.data.expiresAt)}. Please contact{" "}
              {state.data.orgName ?? "the organization"} to request a new link.
            </p>
          </div>
        )}

        {/* Revoked state */}
        {state.kind === "revoked" && (
          <div className="rounded-lg border border-slate-200 bg-white p-8 text-center shadow-sm dark:border-slate-800 dark:bg-slate-900">
            <AlertCircle className="mx-auto mb-4 h-12 w-12 text-slate-400" />
            <h2 className="mb-2 text-lg font-semibold text-slate-900 dark:text-slate-100">
              Request Withdrawn
            </h2>
            <p className="text-slate-600 dark:text-slate-400">
              This acceptance request has been withdrawn. Please contact{" "}
              {state.data.orgName ?? "the organization"} for more information.
            </p>
          </div>
        )}

        {/* Ready state — PDF viewer + acceptance form */}
        {state.kind === "ready" && (
          <div className="space-y-6">
            <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900">
              <div className="mb-4 flex items-center gap-2">
                <FileText className="h-5 w-5 text-slate-500" />
                <h2 className="text-lg font-semibold text-slate-900 dark:text-slate-100">
                  {state.data.documentTitle ??
                    state.data.documentFileName ??
                    "Document"}
                </h2>
              </div>
              <p className="mb-6 text-sm text-slate-600 dark:text-slate-400">
                Please review the document below. When you&apos;re ready, type
                your full name and click &apos;I Accept&apos; to confirm.
              </p>

              {/* PDF viewer */}
              {state.pdfUrl && (
                <iframe
                  src={state.pdfUrl}
                  className="h-[600px] w-full rounded-lg border"
                  title="Document preview"
                />
              )}
            </div>

            {/* Acceptance form */}
            <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900">
              <form onSubmit={handleSubmit} className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="acceptor-name">Full Legal Name</Label>
                  <Input
                    id="acceptor-name"
                    type="text"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder="Type your full name"
                    minLength={2}
                    maxLength={255}
                    required
                  />
                </div>

                {submitError && (
                  <p className="text-sm text-red-600 dark:text-red-400">
                    {submitError}
                  </p>
                )}

                <Button
                  type="submit"
                  disabled={name.trim().length < 2 || submitting}
                  className="w-full"
                  style={
                    state.data.brandColor
                      ? {
                          backgroundColor: state.data.brandColor,
                          borderColor: state.data.brandColor,
                        }
                      : undefined
                  }
                  variant={state.data.brandColor ? "default" : "accent"}
                >
                  {submitting ? (
                    <>
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                      Submitting...
                    </>
                  ) : (
                    "I Accept This Document"
                  )}
                </Button>
              </form>
            </div>
          </div>
        )}

        {/* Accepted state */}
        {state.kind === "accepted" && (
          <div className="rounded-lg border border-slate-200 bg-white p-8 text-center shadow-sm dark:border-slate-800 dark:bg-slate-900">
            <CheckCircle2 className="mx-auto mb-4 h-12 w-12 text-teal-600" />
            <h2 className="mb-2 text-lg font-semibold text-slate-900 dark:text-slate-100">
              Document Accepted
            </h2>
            <p className="text-slate-600 dark:text-slate-400">
              You accepted this document on{" "}
              {formatDate(
                state.acceptResponse?.acceptedAt ?? state.data.acceptedAt,
              )}
              . A confirmation has been sent to your email.
            </p>
          </div>
        )}
      </div>
    </div>
  );
}

/** Next.js page wrapper — unwraps async params via React.use(). */
export default function AcceptancePage({
  params,
}: {
  params: Promise<{ token: string }>;
}) {
  const { token } = React.use(params);
  return <AcceptancePageContent token={token} />;
}
