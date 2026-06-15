import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { getAiProfile } from "@/lib/api/ai";
import { api } from "@/lib/api";
import { getProjectTemplates } from "@/lib/api/templates";
import type { ProjectTemplateResponse } from "@/lib/api/templates";
import type { Customer } from "@/lib/types";
import { NewMatterPageClient } from "./new-matter-page-client";

/**
 * `/org/[slug]/projects/new` route.
 *
 * Full matter intake page with project creation form alongside the AI intake panel.
 * Deep-links from "New Matter for this client" on customer detail pages navigate here
 * with `?customerId=...` to pre-select the customer.
 */
export default async function NewProjectPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const { slug } = await params;
  const resolvedSearchParams = await searchParams;

  const initialCustomerId =
    typeof resolvedSearchParams.customerId === "string" ? resolvedSearchParams.customerId : "";

  // Fetch capabilities
  const caps = await fetchMyCapabilities();
  const canExecuteAi = caps.capabilities.includes("AI_EXECUTE");
  const canReviewGates = caps.capabilities.includes("AI_REVIEW");

  // Check AI configuration
  let isAiConfigured = false;
  if (caps.capabilities.includes("AI_MANAGE")) {
    // OWNER/ADMIN can read the AI profile directly (GET /api/ai/profile is AI_MANAGE-gated).
    try {
      const profile = await getAiProfile();
      isAiConfigured = profile.coldStartCompleted;
    } catch {
      // Non-fatal: panel will show "not configured" tooltip
    }
  } else if (canExecuteAi) {
    // MEMBER with AI_EXECUTE: they wouldn't have this capability without setup being done.
    // Calling getAiProfile() here would 403 and falsely show a "not configured" disabled state.
    isAiConfigured = true;
  }

  // Fetch customers and templates
  let customers: Customer[] = [];
  let templates: ProjectTemplateResponse[] = [];
  try {
    customers = await api.get<Customer[]>("/api/customers?status=ACTIVE");
  } catch {
    // Non-fatal: customer select will be empty
  }
  try {
    templates = await getProjectTemplates();
  } catch {
    // Non-fatal: template select will be empty
  }

  return (
    <NewMatterPageClient
      slug={slug}
      initialCustomerId={initialCustomerId}
      customers={customers}
      templates={templates}
      canExecuteAi={canExecuteAi}
      canReviewGates={canReviewGates}
      isAiConfigured={isAiConfigured}
    />
  );
}
