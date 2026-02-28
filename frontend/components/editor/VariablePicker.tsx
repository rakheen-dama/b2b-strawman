"use client";

import { useEffect, useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import {
  Command,
  CommandInput,
  CommandList,
  CommandEmpty,
  CommandGroup,
  CommandItem,
} from "@/components/ui/command";
import type { TemplateEntityType } from "@/lib/types";
import {
  fetchVariableMetadata,
  type VariableMetadataResponse,
} from "./actions";

interface VariablePickerProps {
  entityType: TemplateEntityType;
  onSelect: (key: string) => void;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function VariablePicker({
  entityType,
  onSelect,
  open,
  onOpenChange,
}: VariablePickerProps) {
  const [metadata, setMetadata] = useState<VariableMetadataResponse | null>(
    null,
  );

  useEffect(() => {
    if (open) {
      fetchVariableMetadata(entityType)
        .then(setMetadata)
        .catch(() => setMetadata(null));
    }
  }, [entityType, open]);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[500px] overflow-hidden p-0 sm:max-w-md">
        <DialogHeader className="px-4 pt-4">
          <DialogTitle>Insert Variable</DialogTitle>
          <DialogDescription>
            Select a variable to insert into the template.
          </DialogDescription>
        </DialogHeader>
        <Command>
          <CommandInput placeholder="Search variables..." />
          <CommandList>
            <CommandEmpty>No variables found.</CommandEmpty>
            {metadata?.groups.map((group) => (
              <CommandGroup key={group.prefix} heading={group.label}>
                {group.variables.map((variable) => (
                  <CommandItem
                    key={variable.key}
                    value={variable.key}
                    onSelect={() => {
                      onSelect(variable.key);
                      onOpenChange(false);
                    }}
                  >
                    <div className="flex flex-col">
                      <span className="font-mono text-xs text-teal-700 dark:text-teal-300">
                        {variable.key}
                      </span>
                      <span className="text-xs text-muted-foreground">
                        {variable.label}
                      </span>
                    </div>
                  </CommandItem>
                ))}
              </CommandGroup>
            ))}
          </CommandList>
        </Command>
      </DialogContent>
    </Dialog>
  );
}
