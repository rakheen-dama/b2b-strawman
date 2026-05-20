"use server";

import { ApiError } from "@/lib/api";
import {
  initiateXeroConnect,
  disconnectXero,
  updateXeroTaxMapping,
  resetXeroTaxMappings,
  getXeroTaxRates,
  importXeroCustomers,
  updateXeroSettings,
} from "@/lib/api/integrations";
import { revalidatePath } from "next/cache";
import type {
  XeroConnectResponse,
  UpdateXeroTaxMappingRequest,
  XeroTaxMapping,
  XeroTaxRate,
  XeroCustomerImportResult,
  UpdateXeroSettingsRequest,
  XeroSettingsResponse,
} from "@/lib/types";

interface ActionResult<T = undefined> {
  success: boolean;
  error?: string;
  data?: T;
}

export async function initiateXeroConnectAction(
  slug: string
): Promise<ActionResult<XeroConnectResponse>> {
  try {
    const data = await initiateXeroConnect();
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return { success: false, error: "Permission denied." };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function disconnectXeroAction(slug: string): Promise<ActionResult> {
  try {
    await disconnectXero();
    revalidatePath(`/org/${slug}/settings/integrations/xero`);
    revalidatePath(`/org/${slug}/settings/integrations`);
    return { success: true };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return { success: false, error: "Permission denied." };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function fetchXeroTaxRatesAction(_slug: string): Promise<XeroTaxRate[]> {
  try {
    // Backend proxies raw Xero response: { TaxRates: [{ Name, TaxType, EffectiveRate }] }
    // Unwrap the envelope and map PascalCase -> camelCase to match XeroTaxRate type
    const raw = await getXeroTaxRates();
    const envelope = raw as unknown as { TaxRates?: Array<Record<string, unknown>> };
    const rates = envelope.TaxRates ?? [];
    return rates.map((r) => ({
      name: String(r.Name ?? ""),
      taxType: String(r.TaxType ?? ""),
      effectiveRate: Number(r.EffectiveRate ?? 0),
    }));
  } catch {
    return [];
  }
}

export async function updateXeroTaxMappingAction(
  slug: string,
  id: string,
  data: UpdateXeroTaxMappingRequest
): Promise<ActionResult<XeroTaxMapping>> {
  try {
    const mapping = await updateXeroTaxMapping(id, data);
    revalidatePath(`/org/${slug}/settings/integrations/xero`);
    return { success: true, data: mapping };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return { success: false, error: "Permission denied." };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function resetXeroTaxMappingsAction(slug: string): Promise<ActionResult> {
  try {
    await resetXeroTaxMappings();
    revalidatePath(`/org/${slug}/settings/integrations/xero`);
    return { success: true };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return { success: false, error: "Permission denied." };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function importXeroCustomersAction(
  slug: string
): Promise<ActionResult<XeroCustomerImportResult>> {
  try {
    const result = await importXeroCustomers();
    revalidatePath(`/org/${slug}/settings/integrations/xero`);
    return { success: true, data: result };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return { success: false, error: "Permission denied." };
      }
      if (error.status === 409) {
        return { success: false, error: "Customers have already been imported from Xero." };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function updateXeroSettingsAction(
  slug: string,
  data: UpdateXeroSettingsRequest
): Promise<ActionResult<XeroSettingsResponse>> {
  try {
    const settings = await updateXeroSettings(data);
    revalidatePath(`/org/${slug}/settings/integrations/xero`);
    return { success: true, data: settings };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return { success: false, error: "Permission denied." };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}
