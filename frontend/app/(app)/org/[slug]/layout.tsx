// CLERK-SPECIFIC: needs nullable orgId for redirect guard — not abstracted per ADR-085
import { auth } from "@clerk/nextjs/server";
import { redirect } from "next/navigation";
import { UserButton, OrganizationSwitcher } from "@clerk/nextjs";
import { DesktopSidebar } from "@/components/desktop-sidebar";
import { MobileSidebar } from "@/components/mobile-sidebar";
import { Breadcrumbs } from "@/components/breadcrumbs";
import { PlanBadge } from "@/components/billing/plan-badge";
import { NotificationBell } from "@/components/notifications/notification-bell";
import { PageTransition } from "@/components/page-transition";

export default async function OrgLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const { orgSlug, orgId } = await auth();

  if (!orgId) {
    redirect("/dashboard");
  }

  if (orgSlug && orgSlug !== slug) {
    redirect(`/org/${orgSlug}/dashboard`);
  }

  return (
    <div className="flex min-h-screen">
      <DesktopSidebar slug={slug} />
      <div className="flex flex-1 flex-col">
        <header className="sticky top-0 z-30 flex h-14 items-center gap-4 border-b border-slate-200/60 bg-slate-100/80 px-4 backdrop-blur-md md:px-6 dark:border-slate-800/60 dark:bg-slate-950/90">
          <MobileSidebar slug={slug} />
          <Breadcrumbs slug={slug} />
          <div className="ml-auto flex items-center gap-3">
            <OrganizationSwitcher
              afterSelectOrganizationUrl="/org/:slug/dashboard"
              afterCreateOrganizationUrl="/org/:slug/dashboard"
              hidePersonal
            />
            <PlanBadge />
            <NotificationBell orgSlug={slug} />
            <UserButton />
          </div>
        </header>
        <main className="flex-1 bg-background dark:bg-slate-950">
          <div className="mx-auto max-w-7xl px-6 py-6 lg:px-10">
            <PageTransition>{children}</PageTransition>
          </div>
        </main>
      </div>
    </div>
  );
}
