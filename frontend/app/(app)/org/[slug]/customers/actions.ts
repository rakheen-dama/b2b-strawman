"use server";

import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type { Customer, UpdateCustomerRequest } from "@/lib/types";
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
  // Promoted customer fields (Epic 463)
  addressLine1?: string;
  addressLine2?: string;
  city?: string;
  stateProvince?: string;
  postalCode?: string;
  country?: string;
  taxNumber?: string;
  contactName?: string;
  contactEmail?: string;
  contactPhone?: string;
  registrationNumber?: string;
  entityType?: string;
  financialYearEnd?: string;
}

export interface UpdateCustomerData {
  name: string;
  email: string;
  phone?: string;
  idNumber?: string;
  notes?: string;
  customerType?: string;
  // Promoted customer fields (Epic 463)
  addressLine1?: string;
  addressLine2?: string;
  city?: string;
  stateProvince?: string;
  postalCode?: string;
  country?: string;
  taxNumber?: string;
  contactName?: string;
  contactEmail?: string;
  contactPhone?: string;
  registrationNumber?: string;
  entityType?: string;
  financialYearEnd?: string;
}

export async function createCustomer(slug: string, data: CreateCustomerData): Promise<ActionResult> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return { success: false, error: "Only admins and owners can manage customers." };
  }

  const {
    name,
    email,
    phone,
    idNumber,
    notes,
    customerType,
    customFields,
    addressLine1,
    addressLine2,
    city,
    stateProvince,
    postalCode,
    country,
    taxNumber,
    contactName,
    contactEmail,
    contactPhone,
    registrationNumber,
    entityType,
    financialYearEnd,
  } = data;

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
    addressLine1,
    addressLine2,
    city,
    stateProvince,
    postalCode,
    country,
    taxNumber,
    contactName,
    contactEmail,
    contactPhone,
    registrationNumber,
    entityType,
    financialYearEnd,
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
  data: UpdateCustomerData
): Promise<ActionResult> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return { success: false, error: "Only admins and owners can manage customers." };
  }

  const name = data.name?.trim() ?? "";
  const email = data.email?.trim() ?? "";

  if (!name) {
    return { success: false, error: "Customer name is required." };
  }
  if (!email) {
    return { success: false, error: "Customer email is required." };
  }

  const body: UpdateCustomerRequest = {
    name,
    email,
    phone: data.phone?.trim() || undefined,
    idNumber: data.idNumber?.trim() || undefined,
    notes: data.notes?.trim() || undefined,
    customerType: (data.customerType as UpdateCustomerRequest["customerType"]) || undefined,
    addressLine1: data.addressLine1?.trim() || undefined,
    addressLine2: data.addressLine2?.trim() || undefined,
    city: data.city?.trim() || undefined,
    stateProvince: data.stateProvince?.trim() || undefined,
    postalCode: data.postalCode?.trim() || undefined,
    country: data.country?.trim() || undefined,
    taxNumber: data.taxNumber?.trim() || undefined,
    contactName: data.contactName?.trim() || undefined,
    contactEmail: data.contactEmail?.trim() || undefined,
    contactPhone: data.contactPhone?.trim() || undefined,
    registrationNumber: data.registrationNumber?.trim() || undefined,
    entityType: data.entityType || undefined,
    financialYearEnd: data.financialYearEnd || undefined,
  };

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
