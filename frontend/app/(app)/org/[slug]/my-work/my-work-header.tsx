"use client";

import { useState } from "react";
import Link from "next/link";
import { PageHeader } from "@b2mash/ui/page-header";
import { DateRangeSelector } from "@/components/dashboard/date-range-selector";

interface MyWorkHeaderProps {
  slug: string;
  from: string;
  to: string;
}

function parseDateString(dateStr: string): Date {
  const [y, m, d] = dateStr.split("-").map(Number);
  return new Date(y, m - 1, d);
}

/**
 * My Work's page header (Phase 91 E8A, K-96): the Timesheet link is the header action and the date
 * range sits in the toolbar row under the title, so the title block keeps its width at 1024 (it
 * was squeezed to 80 px beside the date range and the link).
 */
export function MyWorkHeader({ slug, from, to }: MyWorkHeaderProps) {
  const [dateRange, setDateRange] = useState({
    from: parseDateString(from),
    to: parseDateString(to),
  });

  return (
    <PageHeader
      title="My Work"
      description="Your tasks and time tracking across all matters"
      actions={
        <Link
          href={`/org/${slug}/my-work/timesheet`}
          className="text-foreground hover:bg-muted rounded-full border px-4 py-2 text-sm font-medium transition-colors"
        >
          Timesheet
        </Link>
      }
    >
      <DateRangeSelector value={dateRange} onChange={setDateRange} />
    </PageHeader>
  );
}
