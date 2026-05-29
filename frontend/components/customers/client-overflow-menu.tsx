"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import {
  Archive,
  Download,
  FileText,
  MoreHorizontal,
  Pencil,
  Scale,
  ShieldAlert,
  Sparkles,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { EditCustomerDialog } from "@/components/customers/edit-customer-dialog";
import { ArchiveCustomerDialog } from "@/components/customers/archive-customer-dialog";
import { AnonymizeCustomerDialog } from "@/components/customers/anonymize-customer-dialog";
import { DataExportDialog } from "@/components/customers/data-export-dialog";
import type { CustomerStatus, LifecycleStatus, TemplateListResponse } from "@/lib/types";

interface ClientOverflowMenuProps {
  customerId: string;
  customerName: string;
  customerStatus: CustomerStatus;
  lifecycleStatus: LifecycleStatus | null;
  slug: string;
  isAdmin: boolean;
  isOwner: boolean;
  isAnonymized: boolean;
  templates: TemplateListResponse[];
  aiProviderConfigured: boolean;
  conflictCheckEnabled: boolean;
  kycConfigured: boolean;
  kycVerified: boolean;
}

export function ClientOverflowMenu({
  customerId,
  customerName,
  customerStatus,
  lifecycleStatus,
  slug,
  isAdmin,
  isOwner,
  isAnonymized,
  templates,
  aiProviderConfigured,
  conflictCheckEnabled,
  kycConfigured,
  kycVerified,
}: ClientOverflowMenuProps) {
  const router = useRouter();

  // Controlled dialog states — dialogs rendered outside DropdownMenuContent (OBS-2103)
  const [editOpen, setEditOpen] = useState(false);
  const [archiveOpen, setArchiveOpen] = useState(false);
  const [anonymizeOpen, setAnonymizeOpen] = useState(false);
  const [exportOpen, setExportOpen] = useState(false);

  const showEditClient = !isAnonymized;
  const showSummariseActivity = aiProviderConfigured;
  const showGenerateDocument = isAdmin && templates.length > 0 && !isAnonymized;
  const showConflictCheck = conflictCheckEnabled && !isAnonymized;
  const showVerifyKyc = kycConfigured && !kycVerified && !isAnonymized;
  const showAnonymize = isOwner && !isAnonymized;
  const showArchive = isAdmin && !isAnonymized;

  // Build a minimal Customer object for the EditCustomerDialog
  const customerForEdit = {
    id: customerId,
    name: customerName,
    email: "",
    phone: null,
    idNumber: null,
    status: customerStatus,
    notes: null,
    createdBy: "",
    createdByName: null,
    createdAt: "",
    updatedAt: "",
    lifecycleStatus: lifecycleStatus ?? undefined,
  };

  const hasFirstGroup = showEditClient || showSummariseActivity;
  const hasSecondGroup = showGenerateDocument || showConflictCheck || showVerifyKyc;
  const hasDestructiveGroup = showAnonymize || showArchive;

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button
            variant="ghost"
            size="icon"
            className="size-8"
            data-testid="client-overflow-trigger"
          >
            <MoreHorizontal className="size-4" />
            <span className="sr-only">More actions</span>
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" data-testid="client-overflow-menu">
          {showEditClient && (
            <DropdownMenuItem onSelect={() => setEditOpen(true)}>
              <Pencil className="mr-2 size-4" />
              Edit Client
            </DropdownMenuItem>
          )}

          {showSummariseActivity && (
            <DropdownMenuItem
              onSelect={() =>
                router.push(
                  `/org/${slug}/customers/${customerId}?assistant=INBOX&prompt=Summarise+recent+customer+activity.`
                )
              }
            >
              <Sparkles className="mr-2 size-4" />
              Summarise Activity
            </DropdownMenuItem>
          )}

          {hasFirstGroup && hasSecondGroup && <DropdownMenuSeparator />}

          {showGenerateDocument && (
            <DropdownMenuItem
              onSelect={() =>
                router.push(`/org/${slug}/customers/${customerId}?action=generate-document`)
              }
            >
              <FileText className="mr-2 size-4" />
              Generate Document
            </DropdownMenuItem>
          )}

          {showConflictCheck && (
            <DropdownMenuItem
              onSelect={() =>
                router.push(
                  `/org/${slug}/conflict-check?customerId=${customerId}&checkedName=${encodeURIComponent(customerName)}`
                )
              }
            >
              <Scale className="mr-2 size-4" />
              Run Conflict Check
            </DropdownMenuItem>
          )}

          {showVerifyKyc && (
            <DropdownMenuItem
              onSelect={() => router.push(`/org/${slug}/customers/${customerId}?tab=onboarding`)}
            >
              <ShieldAlert className="mr-2 size-4" />
              Verify KYC
            </DropdownMenuItem>
          )}

          {(hasFirstGroup || hasSecondGroup) && <DropdownMenuSeparator />}

          <DropdownMenuItem onSelect={() => setExportOpen(true)}>
            <Download className="mr-2 size-4" />
            Export Data
          </DropdownMenuItem>

          {hasDestructiveGroup && <DropdownMenuSeparator />}

          {showAnonymize && (
            <DropdownMenuItem
              onSelect={() => setAnonymizeOpen(true)}
              className="text-red-600 focus:text-red-600 dark:text-red-400 dark:focus:text-red-400"
            >
              <ShieldAlert className="mr-2 size-4" />
              Anonymize
            </DropdownMenuItem>
          )}

          {showArchive && (
            <DropdownMenuItem
              onSelect={() => setArchiveOpen(true)}
              className="text-red-600 focus:text-red-600 dark:text-red-400 dark:focus:text-red-400"
            >
              <Archive className="mr-2 size-4" />
              Archive
            </DropdownMenuItem>
          )}
        </DropdownMenuContent>
      </DropdownMenu>

      {/* Dialogs rendered outside DropdownMenuContent — no Slot collision (OBS-2103) */}
      {showEditClient && (
        <EditCustomerDialog
          customer={customerForEdit}
          slug={slug}
          open={editOpen}
          onOpenChange={setEditOpen}
        />
      )}

      <DataExportDialog customerId={customerId} open={exportOpen} onOpenChange={setExportOpen} />

      {showArchive && (
        <ArchiveCustomerDialog
          slug={slug}
          customerId={customerId}
          customerName={customerName}
          open={archiveOpen}
          onOpenChange={setArchiveOpen}
        />
      )}

      {showAnonymize && (
        <AnonymizeCustomerDialog
          slug={slug}
          customerId={customerId}
          customerName={customerName}
          open={anonymizeOpen}
          onOpenChange={setAnonymizeOpen}
        />
      )}
    </>
  );
}
