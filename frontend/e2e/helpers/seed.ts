/**
 * The E2E boot seed (`compose/seed/seed.sh`) as seen by the default-config specs (Phase 88 628F).
 *
 * The seed rows are READ-ONLY for specs (628F R-5): the legal suites, the portal 90-day suite and
 * `scripts/regression-test.sh` read the same rows, and default-config spec files run in parallel
 * workers. A test that changes state creates its own run-unique entity; a shared setting a test
 * must change is restored in `afterAll`; never assert global counts.
 *
 * Look the seed rows up by exact name, never `find(c => c.lifecycleStatus === "ACTIVE")`: sibling
 * spec files create ACTIVE clients of their own.
 */
const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8081";
const MOCK_IDP_URL = process.env.MOCK_IDP_URL || "http://localhost:8090";

export const SEED = {
  org: "e2e-test-org",
  /** ACTIVE, address 1 Long Street, Cape Town 8001 ZA; portal contact auto-provisioned. */
  client: "Acme Corp",
  contactEmail: "contact@acme.example.com",
  /** Acme Corp's matter. Carries the seeded fee notes, information request and Statement of Account. */
  matter: "Website Redesign",
  /** The firm's only GENERAL trust account (primary). */
  trustAccount: "Mathebula & Partners Trust Account",
  /** Fee notes on Acme Corp / Website Redesign, recognised by their notes. */
  feeNoteDraftNotes: "E2E seed fee note — draft",
  feeNoteSentNotes: "E2E seed fee note — sent",
  /** Item of the SENT information request to the Acme portal contact. */
  infoRequestItem: "Certified ID copy",
} as const;

type SeedUser = "alice" | "bob" | "carol";
const USERS: Record<SeedUser, { userId: string; orgRole: string }> = {
  alice: { userId: "user_e2e_alice", orgRole: "owner" },
  bob: { userId: "user_e2e_bob", orgRole: "admin" },
  carol: { userId: "user_e2e_carol", orgRole: "member" },
};

/** A backend bearer token for a seed user (mock IDP). */
export async function seedToken(user: SeedUser = "alice"): Promise<string> {
  const res = await fetch(`${MOCK_IDP_URL}/token`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ...USERS[user], orgId: SEED.org, orgSlug: SEED.org }),
  });
  if (!res.ok) throw new Error(`mock IDP token for ${user}: HTTP ${res.status}`);
  return ((await res.json()) as { access_token: string }).access_token;
}

/** GET a backend path as `user` and return the parsed JSON (throws on a non-2xx answer). */
export async function apiGet<T>(path: string, user: SeedUser = "alice"): Promise<T> {
  const res = await fetch(`${BACKEND_URL}${path}`, {
    headers: { Authorization: `Bearer ${await seedToken(user)}` },
  });
  if (!res.ok) throw new Error(`GET ${path}: HTTP ${res.status}`);
  return (await res.json()) as T;
}

type Named = { id: string; name: string };

async function idByName(path: string, name: string): Promise<string> {
  // Some list endpoints answer a bare array, others a Spring page ({ content: [...] })
  const body = await apiGet<Named[] | { content: Named[] }>(path);
  const list = Array.isArray(body) ? body : body.content;
  const hit = list.find((r) => r.name === name);
  if (!hit) throw new Error(`seed row "${name}" not found via GET ${path} — is the stack seeded?`);
  return hit.id;
}

/** Acme Corp's client id. */
export function acmeId(): Promise<string> {
  return idByName("/api/clients?size=200", SEED.client);
}

/** Website Redesign's matter id. */
export function websiteRedesignId(): Promise<string> {
  return idByName("/api/matters", SEED.matter);
}
