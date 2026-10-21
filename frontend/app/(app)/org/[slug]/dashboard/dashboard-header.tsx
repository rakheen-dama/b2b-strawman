/** Home page header (614B): nothing on Home is date-ranged any more, so no range selector. */
export function DashboardHeader() {
  return (
    <div>
      <h1 className="font-display text-3xl tracking-tight text-slate-950 dark:text-slate-50">
        Home
      </h1>
      <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">What needs you today</p>
    </div>
  );
}
