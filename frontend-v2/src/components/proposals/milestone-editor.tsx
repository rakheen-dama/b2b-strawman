"use client";

import { Plus, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";
import type { MilestoneData } from "@/app/(app)/org/[slug]/proposals/proposal-actions";

export interface MilestoneWithId extends MilestoneData {
  clientId: string;
}

interface MilestoneEditorProps {
  milestones: MilestoneData[];
  onChange: (milestones: MilestoneData[]) => void;
}

function ensureClientIds(milestones: MilestoneData[]): MilestoneWithId[] {
  return milestones.map((m) => ({
    ...m,
    clientId: (m as MilestoneWithId).clientId ?? crypto.randomUUID(),
  }));
}

function stripClientIds(milestones: MilestoneWithId[]): MilestoneData[] {
  return milestones.map(({ clientId: _, ...rest }) => rest);
}

export function MilestoneEditor({
  milestones,
  onChange,
}: MilestoneEditorProps) {
  const items = ensureClientIds(milestones);
  const total = items.reduce((sum, m) => sum + (m.percentage || 0), 0);
  const isValid = total === 100;

  function addMilestone() {
    const newItem: MilestoneWithId = {
      clientId: crypto.randomUUID(),
      description: "",
      percentage: 0,
      relativeDueDays: 0,
    };
    onChange(stripClientIds([...items, newItem]));
  }

  function removeMilestone(clientId: string) {
    onChange(stripClientIds(items.filter((m) => m.clientId !== clientId)));
  }

  function updateMilestone(
    clientId: string,
    field: keyof MilestoneData,
    value: string | number,
  ) {
    const updated = items.map((m) => {
      if (m.clientId !== clientId) return m;
      return { ...m, [field]: value };
    });
    onChange(stripClientIds(updated));
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <Label>Milestones</Label>
        <span
          className={cn(
            "text-sm font-medium tabular-nums",
            isValid ? "text-emerald-600" : "text-red-600",
          )}
          data-testid="milestone-total"
        >
          {total}% / 100%
        </span>
      </div>

      {items.length > 0 && (
        <div className="space-y-2">
          <div className="grid grid-cols-[1fr_80px_80px_32px] gap-2 text-xs font-medium text-slate-500">
            <span>Description</span>
            <span>Percentage</span>
            <span>Due (days)</span>
            <span />
          </div>
          {items.map((milestone) => (
            <div
              key={milestone.clientId}
              className="grid grid-cols-[1fr_80px_80px_32px] gap-2"
              data-testid="milestone-row"
            >
              <Input
                placeholder="Milestone description"
                value={milestone.description}
                onChange={(e) =>
                  updateMilestone(milestone.clientId, "description", e.target.value)
                }
              />
              <Input
                type="number"
                min={0}
                max={100}
                placeholder="%"
                value={milestone.percentage || ""}
                onChange={(e) =>
                  updateMilestone(
                    milestone.clientId,
                    "percentage",
                    e.target.value ? Number(e.target.value) : 0,
                  )
                }
              />
              <Input
                type="number"
                min={0}
                placeholder="Days"
                value={milestone.relativeDueDays || ""}
                onChange={(e) =>
                  updateMilestone(
                    milestone.clientId,
                    "relativeDueDays",
                    e.target.value ? Number(e.target.value) : 0,
                  )
                }
              />
              <Button
                type="button"
                variant="ghost"
                size="icon-xs"
                onClick={() => removeMilestone(milestone.clientId)}
                aria-label="Remove milestone"
              >
                <Trash2 className="h-3.5 w-3.5 text-slate-400" />
              </Button>
            </div>
          ))}
        </div>
      )}

      <Button
        type="button"
        variant="outline"
        size="sm"
        onClick={addMilestone}
      >
        <Plus className="mr-1 h-3.5 w-3.5" />
        Add milestone
      </Button>
    </div>
  );
}
