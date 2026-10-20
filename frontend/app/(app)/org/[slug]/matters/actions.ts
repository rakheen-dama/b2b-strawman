"use server";

import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { ApiError } from "@/lib/api";
import {
  createMatter,
  updateMatter,
  deleteMatter,
  completeMatter,
  archiveMatter,
  reactivateMatter,
} from "@/lib/api/matters";
import { listClients } from "@/lib/api/clients";
import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import type { Client, CreateMatterRequest, UpdateMatterRequest } from "@/lib/types";
import { classifyError } from "@/lib/error-handler";
import { createMessages } from "@/lib/messages";

const PROJECT_PRIORITIES = ["LOW", "MEDIUM", "HIGH"] as const;

function parseProjectPriority(
  raw: string | undefined
): CreateMatterRequest["priority"] | undefined {
  if (!raw) return undefined;
  return (PROJECT_PRIORITIES as readonly string[]).includes(raw)
    ? (raw as CreateMatterRequest["priority"])
    : undefined;
}

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function createProject(slug: string, formData: FormData): Promise<ActionResult> {
  const name = formData.get("name")?.toString().trim() ?? "";
  const description = formData.get("description")?.toString().trim() || undefined;
  const customerId = formData.get("customerId")?.toString().trim() || undefined;
  const dueDate = formData.get("dueDate")?.toString().trim() || undefined;
  const referenceNumber = formData.get("referenceNumber")?.toString().trim() || undefined;
  const priorityRaw = formData.get("priority")?.toString().trim() || undefined;
  const priority = parseProjectPriority(priorityRaw);
  if (priorityRaw !== undefined && priority === undefined) {
    return { success: false, error: "Invalid project priority." };
  }
  const workType = formData.get("workType")?.toString().trim() || undefined;

  if (!name) {
    return { success: false, error: "Project name is required." };
  }

  const body: CreateMatterRequest = {
    name,
    description,
    clientId: customerId,
    dueDate,
    referenceNumber,
    priority,
    workType,
  };

  try {
    await createMatter(body);
  } catch (error) {
    const message =
      error instanceof ApiError
        ? error.message
        : createMessages("errors").t(classifyError(error).messageCode);
    return { success: false, error: message };
  }

  revalidatePath(`/org/${slug}/matters`);
  revalidatePath(`/org/${slug}/dashboard`);

  return { success: true };
}

export async function updateProject(
  slug: string,
  id: string,
  formData: FormData
): Promise<ActionResult> {
  const name = formData.get("name")?.toString().trim() ?? "";
  const description = formData.get("description")?.toString().trim() || undefined;
  const customerIdRaw = formData.get("customerId")?.toString().trim();
  const customerId = customerIdRaw === "" ? null : (customerIdRaw ?? undefined);
  const dueDateRaw = formData.get("dueDate")?.toString().trim();
  const dueDate = dueDateRaw === "" ? null : (dueDateRaw ?? undefined);
  const referenceNumberRaw = formData.get("referenceNumber")?.toString().trim();
  const referenceNumber = referenceNumberRaw === "" ? null : (referenceNumberRaw ?? undefined);
  const priorityRaw = formData.get("priority")?.toString().trim();
  let priority: UpdateMatterRequest["priority"];
  if (priorityRaw === "" || priorityRaw === undefined) {
    priority = null;
  } else {
    const parsedPriority = parseProjectPriority(priorityRaw);
    if (!parsedPriority) {
      return { success: false, error: "Invalid project priority." };
    }
    priority = parsedPriority;
  }
  const workTypeRaw = formData.get("workType")?.toString().trim();
  const workType = workTypeRaw === "" ? null : (workTypeRaw ?? undefined);

  if (!name) {
    return { success: false, error: "Project name is required." };
  }

  const body: UpdateMatterRequest = {
    name,
    description,
    clientId: customerId,
    dueDate,
    referenceNumber,
    priority,
    workType,
  };

  try {
    await updateMatter(id, body);
  } catch (error) {
    const message =
      error instanceof ApiError
        ? error.message
        : createMessages("errors").t(classifyError(error).messageCode);
    return { success: false, error: message };
  }

  revalidatePath(`/org/${slug}/matters`);
  revalidatePath(`/org/${slug}/matters/${id}`);
  revalidatePath(`/org/${slug}/dashboard`);

  return { success: true };
}

export async function deleteProject(slug: string, id: string): Promise<ActionResult> {
  const caps = await fetchMyCapabilities();
  if (!caps.isOwner) {
    return { success: false, error: "Only organization owners can delete projects." };
  }

  try {
    await deleteMatter(id);
  } catch (error) {
    const message =
      error instanceof ApiError
        ? error.message
        : createMessages("errors").t(classifyError(error).messageCode);
    return { success: false, error: message };
  }

  revalidatePath(`/org/${slug}/matters`);
  revalidatePath(`/org/${slug}/dashboard`);

  redirect(`/org/${slug}/matters`);
}

export async function completeProject(
  slug: string,
  projectId: string,
  acknowledgeUnbilledTime?: boolean
): Promise<ActionResult> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return { success: false, error: "Only admins and owners can complete projects." };
  }

  try {
    await completeMatter(projectId, { acknowledgeUnbilledTime });
  } catch (error) {
    const message =
      error instanceof ApiError
        ? error.message
        : createMessages("errors").t(classifyError(error).messageCode);
    return { success: false, error: message };
  }

  revalidatePath(`/org/${slug}/matters`);
  revalidatePath(`/org/${slug}/matters/${projectId}`);
  revalidatePath(`/org/${slug}/dashboard`);

  return { success: true };
}

export async function archiveProject(slug: string, projectId: string): Promise<ActionResult> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return { success: false, error: "Only admins and owners can archive projects." };
  }

  try {
    await archiveMatter(projectId);
  } catch (error) {
    const message =
      error instanceof ApiError
        ? error.message
        : createMessages("errors").t(classifyError(error).messageCode);
    return { success: false, error: message };
  }

  revalidatePath(`/org/${slug}/matters`);
  revalidatePath(`/org/${slug}/matters/${projectId}`);
  revalidatePath(`/org/${slug}/dashboard`);

  return { success: true };
}

export async function reopenProject(slug: string, projectId: string): Promise<ActionResult> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return { success: false, error: "Only admins and owners can reopen projects." };
  }

  try {
    await reactivateMatter(projectId);
  } catch (error) {
    const message =
      error instanceof ApiError
        ? error.message
        : createMessages("errors").t(classifyError(error).messageCode);
    return { success: false, error: message };
  }

  revalidatePath(`/org/${slug}/matters`);
  revalidatePath(`/org/${slug}/matters/${projectId}`);
  revalidatePath(`/org/${slug}/dashboard`);

  return { success: true };
}

export async function fetchActiveCustomers(): Promise<Client[]> {
  return listClients({ status: "ACTIVE" });
}
