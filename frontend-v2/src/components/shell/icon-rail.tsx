"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { motion } from "motion/react";
import {
  Home,
  FolderKanban,
  Users,
  Receipt,
  FileText,
  BarChart3,
  Settings,
} from "lucide-react";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { NAV_ZONES, getActiveZone } from "@/lib/navigation";

/**
 * Map zone IDs to Lucide icon components.
 * We keep the mapping here (inside a "use client" component) to avoid
 * passing non-serializable LucideIcon references across the RSC boundary.
 */
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

interface IconRailProps {
  slug: string;
  orgInitial: string;
}

export function IconRail({ slug, orgInitial }: IconRailProps) {
  const pathname = usePathname();
  const activeZone = getActiveZone(pathname, slug);

  // Separate main zones from admin (settings at bottom)
  const mainZones = NAV_ZONES.filter((z) => z.id !== "admin");
  const adminZone = NAV_ZONES.find((z) => z.id === "admin");

  return (
    <TooltipProvider>
      <nav
        aria-label="Main navigation"
        className="hidden md:flex fixed left-0 top-0 z-40 h-screen w-[var(--rail-width)] flex-col items-center bg-slate-950 pt-3 pb-14"
      >
        {/* Org initial */}
        <div className="mb-4 flex h-10 w-10 items-center justify-center rounded-lg bg-white/10 text-sm font-semibold text-white">
          {orgInitial}
        </div>

        {/* Divider */}
        <div className="mb-2 h-px w-8 bg-white/10" />

        {/* Main zone icons */}
        <div className="flex flex-1 flex-col items-center gap-1">
          {mainZones.map((zone) => {
            const Icon = ZONE_ICONS[zone.id];
            const isActive = activeZone?.id === zone.id;
            // Link to first sub-nav item, or zone prefix
            const href =
              zone.subNav.length > 0
                ? zone.subNav[0].href(slug)
                : `/org/${slug}${zone.matchPrefixes[0]}`;

            return (
              <Tooltip key={zone.id}>
                <TooltipTrigger asChild>
                  <Link
                    href={href}
                    className="relative flex h-10 w-10 items-center justify-center rounded-lg text-slate-400 transition-colors hover:text-white"
                  >
                    {isActive && (
                      <motion.div
                        layoutId="rail-indicator"
                        className="absolute left-0 top-1/2 h-6 w-[3px] -translate-y-1/2 rounded-r-full bg-teal-500"
                        transition={{
                          type: "spring",
                          stiffness: 350,
                          damping: 30,
                        }}
                      />
                    )}
                    <span
                      className={`flex h-10 w-10 items-center justify-center rounded-lg transition-colors ${
                        isActive ? "bg-white/5 text-white" : ""
                      }`}
                    >
                      {Icon && <Icon className="h-5 w-5" />}
                    </span>
                  </Link>
                </TooltipTrigger>
                <TooltipContent side="right" sideOffset={8}>
                  {zone.label}
                </TooltipContent>
              </Tooltip>
            );
          })}
        </div>

        {/* Settings at bottom */}
        {adminZone && (
          <div className="mt-auto">
            <Tooltip>
              <TooltipTrigger asChild>
                <Link
                  href={`/org/${slug}/settings`}
                  className="relative flex h-10 w-10 items-center justify-center rounded-lg text-slate-400 transition-colors hover:text-white"
                >
                  {activeZone?.id === "admin" && (
                    <motion.div
                      layoutId="rail-indicator"
                      className="absolute left-0 top-1/2 h-6 w-[3px] -translate-y-1/2 rounded-r-full bg-teal-500"
                      transition={{
                        type: "spring",
                        stiffness: 350,
                        damping: 30,
                      }}
                    />
                  )}
                  <span
                    className={`flex h-10 w-10 items-center justify-center rounded-lg transition-colors ${
                      activeZone?.id === "admin" ? "bg-white/5 text-white" : ""
                    }`}
                  >
                    <Settings className="h-5 w-5" />
                  </span>
                </Link>
              </TooltipTrigger>
              <TooltipContent side="right" sideOffset={8}>
                {adminZone.label}
              </TooltipContent>
            </Tooltip>
          </div>
        )}
      </nav>
    </TooltipProvider>
  );
}
