"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { ChevronDown, ChevronRight, Plus, ClipboardList } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { ChecklistProgressBar } from "@/components/compliance/ChecklistProgressBar";
import { ChecklistInstanceItemRow } from "@/components/compliance/ChecklistInstanceItemRow";
import {
  completeChecklistItem,
  skipChecklistItem,
  reopenChecklistItem,
  instantiateChecklist,
} from "@/app/(app)/org/[slug]/customers/[id]/checklist-actions";
import { cn } from "@/lib/utils";
import type {
  ChecklistInstanceResponse,
  ChecklistInstanceStatus,
  ChecklistTemplateResponse,
} from "@/lib/types";

type InstanceBadgeVariant = "success" | "neutral" | "destructive";

const INSTANCE_STATUS_CONFIG: Record<
  ChecklistInstanceStatus,
  { label: string; variant: InstanceBadgeVariant }
> = {
  IN_PROGRESS: { label: "In Progress", variant: "neutral" },
  COMPLETED: { label: "Completed", variant: "success" },
  CANCELLED: { label: "Cancelled", variant: "destructive" },
};

interface ChecklistInstancePanelProps {
  customerId: string;
  instances: ChecklistInstanceResponse[];
  isAdmin: boolean;
  slug: string;
  templateNames: Record<string, string>;
  templates?: ChecklistTemplateResponse[];
}

function computeProgress(instance: ChecklistInstanceResponse) {
  const items = instance.items ?? [];
  const total = items.length;
  const completed = items.filter(
    (i) => i.status === "COMPLETED" || i.status === "SKIPPED",
  ).length;
  const requiredTotal = items.filter((i) => i.required).length;
  const requiredCompleted = items.filter(
    (i) => i.required && i.status === "COMPLETED",
  ).length;
  return { completed, total, requiredCompleted, requiredTotal };
}

