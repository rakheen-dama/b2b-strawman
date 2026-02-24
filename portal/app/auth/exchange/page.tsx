"use client";

import { Suspense, useEffect, useState } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { publicFetch } from "@/lib/api-client";
import { storeAuth } from "@/lib/auth";

interface ExchangeBackendResponse {
  token: string;
  customerId: string;
  customerName: string;
}

function ExchangeHandler() {
  const searchParams = useSearchParams();
  const router = useRouter();

  const token = searchParams.get("token");
  const orgId = searchParams.get("orgId");

  const missingParams = !token || !orgId;
  const [error, setError] = useState<string | null>(
    missingParams ? "Invalid login link. Please request a new one." : null,
  );

  useEffect(() => {
    if (missingParams) return;

    let cancelled = false;

    async function exchange() {
      try {
        const response = await publicFetch("/portal/auth/exchange", {
          method: "POST",
          body: JSON.stringify({ token, orgId }),
        });

        if (!response.ok) {
          if (!cancelled) {
            setError("Link expired or invalid. Please request a new login link.");
          }
          return;
        }

        const data: ExchangeBackendResponse = await response.json();

        storeAuth(data.token, {
          id: data.customerId,
          name: data.customerName,
          email: "",
          orgId: orgId!,
        });

        if (!cancelled) {
          router.push("/projects");
        }
      } catch {
        if (!cancelled) {
          setError("Network error. Please check your connection and try again.");
        }
      }
    }

    exchange();
    return () => {
      cancelled = true;
    };
  }, [missingParams, token, orgId, router]);

  if (error) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-slate-50 px-4">
        <Card className="w-full max-w-md">
          <CardHeader className="text-center">
            <CardTitle className="text-xl">Login Failed</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col items-center gap-4">
            <p className="text-center text-sm text-slate-600">{error}</p>
            <Button variant="accent" asChild>
              <Link href="/login">Back to Login</Link>
            </Button>
          </CardContent>
        </Card>
      </main>
    );
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-slate-50">
      <div className="flex flex-col items-center gap-3">
        <div className="h-8 w-8 animate-spin rounded-full border-2 border-slate-300 border-t-teal-600" />
        <p className="text-sm text-slate-500">Verifying your link...</p>
      </div>
    </main>
  );
}

export default function ExchangePage() {
  return (
    <Suspense
      fallback={
        <main className="flex min-h-screen items-center justify-center bg-slate-50">
          <p className="text-sm text-slate-500">Loading...</p>
        </main>
      }
    >
      <ExchangeHandler />
    </Suspense>
  );
}
