export default async function DashboardPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-display text-2xl font-semibold text-slate-900 dark:text-white">
          Dashboard
        </h1>
        <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
          Welcome to {slug}. Dashboard content coming soon.
        </p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {["Revenue", "Utilization", "Unbilled Hours", "Overdue Tasks"].map(
          (label) => (
            <div
              key={label}
              className="rounded-lg border border-slate-200 bg-card p-4 shadow-sm dark:border-slate-800"
            >
              <p className="text-sm text-slate-500 dark:text-slate-400">
                {label}
              </p>
              <p className="mt-1 font-mono text-2xl font-semibold tabular-nums text-slate-900 dark:text-white">
                --
              </p>
            </div>
          ),
        )}
      </div>
    </div>
  );
}
