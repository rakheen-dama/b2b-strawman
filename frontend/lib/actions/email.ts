"use server";

import { api, ApiError } from "@/lib/api";
import type {
  EmailDeliveryLogEntry,
  EmailDeliveryStats,
  DeliveryLogParams,
} from "@/lib/api/email";

interface ActionResult<T = undefined> {
  success: boolean;
  error?: string;
  data?: T;
}

interface PaginatedDeliveryLog {
  content: EmailDeliveryLogEntry[];
  page: {
    totalElements: number;
    totalPages: number;
    size: number;
    number: number;
  };
}

export async function getDeliveryLog(
  params?: DeliveryLogParams
): Promise<ActionResult<PaginatedDeliveryLog>> {
  try {
    const searchParams = new URLSearchParams();
    if (params?.status) searchParams.set("status", params.status);
    if (params?.from) searchParams.set("from", params.from);
    if (params?.to) searchParams.set("to", params.to);
    if (params?.page !== undefined)
      searchParams.set("page", String(params.page));
    if (params?.size !== undefined)
      searchParams.set("size", String(params.size));

    const query = searchParams.toString();
    const data = await api.get<PaginatedDeliveryLog>(
      `/api/email/delivery-log${query ? `?${query}` : ""}`
    );
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function getEmailStats(): Promise<
  ActionResult<EmailDeliveryStats>
> {
  try {
    const data = await api.get<EmailDeliveryStats>("/api/email/stats");
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function sendTestEmail(): Promise<
  ActionResult<EmailDeliveryLogEntry>
> {
  try {
    const data = await api.post<EmailDeliveryLogEntry>("/api/email/test");
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}
