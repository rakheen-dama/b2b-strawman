"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { usePortalContext } from "@/hooks/use-portal-context";
import { MatterSelector } from "@/components/trust/matter-selector";
import { Skeleton } from "@/components/ui/skeleton";
import {
  getTrustSummary,
  type PortalTrustMatterSummary,
} from "@/lib/api/trust";

function IndexSkeleton() {
  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {Array.from({ length: 3 }).map((_, i) => (
        <Skeleton key={i} className="h-36 w-full" />
      ))}
    </div>
  );
}

export default function TrustIndexPage() {
  const ctx = usePortalContext();
  const router = useRouter();
  const [matters, setMatters] = useState<PortalTrustMatterSummary[] | null>(
    null,
  );
  const [error, setError] = useState<string | null>(null);

  // Module gate: redirect once the portal context has loaded and the
  // trust_accounting module is not enabled. This guards against the
  // empty-page flash — the backend also 404s, but this avoids the round-trip.
  useEffect(() => {
    if (ctx && !ctx.enabledModules.includes("trust_accounting")) {
      router.replace("/home");
    }
  }, [ctx, router]);

  // Fetch summary.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await getTrustSummary();
        if (!cancelled) setMatters(res.matters);
      } catch (err) {
        if (!cancelled) {
          setError(
            err instanceof Error ? err.message : "Failed to load trust summary",
          );
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  // Auto-redirect when there's exactly one matter with activity.
  useEffect(() => {
    if (matters && matters.length === 1) {
      router.replace(`/trust/${matters[0].matterId}`);
    }
  }, [matters, router]);

  return (
    <div>
      <h1 className="font-display mb-6 text-2xl font-semibold text-slate-900">
        Trust
      </h1>
      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          {error}
        </div>
      )}
      {!error && matters === null && <IndexSkeleton />}
      {!error && matters !== null && matters.length !== 1 && (
        <MatterSelector matters={matters} />
      )}
    </div>
  );
}
