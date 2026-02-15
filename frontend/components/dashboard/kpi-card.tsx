import Link from "next/link";
import { ArrowUp, ArrowDown } from "lucide-react";

import { cn } from "@/lib/utils";
import { Card } from "@/components/ui/card";
import { SparklineChart } from "@/components/dashboard/sparkline-chart";

interface KpiCardProps {
  label: string;
  value: string | number;
  changePercent?: number | null;
  changeDirection?: "positive" | "negative" | "neutral";
  trend?: number[] | null;
  href?: string | null;
  emptyState?: string | null;
}

function ChangeIndicator({
  changePercent,
  changeDirection,
}: {
  changePercent: number;
  changeDirection: "positive" | "negative" | "neutral";
}) {
  if (changeDirection === "neutral") {
    return (
      <span className="text-xs text-muted-foreground">
        {changePercent > 0 ? "+" : ""}
        {changePercent}%
      </span>
    );
  }

  const isPositive = changeDirection === "positive";

  return (
    <span
      className={cn(
        "inline-flex items-center gap-0.5 text-xs font-medium",
        isPositive ? "text-green-600 dark:text-green-400" : "text-red-600 dark:text-red-400"
      )}
    >
      {isPositive ? (
        <ArrowUp className="size-3" />
      ) : (
        <ArrowDown className="size-3" />
      )}
      {Math.abs(changePercent)}%
    </span>
  );
}

function KpiCardContent({
  label,
  value,
  changePercent,
  changeDirection,
  trend,
  emptyState,
}: KpiCardProps) {
  const isEmpty =
    emptyState != null && (value === null || value === 0 || value === "0");

  return (
    <div className="flex flex-col gap-1 px-4 py-3">
      <span className="text-xs uppercase tracking-wider text-muted-foreground">{label}</span>
      <div className="flex items-end justify-between gap-2">
        <div className="flex flex-col gap-1">
          {isEmpty ? (
            <span className="text-sm text-muted-foreground italic">
              {emptyState}
            </span>
          ) : (
            <span className="text-2xl font-bold font-mono tabular-nums tracking-tight">{value}</span>
          )}
          {changePercent != null && changeDirection && !isEmpty && (
            <ChangeIndicator
              changePercent={changePercent}
              changeDirection={changeDirection}
            />
          )}
        </div>
        {trend && trend.length > 0 && !isEmpty && (
          <SparklineChart data={trend} />
        )}
      </div>
    </div>
  );
}

export function KpiCard(props: KpiCardProps) {
  const { href, ...rest } = props;

  if (href) {
    return (
      <Link href={href} className="block h-full">
        <Card className="h-full transition-shadow hover:shadow-md">
          <KpiCardContent {...rest} />
        </Card>
      </Link>
    );
  }

  return (
    <Card className="h-full">
      <KpiCardContent {...rest} />
    </Card>
  );
}
