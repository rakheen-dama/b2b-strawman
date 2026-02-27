import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { getAuthContext } from "@/lib/auth";
import { getClauses, getClauseCategories } from "@/lib/actions/clause-actions";
import { ClausesContent } from "@/components/clauses/clauses-content";
import type { Clause } from "@/lib/actions/clause-actions";

export default async function ClausesSettingsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const { orgRole } = await getAuthContext();

  const canManage = orgRole === "org:admin" || orgRole === "org:owner";

  const [clausesResult, categoriesResult] = await Promise.allSettled([
    getClauses(true),
    getClauseCategories(),
  ]);

  const clauses: Clause[] =
    clausesResult.status === "fulfilled" ? clausesResult.value : [];
  const categories: string[] =
    categoriesResult.status === "fulfilled" ? categoriesResult.value : [];

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
          Clause Library
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Manage reusable clauses for document generation. System clauses can be
          cloned and customized.
        </p>
      </div>

      <ClausesContent
        slug={slug}
        clauses={clauses}
        categories={categories}
        canManage={canManage}
      />
    </div>
  );
}
