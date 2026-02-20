"use client";

import { Check } from "lucide-react";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import { cn } from "@/lib/utils";
import type { ProjectTemplateResponse } from "@/lib/api/templates";

interface TemplatePickerProps {
  templates: ProjectTemplateResponse[];
  selectedId: string | null;
  onSelect: (id: string) => void;
}

export function TemplatePicker({ templates, selectedId, onSelect }: TemplatePickerProps) {
  return (
    <Command className="rounded-lg border border-slate-200 dark:border-slate-800">
      <CommandInput placeholder="Search templates..." />
      <CommandList>
        <CommandEmpty>No active templates found.</CommandEmpty>
        <CommandGroup>
          {templates.map((template) => (
            <CommandItem
              key={template.id}
              value={template.name}
              onSelect={() => onSelect(template.id)}
              className="gap-3 py-3 data-[selected=true]:bg-slate-100 dark:data-[selected=true]:bg-slate-800"
            >
              <Check
                className={cn(
                  "size-4 shrink-0 text-teal-600",
                  selectedId === template.id ? "opacity-100" : "opacity-0",
                )}
              />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-semibold text-slate-900 dark:text-slate-100">
                  {template.name}
                </p>
                <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">
                  {template.taskCount} {template.taskCount === 1 ? "task" : "tasks"}
                  {template.tags.length > 0 && (
                    <>
                      {" · "}
                      {template.tags
                        .slice(0, 3)
                        .map((t) => t.name)
                        .join(", ")}
                    </>
                  )}
                </p>
              </div>
            </CommandItem>
          ))}
        </CommandGroup>
      </CommandList>
    </Command>
  );
}
