"use server";

import { api, handleApiError } from "@/lib/api";
import type {
  Customer,
  CreateCustomerRequest,
  UpdateCustomerRequest,
  TransitionResponse,
} from "@/lib/types";

export async function getCustomers(
  lifecycleStatus?: string
): Promise<Customer[]> {
  try {
    const params = lifecycleStatus
      ? `?lifecycleStatus=${lifecycleStatus}`
      : "";
    return await api.get<Customer[]>(`/api/customers${params}`);
  } catch (error) {
    handleApiError(error);
  }
}

export async function getLifecycleSummary(): Promise<Record<string, number>> {
  try {
    return await api.get<Record<string, number>>(
      "/api/customers/lifecycle-summary"
    );
  } catch (error) {
    handleApiError(error);
  }
}

export async function getCustomer(id: string): Promise<Customer> {
  try {
    return await api.get<Customer>(`/api/customers/${id}`);
  } catch (error) {
    handleApiError(error);
  }
}

export async function createCustomer(
  data: CreateCustomerRequest
): Promise<Customer> {
  try {
    return await api.post<Customer>("/api/customers", data);
  } catch (error) {
    handleApiError(error);
  }
}

export async function updateCustomer(
  id: string,
  data: UpdateCustomerRequest
): Promise<Customer> {
  try {
    return await api.put<Customer>(`/api/customers/${id}`, data);
  } catch (error) {
    handleApiError(error);
  }
}

export async function archiveCustomer(id: string): Promise<Customer> {
  try {
    return await api.delete<Customer>(`/api/customers/${id}`);
  } catch (error) {
    handleApiError(error);
  }
}

export async function unarchiveCustomer(id: string): Promise<Customer> {
  try {
    return await api.post<Customer>(`/api/customers/${id}/unarchive`);
  } catch (error) {
    handleApiError(error);
  }
}

export async function transitionLifecycle(
  id: string,
  targetStatus: string,
  notes?: string
): Promise<TransitionResponse> {
  try {
    return await api.post<TransitionResponse>(
      `/api/customers/${id}/transition`,
      { targetStatus, notes }
    );
  } catch (error) {
    handleApiError(error);
  }
}

export async function getLifecycleHistory(id: string) {
  try {
    return await api.get(`/api/customers/${id}/lifecycle`);
  } catch (error) {
    handleApiError(error);
  }
}

export async function getCustomerProjects(id: string) {
  try {
    return await api.get(`/api/customers/${id}/projects`);
  } catch (error) {
    handleApiError(error);
  }
}
