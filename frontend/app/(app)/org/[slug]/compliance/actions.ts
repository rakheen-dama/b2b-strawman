"use server";

import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { ApiError } from "@/lib/api";
import {
  getLifecycleStatusCounts,
  getOnboardingPipelineData,
  getDashboardDataRequests,
  runDormancyCheck as apiRunDormancyCheck,
  transitionLifecycle,
} from "@/lib/compliance-api";
import {
  invokeComplianceAudit,
  getAuditReports,
  getAuditFindings,
  updateFindingStatus,
} from "@/lib/api/compliance-audit";
import { revalidatePath } from "next/cache";
import type { DataRequestResponse } from "@/lib/types";
import type {
  ComplianceAuditInvokeResponse,
  ComplianceAuditReportResponse,
  ComplianceAuditFindingResponse,
  FindingFilters,
  PaginatedResponse,
} from "@/lib/api/compliance-audit";

interface ActionResult {
  success: boolean;
  error?: string;
}

interface DormancyCandidate {
  customerId: string;
  customerName: string;
  lastActivityDate: string | null;
  daysSinceActivity: number;
}

interface DashboardData {
  lifecycleCounts: Record<string, number>;
  onboardingCustomers: Array<{
    id: string;
    name: string;
    lifecycleStatusChangedAt: string | null;
    checklistProgress: { completed: number; total: number };
  }>;
  openDataRequests: {
    total: number;
    urgent: DataRequestResponse[];
  };
}

export async function getComplianceDashboardData(
  orgSlug: string
): Promise<{ success: boolean; data?: DashboardData; error?: string }> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return { success: false, error: "Only admins and owners can view compliance data." };
  }

  try {
    const [lifecycleCounts, onboardingCustomers, urgentRequests] = await Promise.all([
      getLifecycleStatusCounts(),
      getOnboardingPipelineData(),
      getDashboardDataRequests(),
    ]);

    return {
      success: true,
      data: {
        lifecycleCounts,
        onboardingCustomers,
        openDataRequests: {
          total: urgentRequests.length,
          urgent: urgentRequests,
        },
      },
    };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to load compliance dashboard data." };
  }
}

export async function runDormancyCheck(
  orgSlug: string
): Promise<{ success: boolean; candidates?: DormancyCandidate[]; error?: string }> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return { success: false, error: "Only admins and owners can run dormancy checks." };
  }

  try {
    const result = await apiRunDormancyCheck();
    return { success: true, candidates: result.candidates };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to run dormancy check." };
  }
}

export async function markCustomerDormant(customerId: string, slug: string): Promise<ActionResult> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return { success: false, error: "Only admins and owners can mark customers as dormant." };
  }

  try {
    await transitionLifecycle(customerId, "DORMANT", "Marked dormant via compliance dashboard");
    revalidatePath(`/org/${slug}/compliance`);
    return { success: true };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to mark customer as dormant." };
  }
}

// ---- Compliance Audit Actions ----

export async function invokeComplianceAuditAction(
  slug: string
): Promise<{ success: boolean; error?: string; data?: ComplianceAuditInvokeResponse }> {
  const caps = await fetchMyCapabilities();
  if (!caps.capabilities.includes("AI_EXECUTE")) {
    return { success: false, error: "You do not have permission to invoke AI skills." };
  }

  try {
    const result = await invokeComplianceAudit();
    revalidatePath(`/org/${slug}/compliance`);
    if (result.status === "FAILED") {
      return {
        success: false,
        error: "Compliance audit failed. Check execution history for details.",
      };
    }
    return { success: true, data: result };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "AI budget exhausted or skill not permitted. Check AI settings.",
        };
      }
      if (error.status === 409) {
        return {
          success: false,
          error: "A compliance audit is already in progress. Please wait for it to complete.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred. Please try again." };
  }
}

export async function fetchAuditReportsAction(
  slug: string,
  page?: number,
  size?: number
): Promise<{
  success: boolean;
  error?: string;
  data?: PaginatedResponse<ComplianceAuditReportResponse>;
}> {
  try {
    const data = await getAuditReports(page, size);
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to load audit reports." };
  }
}

export async function fetchAuditFindingsAction(
  slug: string,
  reportId: string,
  filters?: FindingFilters,
  page?: number,
  size?: number
): Promise<{
  success: boolean;
  error?: string;
  data?: PaginatedResponse<ComplianceAuditFindingResponse>;
}> {
  try {
    const data = await getAuditFindings(reportId, filters, page, size);
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to load audit findings." };
  }
}

export async function acknowledgeFindingAction(
  slug: string,
  reportId: string,
  findingId: string
): Promise<ActionResult> {
  const caps = await fetchMyCapabilities();
  if (!caps.capabilities.includes("AI_REVIEW")) {
    return { success: false, error: "You do not have permission to review findings." };
  }

  try {
    await updateFindingStatus(reportId, findingId, "ACKNOWLEDGED");
    revalidatePath(`/org/${slug}/compliance`);
    return { success: true };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to acknowledge finding." };
  }
}

export async function startProgressAction(
  slug: string,
  reportId: string,
  findingId: string
): Promise<ActionResult> {
  const caps = await fetchMyCapabilities();
  if (!caps.capabilities.includes("AI_REVIEW")) {
    return { success: false, error: "You do not have permission to review findings." };
  }

  try {
    await updateFindingStatus(reportId, findingId, "IN_PROGRESS");
    revalidatePath(`/org/${slug}/compliance`);
    return { success: true };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to start progress on finding." };
  }
}

export async function resolveFindingAction(
  slug: string,
  reportId: string,
  findingId: string,
  notes: string
): Promise<ActionResult> {
  const caps = await fetchMyCapabilities();
  if (!caps.capabilities.includes("AI_REVIEW")) {
    return { success: false, error: "You do not have permission to review findings." };
  }

  try {
    await updateFindingStatus(reportId, findingId, "RESOLVED", notes);
    revalidatePath(`/org/${slug}/compliance`);
    return { success: true };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to resolve finding." };
  }
}

export async function markFalsePositiveAction(
  slug: string,
  reportId: string,
  findingId: string,
  notes: string
): Promise<ActionResult> {
  const caps = await fetchMyCapabilities();
  if (!caps.capabilities.includes("AI_REVIEW")) {
    return { success: false, error: "You do not have permission to review findings." };
  }

  try {
    await updateFindingStatus(reportId, findingId, "FALSE_POSITIVE", notes);
    revalidatePath(`/org/${slug}/compliance`);
    return { success: true };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to mark finding as false positive." };
  }
}
