import { TrendingUp, TrendingDown, Minus } from "lucide-react";

import { Card, CardContent } from "@/components/ui/card";
import { cn } from "@/lib/utils";

interface KpiTrend {
  value: number;
  direction: "up" | "down" | "flat";
}

interface KpiItem {
  label: string;
  value: string | number;
  trend?: KpiTrend;
}

interface KpiStripProps {
  items: KpiItem[];
  className?: string;
}

const trendConfig = {
  up: {
    icon: TrendingUp,
    color: "text-emerald-600",
    prefix: "+",
  },
  down: {
    icon: TrendingDown,
    color: "text-red-600",
    prefix: "",
  },
  flat: {
    icon: Minus,
    color: "text-slate-400",
    prefix: "",
  },
} as const;

export function KpiStrip({ items, className }: KpiStripProps) {
  return (
    <div
      className={cn(
        "grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5",
        className
      )}
    >
      {items.map((item) => (
        <Card key={item.label} className="gap-3 py-4">
          <CardContent className="flex flex-col gap-1">
            <span className="text-xs font-medium uppercase tracking-wider text-slate-500">
              {item.label}
            </span>
            <div className="flex items-baseline gap-2">
              <span className="font-mono text-2xl font-semibold tabular-nums text-slate-900">
                {item.value}
              </span>
              {item.trend && <KpiTrendIndicator trend={item.trend} />}
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}

function KpiTrendIndicator({ trend }: { trend: KpiTrend }) {
  const config = trendConfig[trend.direction];
  const Icon = config.icon;

  return (
    <span
      className={cn(
        "inline-flex items-center gap-0.5 text-xs font-medium",
        config.color
      )}
    >
      <Icon className="h-3 w-3" />
      <span className="tabular-nums">
        {config.prefix}
        {trend.value}%
      </span>
    </span>
  );
}
