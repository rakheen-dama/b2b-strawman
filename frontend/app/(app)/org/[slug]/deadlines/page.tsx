import { fetchDeadlines } from "./actions";
import { DeadlinePageClient } from "./deadline-page-client";

function getMonthRange(): {
  from: string;
  to: string;
  year: number;
  month: number;
} {
  const now = new Date();
  const year = now.getFullYear();
  const month = now.getMonth() + 1; // 1-indexed
  const from = `${year}-${String(month).padStart(2, "0")}-01`;
  const lastDay = new Date(year, month, 0).getDate();
  const to = `${year}-${String(month).padStart(2, "0")}-${String(lastDay).padStart(2, "0")}`;
  return { from, to, year, month };
}

export default async function DeadlinesPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const { from, to, year, month } = getMonthRange();

  let initialDeadlines: import("@/lib/types").CalculatedDeadline[] = [];
  let initialTotal = 0;
  try {
    const result = await fetchDeadlines(from, to);
    initialDeadlines = result.deadlines;
    initialTotal = result.total;
  } catch (error) {
    console.error("Failed to fetch deadlines:", error);
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Regulatory Deadlines
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Filing deadlines for all clients, calculated from financial year-end
          dates
        </p>
      </div>

      <DeadlinePageClient
        initialDeadlines={initialDeadlines}
        initialTotal={initialTotal}
        initialYear={year}
        initialMonth={month}
        slug={slug}
      />
    </div>
  );
}
