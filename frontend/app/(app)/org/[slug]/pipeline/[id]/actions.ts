"use server";

import { revalidatePath } from "next/cache";
import { getAuthContext } from "@/lib/auth";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { ApiError } from "@/lib/api";
import {
  createDealProposal,
  linkDealProposal,
  type CreateDealProposalRequest,
  type LinkedProposalDto,
} from "@/lib/api/crm";

export interface ProposalActionResult {
  success: boolean;
  error?: string;
  status?: number;
  proposal?: LinkedProposalDto;
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

/**
 * Creates (and optionally sends) a proposal attached to a deal. Mirrors the
 * shape of the pipeline `transitionDealAction`: capability-gated, revalidates
 * the deal detail route, and maps {@link ApiError} to a friendly result.
 */
export async function createDealProposalAction(
  slug: string,
  dealId: string,
  req: CreateDealProposalRequest
): Promise<ProposalActionResult> {
  const denied = await requireManageDeals(slug);
  if (denied) return { success: false, error: denied };

  try {
    const proposal = await createDealProposal(dealId, req);
    revalidatePath(`/org/${slug}/pipeline/${dealId}`);
    return { success: true, proposal };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message, status: error.status };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

/** Links an existing proposal to a deal. */
export async function linkDealProposalAction(
  slug: string,
  dealId: string,
  proposalId: string
): Promise<ProposalActionResult> {
  const denied = await requireManageDeals(slug);
  if (denied) return { success: false, error: denied };

  try {
    await linkDealProposal(dealId, proposalId);
    revalidatePath(`/org/${slug}/pipeline/${dealId}`);
    return { success: true };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message, status: error.status };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}
