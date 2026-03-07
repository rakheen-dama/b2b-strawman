import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { getAuthContext } from "@/lib/auth";
import { listRules, listTemplates } from "@/lib/api/automations";
import { RuleList } from "@/components/automations/rule-list";
import type {
  AutomationRuleResponse,
  TemplateDefinitionResponse,
} from "@/lib/api/automations";

export default async function AutomationsSettingsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const { orgRole } = await getAuthContext();

  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  let rules: AutomationRuleResponse[] = [];
  let templates: TemplateDefinitionResponse[] = [];

  try {
    [rules, templates] = await Promise.all([listRules(), listTemplates()]);
  } catch {
    // Non-fatal: show empty state on API failure
  }

  return (
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
      </div>

      <RuleList
        slug={slug}
        rules={rules}
        templates={templates}
        canManage={isAdmin}
      />
    </div>
  );
}
