"use client";

import { useTransition } from "react";
import { useRouter } from "next/navigation";
import { Lock, Globe, Send, Loader2 } from "lucide-react";
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

interface VisibilityVariant {
  label: string;
  tooltipText: string;
  nextVisibility: DocumentVisibility | null;
  className: string;
  Icon: typeof Lock;
}

function variantFor(visibility: DocumentVisibility): VisibilityVariant {
  switch (visibility) {
    case "SHARED":
      return {
        label: "Shared",
        tooltipText: "Visible to customer portal. Click to make internal only.",
        nextVisibility: "INTERNAL",
        className:
          "text-teal-600 hover:bg-teal-50 hover:text-teal-700 dark:text-teal-400 dark:hover:bg-teal-950 dark:hover:text-teal-300",
        Icon: Globe,
      };
    case "PORTAL":
      return {
        label: "Portal",
        tooltipText: "System-managed (auto-shared by closure pack). Toggle from the closure flow.",
        nextVisibility: null,
        className: "text-teal-700/70 dark:text-teal-300/70 cursor-not-allowed",
        Icon: Send,
      };
    case "INTERNAL":
    default:
      return {
        label: "Internal",
        tooltipText: "Internal only. Click to share with customer.",
        nextVisibility: "SHARED",
        className:
          "text-slate-500 hover:bg-slate-100 hover:text-slate-700 dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-slate-300",
        Icon: Lock,
      };
  }
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

  const variant = variantFor(visibility);
  const isSystemManaged = visibility === "PORTAL";

  const handleToggle = () => {
    if (variant.nextVisibility === null) {
      return;
    }
    const target = variant.nextVisibility;
    startTransition(async () => {
      const result = await toggleDocumentVisibility(slug, customerId, documentId, target);
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
      disabled={disabled || isPending || isSystemManaged}
      title={variant.tooltipText}
      className={variant.className}
    >
      {isPending ? (
        <Loader2 className="mr-1 size-3.5 animate-spin" />
      ) : (
        <variant.Icon className="mr-1 size-3.5" />
      )}
      {variant.label}
    </Button>
  );
}
