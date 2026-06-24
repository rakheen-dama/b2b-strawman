"use server";

import { revalidatePath } from "next/cache";
import { getAuthContext } from "@/lib/auth";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { ApiError } from "@/lib/api";
import {
  intakeDeal,
  createDeal,
  transitionDeal,
  type IntakeRequest,
  type CreateDealRequest,
  type TransitionRequest,
  type DealResponse,
} from "@/lib/api/crm";

export interface DealActionResult {
  success: boolean;
  error?: string;
  status?: number;
  deal?: DealResponse;
}

async function requireManageDeals(slug: string): Promise<string | null> {
  const { orgSlug } = await getAuthContext();
  if (slug !== orgSlug) return "Organization mismatch.";
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner && !caps.capabilities.includes("MANAGE_DEALS")) {
    return "You do not have permission to manage deals.";
  }
  return null;
}

export async function intakeDealAction(
  slug: string,
  req: IntakeRequest
): Promise<DealActionResult> {
  const denied = await requireManageDeals(slug);
  if (denied) return { success: false, error: denied };

  try {
    const deal = await intakeDeal(req);
    revalidatePath(`/org/${slug}/pipeline`);
    return { success: true, deal };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message, status: error.status };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function createDealAction(
  slug: string,
  req: CreateDealRequest
): Promise<DealActionResult> {
  const denied = await requireManageDeals(slug);
  if (denied) return { success: false, error: denied };

  try {
    const deal = await createDeal(req);
    revalidatePath(`/org/${slug}/pipeline`);
    return { success: true, deal };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message, status: error.status };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function transitionDealAction(
  slug: string,
  dealId: string,
  req: TransitionRequest
): Promise<DealActionResult> {
  const denied = await requireManageDeals(slug);
  if (denied) return { success: false, error: denied };

  try {
    const deal = await transitionDeal(dealId, req);
    revalidatePath(`/org/${slug}/pipeline`);
    return { success: true, deal };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message, status: error.status };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}
