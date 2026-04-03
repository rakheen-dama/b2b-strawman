"use client";

import { useEffect, useRef, useState, useTransition } from "react";
import Link from "next/link";
import {
  Table,
  TableHeader,
  TableBody,
  TableRow,
  TableHead,
  TableCell,
} from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import { TriggerTypeBadge } from "@/components/automations/trigger-type-badge";
import { TemplateGallery } from "@/components/automations/template-gallery";
import {
  toggleRuleAction,
  deleteRuleAction,
} from "@/app/(app)/org/[slug]/settings/automations/actions";
import { toast } from "sonner";
import { RelativeDate } from "@/components/ui/relative-date";
import { Zap, Plus, LayoutGrid, Trash2 } from "lucide-react";
import { docsLink } from "@/lib/docs";
import type {
  AutomationRuleResponse,
  TemplateDefinitionResponse,
} from "@/lib/api/automations";

interface RuleListProps {
  slug: string;
  rules: AutomationRuleResponse[];
  templates: TemplateDefinitionResponse[];
  canManage: boolean;
}

export function RuleList({ slug, rules, templates, canManage }: RuleListProps) {
  const [galleryOpen, setGalleryOpen] = useState(false);
  const [isPending, startTransition] = useTransition();
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [confirmDeleteId, setConfirmDeleteId] = useState<string | null>(null);
  const [optimisticToggles, setOptimisticToggles] = useState<Record<string, boolean>>({});
  const confirmTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (confirmDeleteId) {
      confirmTimerRef.current = setTimeout(() => {
        setConfirmDeleteId(null);
      }, 3000);
    }
    return () => {
      if (confirmTimerRef.current) {
        clearTimeout(confirmTimerRef.current);
        confirmTimerRef.current = null;
      }
    };
  }, [confirmDeleteId]);

  const activatedSlugs = rules
    .filter((r) => r.templateSlug)
    .map((r) => r.templateSlug!);

  function handleToggle(ruleId: string, currentEnabled: boolean) {
    // Optimistic update
    setOptimisticToggles(prev => ({ ...prev, [ruleId]: !currentEnabled }));

    startTransition(async () => {
      const result = await toggleRuleAction(slug, ruleId);
      if (result.success) {
        toast.success("Rule toggled successfully");
        // Clear optimistic state — server re-render will provide fresh data
        setOptimisticToggles(prev => {
          const next = { ...prev };
          delete next[ruleId];
          return next;
        });
      } else {
        // Revert optimistic state
        setOptimisticToggles(prev => {
          const next = { ...prev };
          delete next[ruleId];
          return next;
        });
        toast.error(result.error ?? "Failed to toggle rule");
      }
    });
  }

  function handleDelete(ruleId: string) {
    if (confirmDeleteId !== ruleId) {
      setConfirmDeleteId(ruleId);
      return;
    }
    setDeletingId(ruleId);
    setConfirmDeleteId(null);
    startTransition(async () => {
      const result = await deleteRuleAction(slug, ruleId);
      setDeletingId(null);
      if (result.success) {
        toast.success("Rule deleted successfully");
      } else {
        toast.error(result.error ?? "Failed to delete rule");
      }
    });
  }

  return (
    <div className="space-y-6">
      {/* Actions */}
      {canManage && (
        <div className="flex items-center justify-end gap-2">
          <Button
            size="sm"
            variant="outline"
            onClick={() => setGalleryOpen(true)}
          >
            <LayoutGrid className="mr-1.5 size-4" />
            Browse Templates
          </Button>
          <Button size="sm" asChild>
            <Link href={`/org/${slug}/settings/automations/new`}>
              <Plus className="mr-1.5 size-4" />
              New Automation
            </Link>
          </Button>
        </div>
      )}

      {/* Rules Table or Empty State */}
      {rules.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-16 text-center">
          <Zap className="size-12 text-slate-300 dark:text-slate-700" />
          <h2 className="mt-4 font-display text-lg text-slate-900 dark:text-slate-100">
            No automation rules yet
          </h2>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            {canManage
              ? "Create your first automation or browse templates to get started."
              : "No automation rules have been created yet."}
          </p>
          {canManage && (
            <div className="mt-4 flex gap-2">
              <Button
                size="sm"
                variant="outline"
                onClick={() => setGalleryOpen(true)}
              >
                <LayoutGrid className="mr-1.5 size-4" />
                Browse Templates
              </Button>
              <Button size="sm" asChild>
                <Link href={`/org/${slug}/settings/automations/new`}>
                  <Plus className="mr-1.5 size-4" />
                  New Automation
                </Link>
              </Button>
            </div>
          )}
          <a
            href={docsLink("/features/workflow-automations")}
            target="_blank"
            rel="noopener noreferrer"
            className="mt-2 text-sm text-teal-600 hover:text-teal-700"
          >
            Read the guide
          </a>
        </div>
      ) : (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Trigger</TableHead>
                <TableHead>Enabled</TableHead>
                <TableHead>Last Updated</TableHead>
                <TableHead>Actions</TableHead>
                {canManage && <TableHead className="w-10" />}
              </TableRow>
            </TableHeader>
            <TableBody>
              {rules.map((rule) => (
                <TableRow
                  key={rule.id}
                  data-testid="automation-row"
                >
                  <TableCell>
                    <Link
                      href={`/org/${slug}/settings/automations/${rule.id}`}
                      className="block"
                    >
                      <p className="font-medium text-slate-950 hover:text-teal-600 dark:text-slate-50 dark:hover:text-teal-400">
                        {rule.name}
                      </p>
                      {rule.description && (
                        <p className="text-xs text-slate-500 dark:text-slate-400">
                          {rule.description}
                        </p>
                      )}
                    </Link>
                  </TableCell>
                  <TableCell>
                    <TriggerTypeBadge triggerType={rule.triggerType} />
                  </TableCell>
                  <TableCell>
                    <Switch
                      checked={optimisticToggles[rule.id] ?? rule.enabled}
                      size="sm"
                      onCheckedChange={() => handleToggle(rule.id, optimisticToggles[rule.id] ?? rule.enabled)}
                      disabled={!canManage || isPending}
                      aria-label={`Toggle ${rule.name}`}
                    />
                  </TableCell>
                  <TableCell>
                    <span className="text-sm text-slate-600 dark:text-slate-400">
                      {rule.updatedAt
                        ? <RelativeDate iso={rule.updatedAt} />
                        : "Never"}
                    </span>
                  </TableCell>
                  <TableCell>
                    <span className="font-mono text-sm tabular-nums text-slate-600 dark:text-slate-400">
                      {rule.actions.length}
                    </span>
                  </TableCell>
                  {canManage && (
                    <TableCell>
                      <Button
                        size="sm"
                        variant="ghost"
                        className="size-8 p-0 text-slate-400 hover:text-red-600 dark:hover:text-red-400"
                        onClick={(e) => {
                          e.stopPropagation();
                          handleDelete(rule.id);
                        }}
                        disabled={isPending && deletingId === rule.id}
                        aria-label={`Delete ${rule.name}`}
                      >
                        <Trash2 className="size-4" />
                      </Button>
                    </TableCell>
                  )}
                </TableRow>
              ))}
            </TableBody>
          </Table>
          {canManage && (
            <div className="flex justify-end">
              <Link
                href={`/org/${slug}/settings/automations/executions`}
                className="text-sm text-teal-600 underline-offset-4 hover:text-teal-700 hover:underline dark:text-teal-400 dark:hover:text-teal-300"
              >
                View Execution Log &rarr;
              </Link>
            </div>
          )}
        </>
      )}

      {/* Template Gallery Sheet */}
      <TemplateGallery
        slug={slug}
        templates={templates}
        activatedSlugs={activatedSlugs}
        open={galleryOpen}
        onOpenChange={setGalleryOpen}
      />
    </div>
  );
}
