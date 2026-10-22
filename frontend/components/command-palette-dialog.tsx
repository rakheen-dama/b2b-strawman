"use client";

import { useRouter } from "next/navigation";
import { Settings, Clock } from "lucide-react";
import {
  CommandDialog,
  CommandInput,
  CommandList,
  CommandEmpty,
  CommandGroup,
  CommandItem,
} from "@/components/ui/command";
import { NAV_GROUPS, UTILITY_ITEMS } from "@/lib/nav-items";
import { isNavEntryVisible, type NavFilterAccess } from "@/lib/nav-filter";
import { SETTINGS_FAMILIES, settingsHref, visibleSettingsTabs } from "@/lib/settings-nav";
import { useCapabilities } from "@/lib/capabilities";
import { useOrgProfile } from "@/lib/org-profile";
import { useRecentItems } from "@/components/recent-items-provider";

interface CommandPaletteDialogProps {
  slug: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function CommandPaletteDialog({ slug, open, onOpenChange }: CommandPaletteDialogProps) {
  const router = useRouter();
  const { hasCapability, isAdmin, isOwner } = useCapabilities();
  const { isModuleEnabled } = useOrgProfile();
  const { items: recentItems } = useRecentItems();
  const access: NavFilterAccess = { hasCapability, isAdmin, isOwner, isModuleEnabled };

  // Same gates as the sidebar zones (lib/nav-filter.ts, shared with nav-zone.tsx — 612B.1, 616E).
  const pages = [...NAV_GROUPS.flatMap((g) => g.items), ...UTILITY_ITEMS].filter((item) =>
    isNavEntryVisible(item, access)
  );

  // One settings nav source (616E, ADR-341 §12.7.3): exactly the tabs the family pages and the
  // settings rail show this user, as "Settings › {Family} › {Tab}" → /settings/{family}?tab={tab}.
  // Keywords are the tab's own (old palette titles); descriptions stay out of the search index.
  const settings = SETTINGS_FAMILIES.flatMap((family) =>
    visibleSettingsTabs(family, access).map((tab) => ({
      key: `${family.id}/${tab.id}`,
      label: `Settings › ${family.label} › ${tab.label}`,
      href: settingsHref(slug, family.id, tab.id),
      keywords: tab.keywords ? [...tab.keywords] : undefined,
    }))
  );

  const navigate = (href: string) => {
    onOpenChange(false);
    router.push(href);
  };

  return (
    <CommandDialog
      open={open}
      onOpenChange={onOpenChange}
      title="Command Palette"
      description="Search pages and settings"
    >
      <CommandInput placeholder="Search pages, settings..." />
      <CommandList>
        <CommandEmpty>No results found.</CommandEmpty>
        {recentItems.length > 0 && (
          <CommandGroup heading="Recent">
            {recentItems.map((item) => (
              <CommandItem
                key={`recent:${item.href}`}
                value={`recent:${item.href}`}
                onSelect={() => navigate(item.href)}
              >
                <Clock className="h-4 w-4" />
                {item.label}
              </CommandItem>
            ))}
          </CommandGroup>
        )}
        <CommandGroup heading="Pages">
          {pages.map((item) => (
            <CommandItem
              key={item.label}
              onSelect={() => navigate(item.href(slug))}
              keywords={item.keywords}
            >
              <item.icon className="h-4 w-4" />
              {item.label}
            </CommandItem>
          ))}
        </CommandGroup>
        <CommandGroup heading="Settings">
          {settings.map((entry) => (
            <CommandItem
              key={entry.key}
              value={entry.label}
              keywords={entry.keywords}
              onSelect={() => navigate(entry.href)}
            >
              <Settings className="h-4 w-4" />
              {entry.label}
            </CommandItem>
          ))}
        </CommandGroup>
      </CommandList>
    </CommandDialog>
  );
}
