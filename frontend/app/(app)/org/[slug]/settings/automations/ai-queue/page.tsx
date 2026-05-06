import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { ApiError } from "@/lib/api/client";
import {
  listInvocations,
  type InvocationFilter,
  type InvocationPage,
  type InvocationStatus,
} from "@/lib/api/ai-invocations";
import { AiQueueClient } from "./ai-queue-client";

const PAGE_SIZE = 25;
const VALID_STATUSES: ReadonlySet<string> = new Set([
  "RUNNING",
  "PENDING_APPROVAL",
  "APPROVED",
  "REJECTED",
  "AUTO_APPLIED",
  "FAILED",
  "EXPIRED",
]);

type SearchParams = {
  page?: string;
  status?: string;
  specialistId?: string;
  from?: string;
  to?: string;
  contextEntityType?: string;
  contextEntityId?: string;
  actorId?: string;
};

function parseFilter(sp: SearchParams): InvocationFilter {
  const requestedPage = Math.max(0, parseInt(sp.page ?? "0", 10) || 0);
  const status = sp.status && VALID_STATUSES.has(sp.status)
    ? (sp.status as InvocationStatus)
    : undefined;

  return {
    page: requestedPage,
    size: PAGE_SIZE,
    status,
    specialistId: sp.specialistId || undefined,
    from: sp.from || undefined,
    to: sp.to || undefined,
    contextEntityType: sp.contextEntityType || undefined,
    contextEntityId: sp.contextEntityId || undefined,
    actorId: sp.actorId || undefined,
  };
}

export default async function AiQueuePage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<SearchParams>;
}) {
  const { slug } = await params;
  const sp = await searchParams;
  const filter = parseFilter(sp);

  const backLink = (
    <Link
      href={`/org/${slug}/settings/automations`}
      className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
    >
      <ChevronLeft className="size-4" />
      Automations
    </Link>
  );

  let invocations: InvocationPage;
  try {
    invocations = await listInvocations(filter);
  } catch (error) {
    if (error instanceof ApiError && error.status === 403) {
      return (
        <div className="space-y-8">
          {backLink}
          <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
            AI Review Queue
          </h1>
          <p className="text-slate-600 dark:text-slate-400">
            Not authorised. The AI review queue is only visible to members with the{" "}
            <code className="font-mono text-xs">AI_ASSISTANT_USE</code> capability.
          </p>
        </div>
      );
    }
    throw error;
  }

  return (
    <div className="space-y-8">
      {backLink}
      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          AI Review Queue
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Review and approve AI specialist suggestions before they are applied.
        </p>
      </div>
      <AiQueueClient
        slug={slug}
        initialData={invocations}
        initialFilter={filter}
      />
    </div>
  );
}
