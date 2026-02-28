"use client";

import { useCallback, useEffect, useRef, useState, useTransition } from "react";
import { useParams, useRouter } from "next/navigation";
import {
  FolderOpen,
  Users,
  FileText,
  CheckSquare,
  Plus,
  Clock,
  Settings,
  Loader2,
} from "lucide-react";
import {
  CommandDialog,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
  CommandSeparator,
} from "@/components/ui/command";
import {
  searchAll,
  type CommandSearchResult,
} from "@/lib/actions/command-palette-search";

// ---- Recent items (localStorage) ----

interface RecentItem {
  id: string;
  label: string;
  type: CommandSearchResult["type"];
  href: string;
  visitedAt: number;
}

const RECENT_KEY = "command-palette-recent";
const MAX_RECENT = 5;

function getRecentItems(): RecentItem[] {
  if (typeof window === "undefined") return [];
  try {
    const raw = localStorage.getItem(RECENT_KEY);
    if (!raw) return [];
    return JSON.parse(raw) as RecentItem[];
  } catch {
    return [];
  }
}

function addRecentItem(item: Omit<RecentItem, "visitedAt">) {
  const items = getRecentItems().filter((r) => r.id !== item.id);
  items.unshift({ ...item, visitedAt: Date.now() });
  localStorage.setItem(RECENT_KEY, JSON.stringify(items.slice(0, MAX_RECENT)));
}

// ---- Icon map ----

const typeIcons: Record<CommandSearchResult["type"], React.ElementType> = {
  project: FolderOpen,
  customer: Users,
  invoice: FileText,
  task: CheckSquare,
};

const typeColors: Record<CommandSearchResult["type"], string> = {
  project: "text-teal-600",
  customer: "text-violet-600",
  invoice: "text-amber-600",
  task: "text-blue-600",
};

// ---- Component ----

