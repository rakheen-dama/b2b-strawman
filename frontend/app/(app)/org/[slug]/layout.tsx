import { auth } from "@clerk/nextjs/server";
import { redirect } from "next/navigation";
import { UserButton, OrganizationSwitcher } from "@clerk/nextjs";
import { LayoutDashboard, FolderOpen, Users } from "lucide-react";
import Link from "next/link";
import { Separator } from "@/components/ui/separator";

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

  const navItems = [
    { label: "Dashboard", href: `/org/${slug}/dashboard`, icon: LayoutDashboard },
    { label: "Projects", href: `/org/${slug}/projects`, icon: FolderOpen },
    { label: "Team", href: `/org/${slug}/team`, icon: Users },
  ];

  return (
    <div className="flex min-h-screen">
      <aside className="flex w-60 flex-col border-r bg-neutral-50 dark:bg-neutral-950">
        <div className="flex h-14 items-center px-4 font-semibold">DocTeams</div>
        <Separator />
        <nav className="flex flex-1 flex-col gap-1 p-2">
          {navItems.map((item) => (
            <Link
              key={item.label}
              href={item.href}
              className="flex items-center gap-2 rounded-md px-3 py-2 text-sm text-neutral-700 transition-colors hover:bg-neutral-200 dark:text-neutral-300 dark:hover:bg-neutral-800"
            >
              <item.icon className="h-4 w-4" />
              {item.label}
            </Link>
          ))}
        </nav>
      </aside>
      <div className="flex flex-1 flex-col">
        <header className="flex h-14 items-center justify-between border-b px-6">
          <OrganizationSwitcher
            afterSelectOrganizationUrl="/org/:slug/dashboard"
            afterCreateOrganizationUrl="/org/:slug/dashboard"
            hidePersonal
          />
          <UserButton />
        </header>
        <main className="flex-1 p-6">{children}</main>
      </div>
    </div>
  );
}
