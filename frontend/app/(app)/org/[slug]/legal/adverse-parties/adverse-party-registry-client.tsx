"use client";

import {
  useState,
  useTransition,
  useCallback,
  useEffect,
  useRef,
} from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { MoreHorizontal, Search, Link2, Trash2 } from "lucide-react";
import { cn } from "@/lib/utils";
import { AdversePartyDialog } from "@/components/legal/adverse-party-dialog";
import { LinkAdversePartyDialog } from "@/components/legal/link-adverse-party-dialog";
import {
  fetchAdverseParties,
  deleteAdverseParty,
  fetchAdverseParty,
} from "./actions";
import type { AdverseParty, AdversePartyType } from "@/lib/types";

function partyTypeBadge(partyType: AdversePartyType) {
  switch (partyType) {
    case "NATURAL_PERSON":
      return <Badge variant="neutral">Person</Badge>;
    case "COMPANY":
      return (
        <Badge className="bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300">
          Company
        </Badge>
      );
    case "TRUST":
      return (
        <Badge className="bg-purple-100 text-purple-700 dark:bg-purple-900 dark:text-purple-300">
          Trust
        </Badge>
      );
    case "CLOSE_CORPORATION":
      return (
        <Badge className="bg-teal-100 text-teal-700 dark:bg-teal-900 dark:text-teal-300">
          CC
        </Badge>
      );
    case "PARTNERSHIP":
      return (
        <Badge className="bg-amber-100 text-amber-700 dark:bg-amber-900 dark:text-amber-300">
          Partnership
        </Badge>
      );
    default:
      return <Badge variant="neutral">{partyType}</Badge>;
  }
}

interface AdversePartyRegistryClientProps {
  initialParties: AdverseParty[];
  initialTotal: number;
  slug: string;
}

