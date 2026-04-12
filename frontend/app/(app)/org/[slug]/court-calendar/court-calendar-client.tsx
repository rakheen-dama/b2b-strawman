"use client";

import { useState, useMemo, useTransition, useCallback, useEffect, useRef } from "react";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { X } from "lucide-react";
import { cn } from "@/lib/utils";
import { CourtDateListView } from "@/components/legal/court-date-list-view";
import { CourtCalendarView } from "@/components/legal/court-calendar-view";
import { PrescriptionTab } from "@/components/legal/prescription-tab";
import { CreateCourtDateDialog } from "@/components/legal/create-court-date-dialog";
import { EditCourtDateDialog } from "@/components/legal/edit-court-date-dialog";
import { PostponeDialog } from "@/components/legal/postpone-dialog";
import { CancelCourtDateDialog } from "@/components/legal/cancel-court-date-dialog";
import { OutcomeDialog } from "@/components/legal/outcome-dialog";
import { fetchCourtDates, fetchPrescriptionTrackers, type CourtDateFilters } from "./actions";
import type { CourtDate, CourtDateStatus, CourtDateType, PrescriptionTracker } from "@/lib/types";

type ViewMode = "list" | "calendar" | "prescriptions";

interface CourtCalendarClientProps {
  initialCourtDates: CourtDate[];
  initialTotal: number;
  initialTrackers: PrescriptionTracker[];
  slug: string;
}

