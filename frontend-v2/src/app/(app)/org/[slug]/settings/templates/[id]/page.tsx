import { getAuthContext } from "@/lib/auth";
import { api } from "@/lib/api";
import { SettingsSidebar } from "@/components/shell/settings-sidebar";
import { TemplateEditor } from "@/components/templates/template-editor";
import type { TemplateDetailResponse } from "@/lib/types";
import { notFound } from "next/navigation";

export default async function TemplateDetailPage({
  params,
}: {
  params: Promise<{ slug: string; id: string }>;
}) {
  const { slug, id } = await params;
  const { orgRole } = await getAuthContext();
  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  let template: TemplateDetailResponse | null = null;
  try {
    template = await api.get<TemplateDetailResponse>(
      `/api/templates/${id}`,
    );
  } catch {
    notFound();
  }

  if (!template) notFound();

  return (
    <SettingsSidebar slug={slug}>
      <div className="space-y-6">
        <div>
          <h1 className="font-display text-2xl font-semibold tracking-tight text-slate-900">
            {template.name}
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            Edit template content and settings.
          </p>
        </div>

        <TemplateEditor
          slug={slug}
          template={template}
          canEdit={isAdmin}
        />
      </div>
    </SettingsSidebar>
  );
}
