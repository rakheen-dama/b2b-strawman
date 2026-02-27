"use client";

import { useCallback, useEffect, useState } from "react";
import {
  CheckCircle,
  AlertTriangle,
  XCircle,
  FileText,
  Loader2,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { formatDate } from "@/lib/format";
import { isSafeImageUrl, isValidHexColor } from "@/lib/utils";
import {
  getAcceptancePageData,
  getAcceptancePdfUrl,
  submitAcceptance,
} from "@/lib/api/acceptance";
import type { AcceptancePageData } from "@/lib/types";

interface AcceptancePageProps {
  token: string;
}

type PageState =
  | "LOADING"
  | "ERROR"
  | "PENDING"
  | "SUBMITTING"
  | "ACCEPTED"
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
        } else if (data.status === "EXPIRED") {
          setPageState("EXPIRED");
        } else if (data.status === "REVOKED") {
          setPageState("REVOKED");
        } else {
          setPageState("PENDING");
        }
      } catch (err) {
        if (!cancelled) {
          setError(
            err instanceof Error ? err.message : "Failed to load acceptance page",
          );
          setPageState("ERROR");
        }
      }
    }

    fetchData();
    return () => {
      cancelled = true;
    };
  }, [token]);

  const handleSubmit = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();
      if (name.trim().length < 2) return;

      setPageState("SUBMITTING");
      setError(null);

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

  const pdfUrl = getAcceptancePdfUrl(token);
  const orgName = pageData?.orgName ?? "DocTeams";
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

  return (
    <main className="min-h-screen bg-slate-50 px-4 py-8">
      <div className="mx-auto max-w-3xl space-y-6">
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

        {/* PDF Viewer */}
        <iframe
          src={pdfUrl}
          className="h-[600px] w-full rounded-lg border border-slate-200"
          title="Document PDF"
        />

        {/* Status-Specific Content */}
        {pageState === "EXPIRED" && (
          <Card className="border-amber-200 bg-amber-50">
            <CardContent className="py-6 text-center">
              <AlertTriangle className="mx-auto mb-3 h-10 w-10 text-amber-500" />
              <p className="text-sm font-medium text-amber-800">
                This acceptance request has expired. Please contact {orgName} for
                a new link.
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
                {acceptorName
                  ? `${acceptorName} accepted this document`
                  : "You have accepted this document"}{" "}
                on {acceptedAt ? formatDate(acceptedAt) : "an unknown date"}.
              </p>
            </CardContent>
          </Card>
        )}

        {(pageState === "PENDING" || pageState === "SUBMITTING") && (
          <Card>
            <CardContent className="py-6">
              <form onSubmit={handleSubmit} className="space-y-4">
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
                    disabled={pageState === "SUBMITTING"}
                  />
                </div>

                {error && (
                  <p className="text-sm text-red-600" role="alert">
                    {error}
                  </p>
                )}

                <Button
                  type="submit"
                  variant="accent"
                  disabled={name.trim().length < 2 || pageState === "SUBMITTING"}
                  className="w-full"
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

                <p className="text-xs text-slate-400">
                  Your IP address and browser information will be recorded for
                  verification purposes.
                </p>
              </form>
            </CardContent>
          </Card>
        )}
      </div>
    </main>
  );
}
