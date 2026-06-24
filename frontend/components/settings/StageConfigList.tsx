"use client";

import { useState, useTransition } from "react";
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
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Archive, Loader2, Plus, Trash2 } from "lucide-react";
import { nativeSelectClassName } from "@/lib/styles/native-select";
import { StageReorder } from "./StageReorder";
import { StageEditDialog } from "./StageEditDialog";
import { stageCreateSchema, type StageCreateFormData } from "@/lib/schemas/deal";
import {
  reorderStagesAction,
  archiveStageAction,
  deleteStageAction,
  createStageAction,
} from "@/app/(app)/org/[slug]/settings/pipeline/actions";
import type { StageDto } from "@/lib/api/crm";

export interface StageConfigListProps {
  slug: string;
  stages: StageDto[];
}

export function StageConfigList({ slug, stages: initialStages }: StageConfigListProps) {
  const [stages, setStages] = useState<StageDto[]>(initialStages);
  const [message, setMessage] = useState<string | null>(null);
  const [isError, setIsError] = useState(false);
  const [, startTransition] = useTransition();

  function notify(text: string, error = false) {
    setMessage(text);
    setIsError(error);
  }

  function handleReorder(orderedIds: string[]) {
    // Optimistic: reflect new order immediately.
    const byId = new Map(stages.map((s) => [s.id, s]));
    const optimistic = orderedIds
      .map((id, i) => {
        const s = byId.get(id);
        return s ? { ...s, position: i } : null;
      })
      .filter((s): s is StageDto => s != null);
    const previous = stages;
    setStages(optimistic);
    setMessage(null);

    startTransition(async () => {
      const result = await reorderStagesAction(slug, {
        positions: orderedIds.map((id, i) => ({ id, position: i })),
      });
      if (result.success && result.stages) {
        setStages(result.stages);
      } else {
        setStages(previous);
        notify(result.error ?? "Failed to reorder stages.", true);
      }
    });
  }

  function handleArchive(stage: StageDto) {
    setMessage(null);
    startTransition(async () => {
      const result = await archiveStageAction(slug, stage.id);
      if (result.success && result.stage) {
        setStages((cur) => cur.map((s) => (s.id === stage.id ? result.stage! : s)));
        notify(`"${stage.name}" archived.`);
      } else {
        notify(result.error ?? "Failed to archive stage.", true);
      }
    });
  }

  function handleDelete(stage: StageDto, onDone: (ok: boolean) => void) {
    setMessage(null);
    startTransition(async () => {
      const result = await deleteStageAction(slug, stage.id);
      if (result.success) {
        setStages((cur) => cur.filter((s) => s.id !== stage.id));
        notify(`"${stage.name}" deleted.`);
        onDone(true);
      } else {
        // Surface 409 DeleteGuard (deals attached) / 400 last-of-type.
        notify(result.error ?? "Failed to delete stage.", true);
        onDone(false);
      }
    });
  }

  function handleCreated(stage: StageDto) {
    setStages((cur) => [...cur, stage]);
    notify(`"${stage.name}" created.`);
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <p className="text-sm text-slate-600 dark:text-slate-400">
          Drag stages to reorder. The order determines how columns appear on the board.
        </p>
        <CreateStageDialog
          slug={slug}
          nextPosition={stages.length}
          onCreated={handleCreated}
          onError={(e) => notify(e, true)}
        />
      </div>

      {message && (
        <p
          className={`text-sm ${
            isError ? "text-red-600 dark:text-red-400" : "text-green-600 dark:text-green-400"
          }`}
        >
          {message}
        </p>
      )}

      {stages.length === 0 ? (
        <p className="rounded-lg border border-dashed border-slate-300 px-4 py-12 text-center text-sm text-slate-500 dark:border-slate-700 dark:text-slate-400">
          No stages configured yet.
        </p>
      ) : (
        <StageReorder
          stages={stages}
          onReorder={handleReorder}
          renderActions={(stage) => (
            <>
              <StageEditDialog slug={slug} stage={stage} />
              {!stage.archived && (
                <Button
                  variant="plain"
                  size="sm"
                  className="h-7 gap-1 text-xs"
                  onClick={() => handleArchive(stage)}
                >
                  <Archive className="size-3.5" /> Archive
                </Button>
              )}
              <DeleteStageButton stage={stage} onConfirm={handleDelete} />
            </>
          )}
        />
      )}
    </div>
  );
}

function DeleteStageButton({
  stage,
  onConfirm,
}: {
  stage: StageDto;
  onConfirm: (stage: StageDto, onDone: (ok: boolean) => void) => void;
}) {
  const [open, setOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);

  return (
    <AlertDialog open={open} onOpenChange={setOpen}>
      <Button
        variant="plain"
        size="sm"
        className="h-7 gap-1 text-xs text-red-600 hover:text-red-700 dark:text-red-400"
        onClick={() => setOpen(true)}
      >
        <Trash2 className="size-3.5" /> Delete
      </Button>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Delete &ldquo;{stage.name}&rdquo;?</AlertDialogTitle>
          <AlertDialogDescription>
            This cannot be undone. Stages with attached deals cannot be deleted.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel disabled={deleting}>Cancel</AlertDialogCancel>
          <AlertDialogAction
            onClick={(e) => {
              e.preventDefault();
              setDeleting(true);
              onConfirm(stage, (ok) => {
                setDeleting(false);
                if (ok) setOpen(false);
              });
            }}
            disabled={deleting}
          >
            {deleting && <Loader2 className="mr-1.5 size-4 animate-spin" />}
            Delete
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}

function CreateStageDialog({
  slug,
  nextPosition,
  onCreated,
  onError,
}: {
  slug: string;
  nextPosition: number;
  onCreated: (stage: StageDto) => void;
  onError: (error: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const form = useForm<StageCreateFormData>({
    resolver: zodResolver(stageCreateSchema),
    defaultValues: { name: "", defaultProbabilityPct: "50", stageType: "OPEN" },
  });

  function handleOpenChange(o: boolean) {
    setOpen(o);
    if (!o) {
      setError(null);
      form.reset();
    }
  }

  async function onSubmit(values: StageCreateFormData) {
    setError(null);
    setIsSubmitting(true);
    try {
      const result = await createStageAction(slug, {
        name: values.name.trim(),
        position: nextPosition,
        defaultProbabilityPct: Number(values.defaultProbabilityPct),
        stageType: values.stageType,
      });
      if (result.success && result.stage) {
        onCreated(result.stage);
        handleOpenChange(false);
      } else {
        setError(result.error ?? "Something went wrong.");
        onError(result.error ?? "Failed to create stage.");
      }
    } catch {
      setError("A network error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <Button size="sm" onClick={() => setOpen(true)}>
        <Plus className="mr-1.5 size-4" /> New Stage
      </Button>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>New stage</DialogTitle>
          <DialogDescription>Add a stage to your pipeline.</DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form
            id="stage-create-form"
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
          <Button type="submit" form="stage-create-form" disabled={isSubmitting}>
            {isSubmitting && <Loader2 className="mr-1.5 size-4 animate-spin" />}
            Create
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
