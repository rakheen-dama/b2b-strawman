/**
 * Fixtures for the Phase 69 Epic 510 admin-POV 30-day audit capstone.
 *
 * One seed-helper per Day-N checkpoint of `qa/testplan/demos/admin-audit-30day-keycloak.md`.
 * Each helper:
 *   - Takes `{ token, orgSlug, ... }`.
 *   - Calls the relevant backend API via `request.newContext({...})`.
 *   - Returns a typed result with the entity ID(s) and (where exposed) the
 *     identifier the QA agent needs to locate the corresponding `audit_event`
 *     row (closureLogId, approval ID, DSAR pack ID, export ID).
 *
 * For the full assertions and the orchestrated lifecycle run, see slice 510B.
 * 510A delivers the seed surface only.
 *
 * Re-exports `loginAs` and `getApiToken` from `../../fixtures/auth` so spec
 * files in this directory have a single import root.
 */
import { request } from "@playwright/test";

export { loginAs, getApiToken } from "../../fixtures/auth";

const API_BASE = process.env.API_BASE_URL || "http://localhost:8081";

// ---------------------------------------------------------------------------
// Shared types
// ---------------------------------------------------------------------------

export interface SeedOpts {
  token: string;
  orgSlug: string;
}

export interface MatterSummary {
  id: string;
  name: string;
  status: string;
}

function authHeaders(token: string): Record<string, string> {
  return {
    Authorization: `Bearer ${token}`,
    "Content-Type": "application/json",
  };
}

// ---------------------------------------------------------------------------
// Shared helper — find a matter by name (used by Days 15 and 20)
// ---------------------------------------------------------------------------

/**
 * Look up a matter ID by display name. Useful when later checkpoints need to
 * operate on the matter created by `seedDay0LoginAndMatterCreation`.
 */
export async function getMatterIdByName(
  token: string,
  _orgSlug: string,
  name: string
): Promise<MatterSummary | null> {
  const ctx = await request.newContext({
    extraHTTPHeaders: { Authorization: `Bearer ${token}` },
  });
  try {
    const res = await ctx.get(`${API_BASE}/api/projects?size=200`);
    if (!res.ok()) return null;
    const body = (await res.json()) as { content?: MatterSummary[] } | MatterSummary[];
    const projects = Array.isArray(body) ? body : (body.content ?? []);
    return projects.find((p) => p && p.name === name) ?? null;
  } finally {
    await ctx.dispose();
  }
}

// ---------------------------------------------------------------------------
// Day 0 — Login + matter creation + profile-gated seeds
// ---------------------------------------------------------------------------

export interface Day0SeedResult {
  matterId: string;
  matterName: string;
  trustTransactionId?: string;
  deadlineId?: string;
  retainerId?: string;
}

/**
 * Day 0 seed: log Alice in (audit eventType `auth.login.*` — emitted naturally
 * via Keycloak/gateway), create a matter (audit eventType `matter.created` /
 * `project.created`), then optionally seed profile-gated state:
 *   - `[legal-za]`   trust deposit  → `trust_transaction.deposited`
 *   - `[accounting-za]` deadline    → `deadline.created`
 *   - `[consulting-za]` retainer    → `retainer.created`
 *
 * The login event itself is emitted by the natural `loginAs()` flow; the
 * caller drives that separately. This helper handles the API-side seeds only.
 *
 * Audit eventTypes produced: `matter.created`, optional `trust_transaction.deposited`,
 * optional `deadline.created`, optional `retainer.created`.
 */
