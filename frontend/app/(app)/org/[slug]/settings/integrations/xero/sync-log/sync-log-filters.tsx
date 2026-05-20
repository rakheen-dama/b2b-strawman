"use client";

import { useRouter } from "next/navigation";
import { useCallback } from "react";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Button } from "@/components/ui/button";
import { X } from "lucide-react";

interface SyncLogFiltersProps {
  slug: string;
  currentState?: string;
  currentEntityType?: string;
  currentDirection?: string;
}

const SYNC_STATES = [
  { value: "PENDING", label: "Pending" },
  { value: "IN_FLIGHT", label: "In Flight" },
  { value: "COMPLETED", label: "Completed" },
  { value: "FAILED_RETRYING", label: "Retrying" },
  { value: "DEAD_LETTER", label: "Dead Letter" },
  { value: "BLOCKED_TRUST_BOUNDARY", label: "Blocked (Trust)" },
  { value: "RECONCILE_DRIFT", label: "Reconcile Drift" },
];

const ENTITY_TYPES = [
  { value: "INVOICE", label: "Invoice" },
  { value: "CUSTOMER", label: "Customer" },
];

const DIRECTIONS = [
  { value: "PUSH", label: "Push" },
  { value: "PULL", label: "Pull" },
];

export function SyncLogFilters({
  slug,
  currentState,
  currentEntityType,
  currentDirection,
}: SyncLogFiltersProps) {
  const router = useRouter();

  const updateFilter = useCallback(
    (key: string, value: string | undefined) => {
      const params = new URLSearchParams(window.location.search);
      if (value && value !== "ALL") {
        params.set(key, value);
      } else {
        params.delete(key);
      }
      // Reset to page 1 on filter change
      params.delete("page");
      router.push(`?${params.toString()}`);
    },
    [router]
  );

  const clearFilters = useCallback(() => {
    router.push(`/org/${slug}/settings/integrations/xero/sync-log`);
  }, [router, slug]);

  const hasFilters = currentState || currentEntityType || currentDirection;

  return (
    <div className="flex flex-wrap items-center gap-3">
      <Select value={currentState ?? "ALL"} onValueChange={(v) => updateFilter("state", v)}>
        <SelectTrigger className="w-[180px]">
          <SelectValue placeholder="All States" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="ALL">All States</SelectItem>
          {SYNC_STATES.map((s) => (
            <SelectItem key={s.value} value={s.value}>
              {s.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>

      <Select
        value={currentEntityType ?? "ALL"}
        onValueChange={(v) => updateFilter("entityType", v)}
      >
        <SelectTrigger className="w-[160px]">
          <SelectValue placeholder="All Entity Types" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="ALL">All Entity Types</SelectItem>
          {ENTITY_TYPES.map((e) => (
            <SelectItem key={e.value} value={e.value}>
              {e.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>

      <Select value={currentDirection ?? "ALL"} onValueChange={(v) => updateFilter("direction", v)}>
        <SelectTrigger className="w-[140px]">
          <SelectValue placeholder="All Directions" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="ALL">All Directions</SelectItem>
          {DIRECTIONS.map((d) => (
            <SelectItem key={d.value} value={d.value}>
              {d.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>

      {hasFilters && (
        <Button variant="ghost" size="sm" onClick={clearFilters}>
          <X className="mr-1.5 size-4" />
          Clear
        </Button>
      )}
    </div>
  );
}
