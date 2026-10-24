"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import {
  CheckCircle,
  AlertTriangle,
  XCircle,
  ExternalLink,
  FileText,
  Loader2,
} from "lucide-react";
import { Button } from "@b2mash/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@b2mash/ui/card";
import { Input } from "@b2mash/ui/input";
import { Label } from "@b2mash/ui/label";
import { Skeleton } from "@b2mash/ui/skeleton";
import { StickyActionBar } from "@/components/ui/sticky-action-bar";
import { BRAND_NAME } from "@/lib/brand";
import { formatDateTime } from "@b2mash/shared/format";
import { isSafeImageUrl, isValidHexColor } from "@/lib/utils";
import {
  DeclineConflictError,
  declineAcceptance,
  getAcceptancePageData,
  getAcceptancePdfUrl,
  submitAcceptance,
} from "@/lib/api/acceptance";
import type { AcceptancePageData } from "@/lib/types";
import { DeclineDialog } from "./decline-dialog";

interface AcceptancePageProps {
  token: string;
}

type PageState =
  | "LOADING"
  | "ERROR"
  | "PENDING"
  | "SUBMITTING"
  | "DECLINING"
  | "ACCEPTED"
  | "DECLINED"
  | "EXPIRED"
  | "REVOKED";

function LoadingSkeleton() {
  return (
    <div className="space-y-6">
      <Skeleton className="mx-auto h-12 w-32" />
      <Skeleton className="h-6 w-1/2" />
      <Skeleton className="h-[600px] w-full" />
      <Skeleton className="h-48 w-full" />
    </div>
  );
}

