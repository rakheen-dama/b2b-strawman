import { expect } from "@playwright/test";

/**
 * Phase 88 628G — firm-side setup for the portal suites: the run-unique rows the boot seed does not
 * carry (an information request to submit, a trust deposit) plus read-only seed lookups.
 *
 * Portal-local on purpose: the portal does not import from frontend/e2e (628B R-1). Mirrors
 * frontend/e2e/helpers/{api,seed}.ts. The boot-seed rows (compose/seed/seed.sh) are READ-ONLY for
 * specs (628F R-5): helpers here CREATE run-unique rows; they never edit or consume the seed's.
 * Every call asserts its answer, so a refused setup step fails the test where it happened.
 */
const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8081";
const MOCK_IDP_URL = process.env.MOCK_IDP_URL || "http://localhost:8090";
const ORG_SLUG = process.env.PORTAL_ORG_SLUG || "e2e-test-org";

/**
 * The boot seed, HAND-COPIED (the portal cannot import frontend/e2e — 628B R-1). Source of truth:
 * frontend/e2e/helpers/seed.ts (the names) and the compose/seed/** scripts that create the rows
 * (compose/seed/seed.sh and the scripts it runs). A rename or new row there MUST be mirrored here.
 */
export const SEED = {
  org: "e2e-test-org",
  orgName: "E2E Test Organization",
  client: "Acme Corp",
  contactEmail: "contact@acme.example.com",
  matter: "Website Redesign",
  trustAccount: "Mathebula & Partners Trust Account",
  /** The SENT fee note on Acme / Website Redesign (the DRAFT is not in the portal read model). */
  feeNoteSentNumber: "INV-0001",
  /** Item of the SENT information request REQ-0001 (read-only for the portal suite). */
  infoRequestItem: "Certified ID copy",
  /** The shared Statement of Account (StatementService file name). */
  statementFile:
    /^statement-of-account-website-redesign-\d{4}-\d{2}-\d{2}\.pdf$/,
} as const;

/** When this worker loaded the helpers: the 90-day files run in one worker (`workers: 1`). */
export const RUN_STARTED_AT = Date.now();

/** A per-run suffix for names / references: the portal suite is re-runnable on a populated stack. */
export const RUN_ID = RUN_STARTED_AT.toString(36).slice(-5);

let alice: Promise<string> | undefined;

/** Alice (owner) bearer token from the mock IDP (it reads only userId + orgSlug). */
function aliceToken(): Promise<string> {
  alice ??= (async () => {
    const res = await fetch(`${MOCK_IDP_URL}/token`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ userId: "user_e2e_alice", orgSlug: ORG_SLUG }),
    });
    if (!res.ok) throw new Error(`mock IDP /token → ${res.status}`);
    return ((await res.json()) as { access_token: string }).access_token;
  })();
  return alice;
}

/** A firm API call as Alice that must answer 2xx; returns the parsed body (null when empty). */
export async function firmApiOk<T>(
  path: string,
  init: { method?: string; body?: unknown } = {},
): Promise<T> {
  const res = await fetch(`${BACKEND_URL}${path}`, {
    method: init.method ?? "GET",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${await aliceToken()}`,
    },
    body: init.body === undefined ? undefined : JSON.stringify(init.body),
  });
  const text = await res.text();
  expect(
    res.ok,
    `${init.method ?? "GET"} ${path} → ${res.status} ${text}`,
  ).toBe(true);
  return (text ? JSON.parse(text) : null) as T;
}

/** The id of the one row of `path` that `pick` selects (bare array or Spring page body). */
async function idWhere<T extends { id: string }>(
  path: string,
  pick: (row: T) => boolean,
  what: string,
): Promise<string> {
  const body = await firmApiOk<T[] | { content: T[] }>(path);
  const hit = (Array.isArray(body) ? body : body.content).find(pick);
  expect(
    hit,
    `seed row ${what} via GET ${path} — is the stack seeded?`,
  ).toBeDefined();
  return hit!.id;
}

/** Acme Corp's client id. */
export const acmeId = () =>
  idWhere<{ id: string; name: string }>(
    "/api/clients?size=200",
    (c) => c.name === SEED.client,
    SEED.client,
  );

/** Website Redesign's matter id. */
export const websiteRedesignId = () =>
  idWhere<{ id: string; name: string }>(
    "/api/matters",
    (m) => m.name === SEED.matter,
    SEED.matter,
  );

/** The firm's GENERAL trust account id. */
export const trustAccountId = () =>
  idWhere<{ id: string; accountName: string }>(
    "/api/trust-accounts",
    (a) => a.accountName === SEED.trustAccount,
    SEED.trustAccount,
  );

/** The Acme portal contact's id. */
export const acmeContactId = async () =>
  idWhere<{ id: string; email: string }>(
    `/api/clients/${await acmeId()}/portal-contacts`,
    (c) => c.email === SEED.contactEmail,
    SEED.contactEmail,
  );

/** UTC today, YYYY-MM-DD (the containers run UTC). */
export const todayUtc = () => new Date().toISOString().slice(0, 10);

/**
 * A RECORDED trust deposit for Acme on Website Redesign (no approval step). The portal read model
 * syncs after commit (TrustLedgerPortalSyncService.onTrustTransactionRecorded) and the contact is
 * emailed "<org>: Trust account activity" (no magic-link token is minted).
 */
export async function recordAcmeDeposit(
  amount: number,
  description: string,
  reference: string,
): Promise<{ id: string; status: string }> {
  return firmApiOk<{ id: string; status: string }>(
    `/api/trust-accounts/${await trustAccountId()}/transactions/deposit`,
    {
      method: "POST",
      body: {
        clientId: await acmeId(),
        matterId: await websiteRedesignId(),
        amount,
        reference,
        description,
        transactionDate: todayUtc(),
      },
    },
  );
}

/**
 * A run-unique SENT information request to the Acme contact on Website Redesign with one required
 * FILE_UPLOAD item (body as compose/seed/lib/information-requests.sh). Sending mints one magic-link
 * token for the contact (cap 3 per contact per 5 min); a capped mint is caught server-side and the
 * request is still SENT.
 */
export async function sendAcmeInfoRequest(
  itemName: string,
): Promise<{ id: string; requestNumber: string; status: string }> {
  const created = await firmApiOk<{ id: string }>("/api/information-requests", {
    method: "POST",
    body: {
      clientId: await acmeId(),
      matterId: await websiteRedesignId(),
      portalContactId: await acmeContactId(),
      items: [
        {
          name: itemName,
          description: "Upload a certified copy of your ID document",
          responseType: "FILE_UPLOAD",
          required: true,
          fileTypeHints: "PDF, JPG, PNG",
          sortOrder: 0,
        },
      ],
    },
  });
  return firmApiOk<{ id: string; requestNumber: string; status: string }>(
    `/api/information-requests/${created.id}/send`,
    { method: "POST" },
  );
}

/** A portal API GET as the signed-in contact (JWT from helpers/auth.ts getPortalJwt). */
export async function portalApiGet<T>(
  path: string,
  portalJwt: string,
): Promise<T> {
  const res = await fetch(`${BACKEND_URL}${path}`, {
    headers: { Authorization: `Bearer ${portalJwt}` },
  });
  expect(res.ok, `GET ${path} → ${res.status}`).toBe(true);
  return (await res.json()) as T;
}
