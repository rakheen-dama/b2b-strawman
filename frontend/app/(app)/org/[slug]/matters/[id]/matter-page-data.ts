import "server-only";

import { getCurrentUserEmail } from "@/lib/auth";
import {
  api,
  getFieldDefinitions,
  getFieldGroups,
  getGroupMembers,
  getTags,
  getTemplates,
  getViews,
} from "@/lib/api";
import {
  getMatterProfitability,
  getMatterSetupStatus,
  getMatterTags,
  getMatterTimeSummary,
  getMatterTimeSummaryByMember,
  getMatterTimeSummaryByTask,
  listMatterClients,
  listMatterDocuments,
  listMatterMembers,
  listMatterTasks,
} from "@/lib/api/matters";
import { getClientFicaStatus } from "@/lib/api/clients";
import { getProjectCorrespondence, type CorrespondenceListItem } from "@/lib/api/correspondence";
import {
  getProjectRequests,
  type InformationRequestResponse,
} from "@/lib/api/information-requests";
import { getAiProfile } from "@/lib/api/ai";
import type { MyCapabilitiesResponse } from "@/lib/api/capabilities";
import type { SetupStep } from "@/components/setup/types";
import { PROMOTED_PROJECT_SLUGS, PROMOTED_TASK_SLUGS } from "@/lib/constants/promoted-field-slugs";
import {
  HEADER_FIELD_SLUGS,
  buildHeaderFields,
  type HeaderField,
} from "@/lib/constants/header-field-slugs";
import type {
  OrgMember,
  Matter,
  Client,
  Document,
  MatterMember,
  Task,
  MatterTimeSummary,
  MemberTimeSummary,
  TaskTimeSummary,
  BillingRate,
  OrgSettings,
  MatterProfitabilityResponse,
  FieldDefinitionResponse,
  FieldGroupResponse,
  FieldGroupMemberResponse,
  TagResponse,
  TemplateListResponse,
  SavedViewResponse,
  MatterSetupStatus,
  FicaStatus,
} from "@/lib/types";

// The matter page's tab-panel reads (Phase 90 641B, §15.3.5: pages shrink by extracting loaders,
// not by touching tab panels). Moved verbatim out of page.tsx — same order, same non-fatal
// fallbacks. The hero reads (trust, outstanding, unbilled, deadlines) and the budget live in
// ./hero-tiles.ts. Server-only: never import from a "use client" file.

export interface MatterPageData {
  setupStatus: MatterSetupStatus | null;
  setupSteps: SetupStep[];
  canExecuteAi: boolean;
  canReviewGates: boolean;
  isAiConfigured: boolean;
  documents: Document[];
  correspondence: CorrespondenceListItem[];
  members: MatterMember[];
  tasks: Task[];
  customers: Client[];
  projectRequests: InformationRequestResponse[];
  ficaStatus: FicaStatus | null;
  timeSummary: MatterTimeSummary;
  timeSummaryByTask: TaskTimeSummary[];
  timeSummaryByMember: MemberTimeSummary[] | null;
  projectBillingRates: BillingRate[];
  defaultCurrency: string;
  projectProfitability: MatterProfitabilityResponse | null;
  currentMemberId: string | null;
  projectFieldDefs: FieldDefinitionResponse[];
  projectFieldGroups: FieldGroupResponse[];
  projectGroupMembers: Record<string, FieldGroupMemberResponse[]>;
  headerFields: HeaderField[];
  taskFieldDefs: FieldDefinitionResponse[];
  taskFieldGroups: FieldGroupResponse[];
  taskGroupMembers: Record<string, FieldGroupMemberResponse[]>;
  savedTaskViews: SavedViewResponse[];
  projectTags: TagResponse[];
  allTags: TagResponse[];
  projectTemplates: TemplateListResponse[];
}

