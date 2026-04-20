"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { ArrowLeft } from "lucide-react";
import { usePortalContext } from "@/hooks/use-portal-context";
import { HourBankCard } from "@/components/retainer/hour-bank-card";
import { ConsumptionList } from "@/components/retainer/consumption-list";
import { Skeleton } from "@/components/ui/skeleton";
import {
  listRetainers,
  type PortalRetainerSummary,
} from "@/lib/api/retainer";

const RETAINER_MODULE = "retainer_agreements";

function DetailSkeleton() {
  return (
    <div className="space-y-8">
      <Skeleton className="h-4 w-32" />
      <Skeleton className="h-56 w-full md:w-96" />
      <Skeleton className="h-48 w-full" />
    </div>
  );
}

export default function RetainerDetailPage() {
  const params = useParams();
  const retainerId = Array.isArray(params.id)
    ? params.id[0]
    : (params.id ?? "");
  const ctx = usePortalContext();
  const router = useRouter();

  const [summary, setSummary] = useState<PortalRetainerSummary | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Module gate — redirect once context has loaded if module disabled.
  useEffect(() => {
    if (ctx && !ctx.enabledModules.includes(RETAINER_MODULE)) {
      router.replace("/home");
    }
  }, [ctx, router]);

  useEffect(() => {
    if (!retainerId || !ctx?.enabledModules.includes(RETAINER_MODULE)) {
      return;
    }
    let cancelled = false;
    setIsLoading(true);
    setError(null);
    setSummary(null);
    (async () => {
      try {
        const retainers = await listRetainers();
        if (cancelled) return;
        const match = retainers.find((r) => r.id === retainerId) ?? null;
        setSummary(match);
        setError(null);
      } catch (err) {
        if (!cancelled) {
          console.error("Failed to load retainer details", err);
          setError("Failed to load retainer details");
        }
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [retainerId, ctx]);

  if (!retainerId) {
    return (
      <div className="space-y-4">
        <Link
          href="/retainer"
          className="inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-700"
        >
          <ArrowLeft className="size-4" />
          Back to retainers
        </Link>
        <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">
          Invalid retainer id.
        </div>
      </div>
    );
  }

  if (isLoading) return <DetailSkeleton />;

  if (error) {
    return (
      <div className="space-y-4">
        <Link
          href="/retainer"
          className="inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-700"
        >
          <ArrowLeft className="size-4" />
          Back to retainers
        </Link>
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          {error}
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <Link
        href="/retainer"
        className="inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-700"
      >
        <ArrowLeft className="size-4" />
        Back to retainers
      </Link>

      {summary ? (
        <>
          <HourBankCard summary={summary} />

          <section>
            <h2 className="font-display mb-4 text-lg font-semibold text-slate-900">
              Consumption
            </h2>
            <ConsumptionList
              retainerId={summary.id}
              periodStart={summary.periodStart}
              periodEnd={summary.periodEnd}
              periodType={summary.periodType}
            />
          </section>
        </>
      ) : (
        <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">
          No retainer found for this id.
        </div>
      )}
    </div>
  );
}
