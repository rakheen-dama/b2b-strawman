import { notFound } from "next/navigation";
import { getOrgSettings } from "@/lib/api/settings";
import { fetchCourtDates, fetchPrescriptionTrackers } from "./actions";
import { CourtCalendarClient } from "./court-calendar-client";
import type { CourtDate, PrescriptionTracker } from "@/lib/types";

export default async function CourtCalendarPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;

  let settings;
  try {
    settings = await getOrgSettings();
  } catch {
    notFound();
  }

  const enabledModules = settings.enabledModules ?? [];
  if (!enabledModules.includes("court_calendar")) {
    notFound();
  }

  let initialCourtDates: CourtDate[] = [];
  let initialTotal = 0;
  let initialTrackers: PrescriptionTracker[] = [];

  try {
    const [courtDateResult, trackerResult] = await Promise.all([
      fetchCourtDates(),
      fetchPrescriptionTrackers(),
    ]);
    initialCourtDates = courtDateResult.content;
    initialTotal = courtDateResult.page.totalElements;
    initialTrackers = trackerResult.content;
  } catch (error) {
    console.error("Failed to fetch court calendar data:", error);
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Court Calendar
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Manage court dates, hearings, and prescription tracking
        </p>
      </div>

      <CourtCalendarClient
        initialCourtDates={initialCourtDates}
        initialTotal={initialTotal}
        initialTrackers={initialTrackers}
        slug={slug}
      />
    </div>
  );
}
