import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { PageHeader } from "@b2mash/ui/page-header";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { settingsHref } from "@/lib/settings-nav";
import { redirect } from "next/navigation";
import { listExecutions, listRules } from "@/lib/api/automations";
import type {
  AutomationExecutionResponse,
  PaginatedResponse,
  ExecutionStatus,
} from "@/lib/api/automations";
import { ExecutionLog } from "@/components/automations/execution-log";

export default async function ExecutionLogPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<{ status?: string }>;
}) {
  const { slug } = await params;
  const resolvedSearchParams = await searchParams;

  const caps = await fetchMyCapabilities();
  const isAdmin = caps.isAdmin || caps.isOwner;

  if (!isAdmin) {
    redirect(settingsHref(slug, "connections", "automations"));
  }

  let executions: PaginatedResponse<AutomationExecutionResponse> = {
    content: [],
    page: { totalElements: 0, totalPages: 0, size: 20, number: 0 },
  };
  let rules: { id: string; name: string }[] = [];

  try {
    const statusParam = resolvedSearchParams.status as ExecutionStatus | undefined;
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
        href={settingsHref(slug, "connections", "automations")}
        className="text-muted-foreground hover:text-foreground inline-flex items-center gap-1 text-sm"
      >
        <ChevronLeft className="size-4" />
        Automations
      </Link>

      <PageHeader
        title="Execution Log"
        description="View the history of all automation rule executions."
      />

      <ExecutionLog initialExecutions={executions} rules={rules} />
    </div>
  );
}
