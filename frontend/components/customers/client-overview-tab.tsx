"use client";

import type { ReactNode } from "react";
import Link from "next/link";
import { CheckCircle, Clock, RefreshCw } from "lucide-react";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { SetupProgressCard } from "@/components/setup/setup-progress-card";
import { TemplateReadinessCard } from "@/components/setup/template-readiness-card";
import { LifecycleStatusBadge } from "@/components/compliance/LifecycleStatusBadge";
import type { SetupStep, ContextGroup, TemplateReadinessItem } from "@/components/setup/types";
import type { LifecycleStatus } from "@/lib/types/customer";

interface ClientOverviewTabProps {
  /** Section A: Client Readiness — SetupProgressCard props */
  setupProgressData: {
    title: ReactNode;
    completionPercentage: number;
    overallComplete: boolean;
    steps: SetupStep[];
    canManage: boolean;
    activationBlockers?: string[];
    contextGroups?: ContextGroup[];
  } | null;

  /** Section B: Lifecycle prompt — pre-rendered ReactNode */
  lifecyclePrompt: ReactNode | null;

  /** Section C: Financial summary — unbilled time data */
  unbilledTimeData: {
    amount: string;
    hours: string;
    createInvoiceHref: string;
    viewTimeHref: string;
  } | null;

  /** Section C: Financial summary — retainer summary */
  activeRetainer: {
    name: string;
    status: string;
    allocatedHours: number | null;
    consumedHours: number | null;
    remainingHours: number | null;
    periodStart: string | null;
    periodEnd: string | null;
  } | null;

  /** Section D: Template readiness — TemplateReadinessCard props */
  templateReadiness: {
    templates: TemplateReadinessItem[];
    baseHref: string;
  } | null;

  /** Section E: AI suggestions — pre-rendered ReactNode */
  pendingSuggestions: ReactNode | null;

  /** Section F: FICA panel — pre-rendered ReactNode */
  ficaPanel: ReactNode | null;

  /** Empty state: customer name */
  customerName: string;

  /** Empty state: lifecycle status badge */
  lifecycleStatus: LifecycleStatus | null;

  /** Empty state: linked project count */
  linkedProjectCount: number;
}

export function ClientOverviewTab({
  setupProgressData,
  lifecyclePrompt,
  unbilledTimeData,
  activeRetainer,
  templateReadiness,
  pendingSuggestions,
  ficaPanel,
  customerName,
  lifecycleStatus,
  linkedProjectCount,
}: ClientOverviewTabProps) {
  const hasContent =
    setupProgressData !== null ||
    lifecyclePrompt !== null ||
    unbilledTimeData !== null ||
    activeRetainer !== null ||
    templateReadiness !== null ||
    pendingSuggestions !== null ||
    ficaPanel !== null;

  if (!hasContent) {
    return (
      <div
        data-testid="client-overview-tab"
        className="flex flex-col items-center gap-3 py-12 text-center"
      >
        <CheckCircle className="size-12 text-green-500 dark:text-green-400" />
        <h3 className="font-display text-lg text-slate-900 dark:text-slate-100">{customerName}</h3>
        {lifecycleStatus && <LifecycleStatusBadge status={lifecycleStatus} />}
        <p className="text-muted-foreground text-sm">
          {linkedProjectCount > 0
            ? `Everything looks good — ${linkedProjectCount} linked ${linkedProjectCount === 1 ? "project" : "projects"}.`
            : "Everything looks good — no outstanding items."}
        </p>
      </div>
    );
  }

  const hasFinancialSummary = unbilledTimeData !== null || activeRetainer !== null;

  return (
    <div data-testid="client-overview-tab" className="space-y-4">
      {/* Section A: Client Readiness */}
      {setupProgressData && (
        <SetupProgressCard
          title={setupProgressData.title}
          completionPercentage={setupProgressData.completionPercentage}
          overallComplete={setupProgressData.overallComplete}
          steps={setupProgressData.steps}
          canManage={setupProgressData.canManage}
          activationBlockers={setupProgressData.activationBlockers}
          contextGroups={setupProgressData.contextGroups}
        />
      )}

      {/* Section B: Lifecycle Action Prompt */}
      {lifecyclePrompt}

      {/* Section C: Financial Summary */}
      {hasFinancialSummary && (
        <div className="grid gap-4 md:grid-cols-2">
          {unbilledTimeData && (
            <Card data-testid="unbilled-time-card">
              <CardHeader>
                <div className="flex items-center gap-2">
                  <Clock className="h-4 w-4 text-slate-400" />
                  <CardTitle className="text-base">Unbilled Time</CardTitle>
                </div>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="space-y-1">
                  <p className="font-mono text-2xl font-bold tabular-nums">
                    {unbilledTimeData.amount}
                  </p>
                  <p className="text-muted-foreground text-sm">{unbilledTimeData.hours} hours</p>
                </div>
                <div className="flex items-center gap-2">
                  <Button asChild size="sm">
                    <Link href={unbilledTimeData.createInvoiceHref}>Create Invoice</Link>
                  </Button>
                  <Button asChild size="sm" variant="outline">
                    <Link href={unbilledTimeData.viewTimeHref}>View Time</Link>
                  </Button>
                </div>
              </CardContent>
            </Card>
          )}

          {activeRetainer && (
            <Card data-testid="retainer-status-card">
              <CardHeader>
                <div className="flex items-center gap-2">
                  <RefreshCw className="h-4 w-4 text-slate-400" />
                  <CardTitle className="text-base">Retainer</CardTitle>
                </div>
              </CardHeader>
              <CardContent className="space-y-2">
                <p className="text-sm font-medium">{activeRetainer.name}</p>
                <p className="text-muted-foreground text-xs tracking-wider uppercase">
                  {activeRetainer.status}
                </p>
                {activeRetainer.allocatedHours !== null &&
                  activeRetainer.consumedHours !== null && (
                    <div className="text-sm">
                      <span className="font-mono tabular-nums">{activeRetainer.consumedHours}</span>
                      <span className="text-muted-foreground">
                        {" "}
                        / {activeRetainer.allocatedHours} hours used
                      </span>
                      {activeRetainer.remainingHours !== null && (
                        <span className="text-muted-foreground">
                          {" "}
                          ({activeRetainer.remainingHours} remaining)
                        </span>
                      )}
                    </div>
                  )}
              </CardContent>
            </Card>
          )}
        </div>
      )}

      {/* Section D: Template Readiness */}
      {templateReadiness && (
        <TemplateReadinessCard
          templates={templateReadiness.templates}
          baseHref={templateReadiness.baseHref}
        />
      )}

      {/* Section E: Pending AI Suggestions */}
      {pendingSuggestions}

      {/* Section F: FICA Panel */}
      {ficaPanel}
    </div>
  );
}
