"use client";

import { Badge } from "@b2mash/ui/badge";
import { Button } from "@b2mash/ui/button";
import { LifecycleStatusBadge } from "@/components/compliance/LifecycleStatusBadge";
import { KycStatusBadge } from "@/components/customers/kyc-status-badge";
import type { KycSummary } from "@/components/customers/kyc-status-badge";
import { XeroContactBadge } from "@/components/customers/XeroContactBadge";
import { ClientOverflowMenu } from "@/components/customers/client-overflow-menu";
import { GenerateDocumentDropdown } from "@/components/templates/GenerateDocumentDropdown";
import { DetailHero, type HeroTile } from "@/components/shared/detail-hero";
import { HeaderFactsStrip } from "@/components/shared/header-facts-strip";
import { formatDateInSA } from "@/lib/format";
import type { HeaderField } from "@/lib/constants/header-field-slugs";
import type { Client, ClientStatus, LifecycleStatus, TemplateListResponse } from "@/lib/types";

const STATUS_BADGE: Record<ClientStatus, { label: string; variant: "success" | "neutral" }> = {
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
  customerStatus: ClientStatus
): SmartAction | null {
  if (customerStatus === "ARCHIVED") {
    return { label: "Restore", variant: "outline" };
  }

  switch (lifecycleStatus) {
    case "PROSPECT":
      return { label: "Start Onboarding", variant: "accent" };
    case "ONBOARDING":
      return { label: "Activate Client", variant: "accent" };
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
  customerStatus: ClientStatus;
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
  /** Active CUSTOMER templates — drive the Generate Document control */
  templates: TemplateListResponse[];
  // Props forwarded to ClientOverflowMenu
  aiProviderConfigured: boolean;
  conflictCheckEnabled: boolean;
  kycConfigured: boolean;
  kycVerified: boolean;
  /** Full customer object — forwarded to EditCustomerDialog via overflow menu */
  customer: Client;
  /** Called when the smart primary action button is clicked (Epic 560 wires lifecycle transitions) */
  onPrimaryAction?: () => void;
  /** Called when "Summarise Activity" is selected from overflow menu */
  onSummariseActivity?: () => void;
  /** ID / passport number fact (HEADER_FIELD_SLUGS.CUSTOMER, Phase 88 G-B9). */
  headerFields?: HeaderField[];
  /**
   * The hero KPI strip, built server-side by `clients/[id]/hero-tiles.ts` (ADR-373). Gated tiles
   * are already omitted; values are pre-formatted, so nothing here re-reads or re-gates.
   */
  tiles?: HeroTile[];
}

/**
 * The client page hero (Phase 90 642B, ADR-373): name, status badges, contact, facts, actions and
 * the KPI tiles, composed on the shared DetailHero. Keeps every test id the e2e suites rely on
 * (`client-header-card`, `client-name`, `customer-status-badge`, `lifecycle-badge`,
 * `client-contact`, `client-context`, `client-header-fields`, `client-header-actions`,
 * `smart-primary-action`). Clients have no number, so there is no identifier chip.
 */
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
  headerFields = [],
  tiles,
}: ClientHeaderCardProps) {
  const statusBadge = STATUS_BADGE[customerStatus];
  // The record status (Active / Archived) and the lifecycle status share a row; an ACTIVE record
  // beside a lifecycle badge read "Active Prospect" / "Active Active" (G-B1). Show the record badge
  // only when it says something the lifecycle badge does not (Phase 88 628B, R-11).
  const showRecordBadge = customerStatus !== "ACTIVE" || lifecycleStatus === null;
  const smartAction = getSmartPrimaryAction(lifecycleStatus, customerStatus);
  const isAnonymized = lifecycleStatus === "ANONYMIZED";
  const showGenerateDocument = isAdmin && templates.length > 0 && !isAnonymized;

  return (
    <DetailHero
      data-testid="client-header-card"
      titleTestId="client-name"
      title={customerName}
      status={
        <>
          {showRecordBadge && (
            <Badge variant={statusBadge.variant} data-testid="customer-status-badge">
              {statusBadge.label}
            </Badge>
          )}
          {lifecycleStatus && (
            <span data-testid="lifecycle-badge">
              <LifecycleStatusBadge status={lifecycleStatus} />
            </span>
          )}
          {kycSummary && <KycStatusBadge summary={kycSummary} />}
          {xeroConnected && <XeroContactBadge customerId={customerId} slug={slug} />}
        </>
      }
      subtitle={
        email || phone ? (
          <p data-testid="client-contact">
            {email}
            {email && phone && " · "}
            {phone}
          </p>
        ) : undefined
      }
      facts={
        <>
          <HeaderFactsStrip
            fields={headerFields}
            testId="client-header-fields"
            itemTestIdPrefix="client-header-field"
          />
          {/* Instants render on the SA calendar so SSR (UTC) and the browser agree (641A F2). */}
          <p className="text-muted-foreground text-sm" data-testid="client-context">
            {lifecycleStatusChangedAt && (
              <>Since {formatDateInSA(lifecycleStatusChangedAt)} &middot; </>
            )}
            {linkedProjectCount} {linkedProjectCount === 1 ? "matter" : "matters"}
          </p>
        </>
      }
      actions={
        <div
          className="flex flex-wrap items-center justify-end gap-2"
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
          {/* Generate Document — standalone button with its own dropdown, as on the matter page
              (a dropdown cannot nest in the overflow DropdownMenu) */}
          {showGenerateDocument && (
            <GenerateDocumentDropdown
              templates={templates}
              entityId={customerId}
              entityType="CUSTOMER"
              slug={slug}
              customerId={customerId}
              isAdmin={isAdmin}
            />
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
            aiProviderConfigured={aiProviderConfigured}
            conflictCheckEnabled={conflictCheckEnabled}
            kycConfigured={kycConfigured}
            kycVerified={kycVerified}
            customer={customer}
            onSummariseActivity={onSummariseActivity}
          />
        </div>
      }
      tiles={tiles}
    />
  );
}
