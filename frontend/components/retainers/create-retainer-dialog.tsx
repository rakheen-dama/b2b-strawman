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
import { createRetainerAction } from "@/app/(app)/org/[slug]/retainers/actions";
import { cn } from "@/lib/utils";
import type {
  RetainerType,
  RetainerFrequency,
  RolloverPolicy,
  CreateRetainerRequest,
} from "@/lib/api/retainers";

// Value constants — cannot import from "server-only" module
const FREQUENCY_OPTIONS: { value: RetainerFrequency; label: string }[] = [
  { value: "WEEKLY", label: "Weekly" },
  { value: "FORTNIGHTLY", label: "Fortnightly" },
  { value: "MONTHLY", label: "Monthly" },
  { value: "QUARTERLY", label: "Quarterly" },
  { value: "SEMI_ANNUALLY", label: "Semi-annually" },
  { value: "ANNUALLY", label: "Annually" },
];

const TYPE_OPTIONS: { value: RetainerType; label: string }[] = [
  { value: "HOUR_BANK", label: "Hour Bank" },
  { value: "FIXED_FEE", label: "Fixed Fee" },
];

const ROLLOVER_OPTIONS: { value: RolloverPolicy; label: string }[] = [
  { value: "FORFEIT", label: "Forfeit unused hours" },
  { value: "CARRY_FORWARD", label: "Carry forward (unlimited)" },
  { value: "CARRY_CAPPED", label: "Carry forward (capped)" },
];

const selectClasses =
  "flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-slate-500 dark:border-slate-800";

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
            <Label htmlFor="retainer-type">Type</Label>
            <select
              id="retainer-type"
              className={selectClasses}
              value={type}
              onChange={(e) => setType(e.target.value as RetainerType)}
            >
              {TYPE_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
          </div>

          {/* Frequency */}
          <div className="space-y-2">
            <Label htmlFor="retainer-frequency">Frequency</Label>
            <select
              id="retainer-frequency"
              className={selectClasses}
              value={frequency}
              onChange={(e) =>
                setFrequency(e.target.value as RetainerFrequency)
              }
            >
              {FREQUENCY_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
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
                <Label htmlFor="rollover-policy">Rollover Policy</Label>
                <select
                  id="rollover-policy"
                  className={selectClasses}
                  value={rolloverPolicy}
                  onChange={(e) =>
                    setRolloverPolicy(e.target.value as RolloverPolicy)
                  }
                >
                  {ROLLOVER_OPTIONS.map((opt) => (
                    <option key={opt.value} value={opt.value}>
                      {opt.label}
                    </option>
                  ))}
                </select>
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
            <textarea
              id="retainer-notes"
              className={cn(selectClasses, "min-h-[80px] py-2")}
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
