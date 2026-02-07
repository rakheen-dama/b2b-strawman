# Clerk Webhook Configuration

## Setup Steps

1. Open Clerk Dashboard: https://dashboard.clerk.com
2. Navigate to **Webhooks** in the left sidebar
3. Click **Add Endpoint**
4. Configure:
   - **Endpoint URL**: `https://<your-domain>/api/webhooks/clerk`
   - For local development, use a tunnel: `https://<tunnel-id>.ngrok.io/api/webhooks/clerk`
5. Subscribe to these events:
   - `organization.created`
   - `organization.updated`
   - `organization.deleted`
   - `organizationMembership.created`
   - `organizationMembership.updated`
   - `organizationMembership.deleted`
   - `organizationInvitation.created`
   - `organizationInvitation.accepted`
   - `organizationInvitation.revoked`
6. Click **Create**
7. Copy the **Signing Secret** (starts with `whsec_`)
8. Set `CLERK_WEBHOOK_SIGNING_SECRET=whsec_...` in `frontend/.env.local`

## Local Development Testing

Use Clerk's Dashboard **Send test webhook** feature or create an organization
through the app to trigger real events. The webhook handler logs all events
to the Next.js console.

## Verifying It Works

After configuring, create an organization in the app. Check:

1. Next.js console shows `[webhook] Received event: type=organization.created`
2. If backend is running (Epic 5+), provisioning should succeed
3. If backend is not running, you will see a logged connection error — this is expected
