"use client";

import { useState } from "react";
import { AlertTriangle, Pencil, Plus, Trash2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { WeekCell } from "@/lib/api/capacity";
import {
  createAllocationAction,
  updateAllocationAction,
  deleteAllocationAction,
} from "@/app/(app)/org/[slug]/resources/allocation-actions";

interface ProjectOption {
  id: string;
  name: string;
}

interface AllocationPopoverProps {
  memberId: string;
  memberName: string;
  weekStart: string;
  cell: WeekCell;
  projects: ProjectOption[];
  slug: string;
  children: React.ReactNode;
}

export function AllocationPopover({
  memberId,
  memberName,
  weekStart,
  cell,
  projects,
  slug,
  children,
}: AllocationPopoverProps) {
  const [open, setOpen] = useState(false);
  const [showAddForm, setShowAddForm] = useState(false);
  const [editingAllocationId, setEditingAllocationId] = useState<string | null>(null);
  const [projectId, setProjectId] = useState("");
  const [hours, setHours] = useState("");
  const [note, setNote] = useState("");
  const [editHours, setEditHours] = useState("");
  const [editNote, setEditNote] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [overAllocatedWarning, setOverAllocatedWarning] = useState(false);

  function resetForm() {
    setProjectId("");
    setHours("");
    setNote("");
    setError(null);
    setShowAddForm(false);
  }

  function resetEditForm() {
    setEditingAllocationId(null);
    setEditHours("");
    setEditNote("");
    setError(null);
  }

  function handleOpenChange(newOpen: boolean) {
    setOpen(newOpen);
    if (!newOpen) {
      resetForm();
      resetEditForm();
      setOverAllocatedWarning(false);
    }
  }

  async function handleAdd() {
    if (!projectId || !hours) return;
    setError(null);
    setIsSubmitting(true);
    setOverAllocatedWarning(false);

    try {
      const result = await createAllocationAction(slug, {
        memberId,
        projectId,
        weekStart,
        allocatedHours: Number(hours),
        note: note || undefined,
      });
      if (result.success) {
        resetForm();
        if (result.allocation?.overAllocated) {
          setOverAllocatedWarning(true);
        }
      } else {
        setError(result.error ?? "Failed to create allocation.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleUpdate(allocationId: string) {
    if (!editHours) return;
    setError(null);
    setIsSubmitting(true);

    try {
      const result = await updateAllocationAction(slug, allocationId, {
        allocatedHours: Number(editHours),
        note: editNote || undefined,
      });
      if (result.success) {
        if (result.allocation?.overAllocated) {
          setOverAllocatedWarning(true);
        } else {
          setOverAllocatedWarning(false);
        }
        resetEditForm();
      } else {
        setError(result.error ?? "Failed to update allocation.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleDelete(allocationId: string) {
    setError(null);
    setIsSubmitting(true);

    try {
      const result = await deleteAllocationAction(slug, allocationId);
      if (!result.success) {
        setError(result.error ?? "Failed to delete allocation.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Popover open={open} onOpenChange={handleOpenChange}>
      <PopoverTrigger asChild>{children}</PopoverTrigger>
      <PopoverContent className="w-80 p-0" align="start">
        <div className="border-b border-slate-200 px-4 py-3 dark:border-slate-700">
          <p className="text-sm font-medium text-slate-900 dark:text-slate-100">{memberName}</p>
          <p className="text-xs text-slate-500 dark:text-slate-400">Week of {weekStart}</p>
        </div>

        {/* Existing allocations */}
        <div className="px-4 py-2">
          {cell.allocations.length === 0 ? (
            <p className="py-2 text-sm text-slate-500 dark:text-slate-400">
              No allocations this week.
            </p>
          ) : (
            <ul className="space-y-2">
              {cell.allocations.map((slot) => (
                <li key={slot.id} className="flex items-center justify-between text-sm">
                  {editingAllocationId === slot.id ? (
                    <div className="flex w-full flex-col gap-2">
                      <div className="flex items-center gap-2">
                        <span className="min-w-0 flex-1 truncate font-medium text-slate-900 dark:text-slate-100">
                          {slot.projectName}
                        </span>
                      </div>
                      <div className="flex items-center gap-2">
                        <Input
                          type="number"
                          value={editHours}
                          onChange={(e) => setEditHours(e.target.value)}
                          placeholder="Hours"
                          className="h-7 w-16 text-xs"
                          min={0}
                          step={0.5}
                        />
                        <Input
                          value={editNote}
                          onChange={(e) => setEditNote(e.target.value)}
                          placeholder="Note"
                          className="h-7 flex-1 text-xs"
                        />
                      </div>
                      <div className="flex justify-end gap-1">
                        <Button
                          variant="ghost"
                          size="sm"
                          className="h-6 text-xs"
                          onClick={resetEditForm}
                          disabled={isSubmitting}
                        >
                          Cancel
                        </Button>
                        <Button
                          size="sm"
                          className="h-6 text-xs"
                          onClick={() => handleUpdate(slot.id)}
                          disabled={isSubmitting || !editHours}
                        >
                          {isSubmitting ? "Saving..." : "Save"}
                        </Button>
                      </div>
                    </div>
                  ) : (
                    <>
                      <span className="min-w-0 flex-1 truncate text-slate-700 dark:text-slate-300">
                        {slot.projectName}
                      </span>
                      <span className="mx-2 font-mono text-xs text-slate-600 tabular-nums dark:text-slate-400">
                        {slot.hours}h
                      </span>
                      <div className="flex gap-0.5">
                        <Button
                          variant="ghost"
                          size="icon"
                          className="h-6 w-6"
                          onClick={() => {
                            setEditingAllocationId(slot.id);
                            setEditHours(String(slot.hours));
                            setEditNote("");
                          }}
                          disabled={isSubmitting}
                          aria-label={`Edit ${slot.projectName}`}
                        >
                          <Pencil className="h-3 w-3" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon"
                          className="h-6 w-6 text-red-500 hover:text-red-700"
                          onClick={() => handleDelete(slot.id)}
                          disabled={isSubmitting}
                          aria-label={`Delete ${slot.projectName}`}
                        >
                          <Trash2 className="h-3 w-3" />
                        </Button>
                      </div>
                    </>
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>

        {/* Over-allocation warning */}
        {(cell.overAllocated || overAllocatedWarning) && (
          <div
            className="mx-4 mb-2 flex items-center gap-2 rounded-md bg-amber-50 px-3 py-2 text-xs text-amber-700 dark:bg-amber-950 dark:text-amber-300"
            data-testid="over-allocation-warning"
          >
            <AlertTriangle className="h-3.5 w-3.5 shrink-0" />
            <span>This member is over-allocated for this week.</span>
          </div>
        )}

        {error && <div className="mx-4 mb-2 text-xs text-red-600 dark:text-red-400">{error}</div>}

        {/* Add allocation form */}
        {showAddForm ? (
          <div className="space-y-3 border-t border-slate-200 px-4 py-3 dark:border-slate-700">
            <div className="space-y-1.5">
              <Label htmlFor="alloc-project" className="text-xs">
                Project
              </Label>
              <Select value={projectId} onValueChange={setProjectId}>
                <SelectTrigger className="h-8 text-xs" id="alloc-project">
                  <SelectValue placeholder="Select project" />
                </SelectTrigger>
                <SelectContent>
                  {projects.map((p) => (
                    <SelectItem key={p.id} value={p.id}>
                      {p.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="flex gap-2">
              <div className="w-20 space-y-1.5">
                <Label htmlFor="alloc-hours" className="text-xs">
                  Hours
                </Label>
                <Input
                  id="alloc-hours"
                  type="number"
                  value={hours}
                  onChange={(e) => setHours(e.target.value)}
                  min={0}
                  step={0.5}
                  className="h-8 text-xs"
                />
              </div>
              <div className="flex-1 space-y-1.5">
                <Label htmlFor="alloc-note" className="text-xs">
                  Note
                </Label>
                <Input
                  id="alloc-note"
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                  placeholder="Optional"
                  className="h-8 text-xs"
                />
              </div>
            </div>
            <div className="flex justify-end gap-2">
              <Button
                variant="plain"
                size="sm"
                className="h-7 text-xs"
                onClick={resetForm}
                disabled={isSubmitting}
              >
                Cancel
              </Button>
              <Button
                size="sm"
                className="h-7 text-xs"
                onClick={handleAdd}
                disabled={isSubmitting || !projectId || !hours}
              >
                {isSubmitting ? "Saving..." : "Save"}
              </Button>
            </div>
          </div>
        ) : (
          <div className="border-t border-slate-200 px-4 py-2 dark:border-slate-700">
            <Button
              variant="ghost"
              size="sm"
              className="h-7 w-full justify-start gap-1.5 text-xs text-teal-600 hover:text-teal-700 dark:text-teal-400"
              onClick={() => setShowAddForm(true)}
            >
              <Plus className="h-3.5 w-3.5" />
              Add Allocation
            </Button>
          </div>
        )}
      </PopoverContent>
    </Popover>
  );
}
