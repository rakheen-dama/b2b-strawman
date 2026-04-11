import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { redirect } from "next/navigation";
import { listExecutions, listRules } from "@/lib/api/automations";
import type {
  AutomationExecutionResponse,
  PaginatedResponse,
  ExecutionStatus,
} from "@/lib/api/automations";
import { isModuleEnabledServer } from "@/lib/api/settings";
import { ExecutionLog } from "@/components/automations/execution-log";
import { ModuleDisabledFallback } from "@/components/module-disabled-fallback";

export default async function ExecutionLogPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<{ status?: string }>;
}) {
  const { slug } = await params;
  const resolvedSearchParams = await searchParams;

  // Server-side module gate — short-circuit BEFORE invoking backend data fetches.
  if (!(await isModuleEnabledServer("automation_builder"))) {
    return (
      <ModuleDisabledFallback
        moduleName="Automation Rule Builder"
        slug={slug}
      />
    );
  }

  const caps = await fetchMyCapabilities();
  const isAdmin = caps.isAdmin || caps.isOwner;

  if (!isAdmin) {
    redirect(`/org/${slug}/settings/automations`);
  }

  let executions: PaginatedResponse<AutomationExecutionResponse> = {
    content: [],
    page: { totalElements: 0, totalPages: 0, size: 20, number: 0 },
  };
  let rules: { id: string; name: string }[] = [];

  try {
    const statusParam = resolvedSearchParams.status as
      | ExecutionStatus
      | undefined;
    const [executionsResult, rulesResult] = await Promise.all([
      listExecutions({
        status: statusParam,
        size: 20,
      }),
      listRules(),
    ]);
    executions = executionsResult;
    rules = rulesResult.map((r) => ({ id: r.id, name: r.name }));
  } catch {
    // Non-fatal: show empty state on API failure
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

      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Execution Log
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          View the history of all automation rule executions.
        </p>
      </div>

      <ExecutionLog initialExecutions={executions} rules={rules} />
    </div>
  );
}
