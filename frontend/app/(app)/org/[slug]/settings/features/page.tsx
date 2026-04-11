import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { getModuleSettings } from "@/lib/actions/module-settings";
import { FeaturesSettingsForm } from "@/components/settings/features-settings-form";

export default async function FeaturesSettingsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const [caps, modulesResponse] = await Promise.all([
    fetchMyCapabilities(),
    getModuleSettings(),
  ]);

  const canManage = caps.isAdmin || caps.isOwner;

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
          Features
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Enable additional features for your organization. These can be turned
          on or off at any time.
        </p>
        {!canManage && (
          <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">
            Only admins and owners can change these settings.
          </p>
        )}
      </div>

      <FeaturesSettingsForm
        initialModules={modulesResponse.modules}
        canManage={canManage}
      />
    </div>
  );
}
