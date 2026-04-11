"use client";

import { useState } from "react";
import useSWR from "swr";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { MoreHorizontal, Unlink } from "lucide-react";
import { toast } from "sonner";
import {
  fetchProjectAdverseParties,
  unlinkAdverseParty,
} from "@/app/(app)/org/[slug]/legal/adverse-parties/actions";
import { LinkAdversePartyToProjectDialog } from "@/components/legal/link-adverse-party-to-project-dialog";
import type { AdversePartyLink, AdversePartyRelationship } from "@/lib/types";

interface ProjectAdversePartiesTabProps {
  projectId: string;
  slug: string;
}

function relationshipBadge(relationship: AdversePartyRelationship) {
  switch (relationship) {
    case "OPPOSING_PARTY":
      return <Badge variant="destructive">Opposing Party</Badge>;
    case "WITNESS":
      return <Badge variant="neutral">Witness</Badge>;
    case "CO_ACCUSED":
      return <Badge variant="warning">Co-Accused</Badge>;
    case "RELATED_ENTITY":
      return (
        <Badge className="bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300">
          Related Entity
        </Badge>
      );
    case "GUARANTOR":
      return (
        <Badge className="bg-teal-100 text-teal-700 dark:bg-teal-900 dark:text-teal-300">
          Guarantor
        </Badge>
      );
    default:
      return <Badge variant="neutral">{relationship}</Badge>;
  }
}

export function ProjectAdversePartiesTab({ projectId, slug }: ProjectAdversePartiesTabProps) {
  const [unlinking, setUnlinking] = useState<string | null>(null);

  const { data, isLoading, mutate } = useSWR(
    `project-adverse-parties-${projectId}`,
    () => fetchProjectAdverseParties(projectId),
    { dedupingInterval: 2000 }
  );

  const parties = data ?? [];

  async function handleUnlink(linkId: string) {
    setUnlinking(linkId);
    try {
      const result = await unlinkAdverseParty(slug, linkId);
      if (result.success) {
        toast.success("Party unlinked from project");
        await mutate();
      } else {
        toast.error(result.error ?? "Failed to unlink party");
      }
    } catch {
      toast.error("Failed to unlink party");
    } finally {
      setUnlinking(null);
    }
  }

  if (isLoading) {
    return (
      <div data-testid="project-adverse-parties-tab">
        <p className="text-xs text-slate-500 italic">Loading adverse parties&hellip;</p>
      </div>
    );
  }

  if (parties.length === 0) {
    return (
      <div data-testid="project-adverse-parties-tab" className="space-y-4">
        <div className="flex justify-end">
          <LinkAdversePartyToProjectDialog
            slug={slug}
            projectId={projectId}
            onSuccess={() => mutate()}
          />
        </div>
        <div className="rounded-lg border border-slate-200 p-8 text-center dark:border-slate-800">
          <p className="text-sm text-slate-500 dark:text-slate-400">
            No adverse parties linked to this project.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div data-testid="project-adverse-parties-tab" className="space-y-4">
      <div className="flex justify-end">
        <LinkAdversePartyToProjectDialog
          slug={slug}
          projectId={projectId}
          onSuccess={() => mutate()}
        />
      </div>
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead>
            <tr className="border-b border-slate-200 dark:border-slate-800">
              <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                Party Name
              </th>
              <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                Relationship
              </th>
              <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                Description
              </th>
              <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                Linked
              </th>
              <th className="px-4 py-3 text-right text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                Actions
              </th>
            </tr>
          </thead>
          <tbody>
            {parties.map((link: AdversePartyLink) => (
              <tr
                key={link.id}
                className="border-b border-slate-100 last:border-0 dark:border-slate-800/50"
              >
                <td className="px-4 py-3 text-sm font-medium text-slate-950 dark:text-slate-50">
                  {link.adversePartyName}
                </td>
                <td className="px-4 py-3">{relationshipBadge(link.relationship)}</td>
                <td className="px-4 py-3 text-sm text-slate-600 dark:text-slate-400">
                  {link.description ?? "\u2014"}
                </td>
                <td className="px-4 py-3 font-mono text-sm text-slate-600 dark:text-slate-400">
                  {link.createdAt ? new Date(link.createdAt).toLocaleDateString() : "\u2014"}
                </td>
                <td className="px-4 py-3 text-right">
                  <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                      <Button variant="ghost" size="sm" className="size-8 p-0">
                        <MoreHorizontal className="size-4" />
                        <span className="sr-only">Actions</span>
                      </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                      <DropdownMenuItem
                        onClick={() => handleUnlink(link.id)}
                        disabled={unlinking === link.id}
                      >
                        <Unlink className="mr-2 size-4" />
                        {unlinking === link.id ? "Unlinking\u2026" : "Unlink"}
                      </DropdownMenuItem>
                    </DropdownMenuContent>
                  </DropdownMenu>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
