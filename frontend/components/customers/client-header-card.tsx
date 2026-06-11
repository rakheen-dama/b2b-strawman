"use client";

import { Badge } from "@b2mash/ui/badge";
import { Button } from "@b2mash/ui/button";
import { Card } from "@b2mash/ui/card";
import { LifecycleStatusBadge } from "@/components/compliance/LifecycleStatusBadge";
import { KycStatusBadge } from "@/components/customers/kyc-status-badge";
import type { KycSummary } from "@/components/customers/kyc-status-badge";
import { XeroContactBadge } from "@/components/customers/XeroContactBadge";
import { ClientOverflowMenu } from "@/components/customers/client-overflow-menu";
import { formatDate } from "@/lib/format";
import type { Customer, CustomerStatus, LifecycleStatus, TemplateListResponse } from "@/lib/types";

const STATUS_BADGE: Record<CustomerStatus, { label: string; variant: "success" | "neutral" }> = {
  ACTIVE: { label: "Active", variant: "success" },
  ARCHIVED: { label: "Archived", variant: "neutral" },
};

interface SmartAction {
  label: string;
  variant: "accent" | "outline";
  disabled?: boolean;
  tooltip?: string;
}

function getSmartPrimaryAction(
  lifecycleStatus: LifecycleStatus | null,
  customerStatus: CustomerStatus
): SmartAction | null {
  if (customerStatus === "ARCHIVED") {
    return { label: "Restore", variant: "outline" };
  }

  switch (lifecycleStatus) {
    case "PROSPECT":
      return { label: "Start Onboarding", variant: "accent" };
    case "ONBOARDING":
      return { label: "Activate Customer", variant: "accent" };
    case "ACTIVE":
    case "DORMANT":
      return { label: "Edit", variant: "outline" };
    case "OFFBOARDING":
      return { label: "Complete Offboarding", variant: "accent" };
    case "OFFBOARDED":
    case "ANONYMIZED":
      return null;
    default:
      return null;
  }
}

export interface ClientHeaderCardProps {
  customerId: string;
  customerName: string;
  customerStatus: CustomerStatus;
  lifecycleStatus: LifecycleStatus | null;
  email: string | null;
  phone: string | null;
  lifecycleStatusChangedAt: string | null;
  linkedProjectCount: number;
  kycSummary: KycSummary | null;
  xeroConnected: boolean;
  slug: string;
  isAdmin: boolean;
  isOwner: boolean;
  // Props forwarded to ClientOverflowMenu
  templates: TemplateListResponse[];
  aiProviderConfigured: boolean;
  conflictCheckEnabled: boolean;
  kycConfigured: boolean;
  kycVerified: boolean;
  /** Full customer object — forwarded to EditCustomerDialog via overflow menu */
  customer: Customer;
  /** Called when the smart primary action button is clicked (Epic 560 wires lifecycle transitions) */
  onPrimaryAction?: () => void;
  /** Called when "Summarise Activity" is selected from overflow menu */
  onSummariseActivity?: () => void;
}

export function ClientHeaderCard({
  customerId,
  customerName,
  customerStatus,
  lifecycleStatus,
  email,
  phone,
  lifecycleStatusChangedAt,
  linkedProjectCount,
  kycSummary,
  xeroConnected,
  slug,
  isAdmin,
  isOwner,
  templates,
  aiProviderConfigured,
  conflictCheckEnabled,
  kycConfigured,
  kycVerified,
  customer,
  onPrimaryAction,
  onSummariseActivity,
}: ClientHeaderCardProps) {
  const statusBadge = STATUS_BADGE[customerStatus];
  const smartAction = getSmartPrimaryAction(lifecycleStatus, customerStatus);
  const isAnonymized = lifecycleStatus === "ANONYMIZED";

  return (
    <Card className="p-5" data-testid="client-header-card">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0 space-y-2">
          {/* Row 1 — Name */}
          <h1
            className="font-display line-clamp-2 text-xl font-semibold text-slate-950 dark:text-slate-50"
            title={customerName}
            data-testid="client-name"
          >
            {customerName}
          </h1>

          {/* Row 2 — Badges */}
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant={statusBadge.variant} data-testid="customer-status-badge">
              {statusBadge.label}
            </Badge>
            {lifecycleStatus && (
              <span data-testid="lifecycle-badge">
                <LifecycleStatusBadge status={lifecycleStatus} />
              </span>
            )}
            {kycSummary && <KycStatusBadge summary={kycSummary} />}
            {xeroConnected && <XeroContactBadge customerId={customerId} slug={slug} />}
          </div>

          {/* Row 3 — Contact */}
          {(email || phone) && (
            <p className="text-sm text-slate-600 dark:text-slate-400" data-testid="client-contact">
              {email}
              {email && phone && " · "}
              {phone}
            </p>
          )}

          {/* Row 4 — Context */}
          <p className="text-muted-foreground text-sm" data-testid="client-context">
            {lifecycleStatusChangedAt && (
              <>Since {formatDate(lifecycleStatusChangedAt)} &middot; </>
            )}
            {linkedProjectCount} {linkedProjectCount === 1 ? "engagement" : "engagements"}
          </p>
        </div>

        {/* Row 5 — Actions */}
        <div
          className="flex shrink-0 items-center gap-2 self-end sm:self-auto"
          data-testid="client-header-actions"
        >
          {smartAction && (
            <Button
              variant={smartAction.variant}
              size="sm"
              disabled={smartAction.disabled}
              title={smartAction.tooltip}
              onClick={onPrimaryAction}
              data-testid="smart-primary-action"
            >
              {smartAction.label}
            </Button>
          )}
          <ClientOverflowMenu
            customerId={customerId}
            customerName={customerName}
            customerStatus={customerStatus}
            lifecycleStatus={lifecycleStatus}
            slug={slug}
            isAdmin={isAdmin}
            isOwner={isOwner}
            isAnonymized={isAnonymized}
            templates={templates}
            aiProviderConfigured={aiProviderConfigured}
            conflictCheckEnabled={conflictCheckEnabled}
            kycConfigured={kycConfigured}
            kycVerified={kycVerified}
            customer={customer}
            onSummariseActivity={onSummariseActivity}
          />
        </div>
      </div>
    </Card>
  );
}
