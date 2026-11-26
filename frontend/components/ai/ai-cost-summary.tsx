import { Progress } from "@/components/ui/progress";
import { formatCurrencyFromCents } from "@/lib/format";
import { SettingsSection } from "@/components/settings/settings-section";

interface AiCostSummaryProps {
  costSummary: {
    currentMonthSpentCents: number;
    monthlyBudgetCents: number | null;
    invocationCount: number;
    remainingBudgetCents: number | null;
    periodStart: string;
    periodEnd: string;
  } | null;
}

function formatPeriod(dateStr: string): string {
  const date = new Date(dateStr);
  return date.toLocaleDateString("en-ZA", { month: "long", year: "numeric", timeZone: "UTC" });
}

export function AiCostSummary({ costSummary }: AiCostSummaryProps) {
  if (!costSummary || costSummary.invocationCount === 0) {
    return (
      <SettingsSection title="Usage Summary">
        <p className="text-muted-foreground text-sm">
          No AI invocations this month. Usage data will appear here once you start using AI skills.
        </p>
      </SettingsSection>
    );
  }

  const { currentMonthSpentCents, monthlyBudgetCents, invocationCount, remainingBudgetCents } =
    costSummary;

  const percentage = monthlyBudgetCents
    ? Math.min(100, (currentMonthSpentCents / monthlyBudgetCents) * 100)
    : 0;

  const progressColor =
    percentage >= 100
      ? "text-red-600 dark:text-red-400"
      : percentage >= 80
        ? "text-amber-600 dark:text-amber-400"
        : "text-teal-600 dark:text-teal-400";

  return (
    <SettingsSection title="Usage Summary" description={formatPeriod(costSummary.periodStart)}>
      <div className="space-y-4">
        {/* Current Spend */}
        <div>
          <p className="text-sm font-medium text-slate-700 dark:text-slate-300">Current Spend</p>
          <p className="text-2xl font-semibold text-slate-950 dark:text-slate-50">
            {formatCurrencyFromCents(currentMonthSpentCents)}
          </p>
        </div>

        {/* Budget & Progress */}
        {monthlyBudgetCents && (
          <div className="space-y-2">
            <div className="flex items-center justify-between text-sm">
              <span className="text-slate-600 dark:text-slate-400">
                of {formatCurrencyFromCents(monthlyBudgetCents)} budget
              </span>
              <span className={`font-medium ${progressColor}`}>{Math.round(percentage)}%</span>
            </div>
            <Progress value={percentage} className="h-2" />
            {remainingBudgetCents !== null && remainingBudgetCents > 0 && (
              <p className="text-xs text-slate-500 dark:text-slate-400">
                {formatCurrencyFromCents(remainingBudgetCents)} remaining
              </p>
            )}
            {remainingBudgetCents !== null && remainingBudgetCents <= 0 && (
              <p className="text-xs font-medium text-red-600 dark:text-red-400">Budget exceeded</p>
            )}
          </div>
        )}

        {/* Invocation Count */}
        <div className="border-t border-slate-100 pt-3 dark:border-slate-800">
          <p className="text-sm font-medium text-slate-700 dark:text-slate-300">Invocations</p>
          <p className="text-lg font-semibold text-slate-950 dark:text-slate-50">
            {invocationCount}
          </p>
        </div>
      </div>
    </SettingsSection>
  );
}
