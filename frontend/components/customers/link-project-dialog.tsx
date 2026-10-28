"use client";

import { useMemo, useState } from "react";
import useSWR from "swr";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import { fetchProjects, linkProject } from "@/app/(app)/org/[slug]/clients/[id]/actions";
import type { Matter } from "@/lib/types";

interface LinkProjectDialogProps {
  slug: string;
  customerId: string;
  existingProjects: Matter[];
  children: React.ReactNode;
}

export function LinkProjectDialog({
  slug,
  customerId,
  existingProjects,
  children,
}: LinkProjectDialogProps) {
  const [open, setOpen] = useState(false);
  const [isLinking, setIsLinking] = useState(false);
  const [linkError, setLinkError] = useState<string | null>(null);

  const {
    data: allProjects,
    error: fetchError,
    isLoading,
  } = useSWR<Matter[]>(open ? "link-project-list" : null, () => fetchProjects());

  const availableProjects = useMemo(() => {
    if (!allProjects) return [];
    const linkedIds = new Set(existingProjects.map((p) => p.id));
    return allProjects.filter((p) => !linkedIds.has(p.id));
  }, [allProjects, existingProjects]);

  async function handleLinkProject(projectId: string) {
    setLinkError(null);
    setIsLinking(true);

    try {
      const result = await linkProject(slug, customerId, projectId);
      if (result.success) {
        setOpen(false);
      } else {
        setLinkError(result.error ?? "Failed to link matter.");
      }
    } catch {
      setLinkError("An unexpected error occurred.");
    } finally {
      setIsLinking(false);
    }
  }

  function handleOpenChange(newOpen: boolean) {
    if (isLinking) return;
    if (newOpen) {
      setLinkError(null);
    }
    setOpen(newOpen);
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent className="max-w-md p-0">
        <DialogHeader className="px-4 pt-4">
          <DialogTitle>Link Matter</DialogTitle>
          <DialogDescription>Search and select a matter to link to this client.</DialogDescription>
        </DialogHeader>

        <Command className="border-t">
          <CommandInput placeholder="Search matters..." disabled={isLoading || isLinking} />
          <CommandList>
            {isLoading ? (
              <div className="text-muted-foreground py-6 text-center text-sm">
                Loading matters...
              </div>
            ) : fetchError ? (
              <div className="text-destructive py-6 text-center text-sm">
                Failed to load matters.
              </div>
            ) : availableProjects.length === 0 ? (
              <CommandEmpty>
                {!allProjects || allProjects.length === 0
                  ? "No matters found."
                  : "All matters are already linked to this client."}
              </CommandEmpty>
            ) : (
              <CommandGroup>
                {availableProjects.map((project) => (
                  <CommandItem
                    key={project.id}
                    value={`${project.name} ${project.description ?? ""}`}
                    onSelect={() => handleLinkProject(project.id)}
                    disabled={isLinking}
                    className="gap-3 py-3 data-[selected=true]:bg-slate-100 dark:data-[selected=true]:bg-slate-800"
                  >
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-semibold">{project.name}</p>
                      {project.description && (
                        <p className="truncate text-xs text-slate-600 dark:text-slate-400">
                          {project.description}
                        </p>
                      )}
                    </div>
                  </CommandItem>
                ))}
              </CommandGroup>
            )}
          </CommandList>
        </Command>

        {linkError && <p className="text-destructive px-4 pb-4 text-sm">{linkError}</p>}
      </DialogContent>
    </Dialog>
  );
}
