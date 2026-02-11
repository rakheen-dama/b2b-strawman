import { auth } from "@clerk/nextjs/server";
import { redirect } from "next/navigation";
import { UserButton, OrganizationSwitcher } from "@clerk/nextjs";
import { DesktopSidebar } from "@/components/desktop-sidebar";
import { MobileSidebar } from "@/components/mobile-sidebar";
import { Breadcrumbs } from "@/components/breadcrumbs";
import { PlanBadge } from "@/components/billing/plan-badge";

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
        <header className="flex h-14 items-center gap-4 border-b border-olive-200 bg-white px-4 md:px-6 dark:border-olive-800 dark:bg-olive-950">
          <MobileSidebar slug={slug} />
          <Breadcrumbs slug={slug} />
          <div className="ml-auto flex items-center gap-3">
            <OrganizationSwitcher
              afterSelectOrganizationUrl="/org/:slug/dashboard"
              afterCreateOrganizationUrl="/org/:slug/dashboard"
              hidePersonal
            />
            <PlanBadge />
            <UserButton />
          </div>
        </header>
        <main className="flex-1 bg-olive-50 dark:bg-olive-950">
          <div className="mx-auto max-w-7xl px-6 py-6 lg:px-10">
            {children}
          </div>
        </main>
      </div>
    </div>
  );
}
