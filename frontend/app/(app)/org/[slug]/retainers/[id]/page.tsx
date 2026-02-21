import { getAuthContext } from "@/lib/auth";
import { handleApiError } from "@/lib/api";
import { fetchRetainer } from "@/lib/api/retainers";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { RetainerStatusBadge } from "@/components/retainers/retainer-status-badge";
import { RetainerProgress } from "@/components/retainers/retainer-progress";
import { PeriodHistoryTable } from "@/components/retainers/period-history-table";
import { EditRetainerDialog } from "@/components/retainers/edit-retainer-dialog";
import { RetainerDetailActions } from "@/components/retainers/retainer-detail-actions";
import { FREQUENCY_LABELS, TYPE_LABELS } from "@/lib/retainer-constants";
import { formatLocalDate, formatCurrency } from "@/lib/format";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { ArrowLeft, Pencil, AlertTriangle } from "lucide-react";
import Link from "next/link";
import type { RetainerResponse, PeriodSummary } from "@/lib/api/retainers";

export default async function RetainerDetailPage({
  params,
}: {
  params: Promise<{ slug: string; id: string }>;
}) {
  const { slug, id } = await params;
  const { orgRole } = await getAuthContext();

  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  let retainer: RetainerResponse;
  try {
    retainer = await fetchRetainer(id);
  } catch (error) {
    handleApiError(error);
  }

  // Combine currentPeriod with recentPeriods for the history table
  const allPeriods: PeriodSummary[] = [];
  if (retainer.currentPeriod) {
    allPeriods.push(retainer.currentPeriod);
  }
  for (const p of retainer.recentPeriods) {
    if (!retainer.currentPeriod || p.id !== retainer.currentPeriod.id) {
      allPeriods.push(p);
    }
  }

  return (
    <div className="space-y-8">
      {/* Back link */}
      <div>
        <Link
          href={`/org/${slug}/retainers`}
          className="inline-flex items-center text-sm text-slate-600 transition-colors hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ArrowLeft className="mr-1.5 size-4" />
          Back to Retainers
        </Link>
      </div>

      {/* Header */}
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="font-display text-2xl text-slate-950 dark:text-slate-50">
              {retainer.name}
            </h1>
            <RetainerStatusBadge status={retainer.status} />
          </div>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            {retainer.customerName}
          </p>
          <div className="mt-3 flex flex-wrap items-center gap-3">
            <span className="inline-flex items-center rounded-md bg-slate-100 px-2 py-1 text-xs font-medium text-slate-700 dark:bg-slate-800 dark:text-slate-300">
              {TYPE_LABELS[retainer.type]}
            </span>
            <span className="inline-flex items-center rounded-md bg-slate-100 px-2 py-1 text-xs font-medium text-slate-700 dark:bg-slate-800 dark:text-slate-300">
              {FREQUENCY_LABELS[retainer.frequency]}
            </span>
            <span className="text-sm text-slate-500 dark:text-slate-400">
              Starts {formatLocalDate(retainer.startDate)}
            </span>
            {retainer.endDate && (
              <span className="text-sm text-slate-500 dark:text-slate-400">
                Ends {formatLocalDate(retainer.endDate)}
              </span>
            )}
          </div>
        </div>

        {isAdmin && retainer.status !== "TERMINATED" && (
          <div className="flex shrink-0 gap-2">
            <EditRetainerDialog slug={slug} retainer={retainer}>
              <Button variant="outline" size="sm">
                <Pencil className="mr-1.5 size-4" />
                Edit
              </Button>
            </EditRetainerDialog>
            <RetainerDetailActions slug={slug} retainer={retainer} />
          </div>
        )}
      </div>

      {/* Retainer Info Card */}
      <Card className="shadow-sm">
        <CardHeader>
          <CardTitle className="font-display text-slate-900 dark:text-slate-100">
            Retainer Details
          </CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid grid-cols-2 gap-x-8 gap-y-3 text-sm sm:grid-cols-3">
            {retainer.type === "HOUR_BANK" && retainer.allocatedHours != null && (
              <div>
                <dt className="text-slate-500 dark:text-slate-400">
                  Allocated Hours
                </dt>
                <dd className="mt-0.5 font-medium text-slate-900 dark:text-slate-100">
                  {retainer.allocatedHours.toFixed(1)}h / period
                </dd>
              </div>
            )}
            {retainer.periodFee != null && (
              <div>
                <dt className="text-slate-500 dark:text-slate-400">
                  Period Fee
                </dt>
                <dd className="mt-0.5 font-medium text-slate-900 dark:text-slate-100">
                  {formatCurrency(retainer.periodFee, "USD")}
                </dd>
              </div>
            )}
            {retainer.type === "HOUR_BANK" && retainer.rolloverPolicy && (
              <div>
                <dt className="text-slate-500 dark:text-slate-400">
                  Rollover Policy
                </dt>
                <dd className="mt-0.5 font-medium text-slate-900 dark:text-slate-100">
                  {retainer.rolloverPolicy === "FORFEIT"
                    ? "Forfeit"
                    : retainer.rolloverPolicy === "CARRY_FORWARD"
                      ? "Carry Forward"
                      : `Capped (${retainer.rolloverCapHours ?? 0}h)`}
                </dd>
              </div>
            )}
          </dl>
          {retainer.notes && (
            <p className="mt-4 text-sm text-slate-500 dark:text-slate-400">
              {retainer.notes}
            </p>
          )}
        </CardContent>
      </Card>

      {/* Current Period Card */}
      {retainer.currentPeriod && (
        <Card className="shadow-sm">
          <CardHeader>
            <CardTitle className="font-display text-slate-900 dark:text-slate-100">
              Current Period
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            {/* Consumption alerts — HOUR_BANK only, OPEN periods */}
            {retainer.type === "HOUR_BANK" &&
              retainer.currentPeriod.status === "OPEN" &&
              retainer.allocatedHours != null &&
              retainer.allocatedHours > 0 &&
              (() => {
                const consumptionPercent =
                  (retainer.currentPeriod.consumedHours /
                    retainer.allocatedHours) *
                  100;
                if (consumptionPercent >= 100) {
                  return (
                    <Alert variant="destructive">
                      <AlertTriangle className="size-4" />
                      <AlertDescription>
                        Retainer fully consumed — additional hours are overage.
                      </AlertDescription>
                    </Alert>
                  );
                }
                if (consumptionPercent >= 80) {
                  return (
                    <Alert variant="warning">
                      <AlertTriangle className="size-4" />
                      <AlertDescription>
                        Retainer at {Math.round(consumptionPercent)}% capacity —
                        approaching limit.
                      </AlertDescription>
                    </Alert>
                  );
                }
                return null;
              })()}

            <div className="text-sm text-slate-600 dark:text-slate-400">
              {formatLocalDate(retainer.currentPeriod.periodStart)} &ndash;{" "}
              {formatLocalDate(retainer.currentPeriod.periodEnd)}
            </div>
            <RetainerProgress
              type={retainer.type}
              consumedHours={retainer.currentPeriod.consumedHours}
              allocatedHours={retainer.allocatedHours}
            />
            {retainer.currentPeriod.rolloverHoursIn > 0 && (
              <p className="text-xs text-slate-500 dark:text-slate-400">
                Includes {retainer.currentPeriod.rolloverHoursIn.toFixed(1)}h
                rolled over from previous period
              </p>
            )}
          </CardContent>
        </Card>
      )}

      {/* Period History */}
      <div className="space-y-4">
        <PeriodHistoryTable periods={allPeriods} slug={slug} />
      </div>
    </div>
  );
}
