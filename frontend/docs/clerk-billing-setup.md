# Clerk Billing & Member Limits Setup

Operational guide for configuring Clerk Dashboard billing plans and member limits.

## Prerequisites

- Clerk project with Billing enabled (Stripe integration active)
- Two plans created: **Starter** (free/default) and **Pro** (paid)

## Plan Configuration

### Starter Plan

1. Navigate to **Clerk Dashboard > Billing > Plans > Starter**
2. Under **Member Limits**, set maximum members to **2**
3. Under **Features**, enable the following keys:
   - `max_members_2` — used by frontend `has()` checks

### Pro Plan

1. Navigate to **Clerk Dashboard > Billing > Plans > Pro**
2. Under **Member Limits**, set maximum members to **10**
3. Under **Features**, enable the following keys:
   - `max_members_10` — used by frontend `has()` checks
   - `dedicated_schema` — indicates dedicated infrastructure tier

## How Enforcement Works

Member limits are enforced at three layers (per ADR-014):

| Layer | Mechanism | Catches |
|-------|-----------|---------|
| **Clerk** | Per-plan member limit in Dashboard | Blocks invitations when org is at limit |
| **Backend** | `MemberSyncService` count check | Rejects member sync if count exceeds `PlanLimits.maxMembers(tier)` |
| **Frontend** | Member count check in invite form | Disables invite form and shows upgrade prompt |

## Verifying Configuration

1. Create a test organization on the Starter plan
2. Invite 2 members — should succeed
3. Attempt to invite a 3rd member:
   - Clerk should block the invitation (Dashboard limit)
   - If bypassed, backend returns `403 Plan limit exceeded`
   - Frontend hides the invite form and shows "Upgrade to Pro"

## Matching Backend Constants

Keep Clerk Dashboard limits in sync with `PlanLimits.java`:

```
STARTER_MAX_MEMBERS = 2
PRO_MAX_MEMBERS = 10
```

If these values change, update both Clerk Dashboard and the backend constants.
