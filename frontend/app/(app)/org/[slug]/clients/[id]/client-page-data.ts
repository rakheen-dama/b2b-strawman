import "server-only";

import {
  api,
  getFieldDefinitions,
  getFieldGroups,
  getGroupMembers,
  getTags,
  getTemplates,
} from "@/lib/api";
import {
  getClientProfitability,
  getClientReadiness,
  getClientTags,
  getClientUnbilledSummary,
  listClientMatters,
} from "@/lib/api/clients";
import type { MyCapabilitiesResponse } from "@/lib/api/capabilities";
import type {
  Client,
  ClientReadiness,
  UnbilledTimeSummary,
  TemplateReadiness,
  Document,
  Matter,
  BillingRate,
  OrgMember,
  OrgSettings,
  ClientProfitabilityResponse,
  OrgProfitabilityResponse,
  InvoiceResponse,
  FieldDefinitionResponse,
  FieldGroupResponse,
  FieldGroupMemberResponse,
  TagResponse,
  TemplateListResponse,
  ChecklistInstanceResponse,
  ChecklistTemplateResponse,
  KycIntegrationStatus,
} from "@/lib/types";
import type { SetupStep, ContextGroup } from "@/components/setup/types";
import { PROMOTED_CUSTOMER_SLUGS } from "@/lib/constants/promoted-field-slugs";
import {
  HEADER_FIELD_SLUGS,
  buildHeaderFields,
  clientHeaderFieldValues,
  type HeaderField,
} from "@/lib/constants/header-field-slugs";
import type { KycSummary } from "@/components/customers/kyc-status-badge";
import { fetchTemplateReadiness } from "@/lib/api/setup-status";
import { getCustomerChecklists, getChecklistTemplates } from "@/lib/checklist-api";
import {
  getCustomerRequests,
  type InformationRequestResponse,
} from "@/lib/api/information-requests";
import { isXeroConnected } from "@/lib/api/integrations";
import { checkPrerequisites } from "@/lib/prerequisites";
import type { PrerequisiteCheck } from "@/components/prerequisite/types";
import { getAiProfile } from "@/lib/api/ai";
import { ArrowRight, UserCheck } from "lucide-react";

// The client page's tab-panel reads (Phase 90 642B, §15.3.5: pages shrink by extracting loaders,
// not by touching tab panels). Moved verbatim out of page.tsx — same order, same non-fatal
// fallbacks. The hero reads (open matters, debtor detail, trust, FICA) live in ./hero-tiles.ts;
// /api/settings is read once by the page and passed in. Server-only: never import from a
// "use client" file.

/** The Overview tab's lifecycle ActionCard (Epic 113A); its target also drives the header action. */
export interface LifecycleActionPrompt {
  icon: typeof ArrowRight;
  title: string;
  description: string;
  actionLabel: string;
  targetStatus: "ONBOARDING" | "ACTIVE";
}

export interface ClientPageData {
  /** `/api/clients/{id}/matters` — the Matters tab and the header count ([] when the read failed). */
  linkedProjects: Matter[];
  customerDocuments: Document[];
  defaultCurrency: string;
  enabledModules: string[];
  customerBillingRates: BillingRate[];
  orgMembers: OrgMember[];
  customerProfitability: ClientProfitabilityResponse | null;
  projectBreakdown: OrgProfitabilityResponse | null;
  customerInvoices: InvoiceResponse[];
  customerFieldDefs: FieldDefinitionResponse[];
  customerFieldGroups: FieldGroupResponse[];
  customerGroupMembers: Record<string, FieldGroupMemberResponse[]>;
  headerFields: HeaderField[];
  customerTags: TagResponse[];
  allTags: TagResponse[];
  customerTemplates: TemplateListResponse[];
  checklistInstances: ChecklistInstanceResponse[];
  checklistTemplates: ChecklistTemplateResponse[];
  kycStatus: KycIntegrationStatus;
  xeroConnected: boolean;
  kycSummary: KycSummary | null;
  customerRequests: InformationRequestResponse[];
  checklistTemplateNames: Record<string, string>;
  showOnboardingTab: boolean;
  customerReadiness: ClientReadiness | null;
  customerUnbilledSummary: UnbilledTimeSummary | null;
  customerTemplateReadiness: TemplateReadiness[];
  activationBlockers: string[];
  hasDocuments: boolean;
  hasPendingChecklistItems: boolean;
  isAiConfigured: boolean;
  customerSetupSteps: SetupStep[];
  customerReadinessPercentage: number;
  customerReadinessComplete: boolean;
  contextGroups: ContextGroup[];
  lifecycleActionPrompt: LifecycleActionPrompt | null;
  promotedFieldValues: Record<string, unknown>;
}

