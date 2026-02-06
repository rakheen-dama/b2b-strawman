import { verifyWebhook } from "@clerk/nextjs/webhooks";
import { NextRequest } from "next/server";

export async function POST(req: NextRequest) {
  try {
    const evt = await verifyWebhook(req);

    const eventType = evt.type;
    console.log(`Clerk webhook received: ${eventType}`);

    // Event-specific handlers (implemented in Epic 4)
    switch (eventType) {
      case "organization.created":
        // TODO (Epic 4): Call Spring Boot POST /internal/orgs/provision
        console.log("Organization created:", evt.data.id);
        break;
      case "organization.updated":
        // TODO (Epic 4): Call Spring Boot to upsert org metadata
        console.log("Organization updated:", evt.data.id);
        break;
      default:
        console.log(`Unhandled webhook event: ${eventType}`);
    }

    return new Response("Webhook received", { status: 200 });
  } catch (err) {
    console.error("Error verifying webhook:", err);
    return new Response("Error verifying webhook", { status: 400 });
  }
}