export function ChecklistInstancePanel({
  customerId,
  instances,
  isAdmin,
  slug,
  templateNames,
  templates,
}: ChecklistInstancePanelProps) {
  const router = useRouter();
  const [expandedIds, setExpandedIds] = useState<Set<string>>(() => {
    const initial = new Set<string>();
    for (const inst of instances) {
      if (inst.status !== "COMPLETED") {
        initial.add(inst.id);
      }
    }
    return initial;
  });
  const [addDialogOpen, setAddDialogOpen] = useState(false);
  const [selectedTemplateId, setSelectedTemplateId] = useState<string>("");
  const [isInstantiating, setIsInstantiating] = useState(false);
  const [instantiateError, setInstantiateError] = useState<string | null>(null);

  function toggleExpanded(id: string) {
    setExpandedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }

  async function handleComplete(itemId: string, notes: string, documentId?: string) {
    const result = await completeChecklistItem(slug, customerId, itemId, notes, documentId);
    if (!result.success) {
      throw new Error(result.error ?? "Failed to complete item");
    }
  }

  async function handleSkip(itemId: string, reason: string) {
    const result = await skipChecklistItem(slug, customerId, itemId, reason);
    if (!result.success) {
      throw new Error(result.error ?? "Failed to skip item");
    }
  }

  async function handleReopen(itemId: string) {
    const result = await reopenChecklistItem(slug, customerId, itemId);
    if (!result.success) {
      throw new Error(result.error ?? "Failed to reopen item");
    }
  }

  async function handleInstantiate() {
    if (!selectedTemplateId) return;
    setIsInstantiating(true);
    setInstantiateError(null);
    try {
      const result = await instantiateChecklist(customerId, selectedTemplateId, slug);
      if (result.success) {
        setAddDialogOpen(false);
        setSelectedTemplateId("");
        router.refresh();
      } else {
        setInstantiateError(result.error ?? "Failed to create checklist.");
      }
    } catch {
      setInstantiateError("An unexpected error occurred.");
    } finally {
      setIsInstantiating(false);
    }
  }

  if (instances.length === 0 && !isAdmin) {
    return (
      <div className="py-12 text-center text-sm text-slate-500 dark:text-slate-400">
        <ClipboardList className="mx-auto mb-3 size-8 text-slate-300 dark:text-slate-600" />
        No checklists yet.
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {/* Header with Add button */}
      {isAdmin && (
        <div className="flex justify-end">
          <Dialog open={addDialogOpen} onOpenChange={setAddDialogOpen}>
            <DialogTrigger asChild>
              <Button size="sm" variant="outline">
                <Plus className="mr-1.5 size-4" />
                Manually Add Checklist
              </Button>
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>Add Checklist</DialogTitle>
                <DialogDescription>
                  Select a checklist template to instantiate for this customer.
                </DialogDescription>
              </DialogHeader>
              <div className="space-y-2">
                {templates && templates.length > 0 ? (
                  templates.map((t) => (
                    <button
                      key={t.id}
                      type="button"
                      onClick={() => setSelectedTemplateId(t.id)}
                      className={cn(
                        "w-full rounded-lg border p-3 text-left transition-colors",
                        selectedTemplateId === t.id
                          ? "border-teal-500 bg-teal-50 dark:bg-teal-950"
                          : "border-slate-200 hover:border-slate-300 dark:border-slate-700 dark:hover:border-slate-600",
                      )}
                    >
                      <p className="font-medium text-slate-900 dark:text-slate-100">{t.name}</p>
                      {t.description && (
                        <p className="mt-0.5 text-sm text-slate-500 dark:text-slate-400">
                          {t.description}
                        </p>
                      )}
                    </button>
                  ))
                ) : (
                  <p className="text-sm text-slate-500 dark:text-slate-400">
                    No templates available.
                  </p>
                )}
              </div>
              {instantiateError && (
                <p className="text-sm text-destructive">{instantiateError}</p>
              )}
              <DialogFooter>
                <Button
                  onClick={handleInstantiate}
                  disabled={!selectedTemplateId || isInstantiating}
                >
                  {isInstantiating ? "Creating..." : "Create Checklist"}
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        </div>
      )}

      {/* Empty state */}
      {instances.length === 0 && (
        <div className="py-12 text-center text-sm text-slate-500 dark:text-slate-400">
          <ClipboardList className="mx-auto mb-3 size-8 text-slate-300 dark:text-slate-600" />
          No checklists yet.
        </div>
      )}

      {/* Instance list */}
      {instances.map((instance, index) => {
        const isExpanded = expandedIds.has(instance.id);
        const statusConfig = INSTANCE_STATUS_CONFIG[instance.status] ?? {
          label: instance.status,
          variant: "neutral" as const,
        };
        const progress = computeProgress(instance);
        const templateName =
          templateNames[instance.templateId] ?? `Checklist ${index + 1}`;

        return (
          <div
            key={instance.id}
            className="rounded-lg border border-slate-200 dark:border-slate-800"
          >
            {/* Instance header */}
            <button
              type="button"
              onClick={() => toggleExpanded(instance.id)}
              className="flex w-full items-center gap-3 p-4 text-left"
            >
              {isExpanded ? (
                <ChevronDown className="size-4 shrink-0 text-slate-500" />
              ) : (
                <ChevronRight className="size-4 shrink-0 text-slate-500" />
              )}
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <span className="font-medium text-slate-900 dark:text-slate-100">
                    {templateName}
                  </span>
                  <Badge variant={statusConfig.variant}>{statusConfig.label}</Badge>
                </div>
                <div className="mt-2">
                  <ChecklistProgressBar {...progress} />
                </div>
              </div>
            </button>

            {/* Expanded items */}
            {isExpanded && (
              <div className="space-y-2 border-t border-slate-200 p-4 dark:border-slate-800">
                {(instance.items ?? [])
                  .sort((a, b) => a.sortOrder - b.sortOrder)
                  .map((item) => (
                    <ChecklistInstanceItemRow
                      key={item.id}
                      item={item}
                      instanceItems={instance.items ?? []}
                      onComplete={handleComplete}
                      onSkip={handleSkip}
                      onReopen={handleReopen}
                      isAdmin={isAdmin}
                    />
                  ))}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}
