import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { redirect } from "next/navigation";
import { getRule, getRuleExecutions } from "@/lib/api/automations";
import type {
  AutomationRuleResponse,
  AutomationExecutionResponse,
  PaginatedResponse,
} from "@/lib/api/automations";
import { isModuleEnabledServer } from "@/lib/api/settings";
import { RuleDetailClient } from "./rule-detail-client";
import { ModuleDisabledFallback } from "@/components/module-disabled-fallback";

export default async function AutomationDetailPage({
  params,
}: {
  params: Promise<{ slug: string; id: string }>;
}) {
  const { slug, id } = await params;

  // Server-side module gate — short-circuit BEFORE invoking backend data fetches.
  if (!(await isModuleEnabledServer("automation_builder"))) {
    return <ModuleDisabledFallback moduleName="Automation Rule Builder" slug={slug} />;
  }

  const caps = await fetchMyCapabilities();
  const isAdmin = caps.isAdmin || caps.isOwner;

  if (!isAdmin) {
    redirect(`/org/${slug}/settings/automations`);
  }

  let rule: AutomationRuleResponse | undefined;
  let notFound = false;
  let executions: PaginatedResponse<AutomationExecutionResponse> = {
    content: [],
    page: { totalElements: 0, totalPages: 0, size: 20, number: 0 },
  };

  try {
    rule = await getRule(id);
  } catch (error: unknown) {
    const err = error as { status?: number };
    if (err?.status === 404) {
      notFound = true;
    } else {
      throw error;
    }
  }

  if (rule) {
    try {
      executions = await getRuleExecutions(id, { size: 20 });
    } catch {
      // Non-fatal: show empty executions
    }
  }

  if (notFound || !rule) {
    return (
      <div className="space-y-8">
        <Link
          href={`/org/${slug}/settings/automations`}
          className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ChevronLeft className="size-4" />
          Automations
        </Link>
        <p className="text-slate-600 dark:text-slate-400">
          Rule not found. It may have been deleted.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <Link
        href={`/org/${slug}/settings/automations`}
        className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
      >
        <ChevronLeft className="size-4" />
        Automations
      </Link>

      <RuleDetailClient slug={slug} rule={rule} initialExecutions={executions} />
    </div>
  );
}