export async function seedDay0LoginAndMatterCreation(
  opts: SeedOpts & {
    matterName?: string;
    profile?: "legal-za" | "accounting-za" | "consulting-za";
  }
): Promise<Day0SeedResult> {
  const matterName = opts.matterName ?? "Audit Capstone Matter Day 0";
  const ctx = await request.newContext({ extraHTTPHeaders: authHeaders(opts.token) });
  try {
    const matterRes = await ctx.post(`${API_BASE}/api/projects`, {
      data: { name: matterName },
    });
    if (!matterRes.ok()) {
      throw new Error(
        `seedDay0: matter creation failed (${matterRes.status()}): ${await matterRes.text()}`
      );
    }
    const matter = (await matterRes.json()) as { id: string; name: string };

    const result: Day0SeedResult = { matterId: matter.id, matterName: matter.name };

    if (opts.profile === "legal-za") {
      // TODO[510B-OR-PHASE-70]: confirm exact request body once Day 0 trust deposit
      // helper is wired into the lifecycle. Backend emits `trust_transaction.deposited`.
      const trustRes = await ctx.post(`${API_BASE}/api/trust-transactions`, {
        data: {
          matterId: matter.id,
          amount: 500000, // ZAR 5 000 in cents
          type: "DEPOSIT",
          reference: "Day 0 capstone seed",
        },
      });
      if (trustRes.ok()) {
        const tx = (await trustRes.json()) as { id?: string };
        result.trustTransactionId = tx.id;
      }
    }

    if (opts.profile === "accounting-za") {
      // TODO[510B-OR-PHASE-70]: deadlines API path may differ per vertical profile;
      // confirm endpoint and request shape during 510B integration.
      const dlRes = await ctx.post(`${API_BASE}/api/deadlines`, {
        data: { matterId: matter.id, title: "Day 0 capstone deadline" },
      });
      if (dlRes.ok()) {
        const dl = (await dlRes.json()) as { id?: string };
        result.deadlineId = dl.id;
      }
    }

    if (opts.profile === "consulting-za") {
      // TODO[510B-OR-PHASE-70]: retainer / hour-bank seed endpoint to be confirmed.
      const retainerRes = await ctx.post(`${API_BASE}/api/retainers`, {
        data: { matterId: matter.id, hours: 10 },
      });
      if (retainerRes.ok()) {
        const r = (await retainerRes.json()) as { id?: string };
        result.retainerId = r.id;
      }
    }

    return result;
  } finally {
    await ctx.dispose();
  }
}

// ---------------------------------------------------------------------------
// Day 5 — Permission denial (Carol attempts owner-only action)
// ---------------------------------------------------------------------------

export interface Day5SeedResult {
  /** HTTP status returned by the denied attempt. Expected: 403. */
  deniedStatus: number;
  /** True if the request was denied as expected. */
  denied: boolean;
  /**
   * Whether the corresponding `security.permission.denied` audit row is
   * expected to exist. As of 510A, this is `false` — see TODO below.
   */
  expectAuditRow: boolean;
}

/**
 * Day 5 seed: drive a 403 from a Member-role token attempting an owner-only
 * action. The capstone uses a customer DELETE attempt; any owner-only path
 * works.
 *
 * Audit eventType _intended_: `security.permission.denied` (WARNING).
 *
 * TODO[510B-OR-PHASE-70]: emission gap — `security.permission.denied` is
 * registered in `AuditEventTypeRegistry.java:43` but no backend emitter
 * exists today. This fixture only stages the 403; the audit row will be
 * absent until backend emission lands. Phase 70 backlog candidate.
 */
export async function seedDay5PermissionDenial(
  opts: SeedOpts & { memberToken: string; targetCustomerId: string }
): Promise<Day5SeedResult> {
  const ctx = await request.newContext({
    extraHTTPHeaders: { Authorization: `Bearer ${opts.memberToken}` },
  });
  try {
    const res = await ctx.delete(`${API_BASE}/api/customers/${opts.targetCustomerId}`);
    return {
      deniedStatus: res.status(),
      denied: res.status() === 403,
      // TODO[510B-OR-PHASE-70]: flip to true once the registered event type is emitted.
      expectAuditRow: false,
    };
  } finally {
    await ctx.dispose();
  }
}

// ---------------------------------------------------------------------------
// Day 10 — Trust transaction approval [legal-za]
// ---------------------------------------------------------------------------

export interface Day10SeedResult {
  trustTransactionId: string;
  approved: boolean;
  /** Audit row ID (if backend exposes one in the approval response). */
  auditEventId?: string;
}

/**
 * Day 10 seed (legal-za): post and approve a trust transaction.
 *
 * Audit eventType: `trust_transaction.approved` — emitted by
 * `TrustTransactionService.java:256+` (publishes via `auditService.log(...)`)
 * and propagated through `TrustTransactionApprovalEvent.java:66`.
 */
export async function seedDay10TrustApproval(
  opts: SeedOpts & { matterId: string; amountCents?: number }
): Promise<Day10SeedResult> {
  const ctx = await request.newContext({ extraHTTPHeaders: authHeaders(opts.token) });
  try {
    const postRes = await ctx.post(`${API_BASE}/api/trust-transactions`, {
      data: {
        matterId: opts.matterId,
        amount: opts.amountCents ?? 200000, // ZAR 2 000 in cents
        type: "TRANSFER",
        reference: "Day 10 capstone approval",
      },
    });
    if (!postRes.ok()) {
      throw new Error(
        `seedDay10: trust tx post failed (${postRes.status()}): ${await postRes.text()}`
      );
    }
    const tx = (await postRes.json()) as { id: string };

    const approveRes = await ctx.post(
      `${API_BASE}/api/trust-transactions/${tx.id}/approve`,
      { data: {} }
    );
    return {
      trustTransactionId: tx.id,
      approved: approveRes.ok(),
    };
  } finally {
    await ctx.dispose();
  }
}