export async function loadMatterPageData({
  id,
  project,
  caps,
  canManage,
}: {
  id: string;
  project: Matter;
  caps: MyCapabilitiesResponse;
  canManage: boolean;
}): Promise<MatterPageData> {
  // Setup guidance data (Epic 112A)
  let setupStatus: MatterSetupStatus | null = null;
  try {
    setupStatus = await getMatterSetupStatus(id);
  } catch {
    // Non-fatal: setup guidance cards will not render if fetch fails
  }

  // Map setup status to steps
  const setupSteps: SetupStep[] = setupStatus
    ? [
        {
          label: "Client assigned",
          complete: setupStatus.clientAssigned,
          actionHref: `?tab=clients`,
        },
        {
          label: "Billing rate configured",
          complete: setupStatus.rateCardConfigured,
          actionHref: `?tab=rates`,
          permissionRequired: true,
        },
        {
          label: "Fee estimate set",
          complete: setupStatus.budgetConfigured,
          actionHref: `?tab=budget`,
        },
        {
          label: "Team members added",
          complete: setupStatus.teamAssigned,
          actionHref: `?tab=team`,
        },
        {
          label:
            setupStatus.requiredFields.total === 0
              ? "No required fields defined"
              : `Required fields filled (${setupStatus.requiredFields.filled}/${setupStatus.requiredFields.total})`,
          complete:
            setupStatus.requiredFields.total === 0 ||
            setupStatus.requiredFields.filled === setupStatus.requiredFields.total,
          actionHref: "?tab=fields",
        },
      ]
    : [];

  // AI configuration status — needed for contract review button in documents tab
  const canExecuteAi = caps.capabilities.includes("AI_EXECUTE");
  const canReviewGates = caps.capabilities.includes("AI_REVIEW");
  let isAiConfigured = false;
  if (caps.capabilities.includes("AI_MANAGE")) {
    try {
      const aiProfile = await getAiProfile();
      isAiConfigured = aiProfile.coldStartCompleted;
    } catch {
      // Non-fatal: panel will show disabled state
    }
  } else if (canExecuteAi) {
    // MEMBER with AI_EXECUTE: they wouldn't have this capability without setup being done
    isAiConfigured = true;
  }

  let documents: Document[] = [];
  try {
    documents = await listMatterDocuments(id);
  } catch {
    // Non-fatal: show empty documents list if fetch fails
  }

  let correspondence: CorrespondenceListItem[] = [];
  try {
    correspondence = (await getProjectCorrespondence(id, { size: 50 })).content;
  } catch {
    // Non-fatal: show empty correspondence list if fetch fails
  }

  let members: MatterMember[] = [];
  try {
    members = await listMatterMembers(id);
  } catch {
    // Non-fatal: show empty members list if fetch fails
  }

  let tasks: Task[] = [];
  try {
    tasks = await listMatterTasks(id);
  } catch {
    // Non-fatal: show empty tasks list if fetch fails
  }

  let customers: Client[] = [];
  try {
    customers = await listMatterClients(id);
  } catch {
    // Non-fatal: show empty customers list if fetch fails
  }

  // Fetch information requests for the Requests tab (only if project has customers)
  let projectRequests: InformationRequestResponse[] = [];
  if (customers.length > 0) {
    try {
      projectRequests = await getProjectRequests(id);
    } catch {
      // Non-fatal: requests tab will show empty state
    }
  }

  // FICA onboarding status for the matter's primary customer (GAP-L-46).
  // Surfaces on the Overview tab as a small status tile. Fetch is
  // non-fatal — when the endpoint is unreachable or the profile
  // doesn't carry a FICA template pack, the card renders a soft
  // "Status unavailable" state rather than breaking the page.
  let ficaStatus: FicaStatus | null = null;
  if (customers.length > 0) {
    try {
      ficaStatus = await getClientFicaStatus(customers[0].id);
    } catch {
      ficaStatus = null;
    }
  }

  // Time summary data for the "Time" tab
  let timeSummary: MatterTimeSummary = {
    billableMinutes: 0,
    nonBillableMinutes: 0,
    totalMinutes: 0,
    contributorCount: 0,
    entryCount: 0,
  };
  let timeSummaryByTask: TaskTimeSummary[] = [];
  let timeSummaryByMember: MemberTimeSummary[] | null = null;
  try {
    const [summaryRes, byTaskRes, byMemberRes] = await Promise.all([
      getMatterTimeSummary(id),
      getMatterTimeSummaryByTask(id),
      getMatterTimeSummaryByMember(id).catch(() => null),
    ]);
    timeSummary = summaryRes;
    timeSummaryByTask = byTaskRes;
    timeSummaryByMember = byMemberRes;
  } catch {
    // Non-fatal: show empty time summary if fetch fails
  }

  // Billing rates + settings for the "Rates" tab (only fetched for users who can manage)
  let projectBillingRates: BillingRate[] = [];
  let defaultCurrency = "USD";
  if (canManage) {
    try {
      const [ratesRes, settingsRes] = await Promise.all([
        api.get<{ content: BillingRate[] }>(`/api/billing-rates?matterId=${id}`),
        api.get<OrgSettings>("/api/settings").catch(() => null),
      ]);
      projectBillingRates = ratesRes?.content ?? [];
      if (settingsRes?.defaultCurrency) {
        defaultCurrency = settingsRes.defaultCurrency;
      }
    } catch {
      // Non-fatal: show empty rates tab if fetch fails
    }
  }

  // Matter profitability for the "Financials" tab (lead/admin/owner)
  let projectProfitability: MatterProfitabilityResponse | null = null;
  if (canManage) {
    try {
      projectProfitability = await getMatterProfitability(id);
    } catch {
      // Non-fatal: show empty state in financials tab
    }
  }

  // Resolve current user's backend member ID for "My Tasks" filter and claim/release actions.
  // Match current user email against org members list from the backend.
  let currentMemberId: string | null = null;
  try {
    const [email, orgMembers] = await Promise.all([
      getCurrentUserEmail(),
      api.get<OrgMember[]>("/api/members"),
    ]);
    if (email) {
      const match = orgMembers.find((m) => m.email === email);
      if (match) currentMemberId = match.id;
    }
  } catch {
    // Non-fatal: "My Tasks" filter won't work without member ID
  }

  // Custom field definitions and groups for the Custom Fields section
  let projectFieldDefs: FieldDefinitionResponse[] = [];
  let projectFieldGroups: FieldGroupResponse[] = [];
  const projectGroupMembers: Record<string, FieldGroupMemberResponse[]> = {};
  try {
    const [defs, groups] = await Promise.all([
      getFieldDefinitions("PROJECT"),
      getFieldGroups("PROJECT"),
    ]);
    projectFieldDefs = defs.filter((d) => !PROMOTED_PROJECT_SLUGS.has(d.slug));
    projectFieldGroups = groups;

    // Fetch members for each applied group
    const appliedGroups = project.appliedFieldGroups ?? [];
    if (appliedGroups.length > 0) {
      const memberResults = await Promise.allSettled(
        appliedGroups.map((gId) => getGroupMembers(gId))
      );
      memberResults.forEach((result, i) => {
        if (result.status === "fulfilled") {
          projectGroupMembers[appliedGroups[i]] = result.value;
        }
      });
    }
  } catch {
    // Non-fatal: custom fields section won't render
  }

  // Header facts strip (G-B9): header slugs are not promoted, so they survive the filter above;
  // without definitions the strip still renders, labelled by slug.
  const headerFields = buildHeaderFields(
    HEADER_FIELD_SLUGS.PROJECT,
    project.customFields,
    projectFieldDefs
  );

  // Custom field definitions for tasks (for TaskDetailSheet)
  let taskFieldDefs: FieldDefinitionResponse[] = [];
  let taskFieldGroups: FieldGroupResponse[] = [];
  const taskGroupMembers: Record<string, FieldGroupMemberResponse[]> = {};
  try {
    const [defs, groups] = await Promise.all([getFieldDefinitions("TASK"), getFieldGroups("TASK")]);
    taskFieldDefs = defs.filter((d) => !PROMOTED_TASK_SLUGS.has(d.slug));
    taskFieldGroups = groups;

    // Pre-fetch members for all active TASK field groups
    const activeGroupIds = groups.filter((g) => g.active).map((g) => g.id);
    if (activeGroupIds.length > 0) {
      const memberResults = await Promise.allSettled(
        activeGroupIds.map((gId) => getGroupMembers(gId))
      );
      memberResults.forEach((result, i) => {
        if (result.status === "fulfilled") {
          taskGroupMembers[activeGroupIds[i]] = result.value;
        }
      });
    }
  } catch {
    // Non-fatal: custom fields section won't render in task sheet
  }

  // Saved views for TASK entity type (for ViewSelectorClient in TaskListPanel)
  let savedTaskViews: SavedViewResponse[] = [];
  try {
    savedTaskViews = await getViews("TASK");
  } catch {
    // Non-fatal: view selector won't show saved views
  }

  // Tags for the Tags section
  let projectTags: TagResponse[] = [];
  let allTags: TagResponse[] = [];
  try {
    const [entityTags, orgTags] = await Promise.all([getMatterTags(id), getTags()]);
    projectTags = entityTags;
    allTags = orgTags;
  } catch {
    // Non-fatal: tags section will show empty state
  }

  // Document templates for the "Generate Document" dropdown (only for users who can manage)
  let projectTemplates: TemplateListResponse[] = [];
  if (canManage) {
    try {
      projectTemplates = await getTemplates(undefined, "PROJECT");
    } catch {
      // Non-fatal: hide generate button if template fetch fails
    }
  }

  return {
    setupStatus,
    setupSteps,
    canExecuteAi,
    canReviewGates,
    isAiConfigured,
    documents,
    correspondence,
    members,
    tasks,
    customers,
    projectRequests,
    ficaStatus,
    timeSummary,
    timeSummaryByTask,
    timeSummaryByMember,
    projectBillingRates,
    defaultCurrency,
    projectProfitability,
    currentMemberId,
    projectFieldDefs,
    projectFieldGroups,
    projectGroupMembers,
    headerFields,
    taskFieldDefs,
    taskFieldGroups,
    taskGroupMembers,
    savedTaskViews,
    projectTags,
    allTags,
    projectTemplates,
  };
}
