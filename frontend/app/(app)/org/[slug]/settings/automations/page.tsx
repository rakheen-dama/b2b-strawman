import Link from "next/link";
import { notFound } from "next/navigation";
import { ChevronLeft } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { listRules, listTemplates } from "@/lib/api/automations";
import { RuleList } from "@/components/automations/rule-list";
import type {
  AutomationRuleResponse,
  TemplateDefinitionResponse,
} from "@/lib/api/automations";
import { ModuleGate } from "@/components/module-gate";
import { ModuleDisabledFallback } from "@/components/module-disabled-fallback";

export default async function AutomationsSettingsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const capData = await fetchMyCapabilities();

  if (!capData.isAdmin && !capData.isOwner && !capData.capabilities.includes("AUTOMATIONS")) {
    notFound();
  }

  const isAdmin = capData.isAdmin || capData.isOwner;

  let rules: AutomationRuleResponse[] = [];
  let templates: TemplateDefinitionResponse[] = [];

  try {
    [rules, templates] = await Promise.all([listRules(), listTemplates()]);
  } catch {
    // Non-fatal: show empty state on API failure
  }

  return (
    <ModuleGate
      module="automation_builder"
      fallback={
        <ModuleDisabledFallback
          moduleName="Automation Rule Builder"
          slug={slug}
        />
      }
    >
      <div className="space-y-8">
        <Link
          href={`/org/${slug}/settings`}
          className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ChevronLeft className="size-4" />
          Settings
        </Link>

        <div>
          <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
            Automations
          </h1>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Create rules to automate tasks, notifications, and workflows.
          </p>
          {isAdmin && (
            <Link
              href={`/org/${slug}/settings/automations/executions`}
              className="mt-2 inline-flex items-center gap-1 text-sm text-teal-600 underline-offset-4 hover:text-teal-700 hover:underline dark:text-teal-400 dark:hover:text-teal-300"
            >
              View Execution Log &rarr;
            </Link>
          )}
        </div>

        <RuleList
          slug={slug}
          rules={rules}
          templates={templates}
          canManage={isAdmin}
        />
      </div>
    </ModuleGate>
  );
}
