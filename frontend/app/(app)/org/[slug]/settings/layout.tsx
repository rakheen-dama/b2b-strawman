import { SettingsFamilyBar } from "@/components/settings/settings-family-bar";
import { SettingsSidebar } from "@/components/settings/settings-sidebar";

export default async function SettingsLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;

  // Family navigation: the rail from `md` up, the family link bar below it (K-02). Each wrapper
  // is `display: none` at the other widths, so exactly one "Settings navigation" landmark is in
  // the accessibility tree at any width.
  return (
    <div className="flex gap-6">
      <aside className="hidden w-48 shrink-0 md:block xl:w-60">
        <SettingsSidebar slug={slug} />
      </aside>
      <div className="min-w-0 flex-1">
        <div className="mb-4 md:hidden">
          <SettingsFamilyBar slug={slug} />
        </div>
        {children}
      </div>
    </div>
  );
}
