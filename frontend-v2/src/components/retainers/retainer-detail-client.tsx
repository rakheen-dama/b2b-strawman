"use client";

import Link from "next/link";
import { ArrowLeft, Calendar, Clock, User } from "lucide-react";

import type { RetainerResponse, PeriodSummary } from "@/lib/api/retainers";
import { formatLocalDate, formatCurrency } from "@/lib/format";
import { StatusBadge } from "@/components/ui/status-badge";
import { Card } from "@/components/ui/card";
import { DetailPage } from "@/components/layout/detail-page";
import { ProgressGauge } from "./progress-gauge";
import { ConsumptionTable } from "./consumption-table";

interface RetainerDetailClientProps {
  retainer: RetainerResponse;
  periods: PeriodSummary[];
  orgSlug: string;
}

function formatTypeLabel(type: string): string {
  return type === "HOUR_BANK" ? "Hour Bank" : type === "FIXED_FEE" ? "Fixed Fee" : type;
}

function formatFrequencyLabel(freq: string): string {
  const map: Record<string, string> = {
    WEEKLY: "Weekly",
    FORTNIGHTLY: "Fortnightly",
    MONTHLY: "Monthly",
    QUARTERLY: "Quarterly",
    SEMI_ANNUALLY: "Semi-annually",
    ANNUALLY: "Annually",
  };
  return map[freq] ?? freq;
}

export function RetainerDetailClient({
  retainer,
  periods,
  orgSlug,
}: RetainerDetailClientProps) {
  const currentPeriod = retainer.currentPeriod;

  const header = (
    <div className="space-y-4">
      <Link
        href={`/org/${orgSlug}/retainers`}
        className="inline-flex items-center gap-1.5 text-sm text-slate-500 transition-colors hover:text-slate-700"
      >
        <ArrowLeft className="size-4" />
        Retainers
      </Link>

      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="space-y-2">
          <div className="flex items-center gap-3">
            <h1 className="font-display text-2xl font-semibold tracking-tight text-slate-900">
              {retainer.name}
            </h1>
            <StatusBadge status={retainer.status} />
          </div>
          <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-slate-500">
            <span className="flex items-center gap-1">
              <User className="size-3.5" />
              {retainer.customerName}
            </span>
            <span>{formatTypeLabel(retainer.type)}</span>
            <span>{formatFrequencyLabel(retainer.frequency)}</span>
            <span className="flex items-center gap-1">
              <Calendar className="size-3.5" />
              {formatLocalDate(retainer.startDate)}
              {retainer.endDate && ` \u2013 ${formatLocalDate(retainer.endDate)}`}
            </span>
          </div>
        </div>
      </div>
    </div>
  );

  const overviewContent = (
    <div className="grid gap-6 lg:grid-cols-2">
      {/* Current period gauge */}
      {currentPeriod && currentPeriod.allocatedHours != null && (
        <Card className="p-6">
          <h3 className="mb-4 text-sm font-semibold text-slate-700">
            Current Period
          </h3>
          <ProgressGauge
            consumed={currentPeriod.consumedHours}
            allocated={currentPeriod.allocatedHours}
            label="Hours consumed"
          />
          <div className="mt-4 text-xs text-slate-500">
            {formatLocalDate(currentPeriod.periodStart)} &mdash;{" "}
            {formatLocalDate(currentPeriod.periodEnd)}
          </div>
        </Card>
      )}

      {/* Key details card */}
      <Card className="p-6">
        <h3 className="mb-4 text-sm font-semibold text-slate-700">Details</h3>
        <dl className="grid grid-cols-2 gap-4 text-sm">
          <div>
            <dt className="text-slate-500">Type</dt>
            <dd className="font-medium text-slate-900">
              {formatTypeLabel(retainer.type)}
            </dd>
          </div>
          <div>
            <dt className="text-slate-500">Frequency</dt>
            <dd className="font-medium text-slate-900">
              {formatFrequencyLabel(retainer.frequency)}
            </dd>
          </div>
          {retainer.allocatedHours != null && (
            <div>
              <dt className="text-slate-500">Allocated Hours</dt>
              <dd className="font-mono font-medium tabular-nums text-slate-900">
                {retainer.allocatedHours}h
              </dd>
            </div>
          )}
          {retainer.periodFee != null && (
            <div>
              <dt className="text-slate-500">Period Fee</dt>
              <dd className="font-mono font-medium tabular-nums text-slate-900">
                {formatCurrency(retainer.periodFee, "ZAR")}
              </dd>
            </div>
          )}
          {retainer.rolloverPolicy && (
            <div>
              <dt className="text-slate-500">Rollover</dt>
              <dd className="font-medium text-slate-900">
                {retainer.rolloverPolicy === "FORFEIT"
                  ? "Forfeit"
                  : retainer.rolloverPolicy === "CARRY_FORWARD"
                    ? "Carry forward"
                    : `Carry (cap: ${retainer.rolloverCapHours}h)`}
              </dd>
            </div>
          )}
          <div>
            <dt className="text-slate-500">Created by</dt>
            <dd className="font-medium text-slate-900">
              {retainer.createdByName ?? retainer.createdBy}
            </dd>
          </div>
        </dl>
        {retainer.notes && (
          <div className="mt-4 border-t border-slate-100 pt-4">
            <p className="text-xs font-medium uppercase text-slate-500">
              Notes
            </p>
            <p className="mt-1 whitespace-pre-line text-sm text-slate-600">
              {retainer.notes}
            </p>
          </div>
        )}
      </Card>
    </div>
  );

  const allPeriods = [
    ...(currentPeriod ? [currentPeriod] : []),
    ...retainer.recentPeriods.filter((p) => p.id !== currentPeriod?.id),
    ...periods.filter(
      (p) =>
        p.id !== currentPeriod?.id &&
        !retainer.recentPeriods.some((rp) => rp.id === p.id),
    ),
  ];

  return (
    <DetailPage
      header={header}
      tabs={[
        {
          id: "overview",
          label: "Overview",
          content: overviewContent,
        },
        {
          id: "consumption",
          label: "Consumption",
          count: allPeriods.length,
          content: <ConsumptionTable periods={allPeriods} />,
        },
        {
          id: "timeline",
          label: "Timeline",
          content: (
            <div className="flex flex-col items-center justify-center py-16 text-center">
              <Clock className="mb-3 size-10 text-slate-300" />
              <p className="text-sm font-medium text-slate-700">
                Activity timeline
              </p>
              <p className="mt-1 text-sm text-slate-500">
                Retainer activity and changes will appear here.
              </p>
            </div>
          ),
        },
      ]}
      defaultTab="overview"
    />
  );
}