export function AdversePartyRegistryClient({
  initialParties,
  initialTotal,
  slug,
}: AdversePartyRegistryClientProps) {
  const [parties, setParties] = useState(initialParties);
  const [total, setTotal] = useState(initialTotal);
  const [isPending, startTransition] = useTransition();

  // Search & filter
  const [search, setSearch] = useState("");
  const [typeFilter, setTypeFilter] = useState<AdversePartyType | "">("");

  // Link dialog
  const [linkTarget, setLinkTarget] = useState<AdverseParty | null>(null);

  // Delete state
  const [deleting, setDeleting] = useState<string | null>(null);

  // Link counts (fetched on mount for each party)
  const [linkCounts, setLinkCounts] = useState<Record<string, number>>({});

  const refetch = useCallback(() => {
    startTransition(async () => {
      try {
        const result = await fetchAdverseParties(
          search || undefined,
          typeFilter || undefined
        );
        setParties(result.content);
        setTotal(result.page.totalElements);

        // Fetch link counts for each party
        const counts: Record<string, number> = {};
        for (const party of result.content) {
          try {
            const detail = await fetchAdverseParty(party.id);
            counts[party.id] = detail.links?.length ?? 0;
          } catch {
            counts[party.id] = 0;
          }
        }
        setLinkCounts(counts);
      } catch (err) {
        console.error("Failed to refetch adverse parties:", err);
      }
    });
  }, [search, typeFilter]);

  // Debounced refetch on search/filter change
  const isInitialMount = useRef(true);
  const searchTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => {
    if (isInitialMount.current) {
      isInitialMount.current = false;
      // Fetch initial link counts
      refetch();
      return;
    }
    if (searchTimeoutRef.current) {
      clearTimeout(searchTimeoutRef.current);
    }
    searchTimeoutRef.current = setTimeout(() => {
      refetch();
    }, 300);
    return () => {
      if (searchTimeoutRef.current) clearTimeout(searchTimeoutRef.current);
    };
  }, [search, typeFilter, refetch]);

  async function handleDelete(party: AdverseParty) {
    if (linkCounts[party.id] && linkCounts[party.id] > 0) return;
    setDeleting(party.id);
    try {
      const result = await deleteAdverseParty(slug, party.id);
      if (result.success) {
        refetch();
      }
    } catch {
      // Error handled silently; refetch will show correct state
    } finally {
      setDeleting(null);
    }
  }

  return (
    <div data-testid="adverse-party-registry" className="space-y-4">
      {/* Search & filters */}
      <div className="flex flex-wrap items-center gap-3">
        <div className="relative flex-1 sm:max-w-xs">
          <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
          <Input
            placeholder="Search parties..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-9"
          />
        </div>

        <select
          value={typeFilter}
          onChange={(e) =>
            setTypeFilter(e.target.value as AdversePartyType | "")
          }
          className="flex h-9 rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm dark:border-slate-800"
        >
          <option value="">All Types</option>
          <option value="NATURAL_PERSON">Natural Person</option>
          <option value="COMPANY">Company</option>
          <option value="TRUST">Trust</option>
          <option value="CLOSE_CORPORATION">Close Corporation</option>
          <option value="PARTNERSHIP">Partnership</option>
          <option value="OTHER">Other</option>
        </select>

        <div className="ml-auto">
          <AdversePartyDialog slug={slug} onSuccess={refetch} />
        </div>
      </div>

      {/* Count */}
      <div className="text-sm text-slate-500 dark:text-slate-400">
        {total} part{total !== 1 ? "ies" : "y"}
      </div>

      {/* Table */}
      <div
        className={cn(
          "overflow-x-auto",
          isPending && "opacity-50 transition-opacity"
        )}
      >
        {parties.length === 0 ? (
          <div className="rounded-lg border border-slate-200 p-8 text-center dark:border-slate-800">
            <p className="text-sm text-slate-500 dark:text-slate-400">
              No adverse parties found.
            </p>
          </div>
        ) : (
          <table className="w-full" data-testid="adverse-party-table">
            <thead>
              <tr className="border-b border-slate-200 dark:border-slate-800">
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Name
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  ID Number
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Reg. Number
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Type
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Links
                </th>
                <th className="px-4 py-3 text-right text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody>
              {parties.map((party) => (
                <tr
                  key={party.id}
                  className="border-b border-slate-100 last:border-0 dark:border-slate-800/50"
                >
                  <td className="px-4 py-3 font-medium text-slate-900 dark:text-slate-100">
                    {party.name}
                  </td>
                  <td className="px-4 py-3 font-mono text-sm text-slate-600 dark:text-slate-400">
                    {party.idNumber ?? "-"}
                  </td>
                  <td className="px-4 py-3 font-mono text-sm text-slate-600 dark:text-slate-400">
                    {party.registrationNumber ?? "-"}
                  </td>
                  <td className="px-4 py-3">
                    {partyTypeBadge(party.partyType)}
                  </td>
                  <td className="px-4 py-3 text-sm text-slate-600 dark:text-slate-400">
                    {linkCounts[party.id] ?? 0}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button
                          variant="ghost"
                          size="sm"
                          className="size-8 p-0"
                        >
                          <MoreHorizontal className="size-4" />
                          <span className="sr-only">Actions</span>
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem
                          onClick={() => setLinkTarget(party)}
                        >
                          <Link2 className="mr-2 size-4" />
                          Link to Matter
                        </DropdownMenuItem>
                        <DropdownMenuItem
                          onClick={() => handleDelete(party)}
                          disabled={
                            deleting === party.id ||
                            (linkCounts[party.id] ?? 0) > 0
                          }
                          className="text-red-600 focus:text-red-600"
                        >
                          <Trash2 className="mr-2 size-4" />
                          Delete
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {linkTarget && (
        <LinkAdversePartyDialog
          open={!!linkTarget}
          onOpenChange={(open) => {
            if (!open) setLinkTarget(null);
          }}
          adversePartyId={linkTarget.id}
          adversePartyName={linkTarget.name}
          slug={slug}
          onSuccess={() => {
            setLinkTarget(null);
            refetch();
          }}
        />
      )}
    </div>
  );
}
