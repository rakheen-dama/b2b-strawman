import type { WebhookEvent } from "@clerk/nextjs/webhooks";
import { clerkClient } from "@clerk/nextjs/server";
import {
  internalApiClient,
  InternalApiError,
  type ProvisionOrgRequest,
  type ProvisionOrgResponse,
  type SyncMemberRequest,
  type SyncMemberResponse,
  type UpdateOrgRequest,
} from "@/lib/internal-api";

interface OrgEventData {
  id: string;
  name: string;
  slug: string;
  updated_at: number;
}

interface OrgDeletedEventData {
  id?: string;
  slug?: string;
  deleted: boolean;
}

interface MembershipEventData {
  id: string;
  role: string;
  organization: { id: string };
  public_user_data: { user_id: string };
}

/**
 * Strip the "org:" prefix from Clerk roles.
 * e.g., "org:admin" → "admin", "org:member" → "member"
 */
function mapClerkRole(clerkRole: string): string {
  return clerkRole.startsWith("org:") ? clerkRole.slice(4) : clerkRole;
}

export async function handleOrganizationCreated(
  data: OrgEventData,
  svixId: string | null
): Promise<void> {
  const payload: ProvisionOrgRequest = {
    clerkOrgId: data.id,
    orgName: data.name,
  };

  console.log(
    `[webhook] organization.created: clerkOrgId=${data.id}, name=${data.name}, svixId=${svixId}`
  );

  try {
    const result = await internalApiClient<ProvisionOrgResponse>("/internal/orgs/provision", {
      body: payload,
    });
    console.log(
      `[webhook] Provisioning result: schemaName=${result.schemaName}, status=${result.status}`
    );
  } catch (error) {
    if (error instanceof InternalApiError && error.status === 409) {
      console.log(`[webhook] Org ${data.id} already provisioned (409 Conflict)`);
      return;
    }
    console.error(
      `[webhook] Failed to provision org ${data.id}:`,
      error instanceof InternalApiError
        ? `${error.status} ${error.statusText} - ${error.body}`
        : error
    );
  }
}

export async function handleOrganizationUpdated(
  data: OrgEventData,
  svixId: string | null
): Promise<void> {
  const payload: UpdateOrgRequest = {
    clerkOrgId: data.id,
    orgName: data.name,
    updatedAt: data.updated_at,
  };

  console.log(
    `[webhook] organization.updated: clerkOrgId=${data.id}, name=${data.name}, svixId=${svixId}`
  );

  try {
    await internalApiClient<void>("/internal/orgs/update", {
      method: "PUT",
      body: payload,
    });
    console.log(`[webhook] Org ${data.id} metadata updated`);
  } catch (error) {
    console.error(
      `[webhook] Failed to update org ${data.id}:`,
      error instanceof InternalApiError
        ? `${error.status} ${error.statusText} - ${error.body}`
        : error
    );
  }
}

export async function handleOrganizationDeleted(
  data: OrgDeletedEventData,
  svixId: string | null
): Promise<void> {
  console.log(`[webhook] organization.deleted: id=${data.id}, svixId=${svixId}`);
}

async function syncMember(
  data: MembershipEventData,
  svixId: string | null,
  eventType: "created" | "updated"
): Promise<void> {
  const clerkOrgId = data.organization.id;
  const clerkUserId = data.public_user_data.user_id;
  const orgRole = mapClerkRole(data.role);

  console.log(
    `[webhook] organizationMembership.${eventType}: orgId=${clerkOrgId}, userId=${clerkUserId}, role=${orgRole}, svixId=${svixId}`
  );

  try {
    const client = await clerkClient();
    const user = await client.users.getUser(clerkUserId);

    const email = user.emailAddresses[0]?.emailAddress;
    if (!email) {
      console.error(
        `[webhook] Cannot sync member ${clerkUserId}: no email address found in Clerk`
      );
      return;
    }

    const payload: SyncMemberRequest = {
      clerkOrgId,
      clerkUserId,
      email,
      name: [user.firstName, user.lastName].filter(Boolean).join(" ") || undefined,
      avatarUrl: user.imageUrl || undefined,
      orgRole,
    };

    const result = await internalApiClient<SyncMemberResponse>(
      "/internal/members/sync",
      { body: payload }
    );
    console.log(
      `[webhook] Member synced: memberId=${result.memberId}, action=${result.action}`
    );
  } catch (error) {
    console.error(
      `[webhook] Failed to sync member ${clerkUserId} in org ${clerkOrgId}:`,
      error instanceof InternalApiError
        ? `${error.status} ${error.statusText} - ${error.body}`
        : error
    );
  }
}

export async function handleMembershipCreated(
  data: MembershipEventData,
  svixId: string | null
): Promise<void> {
  return syncMember(data, svixId, "created");
}

export async function handleMembershipUpdated(
  data: MembershipEventData,
  svixId: string | null
): Promise<void> {
  return syncMember(data, svixId, "updated");
}

export async function handleMembershipDeleted(
  data: MembershipEventData,
  svixId: string | null
): Promise<void> {
  const clerkOrgId = data.organization.id;
  const clerkUserId = data.public_user_data.user_id;

  console.log(
    `[webhook] organizationMembership.deleted: orgId=${clerkOrgId}, userId=${clerkUserId}, svixId=${svixId}`
  );

  try {
    await internalApiClient<void>(
      `/internal/members/${encodeURIComponent(clerkUserId)}?clerkOrgId=${encodeURIComponent(clerkOrgId)}`,
      { method: "DELETE" }
    );
    console.log(`[webhook] Member ${clerkUserId} deleted from org ${clerkOrgId}`);
  } catch (error) {
    if (error instanceof InternalApiError && error.status === 404) {
      console.log(
        `[webhook] Member ${clerkUserId} already deleted from org ${clerkOrgId} (404)`
      );
      return;
    }
    console.error(
      `[webhook] Failed to delete member ${clerkUserId} from org ${clerkOrgId}:`,
      error instanceof InternalApiError
        ? `${error.status} ${error.statusText} - ${error.body}`
        : error
    );
  }
}

export async function routeWebhookEvent(event: WebhookEvent, svixId: string | null): Promise<void> {
  switch (event.type) {
    case "organization.created":
      await handleOrganizationCreated(event.data, svixId);
      break;
    case "organization.updated":
      await handleOrganizationUpdated(event.data, svixId);
      break;
    case "organization.deleted":
      await handleOrganizationDeleted(event.data, svixId);
      break;

    case "organizationMembership.created":
      await handleMembershipCreated(event.data as unknown as MembershipEventData, svixId);
      break;
    case "organizationMembership.updated":
      await handleMembershipUpdated(event.data as unknown as MembershipEventData, svixId);
      break;
    case "organizationMembership.deleted":
      await handleMembershipDeleted(event.data as unknown as MembershipEventData, svixId);
      break;

    case "organizationInvitation.created":
    case "organizationInvitation.revoked":
      console.log(`[webhook] ${event.type}: invitationId=${event.data.id} (no-op for MVP)`);
      break;

    case "organizationInvitation.accepted":
      console.log(`[webhook] ${event.type}: invitationId=${event.data.id} (no-op for MVP)`);
      break;

    default:
      console.log(`[webhook] Unhandled event type: ${event.type}`);
  }
}