export async function loadClientPageData({
  id,
  customer,
  caps,
  orgSettings,
}: {
  id: string;
  customer: Client;
  caps: MyCapabilitiesResponse;
  /** The page's single /api/settings read; null when it failed. */
  orgSettings: OrgSettings | null;
}): Promise<ClientPageData> {
  const isAdmin = caps.isAdmin || caps.isOwner;

  let linkedProjects: Matter[] = [];
  try {
    linkedProjects = await listClientMatters(id);
  } catch {
    // Non-fatal: show empty projects list if fetch fails
  }

  let customerDocuments: Document[] = [];
  try {
    customerDocuments = await api.get<Document[]>(`/api/documents?scope=CUSTOMER&customerId=${id}`);
  } catch {
    // Non-fatal: show empty documents list if fetch fails
  }

  // Org settings drive currency + module gates for both admin and non-admin
  // surfaces (e.g. the conflict-check button gate). Read once by the page.
  const defaultCurrency = orgSettings?.defaultCurrency ?? "USD";
  const enabledModules: string[] = orgSettings?.enabledModules ?? [];

  // Billing rates + org members for the "Rates" tab (admin/owner only)
  let customerBillingRates: BillingRate[] = [];
  let orgMembers: OrgMember[] = [];
  let customerProfitability: ClientProfitabilityResponse | null = null;
  let projectBreakdown: OrgProfitabilityResponse | null = null;
  let customerInvoices: InvoiceResponse[] = [];
  if (isAdmin) {
    try {
      const [ratesRes, membersRes, profitabilityRes, breakdownRes, invoicesRes] = await Promise.all(
        [
          api.get<{ content: BillingRate[] }>(`/api/billing-rates?clientId=${id}`),
          api.get<OrgMember[]>("/api/members"),
          getClientProfitability(id).catch(() => null),
          // D4 (Phase 87): backend /api/reports/profitability still takes customerId (ReportController:66) — do not rename until the backend moves.
          api
            .get<OrgProfitabilityResponse>(`/api/reports/profitability?customerId=${id}`)
            .catch(() => null),
          api
            .get<InvoiceResponse[]>(`/api/invoices?clientId=${id}`)
            .catch(() => [] as InvoiceResponse[]),
        ]
      );
      customerBillingRates = ratesRes?.content ?? [];
      orgMembers = membersRes;
      customerProfitability = profitabilityRes;
      projectBreakdown = breakdownRes;
      customerInvoices = invoicesRes ?? [];
    } catch {
      // Non-fatal: show empty rates/financials/invoices tab if fetch fails
    }
  }

  // Custom field definitions and groups for the Custom Fields section
  let customerFieldDefs: FieldDefinitionResponse[] = [];
  let customerFieldGroups: FieldGroupResponse[] = [];
  const customerGroupMembers: Record<string, FieldGroupMemberResponse[]> = {};
  try {
    const [defs, groups] = await Promise.all([
      getFieldDefinitions("CUSTOMER"),
      getFieldGroups("CUSTOMER"),
    ]);
    // Filter out promoted field slugs — they are rendered as first-class
    // form fields (Epic 463) and should not appear in the custom field section.
    customerFieldDefs = defs.filter((d) => !PROMOTED_CUSTOMER_SLUGS.has(d.slug));
    customerFieldGroups = groups;

    // Fetch members for each applied group
    const appliedGroups = customer.appliedFieldGroups ?? [];
    if (appliedGroups.length > 0) {
      const memberResults = await Promise.allSettled(
        appliedGroups.map((gId) => getGroupMembers(gId))
      );
      memberResults.forEach((result, i) => {
        if (result.status === "fulfilled") {
          customerGroupMembers[appliedGroups[i]] = result.value;
        }
      });
    }
  } catch {
    // Non-fatal: custom fields section won't render
  }

  // Header facts strip (G-B9): the ID shows the structural idNumber first (what the Edit dialog
  // writes and the conflict check matches), falling back to the legacy id_passport_number custom field.
  const headerFields = buildHeaderFields(
    HEADER_FIELD_SLUGS.CUSTOMER,
    clientHeaderFieldValues(customer.customFields, customer.idNumber),
    customerFieldDefs
  );

  // Tags for the Tags section
  let customerTags: TagResponse[] = [];
  let allTags: TagResponse[] = [];
  try {
    const [entityTags, orgTags] = await Promise.all([getClientTags(id), getTags()]);
    customerTags = entityTags;
    allTags = orgTags;
  } catch {
    // Non-fatal: tags section will show empty state
  }

  // Document templates for the "Generate Document" dropdown (admin only)
  let customerTemplates: TemplateListResponse[] = [];
  if (isAdmin) {
    try {
      customerTemplates = await getTemplates(undefined, "CUSTOMER");
    } catch {
      // Non-fatal: hide generate button if template fetch fails
    }
  }

  // Checklist instances for the Onboarding tab
  let checklistInstances: ChecklistInstanceResponse[] = [];
  try {
    checklistInstances = await getCustomerChecklists(id);
  } catch {
    // Non-fatal: onboarding tab won't show if fetch fails
  }

  // Checklist templates for the "Manually Add" selector (admin only)
  let checklistTemplates: ChecklistTemplateResponse[] = [];
  if (isAdmin) {
    try {
      checklistTemplates = await getChecklistTemplates();
    } catch {
      // Non-fatal: hide add button if template fetch fails
    }
  }

  // Fetch KYC integration status
  let kycStatus: KycIntegrationStatus = { configured: false, provider: null };
  try {
    kycStatus = await api.get<KycIntegrationStatus>("/api/integrations/kyc/status");
  } catch {
    // Non-fatal: KYC verification buttons won't show
  }

  const xeroConnected = await isXeroConnected();

  // Derive a customer-level KYC summary from existing checklist data so the
  // header can surface a status badge without an extra round-trip. Walks
  // checklist items looking for a verification provider; collapses to one of
  // verified / pending / unverified.
  const kycSummary: KycSummary | null = (() => {
    if (!kycStatus.configured) return null;
    let latestVerified: { verifiedAt: string | null; provider: string } | null = null;
    let hasPending = false;
    for (const inst of checklistInstances) {
      for (const item of inst.items ?? []) {
        // A VERIFIED item without a verifiedAt timestamp is still verified —
        // don't demote it to "pending". The badge tolerates a null timestamp.
        if (item.verificationStatus === "VERIFIED" && item.verificationProvider) {
          const itemVerifiedAt = item.verifiedAt ?? null;
          const isNewer =
            !latestVerified ||
            (itemVerifiedAt &&
              (latestVerified.verifiedAt === null || itemVerifiedAt > latestVerified.verifiedAt));
          if (isNewer) {
            latestVerified = {
              verifiedAt: itemVerifiedAt,
              provider: item.verificationProvider,
            };
          }
        } else if (item.verificationProvider) {
          hasPending = true;
        }
      }
    }
    if (latestVerified) {
      return {
        state: "verified",
        provider: latestVerified.provider,
        verifiedAt: latestVerified.verifiedAt,
      };
    }
    if (hasPending) {
      return { state: "pending" };
    }
    return { state: "unverified" };
  })();

  // Fetch information requests for the Requests tab
  let customerRequests: InformationRequestResponse[] = [];
  try {
    customerRequests = await getCustomerRequests(id);
  } catch {
    // Non-fatal: requests tab will show empty state
  }

  // Build template name lookup for the onboarding panel
  const checklistTemplateNames: Record<string, string> = {};
  for (const t of checklistTemplates) {
    checklistTemplateNames[t.id] = t.name;
  }

  const showOnboardingTab =
    customer.lifecycleStatus === "ONBOARDING" || checklistInstances.length > 0;

  // Setup guidance data (Epic 113A)
  let customerReadiness: ClientReadiness | null = null;
  let customerUnbilledSummary: UnbilledTimeSummary | null = null;
  let customerTemplateReadiness: TemplateReadiness[] = [];
  try {
    const [readinessRes, unbilledRes, templateReadinessRes] = await Promise.all([
      getClientReadiness(id),
      getClientUnbilledSummary(id),
      fetchTemplateReadiness("CUSTOMER", id),
    ]);
    customerReadiness = readinessRes;
    customerUnbilledSummary = unbilledRes;
    customerTemplateReadiness = templateReadinessRes;
  } catch {
    // Non-fatal: setup guidance cards will not render if fetch fails
  }

  // Activation prerequisite check for ONBOARDING customers (Task 248.4)
  let activationPrerequisites: PrerequisiteCheck | null = null;
  if (customer.lifecycleStatus === "ONBOARDING") {
    try {
      activationPrerequisites = await checkPrerequisites("LIFECYCLE_ACTIVATION", "CUSTOMER", id);
    } catch {
      // Non-fatal: activation blockers won't show if check fails
    }
  }

  const activationBlockers: string[] =
    activationPrerequisites && !activationPrerequisites.passed
      ? activationPrerequisites.violations.map((v) => v.message)
      : [];

  // AI FICA panel prerequisites
  const hasDocuments = customerDocuments.length > 0;
  const hasPendingChecklistItems = checklistInstances.some((inst) =>
    (inst.items ?? []).some((item) => item.status === "PENDING")
  );
  let isAiConfigured = false;
  if (caps.capabilities.includes("AI_MANAGE")) {
    // OWNER/ADMIN can check profile directly
    try {
      const aiProfile = await getAiProfile();
      isAiConfigured = aiProfile.coldStartCompleted;
    } catch {
      // Non-fatal: panel will show disabled state
    }
  } else if (caps.capabilities.includes("AI_EXECUTE")) {
    // MEMBER with AI_EXECUTE: they wouldn't have this capability without setup being done
    isAiConfigured = true;
  }

  // Map customer readiness to setup steps
  const customerSetupSteps: SetupStep[] = customerReadiness
    ? [
        {
          label: "Matters linked",
          complete: customerReadiness.hasLinkedMatters,
          actionHref: `?tab=matters`,
        },
        {
          label:
            customerReadiness.checklistProgress === null
              ? "No onboarding checklist"
              : `Onboarding checklist (${customerReadiness.checklistProgress.completed}/${customerReadiness.checklistProgress.total})`,
          complete:
            customerReadiness.checklistProgress === null ||
            customerReadiness.checklistProgress.completed ===
              customerReadiness.checklistProgress.total,
          actionHref: showOnboardingTab ? `?tab=onboarding` : undefined,
        },
        {
          label:
            (customerReadiness.requiredFields?.total ?? 0) === 0
              ? "No required fields defined"
              : `Required fields filled (${customerReadiness.requiredFields?.filled ?? 0}/${customerReadiness.requiredFields?.total ?? 0})`,
          complete:
            (customerReadiness.requiredFields?.total ?? 0) === 0 ||
            (customerReadiness.requiredFields?.filled ?? 0) ===
              (customerReadiness.requiredFields?.total ?? 0),
          actionHref: "#custom-fields",
        },
      ]
    : [];

  // Compute readiness percentage from steps
  const completedStepCount = customerSetupSteps.filter((s) => s.complete).length;
  const customerReadinessPercentage =
    customerSetupSteps.length === 0
      ? 100
      : Math.round((completedStepCount / customerSetupSteps.length) * 100);
  const customerReadinessComplete =
    customerSetupSteps.length === 0 || customerSetupSteps.every((s) => s.complete);

  // Build context groups from required fields for expandable display (Epic 251B)
  const contextGroups: ContextGroup[] =
    customerReadiness && (customerReadiness.requiredFields?.fields?.length ?? 0) > 0
      ? [
          {
            contextLabel: "Required Fields",
            filled: customerReadiness.requiredFields?.filled ?? 0,
            total: customerReadiness.requiredFields?.total ?? 0,
            fields: (customerReadiness.requiredFields?.fields ?? []).map((f) => ({
              name: f.name,
              slug: f.slug,
              filled: f.filled,
            })),
          },
        ]
      : [];

  // Lifecycle action prompt (Epic 113A)
  const lifecycleActionPrompt: LifecycleActionPrompt | null =
    isAdmin && customer.lifecycleStatus && customer.status === "ACTIVE"
      ? customer.lifecycleStatus === "PROSPECT"
        ? {
            icon: ArrowRight,
            title: "Ready to start onboarding?",
            description: "Move this client to Onboarding to begin compliance checklists.",
            actionLabel: "Start Onboarding",
            targetStatus: "ONBOARDING",
          }
        : customer.lifecycleStatus === "ONBOARDING" &&
            (customerReadiness?.checklistProgress == null ||
              customerReadiness.checklistProgress.total === 0 ||
              (customerReadiness.checklistProgress.completed ===
                customerReadiness.checklistProgress.total &&
                customerReadiness.checklistProgress.total > 0))
          ? {
              icon: UserCheck,
              title:
                customerReadiness?.checklistProgress != null &&
                customerReadiness.checklistProgress.total > 0
                  ? "All items verified — Activate Client"
                  : "Ready to activate",
              description:
                customerReadiness?.checklistProgress != null &&
                customerReadiness.checklistProgress.total > 0
                  ? "Onboarding checklist is complete. This client is ready to be activated."
                  : "No onboarding checklist assigned. This client is ready to be activated.",
              actionLabel: "Activate Client",
              targetStatus: "ACTIVE",
            }
          : null
      : null;

  // Pre-compute promoted field values for ClientFieldsTab
  const promotedFieldValues: Record<string, unknown> = {};
  if (customer.entityType) {
    promotedFieldValues["acct_entity_type"] = customer.entityType;
    promotedFieldValues["client_type"] = customer.entityType;
  }
  if (customer.taxNumber) {
    promotedFieldValues["tax_number"] = customer.taxNumber;
    promotedFieldValues["vat_number"] = customer.taxNumber;
  }
  if (customer.registrationNumber) {
    promotedFieldValues["acct_company_registration_number"] = customer.registrationNumber;
    promotedFieldValues["registration_number"] = customer.registrationNumber;
  }

  return {
    linkedProjects,
    customerDocuments,
    defaultCurrency,
    enabledModules,
    customerBillingRates,
    orgMembers,
    customerProfitability,
    projectBreakdown,
    customerInvoices,
    customerFieldDefs,
    customerFieldGroups,
    customerGroupMembers,
    headerFields,
    customerTags,
    allTags,
    customerTemplates,
    checklistInstances,
    checklistTemplates,
    kycStatus,
    xeroConnected,
    kycSummary,
    customerRequests,
    checklistTemplateNames,
    showOnboardingTab,
    customerReadiness,
    customerUnbilledSummary,
    customerTemplateReadiness,
    activationBlockers,
    hasDocuments,
    hasPendingChecklistItems,
    isAiConfigured,
    customerSetupSteps,
    customerReadinessPercentage,
    customerReadinessComplete,
    contextGroups,
    lifecycleActionPrompt,
    promotedFieldValues,
  };
}
