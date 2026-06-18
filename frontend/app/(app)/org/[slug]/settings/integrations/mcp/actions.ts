"use server";

import { ApiError } from "@/lib/api";
import { enableMcp, revokeMcp, getMcpStatus } from "@/lib/api/mcp";
import { revalidatePath } from "next/cache";
import type { McpStatus } from "@/lib/types";

interface ActionResult<T = undefined> {
  success: boolean;
  error?: string;
  data?: T;
}

/**
 * Enables the MCP connector behind a recorded POPIA consent. The backend
 * records the GRANTED consent first, then enables the integration atomically
 * (204 No Content). Re-fetches status to return the fresh effective state and
 * revalidates the settings routes.
 */
export async function enableMcpAction(
  consentVersion: string,
  slug?: string
): Promise<ActionResult<McpStatus>> {
  try {
    await enableMcp(consentVersion);
    if (slug) {
      revalidatePath(`/org/${slug}/settings/integrations/mcp`);
      revalidatePath(`/org/${slug}/settings/integrations`);
    }
    const status = await getMcpStatus();
    return { success: true, data: status };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return { success: false, error: "Permission denied." };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

/**
 * Revokes the MCP connector — disables the integration and appends a REVOKED
 * consent row (204 No Content). Re-fetches status and revalidates.
 */
export async function revokeMcpAction(slug?: string): Promise<ActionResult<McpStatus>> {
  try {
    await revokeMcp();
    if (slug) {
      revalidatePath(`/org/${slug}/settings/integrations/mcp`);
      revalidatePath(`/org/${slug}/settings/integrations`);
    }
    const status = await getMcpStatus();
    return { success: true, data: status };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return { success: false, error: "Permission denied." };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

/**
 * Returns the current effective MCP connector state + consent metadata, or
 * null on any error (mirrors how the Xero page treats a missing connection).
 */
export async function getMcpStatusAction(): Promise<McpStatus | null> {
  try {
    return await getMcpStatus();
  } catch {
    return null;
  }
}
