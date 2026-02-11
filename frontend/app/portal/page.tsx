"use client";

import { useState, useTransition } from "react";
import { useRouter } from "next/navigation";
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

export default function PortalLoginPage() {
  const router = useRouter();
  const [step, setStep] = useState<LoginStep>("email");
  const [email, setEmail] = useState("");
  const [orgSlug, setOrgSlug] = useState("");
  const [magicLink, setMagicLink] = useState<string | null>(null);
  const [token, setToken] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  const handleRequestLink = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    startTransition(async () => {
      try {
        const result = await portalApi.post<MagicLinkResponse>(
          "/portal/auth/request-link",
          { email, orgSlug },
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
        // Extract token from magic link URL or use raw token input
        let tokenValue = token.trim();
        try {
          const url = new URL(tokenValue);
          const paramToken = url.searchParams.get("token");
          if (paramToken) tokenValue = paramToken;
        } catch {
          // Not a URL, use as raw token
        }

        const result = await portalApi.post<PortalAuthResponse>(
          "/portal/auth/exchange",
          { token: tokenValue },
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
    <div className="flex min-h-screen items-center justify-center bg-olive-50 dark:bg-olive-950">
      <div className="w-full max-w-md space-y-8 px-6">
        {/* Header */}
        <div className="text-center">
          <h1 className="font-display text-3xl text-olive-950 dark:text-olive-50">
            DocTeams Portal
          </h1>
          <p className="mt-2 text-olive-600 dark:text-olive-400">
            Access your shared documents and projects
          </p>
        </div>

        {/* Login Card */}
        <div className="rounded-xl border border-olive-200 bg-white p-8 dark:border-olive-800 dark:bg-olive-900">
          {step === "email" && (
            <form onSubmit={handleRequestLink} className="space-y-4">
              <div className="space-y-2">
                <label
                  htmlFor="email"
                  className="text-sm font-medium text-olive-700 dark:text-olive-300"
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
                  className="text-sm font-medium text-olive-700 dark:text-olive-300"
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

              <Button type="submit" className="w-full" disabled={isPending}>
                {isPending ? (
                  <Loader2 className="size-4 animate-spin" />
                ) : (
                  <Mail className="size-4" />
                )}
                Send Magic Link
              </Button>

              <button
                type="button"
                className="w-full text-center text-sm text-olive-500 hover:text-olive-700 dark:text-olive-400 dark:hover:text-olive-200"
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
            <div className="space-y-4">
              <div className="flex flex-col items-center gap-3 text-center">
                <CheckCircle2 className="size-12 text-green-600 dark:text-green-400" />
                <h2 className="text-lg font-semibold text-olive-900 dark:text-olive-100">
                  Magic link generated
                </h2>
                <p className="text-sm text-olive-600 dark:text-olive-400">
                  For the MVP, the magic link is shown below. In production, this would be sent to your email.
                </p>
              </div>

              {magicLink && (
                <div className="space-y-2">
                  <label className="text-sm font-medium text-olive-700 dark:text-olive-300">
                    Your magic link
                  </label>
                  <div className="break-all rounded-lg bg-olive-50 p-3 font-mono text-xs text-olive-700 dark:bg-olive-800 dark:text-olive-300">
                    {magicLink}
                  </div>
                </div>
              )}

              <div className="space-y-2">
                <label
                  htmlFor="token-exchange"
                  className="text-sm font-medium text-olive-700 dark:text-olive-300"
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
                onClick={handleExchangeToken}
                className="w-full"
                disabled={isPending || !token.trim()}
              >
                {isPending ? (
                  <Loader2 className="size-4 animate-spin" />
                ) : null}
                Sign In
              </Button>

              <button
                type="button"
                className="w-full text-center text-sm text-olive-500 hover:text-olive-700 dark:text-olive-400 dark:hover:text-olive-200"
                onClick={() => {
                  setStep("email");
                  setError(null);
                  setToken("");
                }}
              >
                Request a new link
              </button>
            </div>
          )}

          {step === "token" && (
            <form onSubmit={handleExchangeToken} className="space-y-4">
              <div className="space-y-2">
                <label
                  htmlFor="token-direct"
                  className="text-sm font-medium text-olive-700 dark:text-olive-300"
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

              <Button type="submit" className="w-full" disabled={isPending}>
                {isPending ? (
                  <Loader2 className="size-4 animate-spin" />
                ) : null}
                Sign In
              </Button>

              <button
                type="button"
                className="w-full text-center text-sm text-olive-500 hover:text-olive-700 dark:text-olive-400 dark:hover:text-olive-200"
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
    </div>
  );
}
