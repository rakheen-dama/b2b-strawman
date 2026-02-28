"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  Home,
  FolderKanban,
  Users,
  Receipt,
  FileText,
  BarChart3,
  Settings,
} from "lucide-react";
import { NAV_ZONES, getActiveZone } from "@/lib/navigation";

const ZONE_ICONS: Record<string, React.ComponentType<{ className?: string }>> =
  {
    home: Home,
    work: FolderKanban,
    clients: Users,
    money: Receipt,
    docs: FileText,
    reports: BarChart3,
    admin: Settings,
  };

interface IconRailMobileProps {
  slug: string;
}

export function IconRailMobile({ slug }: IconRailMobileProps) {
  const pathname = usePathname();
  const activeZone = getActiveZone(pathname, slug);

  return (
    <nav
      aria-label="Mobile navigation"
      className="fixed bottom-0 left-0 right-0 z-40 flex items-center justify-around border-t border-white/10 bg-slate-950 px-1 pb-[env(safe-area-inset-bottom)] md:hidden"
    >
      {NAV_ZONES.map((zone) => {
        const Icon = ZONE_ICONS[zone.id];
        const isActive = activeZone?.id === zone.id;
        const href =
          zone.subNav.length > 0
            ? zone.subNav[0].href(slug)
            : `/org/${slug}${zone.matchPrefixes[0]}`;

        return (
          <Link
            key={zone.id}
            href={href}
            className={`flex flex-col items-center gap-0.5 px-2 py-2 text-[10px] transition-colors ${
              isActive
                ? "text-teal-500"
                : "text-slate-400 hover:text-slate-200"
            }`}
          >
            {Icon && <Icon className="h-5 w-5" />}
            <span>{zone.label}</span>
          </Link>
        );
      })}
    </nav>
  );
}
