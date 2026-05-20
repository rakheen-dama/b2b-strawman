"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { toast } from "sonner";
import { Settings } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Switch } from "@/components/ui/switch";
import { Label } from "@/components/ui/label";
import { xeroSettingsSchema } from "@/lib/schemas/xero-settings";
import { updateXeroSettingsAction } from "@/app/(app)/org/[slug]/settings/integrations/xero/actions";
import type { XeroSettingsFormData } from "@/lib/schemas/xero-settings";
import type { XeroSettingsResponse } from "@/lib/types";

const POLL_INTERVALS = [
  { value: "5", label: "Every 5 minutes" },
  { value: "15", label: "Every 15 minutes" },
  { value: "30", label: "Every 30 minutes" },
  { value: "60", label: "Every 60 minutes" },
] as const;

interface XeroSettingsFormProps {
  settings: XeroSettingsResponse;
  slug: string;
}

export function XeroSettingsForm({ settings, slug }: XeroSettingsFormProps) {
  const [isSaving, setIsSaving] = useState(false);

  const form = useForm<XeroSettingsFormData>({
    resolver: zodResolver(xeroSettingsSchema),
    defaultValues: {
      paymentPollIntervalMinutes: settings.paymentPollIntervalMinutes,
      pushTrigger: settings.pushTrigger,
      autoSyncEnabled: settings.autoSyncEnabled,
    },
  });

  async function onSubmit(data: XeroSettingsFormData) {
    setIsSaving(true);
    try {
      const result = await updateXeroSettingsAction(slug, data);
      if (result.success) {
        toast.success("Sync settings saved.");
      } else {
        toast.error(result.error ?? "Failed to save settings.");
      }
    } catch {
      toast.error("An unexpected error occurred.");
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center gap-3">
          <div className="flex size-10 items-center justify-center rounded-lg bg-slate-100 dark:bg-slate-800">
            <Settings className="size-5 text-slate-600 dark:text-slate-400" />
          </div>
          <div>
            <CardTitle className="font-display text-lg">Sync Settings</CardTitle>
            <CardDescription>Configure how invoices and payments sync with Xero.</CardDescription>
          </div>
        </div>
      </CardHeader>
      <CardContent>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
            <FormField
              control={form.control}
              name="paymentPollIntervalMinutes"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Payment Poll Interval</FormLabel>
                  <Select
                    value={String(field.value)}
                    onValueChange={(val) => field.onChange(Number(val))}
                  >
                    <FormControl>
                      <SelectTrigger>
                        <SelectValue placeholder="Select interval" />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {POLL_INTERVALS.map((interval) => (
                        <SelectItem key={interval.value} value={interval.value}>
                          {interval.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <FormDescription>
                    How often Kazi checks Xero for new payments.
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="pushTrigger"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Push Trigger</FormLabel>
                  <FormControl>
                    <RadioGroup
                      value={field.value}
                      onValueChange={field.onChange}
                      className="space-y-2"
                    >
                      <div className="flex items-center space-x-2">
                        <RadioGroupItem value="APPROVED" id="push-approved" />
                        <Label htmlFor="push-approved" className="font-normal">
                          On approval — push to Xero when invoice is approved
                        </Label>
                      </div>
                      <div className="flex items-center space-x-2">
                        <RadioGroupItem value="SENT" id="push-sent" />
                        <Label htmlFor="push-sent" className="font-normal">
                          On send — push to Xero when invoice is sent to customer
                        </Label>
                      </div>
                    </RadioGroup>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="autoSyncEnabled"
              render={({ field }) => (
                <FormItem className="flex items-center justify-between rounded-lg border p-4">
                  <div className="space-y-0.5">
                    <FormLabel className="text-base">Auto-sync</FormLabel>
                    <FormDescription>
                      Automatically sync invoices and payments with Xero.
                    </FormDescription>
                  </div>
                  <FormControl>
                    <Switch checked={field.value} onCheckedChange={field.onChange} />
                  </FormControl>
                </FormItem>
              )}
            />

            <Button type="submit" disabled={isSaving}>
              {isSaving ? "Saving..." : "Save Settings"}
            </Button>
          </form>
        </Form>
      </CardContent>
    </Card>
  );
}
