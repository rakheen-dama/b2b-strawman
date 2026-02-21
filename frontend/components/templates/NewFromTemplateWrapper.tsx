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
}

export function NewFromTemplateWrapper({
  slug,
  templates,
  orgMembers,
  customers,
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
      >
        <Button variant="outline" size="sm">
          <LayoutTemplate className="mr-1.5 size-4" />
          New from Template
        </Button>
      </NewFromTemplateDialog>
    </Suspense>
  );
}
