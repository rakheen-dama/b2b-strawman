import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api } from "@/lib/api";
import { ProjectNamingSettings } from "@/components/project-naming/project-naming-settings";
import { TerminologyText } from "@/components/terminology-text";
import type { OrgSettings } from "@/lib/types";

export default async function ProjectNamingPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const caps = await fetchMyCapabilities();

  if (!caps.isAdmin && !caps.isOwner) {
    return (
      <div className="space-y-8">
        <Link
          href={`/org/${slug}/settings`}
          className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ChevronLeft className="size-4" />
          Settings
        </Link>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          <TerminologyText template="{Project} Naming" />
        </h1>
        <p className="text-slate-600 dark:text-slate-400">
          <TerminologyText template="You do not have permission to manage {project} naming settings. Only admins and owners can access this page." />
        </p>
      </div>
    );
  }

  let settings: OrgSettings = { defaultCurrency: "USD" };

  const settingsResult = await api.get<OrgSettings>("/api/settings").catch(() => null);
  if (settingsResult) {
    settings = settingsResult;
  }

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
          <TerminologyText template="{Project} Naming" />
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          <TerminologyText template="Configure an auto-naming pattern for new {projects}." />
        </p>
      </div>

      <ProjectNamingSettings
        slug={slug}
        projectNamingPattern={settings.projectNamingPattern ?? ""}
      />
    </div>
  );
}
