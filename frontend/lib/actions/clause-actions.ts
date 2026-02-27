"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";

export type ClauseSource = "SYSTEM" | "CLONED" | "CUSTOM";

export interface Clause {
  id: string;
  title: string;
  slug: string;
  description: string | null;
  body: string;
  category: string;
  source: ClauseSource;
  sourceClauseId: string | null;
  packId: string | null;
  active: boolean;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateClauseData {
  title: string;
  description?: string;
  body: string;
  category: string;
}

export interface UpdateClauseData {
  title: string;
  description?: string;
  body: string;
  category: string;
}

export interface ActionResult {
  success: boolean;
  error?: string;
}

export async function getClauses(
  includeInactive?: boolean,
  category?: string,
): Promise<Clause[]> {
  const params = new URLSearchParams();
  if (includeInactive) params.set("includeInactive", "true");
  if (category) params.set("category", category);
  const query = params.toString();
  return api.get<Clause[]>(`/api/clauses${query ? `?${query}` : ""}`);
}

export async function getClause(id: string): Promise<Clause> {
  return api.get<Clause>(`/api/clauses/${id}`);
}

export async function createClause(
  slug: string,
  data: CreateClauseData,
): Promise<ActionResult> {
  try {
    await api.post("/api/clauses", data);
    revalidatePath(`/org/${slug}/settings/clauses`);
    return { success: true };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to create clauses.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function updateClause(
  slug: string,
  id: string,
  data: UpdateClauseData,
): Promise<ActionResult> {
  try {
    await api.put(`/api/clauses/${id}`, data);
    revalidatePath(`/org/${slug}/settings/clauses`);
    return { success: true };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to update clauses.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function deleteClause(
  slug: string,
  id: string,
): Promise<ActionResult> {
  try {
    await api.delete(`/api/clauses/${id}`);
    revalidatePath(`/org/${slug}/settings/clauses`);
    return { success: true };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to delete clauses.",
        };
      }
      if (error.status === 409) {
        return {
          success: false,
          error: "This clause is referenced by templates and cannot be deleted.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function deactivateClause(
  slug: string,
  id: string,
): Promise<ActionResult> {
  try {
    await api.post(`/api/clauses/${id}/deactivate`);
    revalidatePath(`/org/${slug}/settings/clauses`);
    return { success: true };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to deactivate clauses.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function cloneClause(
  slug: string,
  id: string,
): Promise<ActionResult> {
  try {
    await api.post(`/api/clauses/${id}/clone`);
    revalidatePath(`/org/${slug}/settings/clauses`);
    return { success: true };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to clone clauses.",
        };
      }
      if (error.status === 409) {
        return {
          success: false,
          error: "A clone of this clause already exists.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function getClauseCategories(): Promise<string[]> {
  return api.get<string[]>("/api/clauses/categories");
}
