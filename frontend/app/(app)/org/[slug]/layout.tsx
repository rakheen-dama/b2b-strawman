import { auth } from "@clerk/nextjs/server";
import { redirect } from "next/navigation";
import { UserButton, OrganizationSwitcher } from "@clerk/nextjs";
import { DesktopSidebar } from "@/components/desktop-sidebar";
import { MobileSidebar } from "@/components/mobile-sidebar";

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
        <header className="flex h-14 items-center gap-4 border-b px-4 md:px-6">
          <MobileSidebar slug={slug} />
          <OrganizationSwitcher
            afterSelectOrganizationUrl="/org/:slug/dashboard"
            afterCreateOrganizationUrl="/org/:slug/dashboard"
            hidePersonal
          />
          <div className="ml-auto">
            <UserButton />
          </div>
        </header>
        <main className="flex-1 p-6">{children}</main>
      </div>
    </div>
  );
}
