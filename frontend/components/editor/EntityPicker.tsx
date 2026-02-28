"use client";

import { useState, useCallback } from "react";
import { Check, ChevronsUpDown } from "lucide-react";
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
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { cn } from "@/lib/utils";
import {
  fetchProjectsForPicker,
  fetchCustomersForPicker,
  fetchInvoicesForPicker,
} from "@/app/(app)/org/[slug]/settings/templates/actions";
import type {
  TemplateEntityType,
  Project,
  Customer,
  InvoiceResponse,
} from "@/lib/types";

interface PickerItem {
  id: string;
  label: string;
  sublabel?: string;
  searchValue: string;
  data: Record<string, unknown>;
}

type PickerData = Project | Customer | InvoiceResponse;

function mapToPickerItems(
  entityType: TemplateEntityType,
  data: PickerData[],
): PickerItem[] {
  switch (entityType) {
    case "PROJECT":
      return (data as Project[]).map((p) => ({
        id: p.id,
        label: p.name,
        searchValue: p.name,
        data: p as unknown as Record<string, unknown>,
      }));
    case "CUSTOMER":
      return (data as Customer[]).map((c) => ({
        id: c.id,
        label: c.name,
        sublabel: c.email,
        searchValue: `${c.name} ${c.email ?? ""}`,
        data: c as unknown as Record<string, unknown>,
      }));
    case "INVOICE":
      return (data as InvoiceResponse[]).map((i) => ({
        id: i.id,
        label: i.invoiceNumber ?? `Draft (${i.id.slice(0, 8)}\u2026)`,
        sublabel: i.customerName,
        searchValue: `${i.invoiceNumber ?? ""} ${i.customerName ?? ""}`,
        data: i as unknown as Record<string, unknown>,
      }));
  }
}

interface EntityPickerProps {
  entityType: TemplateEntityType;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSelect: (entityId: string, entityData: Record<string, unknown>) => void;
}

/**
 * Reusable entity picker dialog. Displays a searchable list of entities
 * (projects, customers, or invoices) and calls onSelect when the user picks one.
 */
export function EntityPicker({
  entityType,
  open,
  onOpenChange,
  onSelect,
}: EntityPickerProps) {
  const [pickerOpen, setPickerOpen] = useState(false);
  const [entityId, setEntityId] = useState("");
  const [entities, setEntities] = useState<PickerItem[]>([]);
  const [entitiesLoading, setEntitiesLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const entityLabel =
    entityType === "PROJECT"
      ? "Project"
      : entityType === "CUSTOMER"
        ? "Customer"
        : "Invoice";

  const selectedItem = entities.find((e) => e.id === entityId);
  const selectedLabel =
    selectedItem?.label ?? `Select a ${entityLabel.toLowerCase()}`;

  const fetchEntities = useCallback(() => {
    setEntitiesLoading(true);
    setEntityId("");
    setError(null);
    setEntities([]);

    const fetcher =
      entityType === "PROJECT"
        ? fetchProjectsForPicker
        : entityType === "CUSTOMER"
          ? fetchCustomersForPicker
          : fetchInvoicesForPicker;

    fetcher()
      .then((data) => {
        setEntities(mapToPickerItems(entityType, data));
        setEntitiesLoading(false);
      })
      .catch(() => {
        setEntitiesLoading(false);
        setError(`Failed to load ${entityLabel.toLowerCase()}s.`);
      });
  }, [entityType, entityLabel]);

  function handleSelect() {
    if (!selectedItem) return;
    onSelect(selectedItem.id, selectedItem.data);
    onOpenChange(false);
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md" onOpenAutoFocus={(e: Event) => { e.preventDefault(); fetchEntities(); }}>
        <DialogHeader>
          <DialogTitle>Select a {entityLabel} for Preview</DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          <div className="space-y-1.5">
            <span className="text-sm font-medium">
              {entityLabel}
            </span>
            <Popover open={pickerOpen} onOpenChange={setPickerOpen}>
              <PopoverTrigger asChild>
                <Button
                  type="button"
                  variant="plain"
                  role="combobox"
                  aria-expanded={pickerOpen}
                  disabled={entitiesLoading}
                  className="w-full justify-between border border-slate-200 bg-white px-3 font-normal dark:border-slate-800 dark:bg-slate-950"
                >
                  {entitiesLoading ? "Loading..." : selectedLabel}
                  <ChevronsUpDown className="ml-2 size-4 shrink-0 opacity-50" />
                </Button>
              </PopoverTrigger>
              <PopoverContent className="w-[400px] p-0" align="start">
                <Command>
                  <CommandInput
                    placeholder={`Search ${entityLabel.toLowerCase()}s...`}
                  />
                  <CommandList>
                    <CommandEmpty>
                      No {entityLabel.toLowerCase()}s found.
                    </CommandEmpty>
                    <CommandGroup>
                      {entities.map((item) => (
                        <CommandItem
                          key={item.id}
                          value={item.searchValue}
                          onSelect={() => {
                            setEntityId(item.id);
                            setPickerOpen(false);
                          }}
                          className="data-[selected=true]:bg-slate-100 dark:data-[selected=true]:bg-slate-800"
                        >
                          <Check
                            className={cn(
                              "mr-2 size-4",
                              entityId === item.id
                                ? "opacity-100"
                                : "opacity-0",
                            )}
                          />
                          <div className="min-w-0 flex-1">
                            <span className="font-medium">{item.label}</span>
                            {item.sublabel && (
                              <p className="truncate text-xs text-slate-500">
                                {item.sublabel}
                              </p>
                            )}
                          </div>
                        </CommandItem>
                      ))}
                    </CommandGroup>
                  </CommandList>
                </Command>
              </PopoverContent>
            </Popover>
          </div>

          {error && <p className="text-sm text-destructive">{error}</p>}

          <div className="flex justify-end gap-3">
            <Button
              variant="plain"
              onClick={() => onOpenChange(false)}
            >
              Cancel
            </Button>
            <Button
              onClick={handleSelect}
              disabled={!entityId}
            >
              Select
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