export function AcceptancePage({ token }: AcceptancePageProps) {
  const [pageState, setPageState] = useState<PageState>("LOADING");
  const [pageData, setPageData] = useState<AcceptancePageData | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [acceptedAt, setAcceptedAt] = useState<string | null>(null);
  const [acceptorName, setAcceptorName] = useState<string | null>(null);
  const [declinedAt, setDeclinedAt] = useState<string | null>(null);
  // Shown only in this session — PortalPageData carries no decline reason (R-6).
  const [declineReason, setDeclineReason] = useState<string | null>(null);
  const [declineOpen, setDeclineOpen] = useState(false);
  const [declineError, setDeclineError] = useState<string | null>(null);
  // Page-level message after a 409 decline (the re-fetched state may hide the form).
  const [notice, setNotice] = useState<string | null>(null);
  // Bumped to re-fetch the page data after a 409, so the true state renders.
  const [reloadKey, setReloadKey] = useState(0);
  // Whichever Decline button opened the dialog (desktop or mobile sticky bar).
  const declineOpenerRef = useRef<HTMLButtonElement | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function fetchData() {
      try {
        const data = await getAcceptancePageData(token);
        if (cancelled) return;

        setPageData(data);

        if (data.status === "ACCEPTED") {
          setAcceptedAt(data.acceptedAt);
          setAcceptorName(data.acceptorName);
          setPageState("ACCEPTED");
        } else if (data.status === "DECLINED") {
          setDeclinedAt(data.declinedAt ?? null);
          setPageState("DECLINED");
        } else if (data.status === "EXPIRED") {
          setPageState("EXPIRED");
        } else if (data.status === "REVOKED") {
          setPageState("REVOKED");
        } else if (
          data.status === "PENDING" ||
          data.status === "SENT" ||
          data.status === "VIEWED"
        ) {
          // "PENDING" here is the UI state = "awaiting user action".
          // Backend auto-flips PENDING→SENT on dispatch and SENT→VIEWED on
          // first portal fetch, so SENT/VIEWED are the states portal actually
          // observes. Render the same accept form for all three.
          setPageState("PENDING");
        } else {
          // Unknown status from backend — treat as error rather than showing the form
          setError(
            "Unable to process this acceptance request. Please contact the sender.",
          );
          setPageState("ERROR");
        }
      } catch (err) {
        if (!cancelled) {
          setError(
            err instanceof Error
              ? err.message
              : "Failed to load acceptance page",
          );
          setPageState("ERROR");
        }
      }
    }

    fetchData();
    return () => {
      cancelled = true;
    };
  }, [token, reloadKey]);

  const handleSubmit = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();
      if (name.trim().length < 2) return;

      setPageState("SUBMITTING");
      setError(null);
      setNotice(null);

      try {
        const response = await submitAcceptance(token, name.trim());
        setAcceptedAt(response.acceptedAt);
        setAcceptorName(response.acceptorName);
        setPageState("ACCEPTED");
      } catch (err) {
        setError(
          err instanceof Error ? err.message : "Failed to submit acceptance",
        );
        setPageState("PENDING");
      }
    },
    [token, name],
  );

  const handleDecline = useCallback(
    async (reason: string) => {
      setPageState("DECLINING");
      setDeclineError(null);
      setNotice(null);

      try {
        const response = await declineAcceptance(token, reason);
        setDeclinedAt(response.declinedAt);
        setDeclineReason(reason.trim() || null);
        setDeclineOpen(false);
        setPageState("DECLINED");
      } catch (err) {
        if (err instanceof DeclineConflictError) {
          // Declined elsewhere, accepted, revoked or expired: re-fetch so the true
          // state renders (the bar stays disabled in DECLINING until it lands).
          setDeclineOpen(false);
          setNotice(err.message);
          setReloadKey((key) => key + 1);
        } else {
          setDeclineError(
            err instanceof Error
              ? err.message
              : "Failed to decline. Please try again.",
          );
          setPageState("PENDING");
        }
      }
    },
    [token],
  );

  function openDeclineDialog(e: React.MouseEvent<HTMLButtonElement>) {
    declineOpenerRef.current = e.currentTarget;
    setDeclineError(null);
    setDeclineOpen(true);
  }

  const pdfUrl = getAcceptancePdfUrl(token);
  const orgName = pageData?.orgName ?? BRAND_NAME;
  const logoUrl = pageData?.orgLogo;
  const brandColor = pageData?.brandColor;
  const showLogo = logoUrl && isSafeImageUrl(logoUrl);
  const accentColor =
    brandColor && isValidHexColor(brandColor) ? brandColor : undefined;

  if (pageState === "LOADING") {
    return (
      <main className="flex min-h-screen items-center justify-center bg-slate-50 px-4">
        <div className="w-full max-w-3xl">
          <LoadingSkeleton />
        </div>
      </main>
    );
  }

  if (pageState === "ERROR") {
    return (
      <main className="flex min-h-screen items-center justify-center bg-slate-50 px-4">
        <Card className="w-full max-w-md">
          <CardContent className="py-8 text-center">
            <AlertTriangle className="mx-auto mb-4 h-12 w-12 text-red-500" />
            <p className="text-sm text-red-600">{error}</p>
          </CardContent>
        </Card>
      </main>
    );
  }

  const showStickyAcceptBar =
    pageState === "PENDING" ||
    pageState === "SUBMITTING" ||
    pageState === "DECLINING";
  const isBusy = pageState === "SUBMITTING" || pageState === "DECLINING";

  // Whole sentences as single strings so the formatter can never split the trailing "."
  // onto its own JSX text node.
  const acceptedBy = acceptorName
    ? `${acceptorName} accepted this document`
    : "You have accepted this document";
  const acceptedSentence = `${acceptedBy} on ${acceptedAt ? formatDateTime(acceptedAt) : "an unknown date"}.`;
  const declinedSentence = declinedAt
    ? `You declined this document on ${formatDateTime(declinedAt)}.`
    : "You declined this document.";

  return (
    <main className="min-h-screen bg-slate-50 px-4 py-8">
      <div
        className={`mx-auto max-w-3xl space-y-6 ${
          showStickyAcceptBar ? "pb-24 md:pb-0" : ""
        }`}
      >
        {/* Org Branding Header */}
        <div className="text-center">
          {showLogo && (
            <img
              src={logoUrl}
              alt={`${orgName} logo`}
              className="mx-auto mb-2 h-12 w-auto"
            />
          )}
          <h1
            className="text-xl font-semibold text-slate-900"
            style={accentColor ? { color: accentColor } : undefined}
          >
            {orgName}
          </h1>
        </div>

        {/* Document Info */}
        <Card>
          <CardHeader className="pb-3">
            <div className="flex items-center gap-2">
              <FileText className="h-5 w-5 text-slate-500" />
              <CardTitle className="text-lg">
                {pageData?.documentTitle}
              </CardTitle>
            </div>
            <p className="text-sm text-slate-500">
              {pageData?.documentFileName}
            </p>
          </CardHeader>
        </Card>

        {/* PDF Viewer — iframe is the primary view; the link is a fallback (ADR-352) */}
        <div className="space-y-2">
          <iframe
            src={pdfUrl}
            className="h-[70vh] w-full rounded-lg border border-slate-200 md:h-[600px]"
            title="Document PDF"
          />
          <div className="flex justify-end">
            <a
              href={pdfUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex min-h-11 items-center gap-1.5 rounded-md text-sm font-medium text-teal-600 hover:text-teal-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-teal-500"
            >
              <ExternalLink className="size-4" aria-hidden="true" />
              Open in new tab
            </a>
          </div>
        </div>

        {notice && (
          <div
            role="alert"
            className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800"
          >
            {notice}
          </div>
        )}

        {/* Status-Specific Content */}
        {pageState === "EXPIRED" && (
          <Card className="border-amber-200 bg-amber-50">
            <CardContent className="py-6 text-center">
              <AlertTriangle className="mx-auto mb-3 h-10 w-10 text-amber-500" />
              <p className="text-sm font-medium text-amber-800">
                This acceptance request has expired. Please contact {orgName}{" "}
                for a new link.
              </p>
            </CardContent>
          </Card>
        )}

        {pageState === "REVOKED" && (
          <Card className="border-red-200 bg-red-50">
            <CardContent className="py-6 text-center">
              <XCircle className="mx-auto mb-3 h-10 w-10 text-red-500" />
              <p className="text-sm font-medium text-red-700">
                This acceptance request has been revoked by {orgName}.
              </p>
            </CardContent>
          </Card>
        )}

        {pageState === "ACCEPTED" && (
          <Card className="border-green-200 bg-green-50">
            <CardContent className="py-6 text-center">
              <CheckCircle className="mx-auto mb-3 h-10 w-10 text-green-500" />
              <p className="text-sm font-medium text-green-700">
                {acceptedSentence}
              </p>
            </CardContent>
          </Card>
        )}

        {pageState === "DECLINED" && (
          <Card className="border-slate-200 bg-slate-100">
            <CardContent className="py-6 text-center">
              <XCircle className="mx-auto mb-3 h-10 w-10 text-slate-500" />
              <p className="text-sm font-medium text-slate-800">
                {declinedSentence}
              </p>
              {declineReason && (
                <p className="mt-2 text-sm break-words text-slate-600">
                  Your reason: &ldquo;{declineReason}&rdquo;
                </p>
              )}
              <p className="mt-2 text-sm text-slate-600">
                {orgName} has been notified.
              </p>
            </CardContent>
          </Card>
        )}

        {showStickyAcceptBar && (
          <Card>
            <CardContent className="py-6">
              <form
                id="accept-form"
                onSubmit={handleSubmit}
                className="space-y-4"
              >
                <p className="text-sm text-slate-600">
                  By typing your name below, you confirm that you have reviewed
                  and accept this document.
                </p>

                <div className="flex flex-col gap-2">
                  <Label htmlFor="acceptor-name">Full name</Label>
                  <Input
                    id="acceptor-name"
                    type="text"
                    placeholder="Your full name"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    required
                    minLength={2}
                    autoComplete="name"
                    autoFocus
                    disabled={isBusy}
                    className="h-11 md:h-9"
                  />
                </div>

                {error && (
                  <p className="text-sm text-red-600" role="alert">
                    {error}
                  </p>
                )}

                {/* Desktop inline submit — hidden on mobile (mobile uses StickyActionBar) */}
                <Button
                  type="submit"
                  variant="accent"
                  disabled={name.trim().length < 2 || isBusy}
                  className="hidden min-h-11 w-full md:inline-flex"
                >
                  {pageState === "SUBMITTING" ? (
                    <>
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                      Submitting...
                    </>
                  ) : (
                    "I Accept"
                  )}
                </Button>

                {/* Desktop decline — mobile uses the sticky bar */}
                <Button
                  type="button"
                  variant="outline"
                  disabled={isBusy}
                  onClick={openDeclineDialog}
                  className="hidden min-h-11 w-full md:inline-flex"
                >
                  Decline
                </Button>

                <p className="text-xs text-slate-400">
                  Your IP address and browser information will be recorded for
                  verification purposes.
                </p>
              </form>
            </CardContent>
          </Card>
        )}
      </div>

      {/* Mobile sticky accept bar — associates with form via `form` attribute */}
      {showStickyAcceptBar && (
        <StickyActionBar>
          <Button
            type="submit"
            form="accept-form"
            variant="accent"
            disabled={name.trim().length < 2 || isBusy}
            aria-label="Accept document"
            className="min-h-11 w-full flex-1"
          >
            {pageState === "SUBMITTING" ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Submitting...
              </>
            ) : (
              "I Accept"
            )}
          </Button>
          <Button
            type="button"
            variant="outline"
            disabled={isBusy}
            onClick={openDeclineDialog}
            aria-label="Decline this document"
            className="min-h-11 flex-1"
          >
            Decline
          </Button>
        </StickyActionBar>
      )}

      <DeclineDialog
        open={declineOpen}
        onOpenChange={setDeclineOpen}
        orgName={orgName}
        isSubmitting={pageState === "DECLINING"}
        error={declineError}
        onConfirm={handleDecline}
        returnFocusRef={declineOpenerRef}
      />
    </main>
  );
}
