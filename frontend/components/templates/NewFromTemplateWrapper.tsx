"use client";

import { lazy, Suspense } from "react";
import { Button } from "@/components/ui/button";
import { LayoutTemplate } from "lucide-react";
import type { ProjectTemplateResponse } from "@/lib/api/templates";
import type { OrgMember, Customer } from "@/lib/types";

const NewFromTemplateDialog = lazy(() =>
  import("@/components/templates/NewFromTemplateDialog").then((m) => ({
    default: m.NewFromTemplateDialog,
  }))
);

interface Props {
  slug: string;
  templates: ProjectTemplateResponse[];
  orgMembers: OrgMember[];
  customers: Customer[];
  /** Auto-open the dialog on mount (deep-linked via `/projects?new=1`). */
  autoOpen?: boolean;
  /** Pre-select this customer on the form when auto-opening. */
  initialCustomerId?: string;
}

export function NewFromTemplateWrapper({
  slug,
  templates,
  orgMembers,
  customers,
  autoOpen,
  initialCustomerId,
}: Props) {
  if (templates.length === 0) return null;

  return (
    <Suspense
      fallback={
        <Button variant="outline" size="sm" disabled>
          <LayoutTemplate className="mr-1.5 size-4" />
          New from Template
        </Button>
      }
    >
      <NewFromTemplateDialog
        slug={slug}
        templates={templates}
        orgMembers={orgMembers}
        customers={customers}
        autoOpen={autoOpen}
        initialCustomerId={initialCustomerId}
      >
        <Button variant="outline" size="sm">
          <LayoutTemplate className="mr-1.5 size-4" />
          New from Template
        </Button>
      </NewFromTemplateDialog>
    </Suspense>
  );
}
