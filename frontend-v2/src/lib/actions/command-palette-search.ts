"use server";

import { api } from "@/lib/api";
import type { Project, Customer, Task } from "@/lib/types";
import type { InvoiceResponse } from "@/lib/types";

export interface CommandSearchResult {
  id: string;
  label: string;
  type: "project" | "customer" | "invoice" | "task";
  subtitle?: string;
}

interface PaginatedResponse<T> {
  content: T[];
  page: { totalElements: number; totalPages: number; size: number; number: number };
}

/**
 * Server action to search across projects, customers, invoices, and tasks.
 * Called from the command palette with debounced input.
 */
export async function searchAll(query: string): Promise<CommandSearchResult[]> {
  if (!query || query.trim().length < 2) return [];

  const encoded = encodeURIComponent(query.trim());

  const [projects, customers, invoices, tasks] = await Promise.allSettled([
    api.get<PaginatedResponse<Project>>(`/api/projects?search=${encoded}&size=5`),
    api.get<PaginatedResponse<Customer>>(`/api/customers?search=${encoded}&size=5`),
    api.get<PaginatedResponse<InvoiceResponse>>(`/api/invoices?search=${encoded}&size=5`),
    api.get<PaginatedResponse<Task>>(`/api/tasks?search=${encoded}&size=5`),
  ]);

  const results: CommandSearchResult[] = [];

  if (projects.status === "fulfilled" && projects.value?.content) {
    for (const p of projects.value.content) {
      results.push({ id: p.id, label: p.name, type: "project", subtitle: p.status });
    }
  }

  if (customers.status === "fulfilled" && customers.value?.content) {
    for (const c of customers.value.content) {
      results.push({ id: c.id, label: c.name, type: "customer", subtitle: c.email });
    }
  }

  if (invoices.status === "fulfilled" && invoices.value?.content) {
    for (const inv of invoices.value.content) {
      results.push({
        id: inv.id,
        label: inv.invoiceNumber || `Invoice #${inv.id.slice(0, 8)}`,
        type: "invoice",
        subtitle: inv.status,
      });
    }
  }

  if (tasks.status === "fulfilled" && tasks.value?.content) {
    for (const t of tasks.value.content) {
      results.push({ id: t.id, label: t.title, type: "task", subtitle: t.status });
    }
  }

  return results;
}
