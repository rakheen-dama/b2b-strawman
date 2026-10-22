import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { getClauses, getClauseCategories } from "@/lib/actions/clause-actions";
import { ClausesContent } from "@/components/clauses/clauses-content";
import type { Clause } from "@/lib/actions/clause-actions";
import type { SettingsTabProps } from "@/components/settings/settings-family-page";

/** Documents › Clauses tab (Phase 87 S9, ADR-341) — the former clauses settings page's loader. */
export async function renderClausesTab({ slug }: SettingsTabProps) {
  const caps = await fetchMyCapabilities();

  const canManage = caps.isAdmin || caps.isOwner;

  const [clausesResult, categoriesResult] = await Promise.allSettled([
    getClauses(true),
    getClauseCategories(),
  ]);

  const clauses: Clause[] = clausesResult.status === "fulfilled" ? clausesResult.value : [];
  const categories: string[] =
    categoriesResult.status === "fulfilled" ? categoriesResult.value : [];

  return (
    <div className="space-y-8">
      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Clause Library</h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Manage reusable clauses for document generation. System clauses can be cloned and
          customized.
        </p>
      </div>

      <ClausesContent slug={slug} clauses={clauses} categories={categories} canManage={canManage} />
    </div>
  );
}
