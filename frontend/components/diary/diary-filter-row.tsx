"use client";

import { useState } from "react";
import { Check, ChevronsUpDown } from "lucide-react";
import { Button } from "@b2mash/ui/button";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { cn } from "@/lib/utils";
import {
  DIARY_KINDS,
  DIARY_KIND_LABELS,
  type DiaryKind,
  type DiaryMatterOption,
  type DiaryMemberOption,
} from "@/app/(app)/org/[slug]/diary/diary-types";
import { DiaryKindIcon } from "./diary-event-row";

/** Fee-earner Select sentinels (Radix Select forbids an empty-string value). */
export const FEE_EARNER_ALL = "all";
export const FEE_EARNER_MINE = "mine";

interface DiaryFilterRowProps {
  /** Kinds the server served (X-Diary-Types) — chips render only for these. */
  typesServed: DiaryKind[];
  kinds: DiaryKind[];
  onKindsChange: (kinds: DiaryKind[]) => void;
  /** "all" | "mine" | member id */
  feeEarner: string;
  onFeeEarnerChange: (value: string) => void;
  members: DiaryMemberOption[];
  showMine: boolean;
  matterId: string | null;
  onMatterChange: (id: string | null) => void;
  matters: DiaryMatterOption[];
}

/** Kind chips, fee-earner Select and matter combobox for the Diary. */
export function DiaryFilterRow({
  typesServed,
  kinds,
  onKindsChange,
  feeEarner,
  onFeeEarnerChange,
  members,
  showMine,
  matterId,
  onMatterChange,
  matters,
}: DiaryFilterRowProps) {
  const [matterOpen, setMatterOpen] = useState(false);
  const chipKinds = DIARY_KINDS.filter((k) => typesServed.includes(k));
  const selectedMatter = matters.find((m) => m.id === matterId) ?? null;

  function toggleKind(kind: DiaryKind) {
    const next = kinds.includes(kind) ? kinds.filter((k) => k !== kind) : [...kinds, kind];
    // Keep DIARY_KINDS order so the URL and comparisons are stable.
    onKindsChange(DIARY_KINDS.filter((k) => next.includes(k)));
  }

  return (
    <div className="flex flex-wrap items-center gap-2" data-testid="diary-filter-row">
      {chipKinds.length > 0 && (
        <div role="group" aria-label="Event kinds" className="flex flex-wrap items-center gap-1.5">
          {chipKinds.map((kind) => {
            const active = kinds.includes(kind);
            return (
              <Button
                key={kind}
                type="button"
                size="sm"
                variant={active ? "soft" : "outline"}
                aria-pressed={active}
                onClick={() => toggleKind(kind)}
                className={cn(!active && "text-slate-600 dark:text-slate-400")}
              >
                <DiaryKindIcon kind={kind} />
                {DIARY_KIND_LABELS[kind]}
              </Button>
            );
          })}
        </div>
      )}

      <Select value={feeEarner} onValueChange={onFeeEarnerChange}>
        <SelectTrigger aria-label="Fee earner" className="w-[200px]" size="sm">
          <SelectValue placeholder="All fee earners" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value={FEE_EARNER_ALL}>All fee earners</SelectItem>
          {showMine && <SelectItem value={FEE_EARNER_MINE}>Mine</SelectItem>}
          {members.map((m) => (
            <SelectItem key={m.id} value={m.id}>
              {m.name}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>

      <Popover open={matterOpen} onOpenChange={setMatterOpen}>
        <PopoverTrigger asChild>
          <Button
            type="button"
            variant="plain"
            role="combobox"
            aria-label="Matter"
            aria-expanded={matterOpen}
            className="h-8 w-[240px] justify-between rounded-md border border-slate-200 bg-white px-3 font-normal dark:border-slate-800 dark:bg-slate-950"
          >
            <span className="truncate">{selectedMatter ? selectedMatter.name : "All matters"}</span>
            <ChevronsUpDown className="ml-2 size-4 shrink-0 opacity-50" aria-hidden="true" />
          </Button>
        </PopoverTrigger>
        <PopoverContent className="w-[320px] p-0" align="start">
          <Command>
            <CommandInput placeholder="Search matters…" />
            <CommandList>
              <CommandEmpty>No matters found.</CommandEmpty>
              <CommandGroup>
                <CommandItem
                  value="all matters"
                  onSelect={() => {
                    onMatterChange(null);
                    setMatterOpen(false);
                  }}
                >
                  <Check
                    className={cn("mr-2 size-4", matterId === null ? "opacity-100" : "opacity-0")}
                  />
                  All matters
                </CommandItem>
                {matters.map((m) => (
                  <CommandItem
                    key={m.id}
                    value={`${m.name} ${m.referenceNumber ?? ""} ${m.id}`}
                    onSelect={() => {
                      onMatterChange(m.id);
                      setMatterOpen(false);
                    }}
                  >
                    <Check
                      className={cn("mr-2 size-4", matterId === m.id ? "opacity-100" : "opacity-0")}
                    />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate">{m.name}</span>
                      {m.referenceNumber && (
                        <span className="text-muted-foreground block truncate text-xs">
                          {m.referenceNumber}
                        </span>
                      )}
                    </span>
                  </CommandItem>
                ))}
              </CommandGroup>
            </CommandList>
          </Command>
        </PopoverContent>
      </Popover>
    </div>
  );
}
