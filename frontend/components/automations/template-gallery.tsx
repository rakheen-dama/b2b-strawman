"use client";

import { useState, useTransition } from "react";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetDescription,
} from "@/components/ui/sheet";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { TriggerTypeBadge } from "@/components/automations/trigger-type-badge";
import { toast } from "sonner";
import { activateTemplateAction } from "@/app/(app)/org/[slug]/settings/automations/actions";
import { Zap, Check } from "lucide-react";
import type { TemplateDefinitionResponse, TriggerType } from "@/lib/api/automations";

interface TemplateGalleryProps {
  slug: string;
  templates: TemplateDefinitionResponse[];
  activatedSlugs: string[];
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function TemplateGallery({
  slug,
  templates,
  activatedSlugs,
  open,
  onOpenChange,
}: TemplateGalleryProps) {
  const [isPending, startTransition] = useTransition();
  const [activatingSlug, setActivatingSlug] = useState<string | null>(null);

  const grouped = templates.reduce<Record<string, TemplateDefinitionResponse[]>>(
    (acc, template) => {
      const category = template.category || "General";
      if (!acc[category]) acc[category] = [];
      acc[category].push(template);
      return acc;
    },
    {}
  );

  const categories = Object.keys(grouped).sort();

  function handleActivate(templateSlug: string) {
    setActivatingSlug(templateSlug);
    startTransition(async () => {
      const result = await activateTemplateAction(slug, templateSlug);
      setActivatingSlug(null);
      if (result.success) {
        toast.success("Template activated successfully");
        onOpenChange(false);
      } else {
        toast.error(result.error ?? "Failed to activate template");
      }
    });
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="w-full overflow-y-auto sm:max-w-lg">
        <SheetHeader>
          <SheetTitle>Automation Templates</SheetTitle>
          <SheetDescription>Browse and activate pre-built automation templates.</SheetDescription>
        </SheetHeader>

        <div className="mt-6 space-y-8">
          {templates.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-12 text-center">
              <Zap className="size-10 text-slate-300 dark:text-slate-700" />
              <p className="mt-3 text-sm text-slate-600 dark:text-slate-400">
                No templates available.
              </p>
            </div>
          ) : (
            categories.map((category) => (
              <div key={category}>
                <h3 className="mb-3 text-sm font-semibold tracking-wide text-slate-500 uppercase dark:text-slate-400">
                  {category}
                </h3>
                <div className="grid gap-3">
                  {grouped[category].map((template) => {
                    const isActivated = activatedSlugs.includes(template.slug);
                    const isActivating = isPending && activatingSlug === template.slug;

                    return (
                      <div
                        key={template.slug}
                        className="rounded-lg border border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-950"
                      >
                        <div className="flex items-start justify-between gap-3">
                          <div className="min-w-0 flex-1">
                            <div className="flex items-center gap-2">
                              <p className="font-medium text-slate-950 dark:text-slate-50">
                                {template.name}
                              </p>
                              {isActivated && (
                                <Badge variant="success">
                                  <Check className="size-3" />
                                  Activated
                                </Badge>
                              )}
                            </div>
                            <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
                              {template.description}
                            </p>
                            <div className="mt-2 flex items-center gap-2">
                              <TriggerTypeBadge triggerType={template.triggerType as TriggerType} />
                              <span className="text-xs text-slate-500 dark:text-slate-400">
                                {template.actionCount}{" "}
                                {template.actionCount === 1 ? "action" : "actions"}
                              </span>
                            </div>
                          </div>
                          {!isActivated && (
                            <Button
                              size="sm"
                              variant="outline"
                              disabled={isActivating}
                              onClick={() => handleActivate(template.slug)}
                            >
                              {isActivating ? "Activating..." : "Activate"}
                            </Button>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            ))
          )}
        </div>
      </SheetContent>
    </Sheet>
  );
}
