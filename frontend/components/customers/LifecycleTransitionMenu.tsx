"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { MoreHorizontal } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { transitionCustomer } from "@/lib/actions/compliance";
import type { Customer } from "@/lib/types";

const TRANSITIONS: Record<string, { label: string; target: string }[]> = {
  PROSPECT: [
    { label: "Start Onboarding", target: "ONBOARDING" },
    { label: "Mark Active", target: "ACTIVE" },
  ],
  ONBOARDING: [
    { label: "Mark Active", target: "ACTIVE" },
    { label: "Offboard", target: "OFFBOARDED" },
  ],
  ACTIVE: [
    { label: "Mark Dormant", target: "DORMANT" },
    { label: "Offboard", target: "OFFBOARDED" },
  ],
  DORMANT: [
    { label: "Reactivate", target: "ACTIVE" },
    { label: "Offboard", target: "OFFBOARDED" },
  ],
};

interface LifecycleTransitionMenuProps {
  customer: Customer;
  canManage: boolean;
  slug: string;
}

export function LifecycleTransitionMenu({
  customer,
  canManage,
  slug,
}: LifecycleTransitionMenuProps) {
  const router = useRouter();
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const transitions = TRANSITIONS[customer.lifecycleStatus] ?? [];

  if (!canManage || transitions.length === 0) {
    return null;
  }

  async function handleTransition(targetStatus: string) {
    setIsLoading(true);
    setError(null);
    try {
      const result = await transitionCustomer(slug, customer.id, targetStatus);
      if (!result.success) {
        setError(result.error ?? "Failed to transition customer.");
      } else {
        router.refresh();
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <div className="flex items-center gap-2">
      {error && <span className="text-xs text-destructive">{error}</span>}
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button
            variant="plain"
            size="icon"
            className="size-8"
            disabled={isLoading}
            aria-label="Lifecycle transitions"
          >
            <MoreHorizontal className="size-4" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          {transitions.map((transition) => (
            <DropdownMenuItem
              key={transition.target}
              onClick={() => handleTransition(transition.target)}
              disabled={isLoading}
            >
              {transition.label}
            </DropdownMenuItem>
          ))}
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  );
}
