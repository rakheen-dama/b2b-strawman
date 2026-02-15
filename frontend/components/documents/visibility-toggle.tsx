"use client";

import { useTransition } from "react";
import { useRouter } from "next/navigation";
import { Lock, Globe, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import type { DocumentVisibility } from "@/lib/types";
import { toggleDocumentVisibility } from "@/app/(app)/org/[slug]/customers/[id]/actions";

interface VisibilityToggleProps {
  documentId: string;
  visibility: DocumentVisibility;
  slug: string;
  customerId: string;
  disabled?: boolean;
}

export function VisibilityToggle({
  documentId,
  visibility,
  slug,
  customerId,
  disabled = false,
}: VisibilityToggleProps) {
  const router = useRouter();
  const [isPending, startTransition] = useTransition();

  const isShared = visibility === "SHARED";
  const nextVisibility: DocumentVisibility = isShared ? "INTERNAL" : "SHARED";
  const label = isShared ? "Shared" : "Internal";
  const tooltipText = isShared
    ? "Visible to customer portal. Click to make internal only."
    : "Internal only. Click to share with customer.";

  const handleToggle = () => {
    startTransition(async () => {
      const result = await toggleDocumentVisibility(slug, customerId, documentId, nextVisibility);
      if (result.success) {
        router.refresh();
      }
    });
  };

  return (
    <Button
      variant="ghost"
      size="sm"
      onClick={handleToggle}
      disabled={disabled || isPending}
      title={tooltipText}
      className={
        isShared
          ? "text-teal-600 hover:bg-teal-50 hover:text-teal-700 dark:text-teal-400 dark:hover:bg-teal-950 dark:hover:text-teal-300"
          : "text-slate-500 hover:bg-slate-100 hover:text-slate-700 dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-slate-300"
      }
    >
      {isPending ? (
        <Loader2 className="mr-1 size-3.5 animate-spin" />
      ) : isShared ? (
        <Globe className="mr-1 size-3.5" />
      ) : (
        <Lock className="mr-1 size-3.5" />
      )}
      {label}
    </Button>
  );
}
