"use client";

import { useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { Switch } from "@/components/ui/switch";
import { Button } from "@/components/ui/button";
import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from "@/components/ui/tabs";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { TriggerTypeBadge } from "@/components/automations/trigger-type-badge";
import { RuleForm } from "@/components/automations/rule-form";
import {
  toggleRuleAction,
  deleteRuleAction,
  updateRuleAction,
  duplicateRuleAction,
} from "@/app/(app)/org/[slug]/settings/automations/actions";
import { MoreHorizontal, Copy, Trash2 } from "lucide-react";
import type { AutomationRuleResponse } from "@/lib/api/automations";

interface RuleDetailClientProps {
  slug: string;
  rule: AutomationRuleResponse;
}

export function RuleDetailClient({ slug, rule }: RuleDetailClientProps) {
  const router = useRouter();
  const [isPending, startTransition] = useTransition();
  const [isSaving, setIsSaving] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  function handleToggle() {
    startTransition(async () => {
      const result = await toggleRuleAction(slug, rule.id);
      if (result.success) {
        toast.success("Rule toggled successfully");
      } else {
        toast.error(result.error ?? "Failed to toggle rule");
      }
    });
  }

  function handleDuplicate() {
    startTransition(async () => {
      const result = await duplicateRuleAction(slug, rule.id);
      if (result.success) {
        toast.success("Rule duplicated successfully");
        if (result.data) {
          router.push(`/org/${slug}/settings/automations/${result.data.id}`);
        }
      } else {
        toast.error(result.error ?? "Failed to duplicate rule");
      }
    });
  }

  function handleDelete() {
    if (!confirmDelete) {
      setConfirmDelete(true);
      setTimeout(() => setConfirmDelete(false), 3000);
      return;
    }
    startTransition(async () => {
      const result = await deleteRuleAction(slug, rule.id);
      if (result.success) {
        toast.success("Rule deleted successfully");
        router.push(`/org/${slug}/settings/automations`);
      } else {
        toast.error(result.error ?? "Failed to delete rule");
      }
    });
  }

  async function handleSave(data: {
    name: string;
    description: string;
    triggerType: string;
    triggerConfig: Record<string, unknown>;
    conditions: Record<string, unknown>[];
  }) {
    setIsSaving(true);
    try {
      const result = await updateRuleAction(slug, rule.id, {
        name: data.name,
        description: data.description || undefined,
        triggerType: data.triggerType as Parameters<typeof updateRuleAction>[2]["triggerType"],
        triggerConfig: data.triggerConfig,
        conditions: data.conditions.length > 0 ? data.conditions : undefined,
      });
      if (result.success) {
        toast.success("Rule updated successfully");
      } else {
        toast.error(result.error ?? "Failed to update rule.");
      }
    } catch {
      toast.error("An unexpected error occurred.");
    } finally {
      setIsSaving(false);
    }
  }

  function handleCancel() {
    router.push(`/org/${slug}/settings/automations`);
  }

  const createdDate = new Date(rule.createdAt).toLocaleDateString("en-US", {
    year: "numeric",
    month: "long",
    day: "numeric",
  });

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-start justify-between">
        <div className="space-y-1">
          <div className="flex items-center gap-3">
            <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
              {rule.name}
            </h1>
            <Switch
              checked={rule.enabled}
              size="sm"
              onCheckedChange={handleToggle}
              disabled={isPending}
              aria-label="Toggle rule"
            />
          </div>
          <div className="flex items-center gap-3 text-sm text-slate-600 dark:text-slate-400">
            <TriggerTypeBadge triggerType={rule.triggerType} />
            <span>Created {createdDate}</span>
          </div>
        </div>

        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="outline" size="sm">
              <MoreHorizontal className="size-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem onClick={handleDuplicate} disabled={isPending}>
              <Copy className="mr-2 size-4" />
              Duplicate
            </DropdownMenuItem>
            <DropdownMenuItem
              onClick={handleDelete}
              disabled={isPending}
              className="text-red-600 dark:text-red-400"
            >
              <Trash2 className="mr-2 size-4" />
              {confirmDelete ? "Confirm Delete" : "Delete"}
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      {/* Tabs */}
      <Tabs defaultValue="configuration">
        <TabsList variant="line">
          <TabsTrigger value="configuration">Configuration</TabsTrigger>
          <TabsTrigger value="execution-log">Execution Log</TabsTrigger>
        </TabsList>

        <TabsContent value="configuration" className="mt-6">
          <RuleForm
            slug={slug}
            rule={rule}
            onSave={handleSave}
            onCancel={handleCancel}
            isSaving={isSaving}
          />
        </TabsContent>

        <TabsContent value="execution-log" className="mt-6">
          <p className="text-sm text-slate-500 dark:text-slate-400">
            Execution log will be available in a future update.
          </p>
        </TabsContent>
      </Tabs>
    </div>
  );
}
