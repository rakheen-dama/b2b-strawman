import Link from "next/link";

interface OnboardingCustomer {
  id: string;
  name: string;
  lifecycleStatusChangedAt: string | null;
  checklistProgress: { completed: number; total: number };
}

interface OnboardingPipelineSectionProps {
  customers: OnboardingCustomer[];
  orgSlug: string;
}

function SimpleProgressBar({ completed, total }: { completed: number; total: number }) {
  const pct = total > 0 ? Math.round((completed / total) * 100) : 0;
  return (
    <div className="space-y-1">
      <div className="h-2 w-full overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800">
        <div
          className="h-full rounded-full bg-teal-500 transition-all"
          style={{ width: `${pct}%` }}
        />
      </div>
      <p className="text-xs text-slate-500 dark:text-slate-400">
        {completed}/{total} items
      </p>
    </div>
  );
}

function getDaysInOnboarding(changedAt: string | null): number {
  if (!changedAt) return 0;
  return Math.floor((Date.now() - new Date(changedAt).getTime()) / 86400000);
}

export function OnboardingPipelineSection({
  customers,
  orgSlug,
}: OnboardingPipelineSectionProps) {
  const sorted = [...customers].sort((a, b) => {
    const daysA = getDaysInOnboarding(a.lifecycleStatusChangedAt);
    const daysB = getDaysInOnboarding(b.lifecycleStatusChangedAt);
    return daysB - daysA;
  });

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
        Onboarding Pipeline
      </h2>
      {sorted.length === 0 ? (
        <p className="text-sm text-slate-500 dark:text-slate-400">
          No customers currently in onboarding
        </p>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-slate-200 dark:border-slate-800">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900">
                <th className="px-4 py-3 text-left font-medium text-slate-600 dark:text-slate-400">
                  Customer
                </th>
                <th className="px-4 py-3 text-left font-medium text-slate-600 dark:text-slate-400">
                  Duration
                </th>
                <th className="px-4 py-3 text-left font-medium text-slate-600 dark:text-slate-400">
                  Progress
                </th>
              </tr>
            </thead>
            <tbody>
              {sorted.map((customer) => {
                const days = getDaysInOnboarding(customer.lifecycleStatusChangedAt);
                return (
                  <tr
                    key={customer.id}
                    className="border-b border-slate-100 last:border-0 dark:border-slate-800"
                  >
                    <td className="px-4 py-3">
                      <Link
                        href={`/org/${orgSlug}/customers/${customer.id}`}
                        className="font-medium text-slate-950 hover:text-teal-600 dark:text-slate-50"
                      >
                        {customer.name}
                      </Link>
                    </td>
                    <td className="px-4 py-3 text-slate-600 dark:text-slate-400">
                      In onboarding {days} {days === 1 ? "day" : "days"}
                    </td>
                    <td className="w-48 px-4 py-3">
                      <SimpleProgressBar
                        completed={customer.checklistProgress.completed}
                        total={customer.checklistProgress.total}
                      />
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
