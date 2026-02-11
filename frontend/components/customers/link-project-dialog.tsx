"use client";

import { useEffect, useMemo, useState } from "react";
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
import {
  fetchProjects,
  linkProject,
} from "@/app/(app)/org/[slug]/customers/[id]/actions";
import type { Project } from "@/lib/types";

interface LinkProjectDialogProps {
  slug: string;
  customerId: string;
  existingProjects: Project[];
  children: React.ReactNode;
}

export function LinkProjectDialog({
  slug,
  customerId,
  existingProjects,
  children,
}: LinkProjectDialogProps) {
  const [open, setOpen] = useState(false);
  const [allProjects, setAllProjects] = useState<Project[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isLinking, setIsLinking] = useState(false);
  const [fetchError, setFetchError] = useState<string | null>(null);
  const [linkError, setLinkError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;

    setIsLoading(true);
    setFetchError(null);

    fetchProjects()
      .then(setAllProjects)
      .catch(() => setFetchError("Failed to load projects."))
      .finally(() => setIsLoading(false));
  }, [open]);

  const availableProjects = useMemo(() => {
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
        setLinkError(result.error ?? "Failed to link project.");
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
      setFetchError(null);
      setLinkError(null);
      setAllProjects([]);
    }
    setOpen(newOpen);
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent className="max-w-md p-0">
        <DialogHeader className="px-4 pt-4">
          <DialogTitle>Link Project</DialogTitle>
          <DialogDescription>
            Search and select a project to link to this customer.
          </DialogDescription>
        </DialogHeader>

        <Command className="border-t">
          <CommandInput placeholder="Search projects..." disabled={isLoading || isLinking} />
          <CommandList>
            {isLoading ? (
              <div className="py-6 text-center text-sm text-muted-foreground">
                Loading projects...
              </div>
            ) : fetchError ? (
              <div className="py-6 text-center text-sm text-destructive">{fetchError}</div>
            ) : availableProjects.length === 0 ? (
              <CommandEmpty>
                {allProjects.length === 0
                  ? "No projects found."
                  : "All projects are already linked to this customer."}
              </CommandEmpty>
            ) : (
              <CommandGroup>
                {availableProjects.map((project) => (
                  <CommandItem
                    key={project.id}
                    value={`${project.name} ${project.description ?? ""}`}
                    onSelect={() => handleLinkProject(project.id)}
                    disabled={isLinking}
                    className="gap-3 py-3 data-[selected=true]:bg-olive-100 dark:data-[selected=true]:bg-olive-800"
                  >
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-semibold">{project.name}</p>
                      {project.description && (
                        <p className="truncate text-xs text-olive-600 dark:text-olive-400">
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

        {linkError && (
          <p className="px-4 pb-4 text-sm text-destructive">{linkError}</p>
        )}
      </DialogContent>
    </Dialog>
  );
}
