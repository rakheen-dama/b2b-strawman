"use server";

import { getAuthContext } from "@/lib/auth";
import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type { Customer, CustomerType, UpdateCustomerRequest, CompletenessScore, AggregatedCompletenessResponse } from "@/lib/types";

interface ActionResult {
  success: boolean;
  error?: string;
}

interface CreateCustomerData {
  name: string;
  email: string;
  phone?: string;
  idNumber?: string;
  notes?: string;
  customerType?: string;
  customFields?: Record<string, unknown>;
}

export async function createCustomer(slug: string, data: CreateCustomerData): Promise<ActionResult> {
  const { orgRole } = await getAuthContext();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can manage customers." };
  }

  const { name, email, phone, idNumber, notes, customerType, customFields } = data;

  if (!name?.trim()) {
    return { success: false, error: "Customer name is required." };
  }
  if (!email?.trim()) {
    return { success: false, error: "Customer email is required." };
  }

  const body = {
    name: name.trim(),
    email: email.trim(),
    phone,
    idNumber,
    notes,
    customerType,
    customFields: customFields ?? {},
  };

  try {
    await api.post<Customer>("/api/customers", body);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/customers`);
  revalidatePath(`/org/${slug}/dashboard`);

  return { success: true };
}

export async function updateCustomer(
  slug: string,
  id: string,
  formData: FormData
): Promise<ActionResult> {
  const { orgRole } = await getAuthContext();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can manage customers." };
  }

  const name = formData.get("name")?.toString().trim() ?? "";
  const email = formData.get("email")?.toString().trim() ?? "";
  const phone = formData.get("phone")?.toString().trim() || undefined;
  const idNumber = formData.get("idNumber")?.toString().trim() || undefined;
  const notes = formData.get("notes")?.toString().trim() || undefined;
  const customerType = (formData.get("customerType")?.toString() as CustomerType) || undefined;

  if (!name) {
    return { success: false, error: "Customer name is required." };
  }
  if (!email) {
    return { success: false, error: "Customer email is required." };
  }

  const body: UpdateCustomerRequest = { name, email, phone, idNumber, notes, customerType };

  try {
    await api.put<Customer>(`/api/customers/${id}`, body);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/customers`);
  revalidatePath(`/org/${slug}/customers/${id}`);
  revalidatePath(`/org/${slug}/dashboard`);

  return { success: true };
}

export async function fetchCompletenessSummary(
  customerIds: string[]
): Promise<Record<string, CompletenessScore>> {
  if (customerIds.length === 0) {
    return {};
  }
  const params = new URLSearchParams();
  customerIds.forEach((id) => params.append("customerIds", id));
  return api.get<Record<string, CompletenessScore>>(
    `/api/customers/completeness-summary?${params.toString()}`
  );
}

export async function fetchAggregatedCompleteness(): Promise<AggregatedCompletenessResponse> {
  return api.get<AggregatedCompletenessResponse>(
    "/api/customers/completeness-summary/aggregated"
  );
}

export async function archiveCustomer(slug: string, id: string): Promise<ActionResult> {
  const { orgRole } = await getAuthContext();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can manage customers." };
  }

  try {
    await api.delete(`/api/customers/${id}`);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/customers`);
  revalidatePath(`/org/${slug}/dashboard`);

  return { success: true };
}
