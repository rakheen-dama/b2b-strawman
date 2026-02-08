"use server";

import { auth, clerkClient } from "@clerk/nextjs/server";
import { headers } from "next/headers";

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function inviteMember(
  emailAddress: string,
  role: "org:member" | "org:admin"
): Promise<ActionResult> {
  const { orgId, userId, orgRole } = await auth();

  if (!orgId || !userId) {
    return { success: false, error: "No active organization." };
  }

  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "You must be an admin to invite members." };
  }

  const headersList = await headers();
  const host = headersList.get("host") ?? "localhost:3000";
  const protocol = headersList.get("x-forwarded-proto") ?? "http";
  const redirectUrl = `${protocol}://${host}/sign-up`;

  try {
    const client = await clerkClient();
    await client.organizations.createOrganizationInvitation({
      organizationId: orgId,
      inviterUserId: userId,
      emailAddress,
      role,
      redirectUrl,
    });
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : "Failed to send invitation.";
    return { success: false, error: message };
  }

  return { success: true };
}