export function CourtCalendarClient({
  initialCourtDates,
  initialTotal,
  initialTrackers,
  slug,
}: CourtCalendarClientProps) {
  const [courtDates, setCourtDates] = useState(initialCourtDates);
  const [total, setTotal] = useState(initialTotal);
  const [trackers, setTrackers] = useState(initialTrackers);
  const [view, setView] = useState<ViewMode>("list");
  const [isPending, startTransition] = useTransition();

  // Filter state
  const [statusFilter, setStatusFilter] = useState<CourtDateStatus | "">("");
  const [dateTypeFilter, setDateTypeFilter] = useState<CourtDateType | "">("");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const [clientSearch, setClientSearch] = useState("");

  // Calendar month navigation
  const now = new Date();
  const [calYear, setCalYear] = useState(now.getFullYear());
  const [calMonth, setCalMonth] = useState(now.getMonth() + 1);

  // Detail panel
  const [selectedCourtDate, setSelectedCourtDate] = useState<CourtDate | null>(null);

  // Dialog state
  const [editTarget, setEditTarget] = useState<CourtDate | null>(null);
  const [postponeTarget, setPostponeTarget] = useState<CourtDate | null>(null);
  const [cancelTarget, setCancelTarget] = useState<CourtDate | null>(null);
  const [outcomeTarget, setOutcomeTarget] = useState<CourtDate | null>(null);

  const refetchCourtDates = useCallback(() => {
    startTransition(async () => {
      try {
        const filters: CourtDateFilters = {};
        if (statusFilter) filters.status = statusFilter;
        if (dateTypeFilter) filters.dateType = dateTypeFilter;
        if (dateFrom) filters.from = dateFrom;
        if (dateTo) filters.to = dateTo;
        const result = await fetchCourtDates(filters);
        setCourtDates(result.content);
        setTotal(result.page.totalElements);
      } catch (err) {
        console.error("Failed to refetch court dates:", err);
      }
    });
  }, [statusFilter, dateTypeFilter, dateFrom, dateTo]);

  const refetchTrackers = useCallback(() => {
    startTransition(async () => {
      try {
        const result = await fetchPrescriptionTrackers();
        setTrackers(result.content);
      } catch (err) {
        console.error("Failed to refetch trackers:", err);
      }
    });
  }, []);

  // Refetch when filters change (avoids stale closure from setTimeout)
  const isInitialMount = useRef(true);
  useEffect(() => {
    if (isInitialMount.current) {
      isInitialMount.current = false;
      return;
    }
    refetchCourtDates();
  }, [statusFilter, dateTypeFilter, dateFrom, dateTo, refetchCourtDates]);

  function handleViewChange(v: string) {
    if (v === "list" || v === "calendar" || v === "prescriptions") {
      setView(v);
      setSelectedCourtDate(null);
    }
  }

  function prevMonth() {
    if (calMonth === 1) {
      setCalYear((y) => y - 1);
      setCalMonth(12);
    } else {
      setCalMonth((m) => m - 1);
    }
  }

  function nextMonth() {
    if (calMonth === 12) {
      setCalYear((y) => y + 1);
      setCalMonth(1);
    } else {
      setCalMonth((m) => m + 1);
    }
  }

  const MONTH_NAMES = [
    "January",
    "February",
    "March",
    "April",
    "May",
    "June",
    "July",
    "August",
    "September",
    "October",
    "November",
    "December",
  ];

  // Client-side text filter on customer/matter name
  const filteredCourtDates = useMemo(() => {
    if (!clientSearch.trim()) return courtDates;
    const q = clientSearch.toLowerCase();
    return courtDates.filter(
      (d) => d.customerName?.toLowerCase().includes(q) || d.projectName?.toLowerCase().includes(q)
    );
  }, [courtDates, clientSearch]);

  const scheduledCount = filteredCourtDates.filter((d) => d.status === "SCHEDULED").length;

  return (
    <div className="space-y-4">
      {/* Filters + create button */}
      <div className="flex flex-wrap items-center gap-3">
        <select
          value={statusFilter}
          onChange={(e) => {
            setStatusFilter(e.target.value as CourtDateStatus | "");
          }}
          className="flex h-9 rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm dark:border-slate-800"
        >
          <option value="">All Statuses</option>
          <option value="SCHEDULED">Scheduled</option>
          <option value="POSTPONED">Postponed</option>
          <option value="HEARD">Heard</option>
          <option value="CANCELLED">Cancelled</option>
        </select>

        <select
          value={dateTypeFilter}
          onChange={(e) => {
            setDateTypeFilter(e.target.value as CourtDateType | "");
          }}
          className="flex h-9 rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm dark:border-slate-800"
        >
          <option value="">All Types</option>
          <option value="HEARING">Hearing</option>
          <option value="TRIAL">Trial</option>
          <option value="MOTION">Motion</option>
          <option value="MEDIATION">Mediation</option>
          <option value="ARBITRATION">Arbitration</option>
          <option value="PRE_TRIAL">Pre-Trial</option>
          <option value="CASE_MANAGEMENT">Case Management</option>
          <option value="TAXATION">Taxation</option>
          <option value="OTHER">Other</option>
        </select>

        <Input
          type="date"
          value={dateFrom}
          onChange={(e) => setDateFrom(e.target.value)}
          placeholder="From date"
          className="h-9 w-36"
          aria-label="From date"
        />
        <Input
          type="date"
          value={dateTo}
          onChange={(e) => setDateTo(e.target.value)}
          placeholder="To date"
          className="h-9 w-36"
          aria-label="To date"
        />
        <Input
          value={clientSearch}
          onChange={(e) => setClientSearch(e.target.value)}
          placeholder="Search client/matter..."
          className="h-9 w-48"
          aria-label="Search client or matter"
        />

        <div className="ml-auto">
          <CreateCourtDateDialog slug={slug} onSuccess={refetchCourtDates} />
        </div>
      </div>

      {/* Tabs + count */}
      <div className="flex items-center justify-between">
        <Tabs value={view} onValueChange={handleViewChange}>
          <TabsList>
            <TabsTrigger value="list">List</TabsTrigger>
            <TabsTrigger value="calendar">Calendar</TabsTrigger>
            <TabsTrigger value="prescriptions">Prescriptions</TabsTrigger>
          </TabsList>
        </Tabs>
        <div className="flex items-center gap-2">
          <span className="text-sm text-slate-600 dark:text-slate-400">{total} court dates</span>
          {scheduledCount > 0 && (
            <Badge className="bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300">
              {scheduledCount} upcoming
            </Badge>
          )}
        </div>
      </div>

      {/* Content area */}
      <div className={cn("flex gap-4", isPending && "opacity-50 transition-opacity")}>
        <div className={selectedCourtDate ? "flex-1" : "w-full"}>
          {view === "list" && (
            <CourtDateListView
              courtDates={filteredCourtDates}
              onEdit={setEditTarget}
              onPostpone={setPostponeTarget}
              onCancel={setCancelTarget}
              onRecordOutcome={setOutcomeTarget}
              onSelect={setSelectedCourtDate}
            />
          )}

          {view === "calendar" && (
            <div>
              {/* Month navigation */}
              <div className="mb-3 flex items-center justify-between">
                <Button variant="ghost" size="sm" onClick={prevMonth}>
                  &larr;
                </Button>
                <span className="text-sm font-medium text-slate-700 dark:text-slate-300">
                  {MONTH_NAMES[calMonth - 1]} {calYear}
                </span>
                <Button variant="ghost" size="sm" onClick={nextMonth}>
                  &rarr;
                </Button>
              </div>
              <CourtCalendarView
                courtDates={filteredCourtDates}
                year={calYear}
                month={calMonth}
                onDayClick={(_date, dayCases) => {
                  if (dayCases.length > 0) {
                    setSelectedCourtDate(dayCases[0]);
                  }
                }}
              />
            </div>
          )}

          {view === "prescriptions" && (
            <PrescriptionTab trackers={trackers} slug={slug} onRefresh={refetchTrackers} />
          )}
        </div>

        {/* Detail side panel */}
        {selectedCourtDate && (
          <div className="w-80 shrink-0 rounded-lg border border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-950">
            <div className="mb-3 flex items-center justify-between">
              <h3 className="text-sm font-semibold text-slate-950 dark:text-slate-50">
                Court Date Details
              </h3>
              <Button
                variant="ghost"
                size="sm"
                className="size-7 p-0"
                onClick={() => setSelectedCourtDate(null)}
              >
                <X className="size-4" />
              </Button>
            </div>

            <dl className="space-y-2 text-sm">
              <div>
                <dt className="text-slate-500 dark:text-slate-400">Date</dt>
                <dd className="font-mono text-slate-700 dark:text-slate-300">
                  {selectedCourtDate.scheduledDate}
                  {selectedCourtDate.scheduledTime && ` at ${selectedCourtDate.scheduledTime}`}
                </dd>
              </div>
              <div>
                <dt className="text-slate-500 dark:text-slate-400">Type</dt>
                <dd className="text-slate-700 dark:text-slate-300">
                  {selectedCourtDate.dateType
                    .split("_")
                    .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
                    .join("-")}
                </dd>
              </div>
              <div>
                <dt className="text-slate-500 dark:text-slate-400">Court</dt>
                <dd className="text-slate-700 dark:text-slate-300">
                  {selectedCourtDate.courtName}
                </dd>
              </div>
              {selectedCourtDate.courtReference && (
                <div>
                  <dt className="text-slate-500 dark:text-slate-400">Reference</dt>
                  <dd className="text-slate-700 dark:text-slate-300">
                    {selectedCourtDate.courtReference}
                  </dd>
                </div>
              )}
              {selectedCourtDate.judgeMagistrate && (
                <div>
                  <dt className="text-slate-500 dark:text-slate-400">Judge / Magistrate</dt>
                  <dd className="text-slate-700 dark:text-slate-300">
                    {selectedCourtDate.judgeMagistrate}
                  </dd>
                </div>
              )}
              <div>
                <dt className="text-slate-500 dark:text-slate-400">Matter</dt>
                <dd className="font-medium text-slate-950 dark:text-slate-50">
                  {selectedCourtDate.projectName}
                </dd>
              </div>
              <div>
                <dt className="text-slate-500 dark:text-slate-400">Client</dt>
                <dd className="text-slate-700 dark:text-slate-300">
                  {selectedCourtDate.customerName}
                </dd>
              </div>
              <div>
                <dt className="text-slate-500 dark:text-slate-400">Status</dt>
                <dd className="text-slate-700 dark:text-slate-300">{selectedCourtDate.status}</dd>
              </div>
              {selectedCourtDate.outcome && (
                <div>
                  <dt className="text-slate-500 dark:text-slate-400">Outcome</dt>
                  <dd className="text-slate-700 dark:text-slate-300">
                    {selectedCourtDate.outcome}
                  </dd>
                </div>
              )}
              {selectedCourtDate.description && (
                <div>
                  <dt className="text-slate-500 dark:text-slate-400">Description</dt>
                  <dd className="text-slate-700 dark:text-slate-300">
                    {selectedCourtDate.description}
                  </dd>
                </div>
              )}
            </dl>

            {/* Action buttons */}
            <div className="mt-4 flex flex-wrap gap-2">
              {(selectedCourtDate.status === "SCHEDULED" ||
                selectedCourtDate.status === "POSTPONED") && (
                <>
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => setEditTarget(selectedCourtDate)}
                  >
                    Edit
                  </Button>
                  {selectedCourtDate.status === "SCHEDULED" && (
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => setPostponeTarget(selectedCourtDate)}
                    >
                      Postpone
                    </Button>
                  )}
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => setCancelTarget(selectedCourtDate)}
                  >
                    Cancel
                  </Button>
                  <Button size="sm" onClick={() => setOutcomeTarget(selectedCourtDate)}>
                    Record Outcome
                  </Button>
                </>
              )}
            </div>
          </div>
        )}
      </div>

      {/* Lifecycle dialogs */}
      {editTarget && (
        <EditCourtDateDialog
          slug={slug}
          courtDate={editTarget}
          open={!!editTarget}
          onOpenChange={(v) => {
            if (!v) setEditTarget(null);
          }}
          onSuccess={() => {
            setSelectedCourtDate(null);
            refetchCourtDates();
          }}
        />
      )}

      {postponeTarget && (
        <PostponeDialog
          slug={slug}
          courtDateId={postponeTarget.id}
          open={!!postponeTarget}
          onOpenChange={(v) => {
            if (!v) setPostponeTarget(null);
          }}
          onSuccess={() => {
            setSelectedCourtDate(null);
            refetchCourtDates();
          }}
        />
      )}

      {cancelTarget && (
        <CancelCourtDateDialog
          slug={slug}
          courtDateId={cancelTarget.id}
          open={!!cancelTarget}
          onOpenChange={(v) => {
            if (!v) setCancelTarget(null);
          }}
          onSuccess={() => {
            setSelectedCourtDate(null);
            refetchCourtDates();
          }}
        />
      )}

      {outcomeTarget && (
        <OutcomeDialog
          slug={slug}
          courtDateId={outcomeTarget.id}
          open={!!outcomeTarget}
          onOpenChange={(v) => {
            if (!v) setOutcomeTarget(null);
          }}
          onSuccess={() => {
            setSelectedCourtDate(null);
            refetchCourtDates();
          }}
        />
      )}
    </div>
  );
}