// ---------------------------------------------------------------------------
// Day 15 — Matter closure with override
// ---------------------------------------------------------------------------

export interface Day15SeedResult {
  matterId: string;
  ok: boolean;
  status: number;
  closureLogId?: string;
}

/**
 * Day 15 seed: close a matter via the override path.
 *
 * Audit eventType: `matter.closure.override_used` (CRITICAL / COMPLIANCE) —
 * emitted by `MatterClosureService.java:271-272`. The standard
 * `matter_closure.closed` row also fires (`MatterClosureService.java:254`).
 *
 * Mirrors `closeMatterWithOverride()` in
 * `frontend/e2e/tests/audit-log/matter-closure-audit-tab.spec.ts`.
 */
export async function seedDay15ClosureOverride(
  opts: SeedOpts & {
    matterId: string;
    justification?: string;
  }
): Promise<Day15SeedResult> {
  const ctx = await request.newContext({ extraHTTPHeaders: authHeaders(opts.token) });
  try {
    const justification =
      opts.justification ?? "Client returned funds — trust account zero (capstone Day 15)";
    const res = await ctx.post(`${API_BASE}/api/matters/${opts.matterId}/closure/close`, {
      data: {
        reason: "OTHER",
        generateClosureLetter: false,
        generateStatementOfAccount: false,
        override: true,
        overrideJustification: justification,
      },
    });
    let closureLogId: string | undefined;
    if (res.ok()) {
      const json = (await res.json()) as { closureLogId?: string };
      closureLogId = json.closureLogId;
    }
    return {
      matterId: opts.matterId,
      ok: res.ok(),
      status: res.status(),
      closureLogId,
    };
  } finally {
    await ctx.dispose();
  }
}

// ---------------------------------------------------------------------------
// Day 20 — Pre-seed customer activity
// ---------------------------------------------------------------------------

export interface Day20SeedResult {
  customerId: string;
  matterId?: string;
  documentId?: string;
}

/**
 * Day 20 seed: ensure a known customer has at least one matter, one document
 * upload, and one edit recorded so the per-entity Audit tab on the customer
 * detail page is non-empty.
 *
 * Audit eventTypes touched: `customer.updated`, `matter.created`,
 * `document.uploaded`. All three are existing backend emitters; no gap.
 */
export async function seedDay20CustomerActivity(
  opts: SeedOpts & { customerId: string }
): Promise<Day20SeedResult> {
  const ctx = await request.newContext({ extraHTTPHeaders: authHeaders(opts.token) });
  try {
    // Touch the customer to emit `customer.updated`.
    await ctx.patch(`${API_BASE}/api/customers/${opts.customerId}`, {
      data: { notes: `Day 20 capstone touch ${new Date().toISOString()}` },
    });

    // Create a matter on the customer.
    const matterRes = await ctx.post(`${API_BASE}/api/projects`, {
      data: { name: "Day 20 capstone matter", customerId: opts.customerId },
    });
    let matterId: string | undefined;
    if (matterRes.ok()) {
      matterId = ((await matterRes.json()) as { id?: string }).id;
    }

    // TODO[510B-OR-PHASE-70]: confirm document-upload endpoint shape; the real
    // capstone may use a multipart upload. For 510A we record the intent only.
    const documentId: string | undefined = undefined;

    return { customerId: opts.customerId, matterId, documentId };
  } finally {
    await ctx.dispose();
  }
}

// ---------------------------------------------------------------------------
// Day 22 — PDF export of last 30 days (reflexive `audit.export.generated`)
// ---------------------------------------------------------------------------

export interface Day22SeedResult {
  ok: boolean;
  status: number;
  contentType?: string;
  /** Bytes of the response body — useful to assert non-zero PDF length. */
  byteLength?: number;
}

/**
 * Day 22 seed: export the audit log as PDF for the last 30 days.
 *
 * Audit eventType: `audit.export.generated` — emitted by
 * `AuditExportService.java:114, 187` (CSV + PDF emitters).
 */
