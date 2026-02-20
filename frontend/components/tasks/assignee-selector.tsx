"use client";

import { useState } from "react";
import { Check, ChevronsUpDown } from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";

interface Member {
  id: string;
  name: string;
  email: string;
}

interface AssigneeSelectorProps {
  members: Member[];
  currentAssigneeId: string | null;
  onAssigneeChange: (id: string | null) => void;
  disabled?: boolean;
}

export function AssigneeSelector({
  members,
  currentAssigneeId,
  onAssigneeChange,
  disabled = false,
}: AssigneeSelectorProps) {
  const [open, setOpen] = useState(false);

  const selectedMember = members.find((m) => m.id === currentAssigneeId) ?? null;

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          type="button"
          variant="plain"
          role="combobox"
          aria-expanded={open}
          disabled={disabled}
          className="w-full justify-between border border-slate-200 bg-white px-3 font-normal dark:border-slate-800 dark:bg-slate-950"
        >
          {selectedMember ? selectedMember.name : "Unassigned"}
          <ChevronsUpDown className="ml-2 size-4 shrink-0 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-[280px] p-0" align="start">
        <Command>
          <CommandInput placeholder="Search members..." />
          <CommandList>
            <CommandEmpty>No members found.</CommandEmpty>
            <CommandGroup>
              <CommandItem
                value="unassigned"
                onSelect={() => {
                  onAssigneeChange(null);
                  setOpen(false);
                }}
              >
                <Check
                  className={cn(
                    "mr-2 size-4",
                    currentAssigneeId === null ? "opacity-100" : "opacity-0",
                  )}
                />
                Unassigned
              </CommandItem>
              {members.map((member) => (
                <CommandItem
                  key={member.id}
                  value={`${member.name} ${member.email}`}
                  onSelect={() => {
                    onAssigneeChange(member.id);
                    setOpen(false);
                  }}
                  className="data-[selected=true]:bg-slate-100 dark:data-[selected=true]:bg-slate-800"
                >
                  <Check
                    className={cn(
                      "mr-2 size-4",
                      currentAssigneeId === member.id ? "opacity-100" : "opacity-0",
                    )}
                  />
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-semibold">{member.name}</p>
                    <p className="truncate text-xs text-slate-600 dark:text-slate-400">
                      {member.email}
                    </p>
                  </div>
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}
