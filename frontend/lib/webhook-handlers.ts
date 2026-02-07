import type { WebhookEvent } from "@clerk/nextjs/webhooks";
import {
  internalApiClient,
  InternalApiError,
  type ProvisionOrgRequest,
  type ProvisionOrgResponse,
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

export async function handleOrganizationCreated(
  data: OrgEventData,
  svixId: string | null,
): Promise<void> {
  const payload: ProvisionOrgRequest = {
    clerkOrgId: data.id,
    orgName: data.name,
  };

  console.log(
    `[webhook] organization.created: clerkOrgId=${data.id}, name=${data.name}, svixId=${svixId}`,
  );

  try {
    const result = await internalApiClient<ProvisionOrgResponse>(
      "/internal/orgs/provision",
      { body: payload },
    );
    console.log(
      `[webhook] Provisioning result: schemaName=${result.schemaName}, status=${result.status}`,
    );
  } catch (error) {
    if (error instanceof InternalApiError && error.status === 409) {
      console.log(
        `[webhook] Org ${data.id} already provisioned (409 Conflict)`,
      );
      return;
    }
    console.error(
      `[webhook] Failed to provision org ${data.id}:`,
      error instanceof InternalApiError
        ? `${error.status} ${error.statusText} - ${error.body}`
        : error,
    );
  }
}

export async function handleOrganizationUpdated(
  data: OrgEventData,
  svixId: string | null,
): Promise<void> {
  const payload: UpdateOrgRequest = {
    clerkOrgId: data.id,
    orgName: data.name,
    updatedAt: data.updated_at,
  };

  console.log(
    `[webhook] organization.updated: clerkOrgId=${data.id}, name=${data.name}, svixId=${svixId}`,
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
        : error,
    );
  }
}

export async function handleOrganizationDeleted(
  data: OrgDeletedEventData,
  svixId: string | null,
): Promise<void> {
  console.log(
    `[webhook] organization.deleted: id=${data.id}, svixId=${svixId}`,
  );
}

export async function routeWebhookEvent(
  event: WebhookEvent,
  svixId: string | null,
): Promise<void> {
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
    case "organizationMembership.updated":
    case "organizationMembership.deleted":
      console.log(
        `[webhook] ${event.type}: memberId=${event.data.id} (no-op for MVP)`,
      );
      break;

    case "organizationInvitation.created":
    case "organizationInvitation.revoked":
      console.log(
        `[webhook] ${event.type}: invitationId=${event.data.id} (no-op for MVP)`,
      );
      break;

    case "organizationInvitation.accepted":
      console.log(
        `[webhook] ${event.type}: invitationId=${event.data.id} (no-op for MVP)`,
      );
      break;

    default:
      console.log(`[webhook] Unhandled event type: ${event.type}`);
  }
}
