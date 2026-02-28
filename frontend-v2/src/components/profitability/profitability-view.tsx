"use client";

import { useState, useTransition, useMemo } from "react";
import { Loader2 } from "lucide-react";

import { cn } from "@/lib/utils";
import { formatCurrency, formatCurrencySafe } from "@/lib/format";
import { KpiStrip } from "@/components/layout/kpi-strip";
import { PeriodSelector } from "@/components/profitability/period-selector";
import { MarginTable } from "@/components/profitability/margin-table";
import { UtilizationChart } from "@/components/profitability/utilization-chart";
import { ProfitabilityBarChart } from "@/components/profitability/profitability-bar-chart";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Card, CardContent } from "@/components/ui/card";
import {
  Table,
  TableHeader,
  TableBody,
  TableHead,
  TableRow,
  TableCell,
} from "@/components/ui/table";
import {
  getOrgProfitability,
  getUtilization,
} from "@/app/(app)/org/[slug]/profitability/actions";
import type {
  OrgProfitabilityResponse,
  UtilizationResponse,
  ProjectProfitabilitySummary,
} from "@/lib/types";

interface CustomerAggregate {
  customerName: string;
  currency: string;
  billableHours: number;
  billableValue: number;
  costValue: number | null;
  margin: number | null;
  marginPercent: number | null;
  projectCount: number;
}

function aggregateByCustomer(
  projects: ProjectProfitabilitySummary[],
): CustomerAggregate[] {
  const map = new Map<string, CustomerAggregate>();

  for (const p of projects) {
    const name = p.customerName ?? "Unassigned";
    const key = `${name}::${p.currency}`;

    if (!map.has(key)) {
      map.set(key, {
        customerName: name,
        currency: p.currency,
        billableHours: 0,
        billableValue: 0,
        costValue: null,
        margin: null,
        marginPercent: null,
        projectCount: 0,
      });
    }

    const agg = map.get(key)!;
    agg.billableHours += p.billableHours;
    agg.billableValue += p.billableValue;
    agg.projectCount++;

    if (p.costValue != null) {
      agg.costValue = (agg.costValue ?? 0) + p.costValue;
    }
  }

  for (const agg of map.values()) {
    if (agg.costValue != null) {
      agg.margin = agg.billableValue - agg.costValue;
      agg.marginPercent =
        agg.billableValue > 0
          ? (agg.margin / agg.billableValue) * 100
          : 0;
    }
  }

  return Array.from(map.values()).sort(
    (a, b) => b.billableValue - a.billableValue,
  );
}

function marginColorClass(percent: number | null): string {
  if (percent == null) return "";
  if (percent >= 30) return "text-emerald-600";
  if (percent >= 10) return "text-amber-600";
  return "text-red-600";
}

interface ProfitabilityViewProps {
  initialProfitability: OrgProfitabilityResponse;
  initialUtilization: UtilizationResponse;
  initialFrom: string;
  initialTo: string;
}

