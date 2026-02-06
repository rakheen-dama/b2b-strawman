import {
  internalApiClient,
  InternalApiError,
} from "@/lib/internal-api";
import { verifyWebhook } from "@clerk/nextjs/webhooks";
import { NextRequest } from "next/server";

interface ProvisionResponse {
  clerkOrgId: string;
  status: string;
}

interface OrganizationEventData {
  id: string;
  name: string;
  slug: string;
  updated_at?: number;
  created_at?: number;
}

export async function POST(req: NextRequest) {
  try {
    const evt = await verifyWebhook(req);

    const eventType = evt.type;
    const svixId = req.headers.get("svix-id");

    console.log(`Clerk webhook received: ${eventType} (svix-id: ${svixId})`);

    const headers: Record<string, string> = {};
    if (svixId) {
      headers["X-Svix-Id"] = svixId;
    }

    switch (eventType) {
      case "organization.created":
        await handleOrganizationCreated(
          evt.data as OrganizationEventData,
          headers,
        );
        break;

      case "organization.updated":
        await handleOrganizationUpdated(
          evt.data as OrganizationEventData,
          headers,
        );
        break;

      case "organization.deleted":
        console.log("Organization deleted:", (evt.data as { id: string }).id);
        // Future: mark org as deleted in backend
        break;

      case "organizationMembership.created":
      case "organizationMembership.updated":
      case "organizationMembership.deleted":
        console.log(`Membership event ${eventType}: no-op for MVP`);
        break;

      case "organizationInvitation.created":
      case "organizationInvitation.accepted":
      case "organizationInvitation.revoked":
        console.log(`Invitation event ${eventType}: no-op for MVP`);
        break;

      default:
        console.log(`Unhandled webhook event: ${eventType}`);
    }

    return new Response("Webhook received", { status: 200 });
  } catch (err) {
    if (err instanceof Error && err.message.includes("verify")) {
      console.error("Webhook verification failed:", err);
      return new Response("Error verifying webhook", { status: 400 });
    }
    console.error("Webhook processing error:", err);
    // Return 200 to prevent Svix from retrying on application errors
    return new Response("Webhook received", { status: 200 });
  }
}

async function handleOrganizationCreated(
  data: OrganizationEventData,
  headers: Record<string, string>,
) {
  console.log(
    `Provisioning organization: ${data.id} (${data.name}, slug: ${data.slug})`,
  );

  try {
    const result = await internalApiClient<ProvisionResponse>(
      "/internal/orgs/provision",
      {
        method: "POST",
        body: {
          clerkOrgId: data.id,
          orgName: data.name,
          slug: data.slug,
        },
        headers,
      },
    );
    console.log(
      `Organization ${data.id} provisioning result: ${result.status}`,
    );
  } catch (err) {
    if (err instanceof InternalApiError && err.status === 409) {
      console.log(`Organization ${data.id} already provisioned`);
      return;
    }
    // Log error but don't fail the webhook — fire-and-forget for MVP
    console.error(`Failed to provision organization ${data.id}:`, err);
  }
}

async function handleOrganizationUpdated(
  data: OrganizationEventData,
  headers: Record<string, string>,
) {
  console.log(`Updating organization: ${data.id} (${data.name})`);

  try {
    await internalApiClient<void>("/internal/orgs/update", {
      method: "POST",
      body: {
        clerkOrgId: data.id,
        orgName: data.name,
        slug: data.slug,
        updatedAt: data.updated_at
          ? new Date(data.updated_at).toISOString()
          : null,
      },
      headers,
    });
    console.log(`Organization ${data.id} updated`);
  } catch (err) {
    console.error(`Failed to update organization ${data.id}:`, err);
  }
}
