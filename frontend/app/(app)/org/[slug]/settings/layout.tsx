import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { SettingsSidebar } from "@/components/settings/settings-sidebar";

export default async function SettingsLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const caps = await fetchMyCapabilities();
  const isAdmin = caps.isAdmin || caps.isOwner;

  return (
    <div className="flex gap-6">
      <aside className="hidden w-60 shrink-0 md:block">
        <SettingsSidebar slug={slug} isAdmin={isAdmin} />
      </aside>
      <div className="min-w-0 flex-1">{children}</div>
    </div>
  );
}
