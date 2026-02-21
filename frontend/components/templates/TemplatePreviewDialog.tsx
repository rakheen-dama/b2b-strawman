"use client";

import { useState, useEffect } from "react";
import { Check, ChevronsUpDown, Eye } from "lucide-react";
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
  DialogTrigger,
} from "@/components/ui/dialog";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { cn } from "@/lib/utils";
import {
  previewTemplateAction,
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

interface TemplatePreviewDialogProps {
  templateId: string;
  entityType: TemplateEntityType;
}

interface PickerItem {
  id: string;
  label: string;
  sublabel?: string;
  searchValue: string;
}

type PickerData = Project | Customer | InvoiceResponse;

function mapToPickerItems(entityType: TemplateEntityType, data: PickerData[]): PickerItem[] {
  switch (entityType) {
    case "PROJECT":
      return (data as Project[]).map((p) => ({
        id: p.id,
        label: p.name,
        searchValue: p.name,
      }));
    case "CUSTOMER":
      return (data as Customer[]).map((c) => ({
        id: c.id,
        label: c.name,
        sublabel: c.email,
        searchValue: `${c.name} ${c.email ?? ""}`,
      }));
    case "INVOICE":
      return (data as InvoiceResponse[]).map((i) => ({
        id: i.id,
        label: i.invoiceNumber ?? `Draft (${i.id.slice(0, 8)}\u2026)`,
        sublabel: i.customerName,
        searchValue: `${i.invoiceNumber ?? ""} ${i.customerName ?? ""}`,
      }));
  }
}

export function TemplatePreviewDialog({
  templateId,
  entityType,
}: TemplatePreviewDialogProps) {
  const [open, setOpen] = useState(false);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [entityId, setEntityId] = useState("");
  const [entities, setEntities] = useState<PickerItem[]>([]);
  const [entitiesLoading, setEntitiesLoading] = useState(false);
  const [html, setHtml] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const entityLabel =
    entityType === "PROJECT"
      ? "Project"
      : entityType === "CUSTOMER"
        ? "Customer"
        : "Invoice";

  const selectedLabel =
    entities.find((e) => e.id === entityId)?.label ?? `Select a ${entityLabel.toLowerCase()}`;

  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    setEntitiesLoading(true);

    const fetcher =
      entityType === "PROJECT"
        ? fetchProjectsForPicker
        : entityType === "CUSTOMER"
          ? fetchCustomersForPicker
          : fetchInvoicesForPicker;

    fetcher()
      .then((data) => {
        if (!cancelled) {
          setEntities(mapToPickerItems(entityType, data));
          setEntitiesLoading(false);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setEntitiesLoading(false);
          setError(`Failed to load ${entityLabel.toLowerCase()}s.`);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [open, entityType, entityLabel]);

  function handleDialogChange(next: boolean) {
    setOpen(next);
    if (!next) {
      setEntityId("");
      setHtml(null);
      setError(null);
    }
  }

  async function handlePreview() {
    if (!entityId.trim()) return;
    setIsLoading(true);
    setError(null);
    setHtml(null);

    try {
      const result = await previewTemplateAction(templateId, entityId.trim());
      if (result.success && result.html) {
        setHtml(result.html);
      } else {
        setError(result.error ?? "Failed to generate preview.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleDialogChange}>
      <DialogTrigger asChild>
        <Button type="button" variant="soft" size="sm">
          <Eye className="mr-1 size-4" />
          Preview
        </Button>
      </DialogTrigger>
      <DialogContent className="max-w-3xl">
        <DialogHeader>
          <DialogTitle>Template Preview</DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          <div className="flex items-end gap-3">
            <div className="flex-1 space-y-1.5">
              <span className="text-sm font-medium">Select a {entityLabel}</span>
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
                    <CommandInput placeholder={`Search ${entityLabel.toLowerCase()}s...`} />
                    <CommandList>
                      <CommandEmpty>No {entityLabel.toLowerCase()}s found.</CommandEmpty>
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
                                entityId === item.id ? "opacity-100" : "opacity-0"
                              )}
                            />
                            <div className="min-w-0 flex-1">
                              <span className="font-medium">{item.label}</span>
                              {item.sublabel && (
                                <p className="truncate text-xs text-slate-500">{item.sublabel}</p>
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
            <Button onClick={handlePreview} disabled={isLoading || !entityId.trim()}>
              {isLoading ? "Generating..." : "Generate Preview"}
            </Button>
          </div>

          <p className="text-xs text-slate-500">
            Preview includes all related data (customer, members, org settings, etc.)
          </p>

          {error && <p className="text-sm text-destructive">{error}</p>}

          {html && (
            <div className="overflow-hidden rounded-lg border border-slate-200 dark:border-slate-800">
              <iframe
                sandbox=""
                srcDoc={html}
                className="h-[500px] w-full bg-white"
                title="Template Preview"
              />
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
