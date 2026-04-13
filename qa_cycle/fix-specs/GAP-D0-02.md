# Fix Spec: GAP-D0-02 — KC invite token single-use UX

## Problem
When a user receives a Keycloak invitation link but is already logged in as a different KC user, the registration succeeds but the redirect shows an "expiredActionMessage" error page. The user must manually log out of KC first, then log in normally. Observed during Day 0 checkpoint 0.22.

## Root Cause (confirmed)
This is Keycloak's built-in behavior for organization invitation tokens — they are single-use action tokens. When consumed while another session is active, Keycloak's redirect loop encounters the expired/consumed token on the second pass. This is not an application bug but a Keycloak platform limitation.

## Fix
**WONT_FIX** — This is a Keycloak platform behavior, not an application bug. Fixing it would require:
1. A custom Keycloak SPI to intercept the invitation flow and handle session conflicts, OR
2. A pre-flight redirect page in the frontend that detects an active KC session and prompts logout before visiting the invite link.

Both approaches require significant infrastructure work (1-2 days minimum) and are outside the scope of this demo QA cycle. The workaround (log out first, then use the link, then log in) is acceptable for demo purposes.

## Scope
N/A — WONT_FIX

## Verification
N/A

## Estimated Effort
L (> 2 hr) — Keycloak SPI or custom pre-flight flow
