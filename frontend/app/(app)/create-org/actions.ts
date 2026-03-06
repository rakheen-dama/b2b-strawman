"use server";

import { cookies } from "next/headers";

const GATEWAY_URL = process.env.GATEWAY_URL || "http://localhost:8443";

interface CreateOrgResult {
  success: boolean;
  slug?: string;
  error?: string;
}

export async function createOrganization(name: string): Promise<CreateOrgResult> {
  const cookieStore = await cookies();
  const sessionCookie = cookieStore.get("SESSION");
  if (!sessionCookie) {
    return { success: false, error: "Not authenticated" };
  }

  const response = await fetch(`${GATEWAY_URL}/bff/orgs`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      cookie: `SESSION=${sessionCookie.value}`,
    },
    body: JSON.stringify({ name }),
    cache: "no-store",
  });

  if (!response.ok) {
    const text = await response.text();
    return { success: false, error: `Failed to create organization: ${text}` };
  }

  const data = (await response.json()) as { orgId: string; slug: string };
  return { success: true, slug: data.slug };
}
