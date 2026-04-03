"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";

export interface DemoProvisionFormData {
  organizationName: string;
  verticalProfile: "GENERIC" | "ACCOUNTING" | "LEGAL";
  adminEmail: string;
  seedDemoData: boolean;
}

export interface DemoProvisionResponse {
  organizationId: string;
  organizationSlug: string;
  organizationName: string;
  verticalProfile: string;
  loginUrl: string;
  demoDataSeeded: boolean;
  adminNote: string;
}

interface ActionResult<T = void> {
  success: boolean;
  data?: T;
  error?: string;
}

export async function provisionDemo(
  data: DemoProvisionFormData,
): Promise<ActionResult<DemoProvisionResponse>> {
  try {
    const response = await api.post<DemoProvisionResponse>(
      "/api/platform-admin/demo/provision",
      data,
    );
    revalidatePath("/platform-admin/demo");
    return { success: true, data: response };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}
