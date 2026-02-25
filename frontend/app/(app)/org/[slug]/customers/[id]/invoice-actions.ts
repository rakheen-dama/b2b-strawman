"use server";

import { getAuthContext } from "@/lib/auth";
import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type {
  UnbilledTimeResponse,
  InvoiceResponse,
  CreateInvoiceDraftRequest,
  ValidationCheck,
} from "@/lib/types";

interface CreateDraftResult {
  success: boolean;
  error?: string;
  invoice?: InvoiceResponse;
}

interface UnbilledTimeResult {
  success: boolean;
  error?: string;
  data?: UnbilledTimeResponse;
}

export async function fetchUnbilledTime(
  customerId: string,
  from?: string,
  to?: string,
): Promise<UnbilledTimeResult> {
  const { orgRole } = await getAuthContext();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can view unbilled time." };
  }

  try {
    const params = new URLSearchParams();
    if (from) params.set("from", from);
    if (to) params.set("to", to);
    const qs = params.toString();
    const url = `/api/customers/${customerId}/unbilled-time${qs ? `?${qs}` : ""}`;
    const data = await api.get<UnbilledTimeResponse>(url);
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to fetch unbilled time." };
  }
}

interface ValidationResult {
  success: boolean;
  error?: string;
  checks?: ValidationCheck[];
}

export async function validateInvoiceGeneration(
  customerId: string,
  timeEntryIds: string[],
  templateId?: string,
): Promise<ValidationResult> {
  try {
    const checks = await api.post<ValidationCheck[]>(
      "/api/invoices/validate-generation",
      { customerId, timeEntryIds, templateId: templateId ?? null },
    );
    return { success: true, checks };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to validate invoice generation." };
  }
}

export async function createInvoiceDraft(
  slug: string,
  customerId: string,
  request: CreateInvoiceDraftRequest,
): Promise<CreateDraftResult> {
  const { orgRole } = await getAuthContext();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can create invoices." };
  }

  try {
    const invoice = await api.post<InvoiceResponse>("/api/invoices", request);
    revalidatePath(`/org/${slug}/customers/${customerId}`);
    return { success: true, invoice };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to create invoice draft." };
  }
}

