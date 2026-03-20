"use client";

import { Suspense, useCallback, useEffect, useRef, useState, useTransition } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Mail, Loader2, CheckCircle2, AlertCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  portalApi,
  setPortalToken,
  setPortalCustomerName,
  PortalApiError,
} from "@/lib/portal-api";
import type { MagicLinkResponse, PortalAuthResponse } from "@/lib/types";

type LoginStep = "email" | "sent" | "token";

interface BrandingData {
  orgName: string | null;
  logoUrl: string | null;
  brandColor: string | null;
  footerText: string | null;
}

function PortalLoginContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [step, setStep] = useState<LoginStep>("email");
  const [email, setEmail] = useState("");
  const [orgSlug, setOrgSlug] = useState(() => searchParams.get("orgId") ?? "");
  const [magicLink, setMagicLink] = useState<string | null>(null);
  const [token, setToken] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();
  const [branding, setBranding] = useState<BrandingData | null>(null);
  const [brandingLoading, setBrandingLoading] = useState(false);
  const lastFetchedOrg = useRef<string>("");

  const fetchBranding = useCallback(async (orgId: string) => {
    if (!orgId.trim() || orgId.trim() === lastFetchedOrg.current) return;
    const trimmed = orgId.trim();
    lastFetchedOrg.current = trimmed;
    setBrandingLoading(true);

    try {
      const backendUrl =
        process.env.NEXT_PUBLIC_BACKEND_URL || "http://localhost:8080";
      const response = await fetch(
        `${backendUrl}/portal/branding?orgId=${encodeURIComponent(trimmed)}`,
      );
      if (response.ok) {
        const data: BrandingData = await response.json();
        setBranding(data);
      } else {
        // Org not found or error — clear branding, fall back to generic
        setBranding(null);
      }
    } catch {
      // Network error — non-fatal, fall back to generic
      setBranding(null);
    } finally {
      setBrandingLoading(false);
    }
  }, []);

  // Re-fetch branding when orgSlug changes (debounced via onBlur) or on initial mount
  // if orgSlug was pre-populated
  useEffect(() => {
    if (orgSlug.trim() && lastFetchedOrg.current !== orgSlug.trim()) {
      const timer = setTimeout(() => fetchBranding(orgSlug), 500);
      return () => clearTimeout(timer);
    }
  }, [orgSlug, fetchBranding]);

  const handleRequestLink = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    startTransition(async () => {
      try {
        const result = await portalApi.post<MagicLinkResponse>(
          "/portal/auth/request-link",
          { email, orgId: orgSlug },
        );
        setMagicLink(result.magicLink ?? null);
        setStep("sent");
      } catch (err) {
        if (err instanceof PortalApiError) {
          setError(err.message || "Failed to send magic link. Please check your email and organization.");
        } else {
          setError("An unexpected error occurred. Please try again.");
        }
      }
    });
  };

  const handleExchangeToken = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    startTransition(async () => {
      try {
        // Extract token and orgId from magic link URL or use raw token input
        let tokenValue = token.trim();
        let exchangeOrgId = orgSlug;
        try {
          const url = new URL(tokenValue);
          const paramToken = url.searchParams.get("token");
          const paramOrg = url.searchParams.get("orgId");
          if (paramToken) tokenValue = paramToken;
          if (paramOrg) exchangeOrgId = paramOrg;
        } catch {
          // Not a URL, use as raw token
        }

        const result = await portalApi.post<PortalAuthResponse>(
          "/portal/auth/exchange",
          { token: tokenValue, orgId: exchangeOrgId },
        );
        setPortalToken(result.token);
        setPortalCustomerName(result.customerName);
        router.push("/portal/projects");
      } catch (err) {
        if (err instanceof PortalApiError) {
          setError(err.message || "Invalid or expired token. Please request a new link.");
        } else {
          setError("An unexpected error occurred. Please try again.");
        }
      }
    });
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 dark:bg-slate-950">
      <div className="w-full max-w-md space-y-8 px-6">
        {/* Header with branding */}
        <div className="text-center">
          {brandingLoading && (
            <div className="mx-auto mb-4 h-12 w-32 animate-pulse rounded bg-slate-200 dark:bg-slate-700" />
          )}
          {!brandingLoading && branding?.logoUrl && (
            <img
              src={branding.logoUrl}
              alt={branding.orgName ?? "Organization logo"}
              className="mx-auto mb-4 h-12"
            />
          )}
          <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
            {branding?.orgName
              ? `${branding.orgName} Portal`
              : "DocTeams Portal"}
          </h1>
          <p className="mt-2 text-slate-600 dark:text-slate-400">
            Access your shared documents and projects
          </p>
        </div>

        {/* Login Card */}
        <div className="overflow-hidden rounded-xl border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
          {/* Brand color stripe */}
          {branding?.brandColor && (
            <div
              className="h-1"
              style={{ backgroundColor: branding.brandColor }}
            />
          )}
          <div className="p-8">
          {step === "email" && (
            <form onSubmit={handleRequestLink} className="space-y-4">
              <div className="space-y-2">
                <label
                  htmlFor="email"
                  className="text-sm font-medium text-slate-700 dark:text-slate-300"
                >
                  Email address
                </label>
                <Input
                  id="email"
                  type="email"
                  placeholder="you@example.com"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                />
              </div>

              <div className="space-y-2">
                <label
                  htmlFor="orgSlug"
                  className="text-sm font-medium text-slate-700 dark:text-slate-300"
                >
                  Organization
                </label>
                <Input
                  id="orgSlug"
                  type="text"
                  placeholder="your-organization"
                  value={orgSlug}
                  onChange={(e) => setOrgSlug(e.target.value)}
                  required
                />
              </div>

              {error && (
                <div className="flex items-center gap-2 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950 dark:text-red-300" role="alert">
                  <AlertCircle className="size-4 shrink-0" />
                  {error}
                </div>
              )}

              <Button
                type="submit"
                className="w-full"
                disabled={isPending}
                style={
                  branding?.brandColor
                    ? {
                        backgroundColor: branding.brandColor,
                        borderColor: branding.brandColor,
                      }
                    : undefined
                }
                variant={branding?.brandColor ? "default" : "accent"}
              >
                {isPending ? (
                  <Loader2 className="size-4 animate-spin" />
                ) : (
                  <Mail className="size-4" />
                )}
                Send Magic Link
              </Button>

              <button
                type="button"
                className="w-full text-center text-sm text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200"
                onClick={() => {
                  setStep("token");
                  setError(null);
                }}
              >
                Already have a token? Enter it here
              </button>
            </form>
          )}

          {step === "sent" && (
            <form onSubmit={handleExchangeToken} className="space-y-4">
              <div className="flex flex-col items-center gap-3 text-center">
                <CheckCircle2 className="size-12 text-green-600 dark:text-green-400" />
                <h2 className="text-lg font-semibold text-slate-900 dark:text-slate-100">
                  Magic link generated
                </h2>
                <p className="text-sm text-slate-600 dark:text-slate-400">
                  For the MVP, the magic link is shown below. In production, this would be sent to your email.
                </p>
              </div>

              {magicLink && (
                <div className="space-y-2">
                  <label className="text-sm font-medium text-slate-700 dark:text-slate-300">
                    Your magic link
                  </label>
                  <div className="break-all rounded-lg bg-slate-50 p-3 font-mono text-xs text-slate-700 dark:bg-slate-800 dark:text-slate-300">
                    {magicLink}
                  </div>
                </div>
              )}

              <div className="space-y-2">
                <label
                  htmlFor="token-exchange"
                  className="text-sm font-medium text-slate-700 dark:text-slate-300"
                >
                  Paste the link or token to sign in
                </label>
                <Input
                  id="token-exchange"
                  type="text"
                  placeholder="Paste magic link or token"
                  value={token}
                  onChange={(e) => setToken(e.target.value)}
                />
              </div>

              {error && (
                <div className="flex items-center gap-2 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950 dark:text-red-300" role="alert">
                  <AlertCircle className="size-4 shrink-0" />
                  {error}
                </div>
              )}

              <Button
                type="submit"
                className="w-full"
                disabled={isPending || !token.trim()}
                style={
                  branding?.brandColor
                    ? {
                        backgroundColor: branding.brandColor,
                        borderColor: branding.brandColor,
                      }
                    : undefined
                }
                variant={branding?.brandColor ? "default" : "accent"}
              >
                {isPending ? (
                  <Loader2 className="size-4 animate-spin" />
                ) : null}
                Sign In
              </Button>

              <button
                type="button"
                className="w-full text-center text-sm text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200"
                onClick={() => {
                  setStep("email");
                  setError(null);
                  setToken("");
                }}
              >
                Request a new link
              </button>
            </form>
          )}

          {step === "token" && (
            <form onSubmit={handleExchangeToken} className="space-y-4">
              <div className="space-y-2">
                <label
                  htmlFor="token-direct"
                  className="text-sm font-medium text-slate-700 dark:text-slate-300"
                >
                  Magic link or token
                </label>
                <Input
                  id="token-direct"
                  type="text"
                  placeholder="Paste your magic link or token"
                  value={token}
                  onChange={(e) => setToken(e.target.value)}
                  required
                />
              </div>

              {error && (
                <div className="flex items-center gap-2 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950 dark:text-red-300" role="alert">
                  <AlertCircle className="size-4 shrink-0" />
                  {error}
                </div>
              )}

              <Button
                type="submit"
                className="w-full"
                disabled={isPending}
                style={
                  branding?.brandColor
                    ? {
                        backgroundColor: branding.brandColor,
                        borderColor: branding.brandColor,
                      }
                    : undefined
                }
                variant={branding?.brandColor ? "default" : "accent"}
              >
                {isPending ? (
                  <Loader2 className="size-4 animate-spin" />
                ) : null}
                Sign In
              </Button>

              <button
                type="button"
                className="w-full text-center text-sm text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200"
                onClick={() => {
                  setStep("email");
                  setError(null);
                  setToken("");
                }}
              >
                Back to email login
              </button>
            </form>
          )}
          </div>
        </div>

        {/* Footer text from branding */}
        {branding?.footerText && (
          <p className="text-center text-xs text-slate-500 dark:text-slate-400">
            {branding.footerText}
          </p>
        )}
      </div>
    </div>
  );
}

export default function PortalLoginPage() {
  return (
    <Suspense fallback={null}>
      <PortalLoginContent />
    </Suspense>
  );
}
