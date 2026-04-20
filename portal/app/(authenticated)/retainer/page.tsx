"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { FileText } from "lucide-react";
import { usePortalContext } from "@/hooks/use-portal-context";
import { HourBankCard } from "@/components/retainer/hour-bank-card";
import { Skeleton } from "@/components/ui/skeleton";
import {
  listRetainers,
  type PortalRetainerSummary,
} from "@/lib/api/retainer";

function IndexSkeleton() {
  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {Array.from({ length: 3 }).map((_, i) => (
        <Skeleton key={i} className="h-56 w-full" />
      ))}
    </div>
  );
}

export default function RetainerIndexPage() {
  const ctx = usePortalContext();
  const router = useRouter();
  const [retainers, setRetainers] = useState<PortalRetainerSummary[] | null>(
    null,
  );
  const [error, setError] = useState<string | null>(null);

  // Module gate: redirect once the portal context has loaded and the
  // retainer_agreements module is not enabled. The backend also 404s
  // (source of truth), but this avoids the round-trip when the entitlement
  // is already known.
  useEffect(() => {
    if (ctx && !ctx.enabledModules.includes("retainer_agreements")) {
      router.replace("/home");
    }
  }, [ctx, router]);

  // Gate the fetch on the entitlement so we never race the redirect.
  useEffect(() => {
    if (!ctx?.enabledModules.includes("retainer_agreements")) return;
    let cancelled = false;
    (async () => {
      try {
        const data = await listRetainers();
        if (!cancelled) setRetainers(data);
      } catch (err) {
        if (!cancelled) {
          setError(
            err instanceof Error
              ? err.message
              : "Failed to load retainer summary",
          );
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [ctx]);

  return (
    <div>
      <h1 className="font-display mb-6 text-2xl font-semibold text-slate-900">
        Retainer
      </h1>

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          {error}
        </div>
      )}

      {!error && retainers === null && <IndexSkeleton />}

      {!error && retainers !== null && retainers.length === 0 && (
        <div
          className="flex flex-col items-center justify-center rounded-lg border border-slate-200 bg-white py-16 text-center"
          data-testid="retainer-empty"
        >
          <FileText
            className="mb-4 size-12 text-slate-300"
            aria-hidden="true"
          />
          <p className="text-lg font-medium text-slate-600">
            No active retainer
          </p>
          <p className="mt-1 text-sm text-slate-500">
            Hour-bank balances and consumption will appear here once your firm
            sets up a retainer agreement.
          </p>
        </div>
      )}

      {!error && retainers !== null && retainers.length > 0 && (
        <div
          className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3"
          aria-label="Active retainers"
        >
          {retainers.map((retainer) => (
            <HourBankCard
              key={retainer.id}
              summary={retainer}
              href={`/retainer/${retainer.id}`}
            />
          ))}
        </div>
      )}
    </div>
  );
}
