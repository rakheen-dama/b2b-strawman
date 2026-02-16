"use client";

import { useState } from "react";
import { ClipboardCheck, Plus } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/empty-state";
import { ChecklistProgress } from "./ChecklistProgress";
import { instantiateChecklist } from "@/lib/actions/checklists";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import type {
  ChecklistInstanceResponse,
  ChecklistInstanceItemResponse,
  ChecklistTemplateResponse,
} from "@/lib/types";

interface InstanceWithItems {
  instance: ChecklistInstanceResponse;
  items: ChecklistInstanceItemResponse[];
}

interface OnboardingTabProps {
  instances: InstanceWithItems[];
  templates: ChecklistTemplateResponse[];
  customerId: string;
  slug: string;
  canManage: boolean;
}

export function OnboardingTab({
  instances,
  templates,
  customerId,
  slug,
  canManage,
}: OnboardingTabProps) {
  const [dialogOpen, setDialogOpen] = useState(false);
  const [loading, setLoading] = useState(false);

  const [error, setError] = useState<string | null>(null);

  async function handleInstantiate(templateId: string) {
    setLoading(true);
    setError(null);
    const result = await instantiateChecklist(slug, customerId, templateId);
    setLoading(false);
    if (result.success) {
      setDialogOpen(false);
    } else {
      setError(result.error ?? "Failed to add checklist.");
    }
  }

  const header = (
    <div className="flex items-center justify-between">
      <div className="flex items-center gap-2">
        <h2 className="font-semibold text-slate-900 dark:text-slate-100">Onboarding</h2>
        {instances.length > 0 && <Badge variant="neutral">{instances.length}</Badge>}
      </div>
      {canManage && templates.length > 0 && (
        <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
          <DialogTrigger asChild>
            <Button size="sm">
              <Plus className="mr-1 size-4" />
              Add Checklist
            </Button>
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Add Checklist</DialogTitle>
            </DialogHeader>
            <div className="space-y-2">
              <p className="text-sm text-slate-600 dark:text-slate-400">
                Select a template to create a new checklist instance for this customer.
              </p>
              {error && (
                <p className="text-sm text-red-600 dark:text-red-400">{error}</p>
              )}
              <div className="space-y-2">
                {templates.map((template) => (
                  <button
                    key={template.id}
                    type="button"
                    onClick={() => handleInstantiate(template.id)}
                    disabled={loading}
                    className="flex w-full items-center justify-between rounded-lg border border-slate-200 px-4 py-3 text-left transition-colors hover:bg-slate-50 dark:border-slate-800 dark:hover:bg-slate-900"
                  >
                    <div>
                      <p className="text-sm font-medium text-slate-950 dark:text-slate-50">
                        {template.name}
                      </p>
                      {template.description && (
                        <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">
                          {template.description}
                        </p>
                      )}
                    </div>
                    <Badge variant="neutral">{template.customerType}</Badge>
                  </button>
                ))}
              </div>
            </div>
          </DialogContent>
        </Dialog>
      )}
    </div>
  );

  if (instances.length === 0) {
    return (
      <div className="space-y-4">
        {header}
        <EmptyState
          icon={ClipboardCheck}
          title="No checklists yet"
          description="Add a checklist to track onboarding and compliance tasks"
        />
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {header}
      <div className="space-y-3">
        {instances.map(({ instance, items }) => (
          <ChecklistProgress
            key={instance.id}
            instance={instance}
            items={items}
            canManage={canManage}
            slug={slug}
            customerId={customerId}
          />
        ))}
      </div>
    </div>
  );
}
