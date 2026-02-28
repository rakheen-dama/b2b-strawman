"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Mail, ArrowRight, Loader2, CheckCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
  CardFooter,
} from "@/components/ui/card";

const TOKEN_KEY = "portal_token";
const CUSTOMER_NAME_KEY = "portal_customer_name";

type Step = "email" | "link-sent" | "exchanging";

export default function PortalLoginPage() {
  const router = useRouter();
  const [step, setStep] = useState<Step>("email");
  const [email, setEmail] = useState("");
  const [orgId, setOrgId] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  async function handleRequestLink(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setIsSubmitting(true);

    try {
      const backendUrl = process.env.NEXT_PUBLIC_BACKEND_URL ?? "";
      const res = await fetch(`${backendUrl}/portal/auth/request-link`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, orgId }),
      });

      if (!res.ok) {
        const body = await res.json().catch(() => null);
        throw new Error(body?.detail ?? "Failed to request magic link");
      }

      const data = await res.json();
      setMessage(data.message);
      setStep("link-sent");

      // In dev mode, if magicLink is returned, auto-exchange
      if (data.magicLink) {
        setStep("exchanging");
        await exchangeToken(data.magicLink);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Something went wrong");
    } finally {
      setIsSubmitting(false);
    }
  }

  async function exchangeToken(magicLinkPath: string) {
    try {
      // Parse token and orgId from the magic link path
      const url = new URL(magicLinkPath, window.location.origin);
      const token = url.searchParams.get("token");
      const linkOrgId = url.searchParams.get("orgId");

      if (!token || !linkOrgId) {
        throw new Error("Invalid magic link");
      }

      const backendUrl = process.env.NEXT_PUBLIC_BACKEND_URL ?? "";
      const res = await fetch(`${backendUrl}/portal/auth/exchange`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ token, orgId: linkOrgId }),
      });

      if (!res.ok) {
        const body = await res.json().catch(() => null);
        throw new Error(body?.detail ?? "Failed to sign in");
      }

      const data = await res.json();
      sessionStorage.setItem(TOKEN_KEY, data.token);
      sessionStorage.setItem(CUSTOMER_NAME_KEY, data.customerName ?? "");
      router.push("/portal/projects");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Sign in failed");
      setStep("email");
    }
  }

  return (
    <div className="flex min-h-[60vh] items-center justify-center">
      <Card className="w-full max-w-md">
        <CardHeader className="text-center">
          <div className="mx-auto mb-2 flex h-12 w-12 items-center justify-center rounded-xl bg-teal-600 text-lg font-bold text-white">
            D
          </div>
          <CardTitle className="text-xl">Client Portal</CardTitle>
          <CardDescription>
            Sign in to view your projects and documents.
          </CardDescription>
        </CardHeader>

        {step === "email" && (
          <form onSubmit={handleRequestLink}>
            <CardContent className="space-y-4">
              <div className="space-y-2">
                <label
                  htmlFor="orgId"
                  className="text-sm font-medium text-slate-700 dark:text-slate-300"
                >
                  Organization ID
                </label>
                <Input
                  id="orgId"
                  type="text"
                  placeholder="org_..."
                  value={orgId}
                  onChange={(e) => setOrgId(e.target.value)}
                  required
                />
              </div>
              <div className="space-y-2">
                <label
                  htmlFor="email"
                  className="text-sm font-medium text-slate-700 dark:text-slate-300"
                >
                  Email address
                </label>
                <div className="relative">
                  <Mail className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
                  <Input
                    id="email"
                    type="email"
                    placeholder="you@company.com"
                    className="pl-9"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    required
                  />
                </div>
              </div>
              {error && (
                <p className="text-sm text-red-600 dark:text-red-400">
                  {error}
                </p>
              )}
            </CardContent>
            <CardFooter>
              <Button
                type="submit"
                className="w-full"
                disabled={isSubmitting || !email || !orgId}
              >
                {isSubmitting ? (
                  <>
                    <Loader2 className="mr-2 size-4 animate-spin" />
                    Sending link...
                  </>
                ) : (
                  <>
                    Send Magic Link
                    <ArrowRight className="ml-2 size-4" />
                  </>
                )}
              </Button>
            </CardFooter>
          </form>
        )}

        {step === "link-sent" && (
          <CardContent className="space-y-4 text-center">
            <CheckCircle className="mx-auto size-10 text-green-500" />
            <p className="text-sm text-slate-600 dark:text-slate-400">
              {message ?? "Check your email for a sign-in link."}
            </p>
            <Button
              variant="ghost"
              onClick={() => {
                setStep("email");
                setError(null);
              }}
              className="text-sm"
            >
              Try a different email
            </Button>
          </CardContent>
        )}

        {step === "exchanging" && (
          <CardContent className="space-y-4 text-center">
            <Loader2 className="mx-auto size-10 animate-spin text-teal-600" />
            <p className="text-sm text-slate-600 dark:text-slate-400">
              Signing you in...
            </p>
          </CardContent>
        )}
      </Card>
    </div>
  );
}
