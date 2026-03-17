"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { RuleForm } from "@/components/automations/rule-form";
import { createRuleAction } from "./actions";
import type { ActionType, DelayUnit } from "@/lib/api/automations";

interface CreateRuleClientProps {
  slug: string;
}

export function CreateRuleClient({ slug }: CreateRuleClientProps) {
  const router = useRouter();
  const [isSaving, setIsSaving] = useState(false);

  async function handleSave(data: {
    name: string;
    description: string;
    triggerType: string;
    triggerConfig: Record<string, unknown>;
    conditions: Record<string, unknown>[];
    actions?: {
      actionType: string;
      actionConfig: Record<string, unknown>;
      sortOrder: number;
      delayDuration: number | null;
      delayUnit: string | null;
    }[];
  }) {
    setIsSaving(true);
    try {
      const result = await createRuleAction(slug, {
        name: data.name,
        description: data.description || undefined,
        triggerType: data.triggerType as Parameters<typeof createRuleAction>[1]["triggerType"],
        triggerConfig: data.triggerConfig,
        conditions: data.conditions.length > 0 ? data.conditions : undefined,
        actions: data.actions?.map((a) => ({
          actionType: a.actionType as ActionType,
          actionConfig: a.actionConfig,
          sortOrder: a.sortOrder,
          delayDuration: a.delayDuration,
          delayUnit: a.delayUnit as DelayUnit | null,
        })),
      });
      if (result.success) {
        toast.success("Rule created successfully");
        router.push(`/org/${slug}/settings/automations`);
      } else {
        toast.error(result.error ?? "Failed to create rule.");
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

  return (
    <RuleForm
      onSave={handleSave}
      onCancel={handleCancel}
      isSaving={isSaving}
    />
  );
}
