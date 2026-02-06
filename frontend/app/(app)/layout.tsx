import { UserButton } from "@clerk/nextjs";
import { LayoutDashboard, FolderOpen, Users } from "lucide-react";
import Link from "next/link";
import { Separator } from "@/components/ui/separator";

const navItems = [
  { label: "Dashboard", href: "/dashboard", icon: LayoutDashboard },
  { label: "Projects", href: "/dashboard", icon: FolderOpen },
  { label: "Team", href: "/dashboard", icon: Users },
];

export default function AppLayout({ children }: { children: React.ReactNode }) {
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
        <header className="flex h-14 items-center justify-end border-b px-6">
          <UserButton afterSignOutUrl="/" />
        </header>
        <main className="flex-1 p-6">{children}</main>
      </div>
    </div>
  );
}
