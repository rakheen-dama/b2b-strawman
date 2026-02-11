"use server";

import { auth } from "@clerk/nextjs/server";
import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type { Customer, CreateCustomerRequest, UpdateCustomerRequest } from "@/lib/types";

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function createCustomer(slug: string, formData: FormData): Promise<ActionResult> {
  const { orgRole } = await auth();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can manage customers." };
  }

  const name = formData.get("name")?.toString().trim() ?? "";
  const email = formData.get("email")?.toString().trim() ?? "";
  const phone = formData.get("phone")?.toString().trim() || undefined;
  const idNumber = formData.get("idNumber")?.toString().trim() || undefined;
  const notes = formData.get("notes")?.toString().trim() || undefined;

  if (!name) {
    return { success: false, error: "Customer name is required." };
  }
  if (!email) {
    return { success: false, error: "Customer email is required." };
  }

  const body: CreateCustomerRequest = { name, email, phone, idNumber, notes };

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
  const { orgRole } = await auth();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can manage customers." };
  }

  const name = formData.get("name")?.toString().trim() ?? "";
  const email = formData.get("email")?.toString().trim() ?? "";
  const phone = formData.get("phone")?.toString().trim() || undefined;
  const idNumber = formData.get("idNumber")?.toString().trim() || undefined;
  const notes = formData.get("notes")?.toString().trim() || undefined;

  if (!name) {
    return { success: false, error: "Customer name is required." };
  }
  if (!email) {
    return { success: false, error: "Customer email is required." };
  }

  const body: UpdateCustomerRequest = { name, email, phone, idNumber, notes };

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

export async function archiveCustomer(slug: string, id: string): Promise<ActionResult> {
  const { orgRole } = await auth();
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