export function ProfitabilityView({
  initialProfitability,
  initialUtilization,
  initialFrom,
  initialTo,
}: ProfitabilityViewProps) {
  const [profitability, setProfitability] =
    useState<OrgProfitabilityResponse>(initialProfitability);
  const [utilization, setUtilization] =
    useState<UtilizationResponse>(initialUtilization);
  const [from, setFrom] = useState(initialFrom);
  const [to, setTo] = useState(initialTo);
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  function handleRangeChange(newFrom: string, newTo: string) {
    setFrom(newFrom);
    setTo(newTo);
    startTransition(async () => {
      const [profResult, utilResult] = await Promise.all([
        getOrgProfitability(newFrom, newTo),
        getUtilization(newFrom, newTo),
      ]);

      if (profResult.data) {
        setProfitability(profResult.data);
      }
      if (utilResult.data) {
        setUtilization(utilResult.data);
      }
      if (profResult.error || utilResult.error) {
        setError(profResult.error || utilResult.error || null);
      } else {
        setError(null);
      }
    });
  }

  // Compute KPIs
  const kpis = useMemo(() => {
    const projects = profitability.projects;
    const totalRevenue = projects.reduce((s, p) => s + p.billableValue, 0);
    const totalCost = projects.reduce(
      (s, p) => s + (p.costValue ?? 0),
      0,
    );
    const totalMargin = totalRevenue - totalCost;
    const avgMarginPct =
      totalRevenue > 0 ? (totalMargin / totalRevenue) * 100 : 0;
    const avgUtil =
      utilization.members.length > 0
        ? utilization.members.reduce(
            (s, m) => s + m.utilizationPercent,
            0,
          ) / utilization.members.length
        : 0;

    // Use the first project's currency as representative, or default to USD
    const currency = projects.length > 0 ? projects[0].currency : "USD";

    return {
      items: [
        { label: "Total Revenue", value: formatCurrency(totalRevenue, currency) },
        { label: "Total Cost", value: formatCurrency(totalCost, currency) },
        {
          label: "Net Margin",
          value: formatCurrency(totalMargin, currency),
        },
        {
          label: "Avg Margin",
          value: `${avgMarginPct.toFixed(1)}%`,
        },
        {
          label: "Avg Utilization",
          value: `${avgUtil.toFixed(1)}%`,
        },
      ],
    };
  }, [profitability, utilization]);

  const customers = useMemo(
    () => aggregateByCustomer(profitability.projects),
    [profitability],
  );

  return (
    <div className="space-y-6">
      {/* Toolbar: Period selector */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <PeriodSelector
          from={from}
          to={to}
          onRangeChange={handleRangeChange}
        />
        {isPending && (
          <div className="flex items-center gap-2 text-sm text-slate-500">
            <Loader2 className="size-4 animate-spin" />
            Refreshing...
          </div>
        )}
      </div>

      {error && (
        <div className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700">
          {error}
        </div>
      )}

      {/* KPI Strip */}
      <KpiStrip items={kpis.items} />

      {/* View tabs: Project / Customer / Team Member */}
      <Tabs defaultValue="project">
        <TabsList>
          <TabsTrigger value="project">By Project</TabsTrigger>
          <TabsTrigger value="customer">By Customer</TabsTrigger>
          <TabsTrigger value="team">Team Utilization</TabsTrigger>
        </TabsList>

        <TabsContent value="project" className="space-y-6 pt-4">
          <Card>
            <CardContent className="pt-6">
              <MarginTable
                data={profitability.projects}
                isLoading={isPending}
              />
            </CardContent>
          </Card>
          <ProfitabilityBarChart data={profitability.projects} />
        </TabsContent>

        <TabsContent value="customer" className="space-y-6 pt-4">
          <Card>
            <CardContent className="pt-6">
              {customers.length === 0 ? (
                <p className="py-12 text-center text-sm text-slate-500">
                  No customer profitability data for this period.
                </p>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Customer</TableHead>
                      <TableHead>Currency</TableHead>
                      <TableHead>Projects</TableHead>
                      <TableHead className="text-right">Revenue</TableHead>
                      <TableHead className="text-right">Cost</TableHead>
                      <TableHead className="text-right">Margin</TableHead>
                      <TableHead className="text-right">Margin %</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {customers.map((c) => (
                      <TableRow
                        key={`${c.customerName}::${c.currency}`}
                      >
                        <TableCell className="font-medium">
                          {c.customerName}
                        </TableCell>
                        <TableCell>{c.currency}</TableCell>
                        <TableCell className="tabular-nums">
                          {c.projectCount}
                        </TableCell>
                        <TableCell className="text-right tabular-nums">
                          {formatCurrency(c.billableValue, c.currency)}
                        </TableCell>
                        <TableCell className="text-right tabular-nums">
                          {formatCurrencySafe(c.costValue, c.currency)}
                        </TableCell>
                        <TableCell className="text-right">
                          {c.margin != null ? (
                            <span
                              className={cn(
                                "tabular-nums font-medium",
                                c.margin >= 0
                                  ? "text-emerald-600"
                                  : "text-red-600",
                              )}
                            >
                              {formatCurrency(c.margin, c.currency)}
                            </span>
                          ) : (
                            <span className="text-slate-400">&mdash;</span>
                          )}
                        </TableCell>
                        <TableCell className="text-right">
                          {c.marginPercent != null ? (
                            <span
                              className={cn(
                                "tabular-nums font-semibold",
                                marginColorClass(c.marginPercent),
                              )}
                            >
                              {c.marginPercent.toFixed(1)}%
                            </span>
                          ) : (
                            <span className="text-slate-400">&mdash;</span>
                          )}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="team" className="pt-4">
          <UtilizationChart data={utilization.members} />
        </TabsContent>
      </Tabs>
    </div>
  );
}
