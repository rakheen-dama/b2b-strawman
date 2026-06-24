"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Button } from "@b2mash/ui/button";
import { Input } from "@b2mash/ui/input";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Loader2, Pencil } from "lucide-react";
import { nativeSelectClassName } from "@/lib/styles/native-select";
import { stageEditSchema, type StageEditFormData } from "@/lib/schemas/deal";
import { updateStageAction } from "@/app/(app)/org/[slug]/settings/pipeline/actions";
import type { StageDto } from "@/lib/api/crm";

export interface StageEditDialogProps {
  slug: string;
  stage: StageDto;
  /** Called with the persisted stage so the parent can refresh its local state. */
  onUpdated?: (stage: StageDto) => void;
}

export function StageEditDialog({ slug, stage, onUpdated }: StageEditDialogProps) {
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const form = useForm<StageEditFormData>({
    resolver: zodResolver(stageEditSchema),
    defaultValues: {
      name: stage.name,
      defaultProbabilityPct: String(stage.defaultProbabilityPct),
      stageType: stage.stageType,
    },
  });

  function handleOpenChange(o: boolean) {
    setOpen(o);
    if (!o) {
      setError(null);
      form.reset({
        name: stage.name,
        defaultProbabilityPct: String(stage.defaultProbabilityPct),
        stageType: stage.stageType,
      });
    }
  }

  async function onSubmit(values: StageEditFormData) {
    setError(null);
    setIsSubmitting(true);
    try {
      const result = await updateStageAction(slug, stage.id, {
        name: values.name.trim(),
        defaultProbabilityPct: Number(values.defaultProbabilityPct),
        stageType: values.stageType,
      });
      if (result.success && result.stage) {
        onUpdated?.(result.stage);
        handleOpenChange(false);
      } else {
        // Surface backend guard errors (e.g. 400 last-of-type on type change).
        setError(result.error ?? "Something went wrong.");
      }
    } catch {
      setError("A network error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <Button variant="plain" size="sm" className="h-7 gap-1 text-xs" onClick={() => setOpen(true)}>
        <Pencil className="size-3.5" /> Edit
      </Button>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Edit stage</DialogTitle>
          <DialogDescription>Update the name, probability, and type.</DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form
            id={`stage-edit-form-${stage.id}`}
            onSubmit={form.handleSubmit(onSubmit)}
            className="space-y-4 py-2"
          >
            <FormField
              control={form.control}
              name="name"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Name</FormLabel>
                  <FormControl>
                    <Input maxLength={80} autoFocus {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="defaultProbabilityPct"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Default probability (%)</FormLabel>
                  <FormControl>
                    <Input
                      type="number"
                      min={0}
                      max={100}
                      value={field.value}
                      onChange={(e) => field.onChange(e.target.value)}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="stageType"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Type</FormLabel>
                  <FormControl>
                    <select
                      value={field.value}
                      onChange={field.onChange}
                      className={nativeSelectClassName}
                    >
                      <option value="OPEN">Open</option>
                      <option value="WON">Won</option>
                      <option value="LOST">Lost</option>
                    </select>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </form>
        </Form>
        {error && <p className="text-destructive text-sm">{error}</p>}
        <DialogFooter>
          <Button
            type="button"
            variant="plain"
            onClick={() => handleOpenChange(false)}
            disabled={isSubmitting}
          >
            Cancel
          </Button>
          <Button type="submit" form={`stage-edit-form-${stage.id}`} disabled={isSubmitting}>
            {isSubmitting && <Loader2 className="mr-1.5 size-4 animate-spin" />}
            Save
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
