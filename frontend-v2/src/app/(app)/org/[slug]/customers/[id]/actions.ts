"use server";

import { api, handleApiError } from "@/lib/api";
import type {
  Customer,
  ChecklistInstanceResponse,
  LifecycleHistoryEntry,
  InvoiceResponse,
  Document,
} from "@/lib/types";

interface LinkedProject {
  id: string;
  name: string;
  description: string | null;
  createdAt: string;
}

export async function getCustomer(id: string): Promise<Customer> {
  try {
    return await api.get<Customer>(`/api/customers/${id}`);
  } catch (error) {
    handleApiError(error);
  }
}

export async function getCustomerProjects(
  id: string
): Promise<LinkedProject[]> {
  try {
    return await api.get<LinkedProject[]>(`/api/customers/${id}/projects`);
  } catch (error) {
    handleApiError(error);
  }
}

export async function getLifecycleHistory(
  id: string
): Promise<LifecycleHistoryEntry[]> {
  try {
    return await api.get<LifecycleHistoryEntry[]>(
      `/api/customers/${id}/lifecycle`
    );
  } catch (error) {
    handleApiError(error);
  }
}

export async function getCustomerChecklist(
  id: string
): Promise<ChecklistInstanceResponse | null> {
  try {
    const instances = await api.get<ChecklistInstanceResponse[]>(
      `/api/customers/${id}/checklists`
    );
    // Return the most recent in-progress or first checklist
    return (
      instances.find((i) => i.status === "IN_PROGRESS") ??
      instances[0] ??
      null
    );
  } catch {
    // Checklist may not exist for this customer
    return null;
  }
}

export async function getCustomerInvoices(
  id: string
): Promise<InvoiceResponse[]> {
  try {
    return await api.get<InvoiceResponse[]>(
      `/api/invoices?customerId=${id}`
    );
  } catch {
    return [];
  }
}

export async function getCustomerDocuments(
  id: string
): Promise<Document[]> {
  try {
    return await api.get<Document[]>(
      `/api/documents?scope=CUSTOMER&customerId=${id}`
    );
  } catch {
    return [];
  }
}

import type { PaginatedProposals } from "@/app/(app)/org/[slug]/proposals/proposal-actions";
import { listCustomerProposals } from "@/app/(app)/org/[slug]/proposals/proposal-actions";

export async function getCustomerProposals(
  id: string,
): Promise<PaginatedProposals> {
  try {
    return await listCustomerProposals(id, 0, 200);
  } catch {
    return {
      content: [],
      page: { number: 0, size: 200, totalElements: 0, totalPages: 0 },
    };
  }
}
