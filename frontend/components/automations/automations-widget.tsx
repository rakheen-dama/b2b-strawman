import Link from "next/link";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Zap } from "lucide-react";
import type { AutomationSummary } from "@/lib/api/automations";

interface AutomationsWidgetProps {
  data: AutomationSummary | null;
  orgSlug: string;
}

export function AutomationsWidget({ data, orgSlug }: AutomationsWidgetProps) {
  if (!data) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Zap className="size-4" />
            Automations
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm italic text-slate-500 dark:text-slate-400">
            Unable to load automation data.
          </p>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Zap className="size-4" />
          Automations
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-sm text-slate-600 dark:text-slate-400">
              Active Rules
            </span>
            <span className="font-mono text-sm font-semibold tabular-nums text-slate-900 dark:text-slate-100">
              {data.activeRulesCount}
            </span>
          </div>
          <div className="flex items-center justify-between">
            <span className="text-sm text-slate-600 dark:text-slate-400">
              Executions Today
            </span>
            <span className="font-mono text-sm font-semibold tabular-nums text-slate-900 dark:text-slate-100">
              {data.todayTotal}
            </span>
          </div>
          {data.todayTotal > 0 && (
            <div className="flex items-center justify-between text-xs text-slate-500 dark:text-slate-400">
              <span>
                {data.todaySucceeded} succeeded
              </span>
              {data.todayFailed > 0 ? (
                <Link
                  href={`/org/${orgSlug}/settings/automations/executions?status=ACTIONS_FAILED`}
                >
                  <Badge variant="destructive">{data.todayFailed} failed</Badge>
                </Link>
              ) : (
                <span className="text-emerald-600 dark:text-emerald-400">
                  0 failed
                </span>
              )}
            </div>
          )}
        </div>
        <div className="mt-4 border-t border-slate-200 pt-3 dark:border-slate-700">
          <Link
            href={`/org/${orgSlug}/settings/automations`}
            className="text-sm text-teal-600 hover:text-teal-700 dark:text-teal-400 dark:hover:text-teal-300"
          >
            Manage Automations
          </Link>
        </div>
      </CardContent>
    </Card>
  );
}
