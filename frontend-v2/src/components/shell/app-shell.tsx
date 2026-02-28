import { IconRail } from "./icon-rail";
import { IconRailMobile } from "./icon-rail-mobile";
import { TopBar } from "./top-bar";
import { SubNav } from "./sub-nav";

interface AppShellProps {
  slug: string;
  children: React.ReactNode;
}

export function AppShell({ slug, children }: AppShellProps) {
  // Derive org initial from slug for the rail
  const orgInitial = slug.charAt(0).toUpperCase();

  return (
    <div className="flex min-h-screen">
      {/* Desktop icon rail */}
      <IconRail slug={slug} orgInitial={orgInitial} />

      {/* Main content area — offset by rail width on desktop */}
      <div className="flex flex-1 flex-col md:ml-[var(--rail-width)]">
        <TopBar slug={slug} />
        <SubNav slug={slug} />

        <main className="flex-1 bg-background dark:bg-slate-950">
          <div className="mx-auto max-w-[var(--content-max-width)] px-4 py-6 md:px-6 lg:px-8">
            {children}
          </div>
        </main>
      </div>

      {/* Mobile bottom tab bar */}
      <IconRailMobile slug={slug} />
    </div>
  );
}
