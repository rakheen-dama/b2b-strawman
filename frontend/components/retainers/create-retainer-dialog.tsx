"use client";

import { useState } from "react";
import { ChevronsUpDown, Check } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { createRetainerAction } from "@/app/(app)/org/[slug]/retainers/actions";
import { cn } from "@/lib/utils";
import type {
  RetainerType,
  RetainerFrequency,
  RolloverPolicy,
  CreateRetainerRequest,
} from "@/lib/api/retainers";
import {
  FREQUENCY_LABELS,
  TYPE_LABELS,
  ROLLOVER_LABELS,
} from "@/lib/retainer-constants";

interface CreateRetainerDialogProps {
  slug: string;
  customers: Array<{ id: string; name: string; email: string }>;
  children: React.ReactNode;
}

export function CreateRetainerDialog({
  slug,
  customers,
  children,
}: CreateRetainerDialogProps) {
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Form fields
  const [customerId, setCustomerId] = useState("");
  const [customerPopoverOpen, setCustomerPopoverOpen] = useState(false);
  const [name, setName] = useState("");
  const [type, setType] = useState<RetainerType>("HOUR_BANK");
  const [frequency, setFrequency] = useState<RetainerFrequency>("MONTHLY");
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [allocatedHours, setAllocatedHours] = useState("");
  const [periodFee, setPeriodFee] = useState("");
  const [rolloverPolicy, setRolloverPolicy] =
    useState<RolloverPolicy>("FORFEIT");
  const [rolloverCapHours, setRolloverCapHours] = useState("");
  const [notes, setNotes] = useState("");

  const selectedCustomer = customers.find((c) => c.id === customerId);

  const canSubmit =
    !isSubmitting &&
    customerId !== "" &&
    name.trim() !== "" &&
    startDate !== "" &&
    (type === "FIXED_FEE" ||
      (allocatedHours !== "" &&
        Number(allocatedHours) > 0 &&
        periodFee !== "" &&
        Number(periodFee) >= 0));

  function handleOpenChange(newOpen: boolean) {
    if (isSubmitting) return;
    if (newOpen) {
      setCustomerId("");
      setName("");
      setType("HOUR_BANK");
      setFrequency("MONTHLY");
      setStartDate("");
      setEndDate("");
      setAllocatedHours("");
      setPeriodFee("");
      setRolloverPolicy("FORFEIT");
      setRolloverCapHours("");
      setNotes("");
      setError(null);
    }
    setOpen(newOpen);
  }

  async function handleSubmit() {
    if (!canSubmit) return;
    setError(null);
    setIsSubmitting(true);

    try {
      const data: CreateRetainerRequest = {
        customerId,
        name: name.trim(),
        type,
        frequency,
        startDate,
      };

      if (endDate) data.endDate = endDate;
      if (notes.trim()) data.notes = notes.trim();

      if (type === "HOUR_BANK") {
        data.allocatedHours = Number(allocatedHours);
        data.periodFee = Number(periodFee);
        data.rolloverPolicy = rolloverPolicy;
        if (rolloverPolicy === "CARRY_CAPPED" && rolloverCapHours) {
          data.rolloverCapHours = Number(rolloverCapHours);
        }
      } else {
        // FIXED_FEE
        if (periodFee) data.periodFee = Number(periodFee);
      }

      const result = await createRetainerAction(slug, data);
      if (result.success) {
        setOpen(false);
      } else {
        setError(result.error ?? "Failed to create retainer.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>New Retainer</DialogTitle>
          <DialogDescription>
            Create a retainer agreement to track recurring client engagements.
          </DialogDescription>
        </DialogHeader>

        <div className="max-h-[60vh] space-y-4 overflow-y-auto py-2">
          {/* Customer Selector */}
          <div className="space-y-2">
            <Label>Customer</Label>
            <Popover
              open={customerPopoverOpen}
              onOpenChange={setCustomerPopoverOpen}
            >
              <PopoverTrigger asChild>
                <Button
                  variant="outline"
                  role="combobox"
                  aria-expanded={customerPopoverOpen}
                  className="w-full justify-between font-normal"
                >
                  {selectedCustomer
                    ? selectedCustomer.name
                    : "Select a customer..."}
                  <ChevronsUpDown className="ml-2 size-4 shrink-0 opacity-50" />
                </Button>
              </PopoverTrigger>
              <PopoverContent className="w-[--radix-popover-trigger-width] p-0">
                <Command>
                  <CommandInput placeholder="Search customers..." />
                  <CommandList>
                    <CommandEmpty>No customers found.</CommandEmpty>
                    <CommandGroup>
                      {customers.map((customer) => (
                        <CommandItem
                          key={customer.id}
                          value={`${customer.name} ${customer.email}`}
                          onSelect={() => {
                            setCustomerId(customer.id);
                            setCustomerPopoverOpen(false);
                          }}
                          className="gap-3 py-2"
                        >
                          <Check
                            className={cn(
                              "size-4 shrink-0",
                              customerId === customer.id
                                ? "opacity-100"
                                : "opacity-0",
                            )}
                          />
                          <div className="min-w-0 flex-1">
                            <p className="truncate text-sm font-medium">
                              {customer.name}
                            </p>
                            {customer.email && (
                              <p className="truncate text-xs text-slate-500">
                                {customer.email}
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

          {/* Name */}
          <div className="space-y-2">
            <Label htmlFor="retainer-name">Name</Label>
            <Input
              id="retainer-name"
              placeholder="e.g. Monthly Retainer - Acme Corp"
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </div>

          {/* Type */}
          <div className="space-y-2">
            <Label>Type</Label>
            <Select
              value={type}
              onValueChange={(v) => setType(v as RetainerType)}
            >
              <SelectTrigger className="w-full" aria-label="Type">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {Object.entries(TYPE_LABELS).map(([value, label]) => (
                  <SelectItem key={value} value={value}>
                    {label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {/* Frequency */}
          <div className="space-y-2">
            <Label>Frequency</Label>
            <Select
              value={frequency}
              onValueChange={(v) => setFrequency(v as RetainerFrequency)}
            >
              <SelectTrigger className="w-full" aria-label="Frequency">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {Object.entries(FREQUENCY_LABELS).map(([value, label]) => (
                  <SelectItem key={value} value={value}>
                    {label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {/* HOUR_BANK specific fields */}
          {type === "HOUR_BANK" && (
            <>
              <div className="space-y-2">
                <Label htmlFor="allocated-hours">Allocated Hours</Label>
                <Input
                  id="allocated-hours"
                  type="number"
                  min="0"
                  step="0.5"
                  placeholder="e.g. 40"
                  value={allocatedHours}
                  onChange={(e) => setAllocatedHours(e.target.value)}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="period-fee">Period Fee</Label>
                <Input
                  id="period-fee"
                  type="number"
                  min="0"
                  step="0.01"
                  placeholder="e.g. 5000.00"
                  value={periodFee}
                  onChange={(e) => setPeriodFee(e.target.value)}
                />
              </div>

              <div className="space-y-2">
                <Label>Rollover Policy</Label>
                <Select
                  value={rolloverPolicy}
                  onValueChange={(v) =>
                    setRolloverPolicy(v as RolloverPolicy)
                  }
                >
                  <SelectTrigger className="w-full" aria-label="Rollover Policy">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {Object.entries(ROLLOVER_LABELS).map(([value, label]) => (
                      <SelectItem key={value} value={value}>
                        {label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              {rolloverPolicy === "CARRY_CAPPED" && (
                <div className="space-y-2">
                  <Label htmlFor="rollover-cap">Rollover Cap (hours)</Label>
                  <Input
                    id="rollover-cap"
                    type="number"
                    min="0"
                    step="0.5"
                    placeholder="e.g. 20"
                    value={rolloverCapHours}
                    onChange={(e) => setRolloverCapHours(e.target.value)}
                  />
                </div>
              )}
            </>
          )}

          {/* FIXED_FEE period fee (optional) */}
          {type === "FIXED_FEE" && (
            <div className="space-y-2">
              <Label htmlFor="period-fee-fixed">
                Period Fee{" "}
                <span className="font-normal text-muted-foreground">
                  (optional)
                </span>
              </Label>
              <Input
                id="period-fee-fixed"
                type="number"
                min="0"
                step="0.01"
                placeholder="e.g. 5000.00"
                value={periodFee}
                onChange={(e) => setPeriodFee(e.target.value)}
              />
            </div>
          )}

          {/* Start Date */}
          <div className="space-y-2">
            <Label htmlFor="start-date">Start Date</Label>
            <Input
              id="start-date"
              type="date"
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
            />
          </div>

          {/* End Date (optional) */}
          <div className="space-y-2">
            <Label htmlFor="end-date">
              End Date{" "}
              <span className="font-normal text-muted-foreground">
                (optional)
              </span>
            </Label>
            <Input
              id="end-date"
              type="date"
              value={endDate}
              onChange={(e) => setEndDate(e.target.value)}
            />
          </div>

          {/* Notes (optional) */}
          <div className="space-y-2">
            <Label htmlFor="retainer-notes">
              Notes{" "}
              <span className="font-normal text-muted-foreground">
                (optional)
              </span>
            </Label>
            <Textarea
              id="retainer-notes"
              className="min-h-[80px]"
              placeholder="Additional notes..."
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
            />
          </div>

          {error && <p className="text-sm text-destructive">{error}</p>}
        </div>

        <DialogFooter>
          <Button
            type="button"
            variant="plain"
            onClick={() => setOpen(false)}
            disabled={isSubmitting}
          >
            Cancel
          </Button>
          <Button
            type="button"
            onClick={handleSubmit}
            disabled={!canSubmit}
          >
            {isSubmitting ? "Creating..." : "Create Retainer"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
