"use client";

import { useState, useEffect, useCallback } from "react";
import { CalendarDays, Clock, Pencil, Plus, Trash2, X } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Sheet,
  SheetClose,
  SheetContent,
  SheetDescription,
  SheetTitle,
} from "@/components/ui/sheet";
import { UtilizationBadge } from "./utilization-badge";
import { LeaveDialog } from "./leave-dialog";
import type {
  MemberRow,
  MemberCapacityResponse,
  LeaveBlockResponse,
  AllocationResponse,
} from "@/lib/api/capacity";
import {
  listCapacityRecordsAction,
  listLeaveAction,
  listAllocationsAction,
  createCapacityRecordAction,
  deleteCapacityRecordAction,
  deleteLeaveAction,
} from "@/app/(app)/org/[slug]/resources/actions";

interface MemberDetailPanelProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  member: MemberRow | null;
  slug: string;
}

export function MemberDetailPanel({
  open,
  onOpenChange,
  member,
  slug,
}: MemberDetailPanelProps) {
  const [capacityRecords, setCapacityRecords] = useState<
    MemberCapacityResponse[]
  >([]);
  const [leaveBlocks, setLeaveBlocks] = useState<LeaveBlockResponse[]>([]);
  const [allocations, setAllocations] = useState<AllocationResponse[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [leaveDialogOpen, setLeaveDialogOpen] = useState(false);
  const [editingLeave, setEditingLeave] = useState<LeaveBlockResponse | null>(
    null,
  );
  const [showCapacityForm, setShowCapacityForm] = useState(false);
  const [newWeeklyHours, setNewWeeklyHours] = useState("");
  const [newEffectiveFrom, setNewEffectiveFrom] = useState("");
  const [capacityNote, setCapacityNote] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  const loadData = useCallback(async () => {
    if (!member) return;
    setIsLoading(true);
    try {
      const [capResult, leaveResult, allocResult] = await Promise.all([
        listCapacityRecordsAction(slug, member.memberId),
        listLeaveAction(slug, member.memberId),
        listAllocationsAction(slug, member.memberId),
      ]);
      if (capResult.success && capResult.records) {
        setCapacityRecords(capResult.records);
      }
      if (leaveResult.success && leaveResult.blocks) {
        setLeaveBlocks(leaveResult.blocks);
      }
      if (allocResult.success && allocResult.allocations) {
        setAllocations(allocResult.allocations);
      }
    } catch {
      // Silently handle — data will remain empty
    } finally {
      setIsLoading(false);
    }
  }, [member, slug]);

  useEffect(() => {
    if (open && member) {
      loadData();
    }
  }, [open, member, loadData]);

  async function handleAddCapacity() {
    if (!member || !newWeeklyHours || !newEffectiveFrom) return;
    setIsSubmitting(true);
    try {
      const result = await createCapacityRecordAction(slug, member.memberId, {
        weeklyHours: Number(newWeeklyHours),
        effectiveFrom: newEffectiveFrom,
        note: capacityNote || undefined,
      });
      if (result.success) {
        setShowCapacityForm(false);
        setNewWeeklyHours("");
        setNewEffectiveFrom("");
        setCapacityNote("");
        await loadData();
      }
    } catch {
      // ignore
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleDeleteCapacity(id: string) {
    if (!member) return;
    setIsSubmitting(true);
    try {
      const result = await deleteCapacityRecordAction(
        slug,
        member.memberId,
        id,
      );
      if (result.success) {
        await loadData();
      }
    } catch {
      // ignore
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleDeleteLeave(id: string) {
    if (!member) return;
    setIsSubmitting(true);
    try {
      const result = await deleteLeaveAction(slug, member.memberId, id);
      if (result.success) {
        await loadData();
      }
    } catch {
      // ignore
    } finally {
      setIsSubmitting(false);
    }
  }

  function handleLeaveDialogClose(newOpen: boolean) {
    setLeaveDialogOpen(newOpen);
    if (!newOpen) {
      setEditingLeave(null);
      loadData();
    }
  }

  if (!member) return null;

  // Group allocations by project for the timeline
  const allocationsByProject = allocations.reduce(
    (acc, a) => {
      const key = a.projectId;
      if (!acc[key]) acc[key] = [];
      acc[key].push(a);
      return acc;
    },
    {} as Record<string, AllocationResponse[]>,
  );

  // Current capacity (latest record)
  const currentCapacity =
    capacityRecords.length > 0
      ? capacityRecords.sort(
          (a, b) =>
            new Date(b.effectiveFrom).getTime() -
            new Date(a.effectiveFrom).getTime(),
        )[0]
      : null;

  return (
    <>
      <Sheet open={open} onOpenChange={onOpenChange}>
        <SheetContent
          side="right"
          className="flex w-full flex-col gap-0 overflow-y-auto p-0 sm:max-w-md"
          showCloseButton={false}
          onPointerDownOutside={(e) => {
            const target = e.target as HTMLElement | null;
            if (!target?.closest("[data-slot='sheet-overlay']")) {
              e.preventDefault();
            }
          }}
        >
          <SheetTitle className="sr-only">Member Detail</SheetTitle>
          <SheetDescription className="sr-only">
            Member capacity, leave blocks, and allocation timeline.
          </SheetDescription>

          {/* Header */}
          <div className="flex items-start justify-between gap-4 border-b border-slate-200 px-6 py-4 dark:border-slate-800">
            <div className="min-w-0 flex-1">
              <h2 className="text-base font-semibold leading-snug text-slate-950 dark:text-slate-50">
                {member.memberName}
              </h2>
              <p className="mt-0.5 text-sm text-slate-500 dark:text-slate-400">
                {currentCapacity
                  ? `${currentCapacity.weeklyHours}h/week`
                  : "No capacity configured"}
              </p>
            </div>
            <SheetClose asChild>
              <Button variant="ghost" size="icon" className="shrink-0" aria-label="Close">
                <X className="size-4" />
              </Button>
            </SheetClose>
          </div>

          {isLoading ? (
            <div className="flex items-center justify-center py-12">
              <p className="text-sm text-slate-500">Loading...</p>
            </div>
          ) : (
            <>
              {/* Utilization summary */}
              <div className="border-b border-slate-200 px-6 py-4 dark:border-slate-800">
                <h3 className="mb-2 text-sm font-medium text-slate-700 dark:text-slate-300">
                  Utilization
                </h3>
                <div className="flex items-center gap-4">
                  <div className="text-center">
                    <p className="font-mono text-lg font-semibold tabular-nums text-slate-900 dark:text-slate-100">
                      {member.totalAllocated}h
                    </p>
                    <p className="text-xs text-slate-500">Allocated</p>
                  </div>
                  <div className="text-center">
                    <p className="font-mono text-lg font-semibold tabular-nums text-slate-900 dark:text-slate-100">
                      {member.totalCapacity}h
                    </p>
                    <p className="text-xs text-slate-500">Capacity</p>
                  </div>
                  <UtilizationBadge percentage={member.avgUtilizationPct} />
                </div>
              </div>

              {/* Capacity config */}
              <div className="border-b border-slate-200 px-6 py-4 dark:border-slate-800">
                <div className="mb-2 flex items-center justify-between">
                  <h3 className="text-sm font-medium text-slate-700 dark:text-slate-300">
                    <Clock className="mr-1.5 inline h-4 w-4" />
                    Capacity History
                  </h3>
                  <Button
                    variant="ghost"
                    size="sm"
                    className="h-7 gap-1 text-xs text-teal-600 hover:text-teal-700"
                    onClick={() => setShowCapacityForm(true)}
                  >
                    <Plus className="h-3 w-3" />
                    Add
                  </Button>
                </div>

                {showCapacityForm && (
                  <div className="mb-3 space-y-2 rounded-md border border-slate-200 p-3 dark:border-slate-700">
                    <div className="flex gap-2">
                      <div className="w-24 space-y-1">
                        <Label className="text-xs">Hours/wk</Label>
                        <Input
                          type="number"
                          value={newWeeklyHours}
                          onChange={(e) => setNewWeeklyHours(e.target.value)}
                          className="h-8 text-xs"
                          min={0}
                        />
                      </div>
                      <div className="flex-1 space-y-1">
                        <Label className="text-xs">Effective From</Label>
                        <Input
                          type="date"
                          value={newEffectiveFrom}
                          onChange={(e) => setNewEffectiveFrom(e.target.value)}
                          className="h-8 text-xs"
                        />
                      </div>
                    </div>
                    <div className="space-y-1">
                      <Label className="text-xs">Note</Label>
                      <Input
                        value={capacityNote}
                        onChange={(e) => setCapacityNote(e.target.value)}
                        placeholder="Optional"
                        className="h-8 text-xs"
                      />
                    </div>
                    <div className="flex justify-end gap-1">
                      <Button
                        variant="ghost"
                        size="sm"
                        className="h-6 text-xs"
                        onClick={() => {
                          setShowCapacityForm(false);
                          setNewWeeklyHours("");
                          setNewEffectiveFrom("");
                          setCapacityNote("");
                        }}
                        disabled={isSubmitting}
                      >
                        Cancel
                      </Button>
                      <Button
                        size="sm"
                        className="h-6 text-xs"
                        onClick={handleAddCapacity}
                        disabled={
                          isSubmitting || !newWeeklyHours || !newEffectiveFrom
                        }
                      >
                        Save
                      </Button>
                    </div>
                  </div>
                )}

                {capacityRecords.length === 0 ? (
                  <p className="text-sm text-slate-500">
                    No capacity records found.
                  </p>
                ) : (
                  <ul className="space-y-1.5">
                    {capacityRecords
                      .sort(
                        (a, b) =>
                          new Date(b.effectiveFrom).getTime() -
                          new Date(a.effectiveFrom).getTime(),
                      )
                      .map((cap) => (
                        <li
                          key={cap.id}
                          className="flex items-center justify-between rounded px-2 py-1.5 text-sm hover:bg-slate-50 dark:hover:bg-slate-800/50"
                        >
                          <div>
                            <span className="font-mono font-medium tabular-nums text-slate-900 dark:text-slate-100">
                              {cap.weeklyHours}h/wk
                            </span>
                            <span className="ml-2 text-xs text-slate-500">
                              from {cap.effectiveFrom}
                              {cap.effectiveTo ? ` to ${cap.effectiveTo}` : ""}
                            </span>
                          </div>
                          <Button
                            variant="ghost"
                            size="icon"
                            className="h-6 w-6 text-slate-400 hover:text-red-500"
                            onClick={() => handleDeleteCapacity(cap.id)}
                            disabled={isSubmitting}
                            aria-label="Delete capacity record"
                          >
                            <Trash2 className="h-3 w-3" />
                          </Button>
                        </li>
                      ))}
                  </ul>
                )}
              </div>

              {/* Allocation timeline */}
              <div className="border-b border-slate-200 px-6 py-4 dark:border-slate-800">
                <h3 className="mb-2 text-sm font-medium text-slate-700 dark:text-slate-300">
                  Allocation Timeline
                </h3>
                {Object.keys(allocationsByProject).length === 0 ? (
                  <p className="text-sm text-slate-500">
                    No allocations found.
                  </p>
                ) : (
                  <ul className="space-y-2">
                    {Object.entries(allocationsByProject).map(
                      ([, allocs]) => {
                        const totalHours = allocs.reduce(
                          (s, a) => s + a.allocatedHours,
                          0,
                        );
                        return (
                          <li
                            key={allocs[0].projectId}
                            className="rounded border border-slate-100 px-3 py-2 dark:border-slate-700"
                          >
                            <div className="flex items-center justify-between">
                              <span className="text-sm font-medium text-slate-900 dark:text-slate-100">
                                Project {allocs[0].projectId.slice(0, 8)}
                              </span>
                              <span className="font-mono text-xs tabular-nums text-slate-600 dark:text-slate-400">
                                {totalHours}h total
                              </span>
                            </div>
                            <div className="mt-1 flex gap-1">
                              {allocs.map((a) => (
                                <span
                                  key={a.id}
                                  className="text-xs text-slate-500"
                                  title={`${a.weekStart}: ${a.allocatedHours}h`}
                                >
                                  {a.weekStart.slice(5)}: {a.allocatedHours}h
                                </span>
                              ))}
                            </div>
                          </li>
                        );
                      },
                    )}
                  </ul>
                )}
              </div>

              {/* Leave blocks */}
              <div className="px-6 py-4">
                <div className="mb-2 flex items-center justify-between">
                  <h3 className="text-sm font-medium text-slate-700 dark:text-slate-300">
                    <CalendarDays className="mr-1.5 inline h-4 w-4" />
                    Leave Blocks
                  </h3>
                  <Button
                    variant="ghost"
                    size="sm"
                    className="h-7 gap-1 text-xs text-teal-600 hover:text-teal-700"
                    onClick={() => {
                      setEditingLeave(null);
                      setLeaveDialogOpen(true);
                    }}
                  >
                    <Plus className="h-3 w-3" />
                    Add Leave
                  </Button>
                </div>

                {leaveBlocks.length === 0 ? (
                  <p className="text-sm text-slate-500">No leave blocks.</p>
                ) : (
                  <ul className="space-y-1.5">
                    {leaveBlocks.map((leave) => (
                      <li
                        key={leave.id}
                        className="flex items-center justify-between rounded px-2 py-1.5 text-sm hover:bg-slate-50 dark:hover:bg-slate-800/50"
                      >
                        <div>
                          <span className="text-slate-900 dark:text-slate-100">
                            {leave.startDate} — {leave.endDate}
                          </span>
                          {leave.note && (
                            <span className="ml-2 text-xs text-slate-500">
                              {leave.note}
                            </span>
                          )}
                        </div>
                        <div className="flex gap-0.5">
                          <Button
                            variant="ghost"
                            size="icon"
                            className="h-6 w-6"
                            onClick={() => {
                              setEditingLeave(leave);
                              setLeaveDialogOpen(true);
                            }}
                            disabled={isSubmitting}
                            aria-label="Edit leave"
                          >
                            <Pencil className="h-3 w-3" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon"
                            className="h-6 w-6 text-slate-400 hover:text-red-500"
                            onClick={() => handleDeleteLeave(leave.id)}
                            disabled={isSubmitting}
                            aria-label="Delete leave"
                          >
                            <Trash2 className="h-3 w-3" />
                          </Button>
                        </div>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </>
          )}
        </SheetContent>
      </Sheet>

      {member && (
        <LeaveDialog
          open={leaveDialogOpen}
          onOpenChange={handleLeaveDialogClose}
          slug={slug}
          memberId={member.memberId}
          editingLeave={editingLeave}
        />
      )}
    </>
  );
}
