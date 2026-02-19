import "server-only";

import { api } from "@/lib/api";
import type {
  ChecklistInstanceResponse,
  ChecklistInstanceItemResponse,
  ChecklistTemplateResponse,
} from "@/lib/types";

export async function getCustomerChecklists(
  customerId: string,
): Promise<ChecklistInstanceResponse[]> {
  return api.get<ChecklistInstanceResponse[]>(`/api/customers/${customerId}/checklists`);
}

export async function getChecklistInstance(id: string): Promise<ChecklistInstanceResponse> {
  return api.get<ChecklistInstanceResponse>(`/api/checklist-instances/${id}`);
}

export async function completeItem(
  id: string,
  body: { notes?: string; documentId?: string },
): Promise<ChecklistInstanceItemResponse> {
  return api.put<ChecklistInstanceItemResponse>(`/api/checklist-items/${id}/complete`, body);
}

export async function skipItem(
  id: string,
  body: { reason: string },
): Promise<ChecklistInstanceItemResponse> {
  return api.put<ChecklistInstanceItemResponse>(`/api/checklist-items/${id}/skip`, body);
}

export async function reopenItem(id: string): Promise<ChecklistInstanceItemResponse> {
  return api.put<ChecklistInstanceItemResponse>(`/api/checklist-items/${id}/reopen`, {});
}

export async function instantiateChecklist(
  customerId: string,
  templateId: string,
): Promise<ChecklistInstanceResponse> {
  return api.post<ChecklistInstanceResponse>(`/api/customers/${customerId}/checklists`, {
    templateId,
  });
}

export async function getChecklistTemplates(
  customerType?: string,
): Promise<ChecklistTemplateResponse[]> {
  const params = customerType ? `?customerType=${customerType}` : "";
  return api.get<ChecklistTemplateResponse[]>(`/api/checklist-templates${params}`);
}
