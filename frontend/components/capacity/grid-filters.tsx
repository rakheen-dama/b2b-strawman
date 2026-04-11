"use client";

import { useState, useEffect } from "react";
import { Search } from "lucide-react";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import { Check, ChevronsUpDown } from "lucide-react";
import { cn } from "@/lib/utils";
import type { TeamCapacityGrid } from "@/lib/api/capacity";

interface ProjectOption {
  id: string;
  name: string;
}

interface GridFiltersProps {
  grid: TeamCapacityGrid;
  projects: ProjectOption[];
  onFilteredGrid: (grid: TeamCapacityGrid) => void;
}

export function GridFilters({ grid, projects, onFilteredGrid }: GridFiltersProps) {
  const [searchTerm, setSearchTerm] = useState("");
  const [selectedProjectIds, setSelectedProjectIds] = useState<Set<string>>(new Set());
  const [showOverAllocatedOnly, setShowOverAllocatedOnly] = useState(false);
  const [projectFilterOpen, setProjectFilterOpen] = useState(false);

  useEffect(() => {
    let filtered = grid.members;

    // Filter by member name
    if (searchTerm.trim()) {
      const term = searchTerm.toLowerCase().trim();
      filtered = filtered.filter((m) => m.memberName.toLowerCase().includes(term));
    }

    // Filter by project
    if (selectedProjectIds.size > 0) {
      filtered = filtered.filter((m) =>
        m.weeks.some((w) => w.allocations.some((a) => selectedProjectIds.has(a.projectId)))
      );
    }

    // Filter by over-allocated
    if (showOverAllocatedOnly) {
      filtered = filtered.filter((m) => m.weeks.some((w) => w.overAllocated));
    }

    onFilteredGrid({ ...grid, members: filtered });
  }, [searchTerm, selectedProjectIds, showOverAllocatedOnly, grid, onFilteredGrid]);

  function toggleProject(projectId: string) {
    setSelectedProjectIds((prev) => {
      const next = new Set(prev);
      if (next.has(projectId)) {
        next.delete(projectId);
      } else {
        next.add(projectId);
      }
      return next;
    });
  }

  return (
    <div className="flex flex-wrap items-center gap-3">
      {/* Member search */}
      <div className="relative">
        <Search className="absolute top-1/2 left-2.5 h-4 w-4 -translate-y-1/2 text-slate-400" />
        <Input
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          placeholder="Search members..."
          className="h-9 w-56 pl-9 text-sm"
          aria-label="Search members"
        />
      </div>

      {/* Project filter */}
      <Popover open={projectFilterOpen} onOpenChange={setProjectFilterOpen}>
        <PopoverTrigger asChild>
          <Button
            type="button"
            variant="outline"
            role="combobox"
            aria-expanded={projectFilterOpen}
            className="h-9 w-52 justify-between text-sm font-normal"
          >
            {selectedProjectIds.size > 0
              ? `${selectedProjectIds.size} project${selectedProjectIds.size > 1 ? "s" : ""} selected`
              : "Filter by project"}
            <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
          </Button>
        </PopoverTrigger>
        <PopoverContent className="w-52 p-0" align="start">
          <Command>
            <CommandInput placeholder="Search projects..." />
            <CommandList>
              <CommandEmpty>No projects found.</CommandEmpty>
              <CommandGroup>
                {projects.map((p) => (
                  <CommandItem key={p.id} value={p.name} onSelect={() => toggleProject(p.id)}>
                    <Check
                      className={cn(
                        "mr-2 h-4 w-4",
                        selectedProjectIds.has(p.id) ? "opacity-100" : "opacity-0"
                      )}
                    />
                    {p.name}
                  </CommandItem>
                ))}
              </CommandGroup>
            </CommandList>
          </Command>
        </PopoverContent>
      </Popover>

      {/* Over-allocated toggle */}
      <div className="flex items-center gap-2">
        <Switch
          id="over-allocated-filter"
          checked={showOverAllocatedOnly}
          onCheckedChange={setShowOverAllocatedOnly}
          size="sm"
        />
        <Label
          htmlFor="over-allocated-filter"
          className="cursor-pointer text-sm text-slate-600 dark:text-slate-400"
        >
          Over-allocated only
        </Label>
      </div>
    </div>
  );
}
