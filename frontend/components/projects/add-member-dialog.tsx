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
  addProjectMember,
  fetchOrgMembers,
} from "@/app/(app)/org/[slug]/projects/[id]/member-actions";
import type { OrgMember, ProjectMember } from "@/lib/types";

function MemberAvatar({ name }: { name: string }) {
  const initial = name.charAt(0).toUpperCase();
  return (
    <div className="flex size-7 shrink-0 items-center justify-center rounded-full bg-slate-100 text-xs font-medium text-slate-700 dark:bg-slate-800 dark:text-slate-300">
      {initial}
    </div>
  );
}

interface AddMemberDialogProps {
  slug: string;
  projectId: string;
  existingMembers: ProjectMember[];
  children: React.ReactNode;
}

export function AddMemberDialog({
  slug,
  projectId,
  existingMembers,
  children,
}: AddMemberDialogProps) {
  const [open, setOpen] = useState(false);
  const [orgMembers, setOrgMembers] = useState<OrgMember[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isAdding, setIsAdding] = useState(false);
  const [fetchError, setFetchError] = useState<string | null>(null);
  const [addError, setAddError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;

    setIsLoading(true);
    setFetchError(null);

    fetchOrgMembers()
      .then(setOrgMembers)
      .catch(() => setFetchError("Failed to load organization members."))
      .finally(() => setIsLoading(false));
  }, [open]);

  const availableMembers = useMemo(() => {
    const existingIds = new Set(existingMembers.map((m) => m.memberId));
    return orgMembers.filter((m) => !existingIds.has(m.id));
  }, [orgMembers, existingMembers]);

  async function handleAddMember(memberId: string) {
    setAddError(null);
    setIsAdding(true);

    try {
      const result = await addProjectMember(slug, projectId, memberId);
      if (result.success) {
        setOpen(false);
      } else {
        setAddError(result.error ?? "Failed to add member.");
      }
    } catch {
      setAddError("An unexpected error occurred.");
    } finally {
      setIsAdding(false);
    }
  }

  function handleOpenChange(newOpen: boolean) {
    if (isAdding) return;
    if (newOpen) {
      setFetchError(null);
      setAddError(null);
      setOrgMembers([]);
    }
    setOpen(newOpen);
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent className="max-w-md p-0">
        <DialogHeader className="px-4 pt-4">
          <DialogTitle>Add Member</DialogTitle>
          <DialogDescription>
            Search and select an organization member to add to this project.
          </DialogDescription>
        </DialogHeader>

        <Command className="border-t">
          <CommandInput placeholder="Search members..." disabled={isLoading || isAdding} />
          <CommandList>
            {isLoading ? (
              <div className="text-muted-foreground py-6 text-center text-sm">
                Loading members...
              </div>
            ) : fetchError ? (
              <div className="text-destructive py-6 text-center text-sm">{fetchError}</div>
            ) : availableMembers.length === 0 ? (
              <CommandEmpty>
                {orgMembers.length === 0
                  ? "No organization members found."
                  : "All organization members are already on this project."}
              </CommandEmpty>
            ) : (
              <CommandGroup>
                {availableMembers.map((member) => (
                  <CommandItem
                    key={member.id}
                    value={`${member.name} ${member.email}`}
                    onSelect={() => handleAddMember(member.id)}
                    disabled={isAdding}
                    className="gap-3 py-3 data-[selected=true]:bg-slate-100 dark:data-[selected=true]:bg-slate-800"
                  >
                    <MemberAvatar name={member.name} />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-semibold">{member.name}</p>
                      <p className="truncate text-xs text-slate-600 dark:text-slate-400">{member.email}</p>
                    </div>
                  </CommandItem>
                ))}
              </CommandGroup>
            )}
          </CommandList>
        </Command>

        {addError && (
          <p className="text-destructive px-4 pb-4 text-sm">{addError}</p>
        )}
      </DialogContent>
    </Dialog>
  );
}
