"use server";

import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type { Customer, CustomerType, UpdateCustomerRequest } from "@/lib/types";
import { classifyError } from "@/lib/error-handler";
import { createMessages } from "@/lib/messages";

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
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
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
    const message =
      error instanceof ApiError
        ? error.message
        : createMessages("errors").t(classifyError(error).messageCode);
    return { success: false, error: message };
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
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
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
    const message =
      error instanceof ApiError
        ? error.message
        : createMessages("errors").t(classifyError(error).messageCode);
    return { success: false, error: message };
  }

  revalidatePath(`/org/${slug}/customers`);
  revalidatePath(`/org/${slug}/customers/${id}`);
  revalidatePath(`/org/${slug}/dashboard`);

  return { success: true };
}

export async function archiveCustomer(slug: string, id: string): Promise<ActionResult> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return { success: false, error: "Only admins and owners can manage customers." };
  }

  try {
    await api.delete(`/api/customers/${id}`);
  } catch (error) {
    const message =
      error instanceof ApiError
        ? error.message
        : createMessages("errors").t(classifyError(error).messageCode);
    return { success: false, error: message };
  }

  revalidatePath(`/org/${slug}/customers`);
  revalidatePath(`/org/${slug}/dashboard`);

  return { success: true };
}
