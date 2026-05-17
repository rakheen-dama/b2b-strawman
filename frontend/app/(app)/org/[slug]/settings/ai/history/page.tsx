import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { getAiExecutions } from "@/lib/api/ai";
import type { AiExecutionListItem } from "@/lib/api/ai";
import { ExecutionHistoryClient } from "./history-client";

export default async function ExecutionHistoryPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<{ skillId?: string; status?: string; page?: string }>;
}) {
  const { slug } = await params;
  const search = await searchParams;
  const caps = await fetchMyCapabilities();
  const isAdmin = caps.isAdmin || caps.isOwner;

  if (!isAdmin && !caps.capabilities.includes("AI_MANAGE")) {
    return (
      <div className="space-y-8">
        <Link
          href={`/org/${slug}/settings/ai`}
          className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ChevronLeft className="size-4" />
          AI Configuration
        </Link>
        <div>
          <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
            Execution History
          </h1>
          <p className="mt-3 text-sm text-slate-600 dark:text-slate-400">
            You do not have permission to view execution history. Contact your administrator.
          </p>
        </div>
      </div>
    );
  }

  const page = search.page ? parseInt(search.page, 10) : 0;
  let executions: AiExecutionListItem[] = [];
  let totalPages = 0;
  let totalElements = 0;

  try {
    const result = await getAiExecutions({
      skillId: search.skillId,
      status: search.status,
      page,
      size: 20,
    });
    executions = result.content;
    totalPages = result.page.totalPages;
    totalElements = result.page.totalElements;
  } catch {
    // Silently handle — show empty state
  }

  return (
    <div className="space-y-6">
      <Link
        href={`/org/${slug}/settings/ai`}
        className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
      >
        <ChevronLeft className="size-4" />
        AI Configuration
      </Link>

      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Execution History
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          View all AI skill executions, costs, and outcomes.
        </p>
      </div>

      <ExecutionHistoryClient
        slug={slug}
        executions={executions}
        currentPage={page}
        totalPages={totalPages}
        totalElements={totalElements}
        currentSkillId={search.skillId}
        currentStatus={search.status}
      />
    </div>
  );
}
