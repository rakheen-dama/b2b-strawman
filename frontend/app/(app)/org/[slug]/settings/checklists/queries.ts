import "server-only";

import { api } from "@/lib/api";
import type { ChecklistTemplateResponse } from "@/lib/types";

export async function getChecklistTemplateDetail(id: string) {
  return api.get<ChecklistTemplateResponse>(`/api/checklist-templates/${id}`);
}
