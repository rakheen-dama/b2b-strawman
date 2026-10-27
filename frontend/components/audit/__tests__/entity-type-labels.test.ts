import fs from "node:fs";
import path from "node:path";
import { describe, it, expect } from "vitest";
import {
  ENTITY_TYPE_LABELS,
  entityDisplayName,
  entityTypeLabel,
  sentenceCaseKey,
} from "../entity-type-labels";

// Cross-file drift guard (Epic 611 technique): the audit entity-type literals are backend storage
// keys (ADR-342). Every literal the backend emits must carry an explicit legal label, so the
// Firm › Audit trail Entity column and Entity type filter never print a raw key.
// components/audit/__tests__ → ../../../.. = repo root.
const REPO = path.resolve(__dirname, "../../../..");
const STABILITY_TEST = path.join(
  REPO,
  "backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/EntityTypeLiteralStabilityTest.java"
);
const MAIN_JAVA = path.join(REPO, "backend/src/main/java");

function javaFiles(dir: string, out: string[] = []): string[] {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) javaFiles(full, out);
    else if (entry.name.endsWith(".java")) out.push(full);
  }
  return out;
}

/**
 * Literals pinned by the ADR-342 guard. They appear Java-escaped in the source, e.g.
 * `Pattern.compile("\\.entityType\\(\\s*\"project\"\\s*\\)")`.
 */
function stabilityPinnedLiterals(): string[] {
  const src = fs.readFileSync(STABILITY_TEST, "utf8");
  return [...src.matchAll(/\\\\\.entityType\\\\\(\\\\s\*\\"([a-z_]+)\\"/g)].map((m) => m[1]);
}

/**
 * Every audit entity literal the backend emits. The two emission shapes in `src/main/java` are
 * `AuditEventBuilder.entityType("x")` and the `AUDIT_ENTITY_TYPE = "x"` constant passed as
 * `.entityType(AUDIT_ENTITY_TYPE)` (CashDigestService). The only other call shape,
 * `.entityType(execution.getEntityType())`, is runtime-only and falls back to sentence case.
 */
function emittedLiterals(): Set<string> {
  const out = new Set<string>();
  for (const file of javaFiles(MAIN_JAVA)) {
    const src = fs.readFileSync(file, "utf8");
    for (const m of src.matchAll(/\.entityType\(\s*"([a-z_]+)"\s*\)/g)) out.add(m[1]);
    for (const m of src.matchAll(/\bAUDIT_ENTITY_TYPE\s*=\s*"([a-z_]+)"/g)) out.add(m[1]);
  }
  return out;
}

describe("ENTITY_TYPE_LABELS", () => {
  it("labels the literals pinned by EntityTypeLiteralStabilityTest with the legal word", () => {
    const pinned = stabilityPinnedLiterals();
    // Parser self-check: a moved or reformatted guard file must fail loudly.
    expect(pinned.length).toBeGreaterThan(0);
    expect(pinned).toEqual(expect.arrayContaining(["project", "customer"]));
    for (const literal of pinned) expect(ENTITY_TYPE_LABELS[literal]).toBeTruthy();
    expect(entityTypeLabel("project")).toBe("Matter");
    expect(entityTypeLabel("customer")).toBe("Client");
  });

  it("has an explicit label for every audit entity literal the backend emits", () => {
    const emitted = emittedLiterals();
    // Parser self-check: 68 on 2026-10-27; a broken regex must not pass vacuously.
    expect(emitted.size).toBeGreaterThanOrEqual(60);
    expect(emitted.has("cash_digest")).toBe(true);
    const missing = [...emitted].filter((literal) => !(literal in ENTITY_TYPE_LABELS)).sort();
    expect(missing).toEqual([]);
  });

  it("never renames a key (ADR-342): keys stay storage literals, legal words stay labels", () => {
    for (const key of Object.keys(ENTITY_TYPE_LABELS)) expect(key).toMatch(/^[a-z][a-z_]*$/);
    expect(ENTITY_TYPE_LABELS).not.toHaveProperty("matter");
    expect(ENTITY_TYPE_LABELS).not.toHaveProperty("client");
    expect(ENTITY_TYPE_LABELS).not.toHaveProperty("fee_note");
  });

  it("uses the legal vocabulary for the ruled labels", () => {
    expect(entityTypeLabel("invoice")).toBe("Fee note");
    expect(entityTypeLabel("invoice_line")).toBe("Fee note line");
    expect(entityTypeLabel("billing_run")).toBe("Fee run");
    expect(entityTypeLabel("legal_disbursement")).toBe("Disbursement");
    expect(entityTypeLabel("project_member")).toBe("Matter team member");
    expect(entityTypeLabel("time_entry")).toBe("Time recording");
    expect(entityTypeLabel("billing_rate")).toBe("Billing rate");
    // R-2: acceptance requests cover any document, not only engagement letters.
    expect(entityTypeLabel("acceptance_request")).toBe("Document for acceptance");
    expect(entityTypeLabel("portal_contact")).toBe("Portal contact");
    expect(entityTypeLabel("checklist_instance_item")).toBe("FICA checklist item");
  });

  it("no label leaks the storage vocabulary (Project / Customer / Invoice)", () => {
    for (const label of Object.values(ENTITY_TYPE_LABELS)) {
      expect(label).not.toMatch(/\b(project|customer|invoice)s?\b/i);
    }
  });

  it("falls back to sentence case for unknown keys", () => {
    expect(sentenceCaseKey("foo_bar.baz")).toBe("Foo bar baz");
    expect(entityTypeLabel("foo_bar.baz")).toBe("Foo bar baz");
    expect(entityTypeLabel("unknown_thing", "Backend label")).toBe("Backend label");
    expect(entityTypeLabel("project", "Project")).toBe("Matter");
    expect(entityTypeLabel("")).toBe("");
  });

  it("derives a display name only from a string invoice number (R-4)", () => {
    expect(entityDisplayName("invoice", { invoice_number: "INV-0003" })).toBe("INV-0003");
    expect(entityDisplayName("invoice", { invoice_number: "" })).toBeNull();
    expect(entityDisplayName("invoice", { invoice_number: 3 })).toBeNull();
    expect(entityDisplayName("invoice", null)).toBeNull();
    expect(entityDisplayName("invoice", undefined)).toBeNull();
    // No other name sources (R-4): project/customer names are not used.
    expect(entityDisplayName("project", { name: "Smith v Jones" })).toBeNull();
    expect(entityDisplayName("project", { name: { from: "a", to: "b" } })).toBeNull();
  });
});
