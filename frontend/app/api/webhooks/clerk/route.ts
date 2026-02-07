import { verifyWebhook } from "@clerk/nextjs/webhooks";
import { NextRequest } from "next/server";
import { routeWebhookEvent } from "@/lib/webhook-handlers";

export async function POST(req: NextRequest) {
  try {
    const evt = await verifyWebhook(req);

    const svixId = req.headers.get("svix-id");

    console.log(
      `[webhook] Received event: type=${evt.type}, svixId=${svixId}`,
    );

    await routeWebhookEvent(evt, svixId);

    return new Response("Webhook processed", { status: 200 });
  } catch (err) {
    console.error("[webhook] Verification failed:", err);
    return new Response("Webhook verification failed", { status: 400 });
  }
}