export async function seedDay22AuditExport(
  opts: SeedOpts & { rangeDays?: number; preset?: string }
): Promise<Day22SeedResult> {
  const ctx = await request.newContext({ extraHTTPHeaders: authHeaders(opts.token) });
  try {
    const rangeDays = opts.rangeDays ?? 30;
    const to = new Date();
    const from = new Date(to.getTime() - rangeDays * 24 * 60 * 60 * 1000);

    const res = await ctx.post(`${API_BASE}/api/audit-events/export.pdf`, {
      data: {
        from: from.toISOString(),
        to: to.toISOString(),
        preset: opts.preset ?? "Sensitive",
      },
    });
    let byteLength: number | undefined;
    if (res.ok()) {
      const body = await res.body();
      byteLength = body.byteLength;
    }
    return {
      ok: res.ok(),
      status: res.status(),
      contentType: res.headers()["content-type"],
      byteLength,
    };
  } finally {
    await ctx.dispose();
  }
}

// ---------------------------------------------------------------------------
// Day 25 — DSAR submission + pack identifier
// ---------------------------------------------------------------------------

export interface Day25SeedResult {
  dsarRequestId: string;
  /** URL or identifier for the generated pack ZIP. Set once pipeline completes. */
  packDownloadUrl?: string;
}

/**
 * Day 25 seed: submit a DSAR via the Phase 50 pipeline. The 510A spec only
 * needs the request ID; 510B drives the full asynchronous pipeline and
 * unzips the pack to assert `audit-trail/events.csv` correctness.
 *
 * Audit eventType (intended): `dsar.requested` (and downstream
 * `dsar.fulfilled` once the pipeline completes).
 */
export async function seedDay25Dsar(
  opts: SeedOpts & { subjectCustomerId: string }
): Promise<Day25SeedResult> {
  const ctx = await request.newContext({ extraHTTPHeaders: authHeaders(opts.token) });
  try {
    // TODO[510B-OR-PHASE-70]: confirm exact DSAR submission endpoint and shape;
    // the Phase 50 pipeline may surface this under /api/dsar/requests or similar.
    const res = await ctx.post(`${API_BASE}/api/dsar/requests`, {
      data: {
        subjectCustomerId: opts.subjectCustomerId,
        scope: "FULL",
        reason: "Capstone Day 25 — admin-driven DSAR fulfilment",
      },
    });
    if (!res.ok()) {
      throw new Error(
        `seedDay25: DSAR submission failed (${res.status()}): ${await res.text()}`
      );
    }
    const body = (await res.json()) as { id: string; packDownloadUrl?: string };
    return { dsarRequestId: body.id, packDownloadUrl: body.packDownloadUrl };
  } finally {
    await ctx.dispose();
  }
}

// ---------------------------------------------------------------------------
// Day 30 — 10 000-row export cap probe
// ---------------------------------------------------------------------------

export interface Day30SeedResult {
  wide: { ok: boolean; status: number };
  narrow: { ok: boolean; status: number; byteLength?: number };
}

/**
 * Day 30 seed: probe the export-row hard cap.
 *
 * 1. Wide window (2 years) — expected to exceed the 10 000-row cap and fail
 *    gracefully (typically 413 Payload Too Large or 422 with structured
 *    error code).
 * 2. Narrow window (30 days) — expected to succeed and emit a fresh
 *    `audit.export.generated` event.
 *
 * Failed exports must NOT emit a reflexive audit row.
 */
export async function seedDay30LargeWindowExport(opts: SeedOpts): Promise<Day30SeedResult> {
  const ctx = await request.newContext({ extraHTTPHeaders: authHeaders(opts.token) });
  try {
    const now = new Date();

    const wideFrom = new Date(now.getTime() - 730 * 24 * 60 * 60 * 1000);
    const wideRes = await ctx.post(`${API_BASE}/api/audit-events/export.pdf`, {
      data: { from: wideFrom.toISOString(), to: now.toISOString() },
    });

    const narrowFrom = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
    const narrowRes = await ctx.post(`${API_BASE}/api/audit-events/export.pdf`, {
      data: { from: narrowFrom.toISOString(), to: now.toISOString() },
    });

    let narrowBytes: number | undefined;
    if (narrowRes.ok()) {
      const body = await narrowRes.body();
      narrowBytes = body.byteLength;
    }

    return {
      wide: { ok: wideRes.ok(), status: wideRes.status() },
      narrow: { ok: narrowRes.ok(), status: narrowRes.status(), byteLength: narrowBytes },
    };
  } finally {
    await ctx.dispose();
  }
}
