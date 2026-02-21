"use client";

import { useRef, useState } from "react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import { Check, ChevronsUpDown, Loader2, X } from "lucide-react";
import {
  fetchEntityOptionsAction,
  type EntityOption,
} from "@/app/(app)/org/[slug]/reports/[reportSlug]/actions";

interface EntityPickerProps {
  entityType: string;
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
  hasError?: boolean;
  id?: string;
}

export function EntityPicker({
  entityType,
  value,
  onChange,
  disabled,
  hasError,
  id,
}: EntityPickerProps) {
  const [open, setOpen] = useState(false);
  const [options, setOptions] = useState<EntityOption[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [fetchError, setFetchError] = useState<string | null>(null);
  const hasFetched = useRef(false);

  function handleOpenChange(nextOpen: boolean) {
    setOpen(nextOpen);
    if (nextOpen && !hasFetched.current) {
      hasFetched.current = true;
      setIsLoading(true);
      setFetchError(null);

      fetchEntityOptionsAction(entityType).then((result) => {
        if (result.data) {
          setOptions(result.data);
        } else {
          setFetchError(result.error ?? "Failed to load options.");
          hasFetched.current = false;
        }
        setIsLoading(false);
      });
    }
  }

  const selectedOption = options.find((o) => o.id === value);
  const entityLabel = entityType.charAt(0).toUpperCase() + entityType.slice(1).toLowerCase();

  return (
    <div className="flex items-center gap-1.5">
      <Popover open={open} onOpenChange={handleOpenChange}>
        <PopoverTrigger asChild>
          <Button
            id={id}
            variant="outline"
            role="combobox"
            aria-expanded={open}
            disabled={disabled}
            className={cn(
              "w-full justify-between font-normal",
              !value && "text-muted-foreground",
              hasError && "border-red-500",
            )}
          >
            {selectedOption
              ? selectedOption.label
              : `Select a ${entityLabel.toLowerCase()}...`}
            <ChevronsUpDown className="ml-auto size-4 shrink-0 opacity-50" />
          </Button>
        </PopoverTrigger>
        <PopoverContent className="w-[var(--radix-popover-trigger-width)] p-0" align="start">
          <Command>
            <CommandInput placeholder={`Search ${entityLabel.toLowerCase()}s...`} />
            <CommandList>
              {isLoading && (
                <div className="flex items-center justify-center py-6">
                  <Loader2 className="size-4 animate-spin text-muted-foreground" />
                </div>
              )}
              {fetchError && (
                <div className="px-3 py-6 text-center text-sm text-red-600">
                  {fetchError}
                </div>
              )}
              {!isLoading && !fetchError && (
                <>
                  <CommandEmpty>No {entityLabel.toLowerCase()}s found.</CommandEmpty>
                  <CommandGroup>
                    {options.map((option) => (
                      <CommandItem
                        key={option.id}
                        value={`${option.label} ${option.secondaryLabel ?? ""}`}
                        onSelect={() => {
                          onChange(option.id);
                          setOpen(false);
                        }}
                      >
                        <Check
                          className={cn(
                            "mr-2 size-4",
                            value === option.id ? "opacity-100" : "opacity-0",
                          )}
                        />
                        <div className="flex flex-col">
                          <span>{option.label}</span>
                          {option.secondaryLabel && (
                            <span className="text-xs text-muted-foreground">
                              {option.secondaryLabel}
                            </span>
                          )}
                        </div>
                      </CommandItem>
                    ))}
                  </CommandGroup>
                </>
              )}
            </CommandList>
          </Command>
        </PopoverContent>
      </Popover>

      {value && !disabled && (
        <Button
          type="button"
          variant="ghost"
          size="icon"
          className="size-8 shrink-0"
          onClick={() => onChange("")}
          aria-label="Clear selection"
        >
          <X className="size-4" />
        </Button>
      )}
    </div>
  );
}
