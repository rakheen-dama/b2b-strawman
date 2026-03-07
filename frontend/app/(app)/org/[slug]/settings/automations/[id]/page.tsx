import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { getAuthContext } from "@/lib/auth";
import { redirect } from "next/navigation";
import { getRule } from "@/lib/api/automations";
import type { AutomationRuleResponse } from "@/lib/api/automations";
import { RuleDetailClient } from "./rule-detail-client";

export default async function AutomationDetailPage({
  params,
}: {
  params: Promise<{ slug: string; id: string }>;
}) {
  const { slug, id } = await params;
  const { orgRole } = await getAuthContext();

  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  if (!isAdmin) {
    redirect(`/org/${slug}/settings/automations`);
  }

  let rule: AutomationRuleResponse | undefined;
  let notFound = false;

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

      <RuleDetailClient slug={slug} rule={rule} />
    </div>
  );
}
