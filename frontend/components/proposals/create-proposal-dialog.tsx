"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { ChevronsUpDown, Check } from "lucide-react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
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
import {
  Form,
  FormField,
  FormItem,
  FormLabel,
  FormControl,
  FormMessage,
} from "@/components/ui/form";
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
import { createProposalAction } from "@/app/(app)/org/[slug]/proposals/actions";
import { createProposalSchema } from "@/lib/schemas/proposal";
import type { CreateProposalFormData } from "@/lib/schemas/proposal";
import type { FeeModel } from "@/lib/types/proposal";
import { cn } from "@/lib/utils";

const FEE_MODEL_LABELS: Record<FeeModel, string> = {
  FIXED: "Fixed Fee",
  HOURLY: "Hourly",
  RETAINER: "Retainer",
};

interface CreateProposalDialogProps {
  slug: string;
  customers: Array<{ id: string; name: string; email: string }>;
  children: React.ReactNode;
}

export function CreateProposalDialog({
  slug,
  customers,
  children,
}: CreateProposalDialogProps) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [customerPopoverOpen, setCustomerPopoverOpen] = useState(false);

  const form = useForm<CreateProposalFormData>({
    resolver: zodResolver(createProposalSchema),
    defaultValues: {
      title: "",
      customerId: "",
      feeModel: "RETAINER",
      fixedFeeAmount: undefined,
      fixedFeeCurrency: "ZAR",
      hourlyRateNote: "",
      retainerAmount: undefined,
      retainerCurrency: "ZAR",
      retainerHoursIncluded: undefined,
      expiresAt: "",
    },
  });

  const feeModel = form.watch("feeModel");
  const customerId = form.watch("customerId");
  const selectedCustomer = customers.find((c) => c.id === customerId);

  function handleOpenChange(newOpen: boolean) {
    if (isSubmitting) return;
    if (newOpen) {
      form.reset();
      setError(null);
    }
    setOpen(newOpen);
  }

  async function onSubmit(values: CreateProposalFormData) {
    setError(null);
    setIsSubmitting(true);

    try {
      const result = await createProposalAction(slug, {
        title: values.title,
        customerId: values.customerId,
        feeModel: values.feeModel,
        ...(values.feeModel === "FIXED" && {
          fixedFeeAmount: values.fixedFeeAmount,
          fixedFeeCurrency: values.fixedFeeCurrency || "ZAR",
        }),
        ...(values.feeModel === "HOURLY" &&
          values.hourlyRateNote && {
            hourlyRateNote: values.hourlyRateNote,
          }),
        ...(values.feeModel === "RETAINER" && {
          retainerAmount: values.retainerAmount,
          retainerCurrency: values.retainerCurrency || "ZAR",
          ...(values.retainerHoursIncluded != null && {
            retainerHoursIncluded: values.retainerHoursIncluded,
          }),
        }),
        ...(values.expiresAt && {
          expiresAt: `${values.expiresAt}T23:59:59Z`,
        }),
      });
      if (result.success && result.data) {
        setOpen(false);
        router.push(`/org/${slug}/proposals/${result.data.id}`);
      } else {
        setError(result.error ?? "Failed to create proposal.");
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
          <DialogTitle>New Proposal</DialogTitle>
          <DialogDescription>
            Create a proposal for a client engagement.
          </DialogDescription>
        </DialogHeader>

        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)}>
            <div className="max-h-[60vh] space-y-4 overflow-y-auto py-2">
              {/* Title */}
              <FormField
                control={form.control}
                name="title"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Title</FormLabel>
                    <FormControl>
                      <Input
                        placeholder="e.g. Annual Audit Proposal"
                        {...field}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              {/* Customer Selector */}
              <FormField
                control={form.control}
                name="customerId"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Customer</FormLabel>
                    <Popover
                      open={customerPopoverOpen}
                      onOpenChange={setCustomerPopoverOpen}
                    >
                      <PopoverTrigger asChild>
                        <FormControl>
                          <Button
                            type="button"
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
                        </FormControl>
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
                                    field.onChange(customer.id);
                                    setCustomerPopoverOpen(false);
                                  }}
                                  className="gap-3 py-2"
                                >
                                  <Check
                                    className={cn(
                                      "size-4 shrink-0",
                                      field.value === customer.id
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
                    <FormMessage />
                  </FormItem>
                )}
              />

              {/* Fee Model */}
              <FormField
                control={form.control}
                name="feeModel"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Fee Model</FormLabel>
                    <Select
                      value={field.value}
                      onValueChange={field.onChange}
                    >
                      <FormControl>
                        <SelectTrigger className="w-full">
                          <SelectValue />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        {Object.entries(FEE_MODEL_LABELS).map(
                          ([value, label]) => (
                            <SelectItem key={value} value={value}>
                              {label}
                            </SelectItem>
                          ),
                        )}
                      </SelectContent>
                    </Select>
                    <FormMessage />
                  </FormItem>
                )}
              />

              {/* FIXED fee fields */}
              {feeModel === "FIXED" && (
                <>
                  <FormField
                    control={form.control}
                    name="fixedFeeAmount"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Fixed Fee Amount</FormLabel>
                        <FormControl>
                          <Input
                            type="number"
                            min="0"
                            step="0.01"
                            placeholder="e.g. 5000.00"
                            value={field.value ?? ""}
                            onChange={(e) => {
                              const val = e.target.value;
                              field.onChange(val === "" ? undefined : Number(val));
                            }}
                            onBlur={field.onBlur}
                            name={field.name}
                            ref={field.ref}
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={form.control}
                    name="fixedFeeCurrency"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Currency</FormLabel>
                        <FormControl>
                          <Input placeholder="ZAR" {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </>
              )}

              {/* HOURLY fee fields */}
              {feeModel === "HOURLY" && (
                <FormField
                  control={form.control}
                  name="hourlyRateNote"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>
                        Hourly Rate Note{" "}
                        <span className="font-normal text-muted-foreground">
                          (optional)
                        </span>
                      </FormLabel>
                      <FormControl>
                        <Input
                          placeholder="e.g. R850/hr"
                          {...field}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              )}

              {/* RETAINER fee fields */}
              {feeModel === "RETAINER" && (
                <>
                  <FormField
                    control={form.control}
                    name="retainerAmount"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Retainer Amount</FormLabel>
                        <FormControl>
                          <Input
                            type="number"
                            min="0"
                            step="0.01"
                            placeholder="e.g. 5500.00"
                            value={field.value ?? ""}
                            onChange={(e) => {
                              const val = e.target.value;
                              field.onChange(val === "" ? undefined : Number(val));
                            }}
                            onBlur={field.onBlur}
                            name={field.name}
                            ref={field.ref}
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={form.control}
                    name="retainerCurrency"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Currency</FormLabel>
                        <FormControl>
                          <Input placeholder="ZAR" {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={form.control}
                    name="retainerHoursIncluded"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>
                          Hours Included{" "}
                          <span className="font-normal text-muted-foreground">
                            (optional)
                          </span>
                        </FormLabel>
                        <FormControl>
                          <Input
                            type="number"
                            min="0"
                            step="0.5"
                            placeholder="e.g. 10"
                            value={field.value ?? ""}
                            onChange={(e) => {
                              const val = e.target.value;
                              field.onChange(val === "" ? undefined : Number(val));
                            }}
                            onBlur={field.onBlur}
                            name={field.name}
                            ref={field.ref}
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </>
              )}

              {/* Expiry Date */}
              <FormField
                control={form.control}
                name="expiresAt"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>
                      Expiry Date{" "}
                      <span className="font-normal text-muted-foreground">
                        (optional)
                      </span>
                    </FormLabel>
                    <FormControl>
                      <Input type="date" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              {error && (
                <p className="text-sm text-destructive">{error}</p>
              )}
            </div>

            <DialogFooter className="mt-4">
              <Button
                type="button"
                variant="plain"
                onClick={() => setOpen(false)}
                disabled={isSubmitting}
              >
                Cancel
              </Button>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? "Creating..." : "Create Proposal"}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
