"use client";

import { useState, useCallback } from "react";
import useSWR from "swr";
import { toast } from "sonner";
import { RotateCcw, Save } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableHead,
  TableHeader,
  TableRow,
  TableCell,
} from "@/components/ui/table";
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
  updateXeroTaxMappingAction,
  resetXeroTaxMappingsAction,
} from "@/app/(app)/org/[slug]/settings/integrations/xero/actions";
import type { XeroTaxMapping, XeroTaxRate } from "@/lib/types";

interface XeroTaxMappingEditorProps {
  mappings: XeroTaxMapping[];
  slug: string;
}

interface EditState {
  externalTaxCode: string;
  displayLabel: string;
}

const NONE_VALUE = "__none__";

export function XeroTaxMappingEditor({ mappings, slug }: XeroTaxMappingEditorProps) {
  const [editStates, setEditStates] = useState<Record<string, EditState>>({});
  const [savingIds, setSavingIds] = useState<Set<string>>(new Set());
  const [isResetting, setIsResetting] = useState(false);
  const [showResetDialog, setShowResetDialog] = useState(false);

  // Fetch available Xero tax rates for the dropdown
  const { data: taxRatesData } = useSWR<XeroTaxRate[]>(
    "/api/integrations/xero/tax-rates",
    null,
    { revalidateOnFocus: false }
  );
  const taxRates = taxRatesData ?? [];

  const getEditState = useCallback(
    (mapping: XeroTaxMapping): EditState => {
      if (editStates[mapping.id]) return editStates[mapping.id];
      return {
        externalTaxCode: mapping.externalTaxCode ?? "",
        displayLabel: mapping.displayLabel ?? "",
      };
    },
    [editStates]
  );

  function updateEditState(id: string, field: keyof EditState, value: string) {
    setEditStates((prev) => ({
      ...prev,
      [id]: {
        ...getEditState(mappings.find((m) => m.id === id)!),
        [field]: value,
      },
    }));
  }

  function hasChanges(mapping: XeroTaxMapping): boolean {
    const state = editStates[mapping.id];
    if (!state) return false;
    return (
      state.externalTaxCode !== (mapping.externalTaxCode ?? "") ||
      state.displayLabel !== (mapping.displayLabel ?? "")
    );
  }

  async function handleSave(mapping: XeroTaxMapping) {
    const state = getEditState(mapping);
    if (!state.externalTaxCode) {
      toast.error("Please select a Xero tax code.");
      return;
    }

    setSavingIds((prev) => new Set(prev).add(mapping.id));
    try {
      const result = await updateXeroTaxMappingAction(slug, mapping.id, {
        externalTaxCode: state.externalTaxCode,
        displayLabel: state.displayLabel,
      });
      if (result.success) {
        toast.success(`Tax mapping for "${mapping.kaziTaxMode}" saved.`);
        // Clear edit state — the saved values are now the defaults
        setEditStates((prev) => {
          const next = { ...prev };
          delete next[mapping.id];
          return next;
        });
      } else {
        toast.error(result.error ?? "Failed to save mapping.");
      }
    } catch {
      toast.error("An unexpected error occurred.");
    } finally {
      setSavingIds((prev) => {
        const next = new Set(prev);
        next.delete(mapping.id);
        return next;
      });
    }
  }

  async function handleReset() {
    setIsResetting(true);
    try {
      const result = await resetXeroTaxMappingsAction(slug);
      if (result.success) {
        toast.success("Tax mappings reset to defaults.");
        setEditStates({});
        setShowResetDialog(false);
      } else {
        toast.error(result.error ?? "Failed to reset mappings.");
      }
    } catch {
      toast.error("An unexpected error occurred.");
    } finally {
      setIsResetting(false);
    }
  }

  return (
    <>
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <div>
              <CardTitle className="font-display text-lg">Tax Code Mappings</CardTitle>
              <CardDescription>
                Map Kazi tax modes to Xero tax codes for accurate invoice syncing.
              </CardDescription>
            </div>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setShowResetDialog(true)}
            >
              <RotateCcw className="mr-2 size-4" />
              Reset to Defaults
            </Button>
          </div>
        </CardHeader>
        <CardContent>
          {mappings.length === 0 ? (
            <p className="text-sm text-slate-600 dark:text-slate-400">
              No tax mappings configured. Connect to Xero to set up mappings.
            </p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Kazi Tax Mode</TableHead>
                  <TableHead>Xero Tax Code</TableHead>
                  <TableHead>Display Label</TableHead>
                  <TableHead className="w-[100px]" />
                </TableRow>
              </TableHeader>
              <TableBody>
                {mappings.map((mapping) => {
                  const state = getEditState(mapping);
                  const isSaving = savingIds.has(mapping.id);
                  const changed = hasChanges(mapping);

                  return (
                    <TableRow key={mapping.id}>
                      <TableCell>
                        <Badge variant="outline">{mapping.kaziTaxMode}</Badge>
                      </TableCell>
                      <TableCell>
                        <Select
                          value={state.externalTaxCode || NONE_VALUE}
                          onValueChange={(val) =>
                            updateEditState(
                              mapping.id,
                              "externalTaxCode",
                              val === NONE_VALUE ? "" : val
                            )
                          }
                        >
                          <SelectTrigger className="w-[200px]">
                            <SelectValue placeholder="Select tax code" />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value={NONE_VALUE}>Not mapped</SelectItem>
                            {taxRates.map((rate) => (
                              <SelectItem key={rate.taxType} value={rate.taxType}>
                                {rate.name} ({rate.effectiveRate}%)
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      </TableCell>
                      <TableCell>
                        <Input
                          value={state.displayLabel}
                          onChange={(e) =>
                            updateEditState(mapping.id, "displayLabel", e.target.value)
                          }
                          placeholder="Display label"
                          className="w-[200px]"
                        />
                      </TableCell>
                      <TableCell>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => handleSave(mapping)}
                          disabled={isSaving || !changed}
                        >
                          <Save className="mr-2 size-4" />
                          {isSaving ? "Saving..." : "Save"}
                        </Button>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <AlertDialog open={showResetDialog} onOpenChange={setShowResetDialog}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Reset Tax Mappings?</AlertDialogTitle>
            <AlertDialogDescription>
              This will reset all tax code mappings to their default values. Any custom
              mappings will be lost.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={isResetting}>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={handleReset} disabled={isResetting}>
              {isResetting ? "Resetting..." : "Reset to Defaults"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
