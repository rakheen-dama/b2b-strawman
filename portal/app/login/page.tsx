"use client";

import { Suspense, useCallback, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { publicFetch } from "@/lib/api-client";
import type { BrandingInfo } from "@/lib/types";

function LoginForm() {
  const searchParams = useSearchParams();
  const orgId = searchParams.get("orgId");

  const [email, setEmail] = useState("");
  const [branding, setBranding] = useState<BrandingInfo | null>(null);
  const [brandingLoading, setBrandingLoading] = useState(!!orgId);
  const [submitting, setSubmitting] = useState(false);
  const [success, setSuccess] = useState(false);
  const [magicLink, setMagicLink] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

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

      try {
        const response = await publicFetch("/portal/auth/request-link", {
          method: "POST",
          body: JSON.stringify({ email, orgId }),
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
    [email, orgId],
  );

  const orgName = branding?.orgName ?? "DocTeams";
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
                Check your email for a login link.
              </p>
              <p className="mt-1 text-sm text-teal-600">
                We sent a link to <span className="font-medium">{email}</span>.
              </p>
            </div>
            {magicLink && (
              <div className="mt-4 rounded-lg border border-amber-200 bg-amber-50 p-4">
                <p className="text-xs font-medium text-amber-800">Dev mode — click to sign in:</p>
                <a
                  href={magicLink}
                  className="mt-1 block text-sm font-medium text-amber-700 underline break-all"
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
          <CardDescription>
            Sign in to your client portal
          </CardDescription>
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
              />
            </div>

            {error && (
              <p className="text-sm text-red-600" role="alert">
                {error}
              </p>
            )}

            <Button type="submit" variant="accent" disabled={submitting}>
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
