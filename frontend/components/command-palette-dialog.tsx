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
import { NAV_GROUPS, UTILITY_ITEMS, SETTINGS_ITEMS } from "@/lib/nav-items";
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
  const { hasCapability, isAdmin } = useCapabilities();
  const { isModuleEnabled } = useOrgProfile();
  const { items: recentItems } = useRecentItems();

  const pages = [
    ...NAV_GROUPS.flatMap((g) => g.items),
    ...UTILITY_ITEMS,
  ].filter((item) => !item.requiredCapability || hasCapability(item.requiredCapability));

  const settings = SETTINGS_ITEMS.filter(
    (item) =>
      !item.comingSoon &&
      (!item.adminOnly || isAdmin) &&
      (!item.requiredModule || isModuleEnabled(item.requiredModule)),
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
          {settings.map((item) => (
            <CommandItem
              key={item.title}
              onSelect={() => navigate(item.href(slug))}
            >
              <Settings className="h-4 w-4" />
              {item.title}
            </CommandItem>
          ))}
        </CommandGroup>
      </CommandList>
    </CommandDialog>
  );
}
