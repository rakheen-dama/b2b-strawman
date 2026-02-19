"use server";

import { auth } from "@clerk/nextjs/server";
import { ApiError } from "@/lib/api";
import {
  getLifecycleStatusCounts,
  getOnboardingPipelineData,
  getDashboardDataRequests,
  runDormancyCheck as apiRunDormancyCheck,
  transitionLifecycle,
} from "@/lib/compliance-api";
import { revalidatePath } from "next/cache";
import type { DataRequestResponse } from "@/lib/types";

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
  orgSlug: string,
): Promise<{ success: boolean; data?: DashboardData; error?: string }> {
  const { orgRole } = await auth();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
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
  orgSlug: string,
): Promise<{ success: boolean; candidates?: DormancyCandidate[]; error?: string }> {
  const { orgRole } = await auth();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
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

export async function markCustomerDormant(
  customerId: string,
  slug: string,
): Promise<ActionResult> {
  const { orgRole } = await auth();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
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