export function CommandPalette() {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<CommandSearchResult[]>([]);
  const [recentItems, setRecentItems] = useState<RecentItem[]>([]);
  const [isPending, startTransition] = useTransition();

  const router = useRouter();
  const params = useParams();
  const slug = params?.slug as string | undefined;

  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Load recents on open
  useEffect(() => {
    if (open) {
      setRecentItems(getRecentItems());
      setQuery("");
      setResults([]);
    }
  }, [open]);

  // Listen for keyboard shortcut (Cmd+K / Ctrl+K)
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "k" && (e.metaKey || e.ctrlKey)) {
        e.preventDefault();
        setOpen((prev) => !prev);
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, []);

  // Listen for custom event from CommandPaletteButton
  useEffect(() => {
    const handleOpen = () => setOpen(true);
    window.addEventListener("command-palette:open", handleOpen);
    return () => window.removeEventListener("command-palette:open", handleOpen);
  }, []);

  // Debounced search
  const handleQueryChange = useCallback((value: string) => {
    setQuery(value);

    if (debounceRef.current) {
      clearTimeout(debounceRef.current);
    }

    if (!value || value.trim().length < 2) {
      setResults([]);
      return;
    }

    debounceRef.current = setTimeout(() => {
      startTransition(async () => {
        const data = await searchAll(value);
        setResults(data);
      });
    }, 300);
  }, []);

  // Build href for a result
  const hrefForResult = useCallback(
    (type: CommandSearchResult["type"], id: string) => {
      if (!slug) return "/";
      switch (type) {
        case "project":
          return `/org/${slug}/projects/${id}`;
        case "customer":
          return `/org/${slug}/customers/${id}`;
        case "invoice":
          return `/org/${slug}/invoices/${id}`;
        case "task":
          return `/org/${slug}/tasks/${id}`;
        default:
          return `/org/${slug}`;
      }
    },
    [slug]
  );

  // Navigate to a result
  const handleSelect = useCallback(
    (type: CommandSearchResult["type"], id: string, label: string) => {
      const href = hrefForResult(type, id);
      addRecentItem({ id, label, type, href });
      setOpen(false);
      router.push(href);
    },
    [hrefForResult, router]
  );

  // Navigate to a recent item
  const handleSelectRecent = useCallback(
    (item: RecentItem) => {
      addRecentItem({ id: item.id, label: item.label, type: item.type, href: item.href });
      setOpen(false);
      router.push(item.href);
    },
    [router]
  );

  // Quick action handlers
  const quickActions = [
    {
      label: "Create Project",
      icon: Plus,
      onSelect: () => {
        setOpen(false);
        if (slug) router.push(`/org/${slug}/projects?create=true`);
      },
    },
    {
      label: "Create Customer",
      icon: Plus,
      onSelect: () => {
        setOpen(false);
        if (slug) router.push(`/org/${slug}/customers?create=true`);
      },
    },
    {
      label: "Log Time",
      icon: Clock,
      onSelect: () => {
        setOpen(false);
        window.dispatchEvent(new CustomEvent("keyboard:create-time-entry"));
      },
    },
    {
      label: "Go to Settings",
      icon: Settings,
      onSelect: () => {
        setOpen(false);
        if (slug) router.push(`/org/${slug}/settings`);
      },
    },
  ];

  // Group results by type
  const grouped = results.reduce(
    (acc, r) => {
      if (!acc[r.type]) acc[r.type] = [];
      acc[r.type].push(r);
      return acc;
    },
    {} as Record<string, CommandSearchResult[]>
  );

  const groupLabels: Record<string, string> = {
    project: "Projects",
    customer: "Customers",
    invoice: "Invoices",
    task: "Tasks",
  };

  const hasResults = results.length > 0;
  const hasQuery = query.trim().length >= 2;

  return (
    <CommandDialog
      open={open}
      onOpenChange={setOpen}
      title="Command Palette"
      description="Search or jump to projects, customers, invoices, and tasks"
      showCloseButton={false}
    >
      <CommandInput
        placeholder="Search projects, customers, invoices, tasks..."
        value={query}
        onValueChange={handleQueryChange}
      />
      <CommandList>
        {/* Loading indicator */}
        {isPending && hasQuery && (
          <div className="flex items-center justify-center gap-2 py-4 text-sm text-slate-500">
            <Loader2 className="h-4 w-4 animate-spin" />
            Searching...
          </div>
        )}

        {/* No results */}
        {!isPending && hasQuery && !hasResults && (
          <CommandEmpty>No results found.</CommandEmpty>
        )}

        {/* Search results grouped by entity type */}
        {hasResults &&
          Object.entries(grouped).map(([type, items]) => {
            const Icon = typeIcons[type as CommandSearchResult["type"]];
            const colorClass = typeColors[type as CommandSearchResult["type"]];
            return (
              <CommandGroup key={type} heading={groupLabels[type] || type}>
                {items.map((item) => (
                  <CommandItem
                    key={item.id}
                    value={`${item.type}-${item.id}-${item.label}`}
                    onSelect={() => handleSelect(item.type, item.id, item.label)}
                  >
                    <Icon className={`h-4 w-4 ${colorClass}`} />
                    <span className="flex-1 truncate">{item.label}</span>
                    {item.subtitle && (
                      <span className="ml-2 text-xs text-slate-400">{item.subtitle}</span>
                    )}
                  </CommandItem>
                ))}
              </CommandGroup>
            );
          })}

        {/* Separator between results and other groups */}
        {hasResults && <CommandSeparator />}

        {/* Recent items (only when no query) */}
        {!hasQuery && recentItems.length > 0 && (
          <CommandGroup heading="Recent">
            {recentItems.map((item) => {
              const Icon = typeIcons[item.type];
              const colorClass = typeColors[item.type];
              return (
                <CommandItem
                  key={item.id}
                  value={`recent-${item.id}-${item.label}`}
                  onSelect={() => handleSelectRecent(item)}
                >
                  <Icon className={`h-4 w-4 ${colorClass}`} />
                  <span className="flex-1 truncate">{item.label}</span>
                  <span className="ml-2 text-xs capitalize text-slate-400">{item.type}</span>
                </CommandItem>
              );
            })}
          </CommandGroup>
        )}

        {/* Quick actions */}
        {!hasQuery && (
          <CommandGroup heading="Quick Actions">
            {quickActions.map((action) => (
              <CommandItem
                key={action.label}
                value={action.label}
                onSelect={action.onSelect}
              >
                <action.icon className="h-4 w-4 text-slate-500" />
                <span>{action.label}</span>
              </CommandItem>
            ))}
          </CommandGroup>
        )}
      </CommandList>
    </CommandDialog>
  );
}
