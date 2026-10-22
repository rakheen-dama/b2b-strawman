import Link from "next/link";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { listRules } from "@/lib/api/automations";
import { RuleList } from "@/components/automations/rule-list";
import type { AutomationRuleResponse } from "@/lib/api/automations";
import type { SettingsTabProps } from "@/components/settings/settings-family-page";

/**
 * Connections › Automations tab (Phase 87 S9, ADR-341) — the former automations settings page's
 * loader. The former page's notFound() guard became an in-panel denial: a tab loader that 404s
 * would 404 the whole family page (D21).
 */
export async function renderAutomationsTab({ slug }: SettingsTabProps) {
  const capData = await fetchMyCapabilities();

  if (!capData.isAdmin && !capData.isOwner && !capData.capabilities.includes("AUTOMATIONS")) {
    return (
      <div className="space-y-8">
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Automations</h1>
        <p className="text-sm text-slate-600 dark:text-slate-400">
          You do not have permission to view automation rules.
        </p>
      </div>
    );
  }

  const isAdmin = capData.isAdmin || capData.isOwner;

  let rules: AutomationRuleResponse[] = [];

  try {
    rules = await listRules();
  } catch {
    // Non-fatal: show empty state on API failure
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Automations</h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Enable or disable the automation rules installed with your practice packs.
        </p>
        {isAdmin && (
          <Link
            href={`/org/${slug}/settings/connections/automations/executions`}
            className="mt-2 inline-flex items-center gap-1 text-sm text-teal-600 underline-offset-4 hover:text-teal-700 hover:underline dark:text-teal-400 dark:hover:text-teal-300"
          >
            View Execution Log &rarr;
          </Link>
        )}
      </div>

      <RuleList slug={slug} rules={rules} canManage={isAdmin} />
    </div>
  );
}
