"use client";

import { Suspense, useCallback, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { Button } from "@b2mash/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@b2mash/ui/card";
import { Input } from "@b2mash/ui/input";
import { Label } from "@b2mash/ui/label";
import { publicFetch } from "@/lib/api-client";
import { getLastOrgId } from "@/lib/auth";
import { BRAND_NAME } from "@/lib/brand";
import type { BrandingInfo } from "@/lib/types";

function LoginForm() {
  const searchParams = useSearchParams();
  // GAP-L-66 / Phase 88 U11 (R-1): `orgId` drives BRANDING only and comes from
  // the query first, then the last-known localStorage hint. Only `queryOrgId`
  // ever goes into the request-link body (the fast path); the localStorage
  // hint never does, so a stale hint cannot pin the request to the wrong
  // firm. Bare /login (no query orgId) signs in by email alone.
  const queryOrgId = searchParams.get("orgId");
  const [orgId, setOrgId] = useState<string | null>(queryOrgId);
  const redirectTo = searchParams.get("redirectTo");

  const [email, setEmail] = useState("");
  const [branding, setBranding] = useState<BrandingInfo | null>(null);
  const [brandingLoading, setBrandingLoading] = useState(!!queryOrgId);
  const [submitting, setSubmitting] = useState(false);
  const [success, setSuccess] = useState(false);
  const [magicLink, setMagicLink] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  // GAP-L-66: hydrate orgId from localStorage on mount when the query param is
  // missing. Done in an effect so SSR matches the initial client render.
  useEffect(() => {
    if (queryOrgId) return;
    const lastOrg = getLastOrgId();
    if (lastOrg) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- SSR hydration: localStorage is browser-only; reading in render would cause hydration mismatch (GAP-L-66). TODO: refactor when React 19 hydration patterns mature.
      setOrgId(lastOrg);
      // eslint-disable-next-line react-hooks/set-state-in-effect -- SSR hydration: see above
      setBrandingLoading(true);
    }
  }, [queryOrgId]);

  // GAP-L-66: persist redirectTo to sessionStorage so the auth/exchange page
  // can route the user back to the originally-requested deep-link after the
  // magic-link round-trip.
  useEffect(() => {
    if (redirectTo && typeof window !== "undefined") {
      sessionStorage.setItem("portal_post_login_redirect", redirectTo);
    }
  }, [redirectTo]);

  useEffect(() => {
    if (!orgId) return;

    let cancelled = false;
    async function fetchBranding() {
      try {
        const response = await publicFetch(
          `/portal/branding?orgId=${encodeURIComponent(orgId!)}`,
        );
        if (!response.ok) {
          return;
        }
        const data: BrandingInfo = await response.json();
        if (!cancelled) {
          setBranding(data);
        }
      } catch {
        // Branding fetch failure is non-fatal — show generic branding
      } finally {
        if (!cancelled) {
          setBrandingLoading(false);
        }
      }
    }

    fetchBranding();
    return () => {
      cancelled = true;
    };
  }, [orgId]);

  const handleSubmit = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();
      setError(null);
      setSubmitting(true);

      // Phase 88 U11 (ADR-351): email alone resolves every firm that knows the
      // address. ?orgId= from the link is the fast path; the localStorage hint
      // only brands the page (a shared device's last firm must not narrow the
      // lookup).
      const body = queryOrgId ? { email, orgId: queryOrgId } : { email };

      try {
        const response = await publicFetch("/portal/auth/request-link", {
          method: "POST",
          body: JSON.stringify(body),
        });

        if (response.status === 429) {
          setError("Too many login attempts. Please try again later.");
          return;
        }

        if (!response.ok) {
          setError("Something went wrong. Please try again.");
          return;
        }

        const data = await response.json();
        if (data.magicLink) {
          setMagicLink(data.magicLink);
        }
        setSuccess(true);
      } catch {
        setError("Network error. Please check your connection and try again.");
      } finally {
        setSubmitting(false);
      }
    },
    [email, queryOrgId],
  );

  const orgName = branding?.orgName ?? BRAND_NAME;
  const logoUrl = branding?.logoUrl;

  if (success) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-slate-50 px-4">
        <Card className="w-full max-w-md">
          <CardHeader className="text-center">
            {logoUrl && (
              <img
                src={logoUrl}
                alt={`${orgName} logo`}
                className="mx-auto mb-2 h-12 w-auto"
              />
            )}
            <CardTitle className="text-xl">{orgName}</CardTitle>
          </CardHeader>
          <CardContent className="text-center">
            <div className="rounded-lg bg-teal-50 p-4">
              <p className="text-sm font-medium text-teal-800">
                Check your email.
              </p>
              <p className="mt-1 text-sm text-teal-600">
                If <span className="font-medium">{email}</span> is registered
                for a client portal, we&apos;ve sent a sign-in link. If you work
                with more than one firm, you&apos;ll get one email from each.
              </p>
            </div>
            {magicLink && (
              <div className="mt-4 rounded-lg border border-amber-200 bg-amber-50 p-4">
                <p className="text-xs font-medium text-amber-800">
                  Dev mode — click to sign in:
                </p>
                <a
                  href={magicLink}
                  className="mt-1 block min-h-11 text-sm font-medium text-amber-700 underline break-all"
                >
                  {magicLink}
                </a>
              </div>
            )}
          </CardContent>
        </Card>
      </main>
    );
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-slate-50 px-4">
      <Card className="w-full max-w-md">
        <CardHeader className="text-center">
          {brandingLoading ? (
            <div className="mx-auto mb-2 h-12 w-32 animate-pulse rounded bg-slate-200" />
          ) : (
            <>
              {logoUrl && (
                <img
                  src={logoUrl}
                  alt={`${orgName} logo`}
                  className="mx-auto mb-2 h-12 w-auto"
                />
              )}
              <CardTitle className="text-xl">{orgName}</CardTitle>
            </>
          )}
          <CardDescription>Sign in to your client portal</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="email">Email address</Label>
              <Input
                id="email"
                type="email"
                placeholder="you@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                autoComplete="email"
                autoFocus
                className="h-11 md:h-9"
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
              disabled={submitting}
              className="min-h-11 md:min-h-9"
            >
              {submitting ? "Sending..." : "Send Magic Link"}
            </Button>
          </form>
        </CardContent>
      </Card>
    </main>
  );
}

export default function LoginPage() {
  return (
    <Suspense
      fallback={
        <main className="flex min-h-screen items-center justify-center bg-slate-50">
          <p className="text-sm text-slate-500">Loading...</p>
        </main>
      }
    >
      <LoginForm />
    </Suspense>
  );
}
