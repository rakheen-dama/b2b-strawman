"use server";

import { ApiError } from "@/lib/api";
import {
  getSyncEntries,
  getSyncSummary,
  getInvoiceSyncStatus,
  retrySyncEntry,
  reconcileSyncEntry,
  forceResyncInvoice,
} from "@/lib/api/integrations";
import { revalidatePath } from "next/cache";
import type { SyncEntryResponse, SyncSummaryResponse } from "@/lib/types";

interface ActionResult<T = undefined> {
  success: boolean;
  error?: string;
  data?: T;
}

export async function fetchSyncEntriesAction(
  slug: string,
  params?: {
    state?: string;
    entityType?: string;
    direction?: string;
    page?: number;
    size?: number;
  }
): Promise<
  ActionResult<{
    content: SyncEntryResponse[];
    page: { totalElements: number; totalPages: number; size: number; number: number };
  }>
> {
  try {
    const data = await getSyncEntries(params);
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function fetchSyncSummaryAction(
  _slug: string
): Promise<ActionResult<SyncSummaryResponse>> {
  try {
    const data = await getSyncSummary();
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function getInvoiceSyncStatusAction(
  _slug: string,
  entityId: string,
  entityType: "INVOICE" | "CUSTOMER"
): Promise<ActionResult<SyncEntryResponse>> {
  try {
    // For invoices, use the dedicated endpoint. For customers, query entries.
    if (entityType === "INVOICE") {
      const data = await getInvoiceSyncStatus(entityId);
      return { success: true, data };
    }
    // For customers, query sync entries filtered by entity
    const result = await getSyncEntries({
      entityType: "CUSTOMER",
      page: 0,
      size: 1,
    });
    const match = result.content.find((e) => e.entityId === entityId && e.state === "COMPLETED");
    if (match) {
      return { success: true, data: match };
    }
    return { success: false, error: "No sync entry found." };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function retrySyncEntryAction(
  slug: string,
  entryId: string
): Promise<ActionResult> {
  try {
    await retrySyncEntry(entryId);
    revalidatePath(`/org/${slug}/settings/integrations/xero/sync-log`);
    return { success: true };
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

export async function reconcileSyncEntryAction(
  slug: string,
  entryId: string
): Promise<ActionResult> {
  try {
    await reconcileSyncEntry(entryId);
    revalidatePath(`/org/${slug}/settings/integrations/xero/sync-log`);
    return { success: true };
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

export async function forceResyncInvoiceAction(
  slug: string,
  invoiceId: string
): Promise<ActionResult> {
  try {
    await forceResyncInvoice(invoiceId);
    revalidatePath(`/org/${slug}/settings/integrations/xero/sync-log`);
    return { success: true };
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
