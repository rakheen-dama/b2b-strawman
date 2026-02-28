import { getAuthContext } from "@/lib/auth";
import { getClauses, getClauseCategories } from "@/lib/actions/clause-actions";
import { SettingsSidebar } from "@/components/shell/settings-sidebar";
import { ClauseList } from "@/components/templates/clause-list";
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
    <SettingsSidebar slug={slug}>
      <div className="space-y-6">
        <div>
          <h1 className="font-display text-2xl font-semibold tracking-tight text-slate-900">
            Clause Library
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            Manage reusable clauses for document generation. System clauses can
            be cloned and customized.
          </p>
        </div>

        <ClauseList
          slug={slug}
          clauses={clauses}
          categories={categories}
          canManage={canManage}
        />
      </div>
    </SettingsSidebar>
  );
}
