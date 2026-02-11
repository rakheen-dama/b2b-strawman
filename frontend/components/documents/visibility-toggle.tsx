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
          ? "text-indigo-600 hover:bg-indigo-50 hover:text-indigo-700 dark:text-indigo-400 dark:hover:bg-indigo-950 dark:hover:text-indigo-300"
          : "text-olive-500 hover:bg-olive-100 hover:text-olive-700 dark:text-olive-400 dark:hover:bg-olive-800 dark:hover:text-olive-300"
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
