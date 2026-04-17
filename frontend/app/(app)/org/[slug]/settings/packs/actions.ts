"use server";

import { ApiError } from "@/lib/api";
import {
  listPackCatalog,
  installPack,
  uninstallPack,
  checkPackUninstallable,
} from "@/lib/api/packs";
import type { PackCatalogEntry, PackInstallResponse, UninstallCheck } from "@/lib/api/packs";
import { revalidatePath } from "next/cache";

export interface ActionResult<T = void> {
  success: boolean;
  error?: string;
  data?: T;
}

export async function fetchCatalogAction(all: boolean): Promise<ActionResult<PackCatalogEntry[]>> {
  try {
    const data = await listPackCatalog({ all });
    return { success: true, data };
  } catch (error) {
    console.error("[fetchCatalogAction] Failed to fetch catalog:", error);
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function fetchUninstallCheckAction(
  packId: string
): Promise<ActionResult<UninstallCheck>> {
  try {
    const data = await checkPackUninstallable(packId);
    return { success: true, data };
  } catch (error) {
    console.error("[fetchUninstallCheckAction] Failed to check uninstall:", packId, error);
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function installPackAction(
  slug: string,
  packId: string
): Promise<ActionResult<PackInstallResponse>> {
  try {
    const result = await installPack(packId);
    revalidatePath(`/org/${slug}/settings/packs`);
    return { success: true, data: result };
  } catch (error) {
    console.error("[installPackAction] Failed to install pack:", packId, error);
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to install packs.",
        };
      }
      if (error.status === 409) {
        return {
          success: false,
          error: error.detail?.detail ?? "Pack is already installed.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function uninstallPackAction(slug: string, packId: string): Promise<ActionResult> {
  try {
    await uninstallPack(packId);
  } catch (error) {
    console.error("[uninstallPackAction] Failed to uninstall pack:", packId, error);
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to uninstall packs.",
        };
      }
      if (error.status === 409) {
        return {
          success: false,
          error:
            error.detail?.detail ??
            "Pack cannot be uninstalled because some items have been used or edited.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/settings/packs`);
  return { success: true };
}
