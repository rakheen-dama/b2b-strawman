import path from "node:path";
import { describe, it, expect } from "vitest";
import ts from "typescript";
import { readSourceTree } from "./helpers/source-scan";

// Phase 91 E3A (ADR-393): every call of the shared EmptyState (`@/components/empty-state`) passes an
// explicit `variant` — default = whole page or page-level tab panel; compact = section, card,
// widget, chart card, dialog body or Sheet (≤ one-line description + one optional action, box
// ≤ 212px); inline = table body or single-line slot. The prop stays optional (fallback "default")
// while the sweep ratchets: the files below belong to later Phase 91 slices, each of which deletes
// only its own lines; 669B deletes the last entry and makes `variant` required in the type.

const SCAN_ROOTS = ["app", "components"] as const;
const SHARED_MODULE = "components/empty-state";

/** File (relative to `frontend/`) → the Phase 91 slice that sets its variants and deletes the line. */
const ALLOWLIST: Record<string, string> = {
  // 665D — Settings › Fees & Trust
  "components/rates/member-rates-table.tsx": "665D",
  // 665E — Settings › Documents
  "app/(app)/org/[slug]/settings/documents/_tabs/templates-content.tsx": "665E",
  // 665F — Settings › Connections
  "app/(app)/org/[slug]/settings/connections/_tabs/packs-page-client.tsx": "665F",
  // 666A — Fee notes
  "app/(app)/org/[slug]/invoices/page.tsx": "666A",
  "app/(app)/org/[slug]/invoices/billing-runs/page.tsx": "666A",
  "app/(app)/org/[slug]/invoices/unbilled/page.tsx": "666A",
  // 668A — Diary, notifications
  "app/(app)/org/[slug]/diary/diary-page-client.tsx": "668A",
  "components/notifications/notifications-page-client.tsx": "668A",
  // 669A — Light pass I
  "app/(app)/org/[slug]/compliance/requests/page.tsx": "669A",
  // 669B — Light pass II (last entries; 669B also makes `variant` required)
  "components/access-request/access-requests-table.tsx": "669B",
  "components/billing/billing-tenants-table.tsx": "669B",
  "components/billing/demo-tenants-table.tsx": "669B",
  "components/team/member-list.tsx": "669B",
  "components/team/pending-invitations.tsx": "669B",
};

/** True when an import specifier written in `rel` resolves to the shared EmptyState module. */
function isSharedModule(rel: string, specifier: string): boolean {
  if (specifier === `@/${SHARED_MODULE}`) return true;
  if (!specifier.startsWith(".")) return false;
  const resolved = path.posix.normalize(path.posix.join(path.posix.dirname(rel), specifier));
  return resolved === SHARED_MODULE;
}

/**
 * One file's shared-EmptyState JSX elements: `total` found through the import-resolving matcher,
 * and the 1-based lines of those without a `variant` attribute.
 */
function scanFile(rel: string, text: string): { total: number; missing: number[] } {
  const sf = ts.createSourceFile(rel, text, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  const names = new Set<string>(); // `import { EmptyState }` / `import { EmptyState as X }`
  const namespaces = new Set<string>(); // `import * as ns` → `<ns.EmptyState>`
  for (const st of sf.statements) {
    if (!ts.isImportDeclaration(st) || !ts.isStringLiteral(st.moduleSpecifier)) continue;
    if (!isSharedModule(rel, st.moduleSpecifier.text)) continue;
    const bindings = st.importClause?.namedBindings;
    if (bindings && ts.isNamedImports(bindings)) {
      for (const el of bindings.elements) {
        if ((el.propertyName ?? el.name).text === "EmptyState") names.add(el.name.text);
      }
    } else if (bindings && ts.isNamespaceImport(bindings)) {
      namespaces.add(bindings.name.text);
    }
  }
  if (names.size === 0 && namespaces.size === 0) return { total: 0, missing: [] };

  const isShared = (tag: ts.JsxTagNameExpression): boolean => {
    if (ts.isIdentifier(tag)) return names.has(tag.text);
    return (
      ts.isPropertyAccessExpression(tag) &&
      ts.isIdentifier(tag.expression) &&
      namespaces.has(tag.expression.text) &&
      tag.name.text === "EmptyState"
    );
  };
  let total = 0;
  const missing: number[] = [];
  const visit = (n: ts.Node) => {
    if ((ts.isJsxOpeningElement(n) || ts.isJsxSelfClosingElement(n)) && isShared(n.tagName)) {
      total += 1;
      // A spread (`{...props}`) does not count: the variant must be visible at the call site.
      const hasVariant = n.attributes.properties.some(
        (p) => ts.isJsxAttribute(p) && p.name.getText(sf) === "variant"
      );
      if (!hasVariant) missing.push(sf.getLineAndCharacterOfPosition(n.getStart(sf)).line + 1);
    }
    ts.forEachChild(n, visit);
  };
  visit(sf);
  return { total, missing };
}

/** `scanFile` for every candidate file under the scan roots. */
function scanTree(): Map<string, { total: number; missing: number[] }> {
  const out = new Map<string, { total: number; missing: number[] }>();
  for (const { rel, text } of readSourceTree(SCAN_ROOTS)) {
    if (!rel.endsWith(".tsx") || !text.includes("EmptyState")) continue;
    out.set(rel, scanFile(rel, text));
  }
  return out;
}

/** Shared-EmptyState calls without `variant`, per file, across the scan roots. */
function offendersByFile(): Map<string, number[]> {
  const out = new Map<string, number[]>();
  for (const [rel, { missing }] of scanTree()) {
    if (missing.length > 0) out.set(rel, missing);
  }
  return out;
}

/** `file:line` for every offender outside the allowlist. */
function unlisted(offenders: Map<string, number[]>, allowlist: Record<string, string>): string[] {
  return [...offenders]
    .filter(([rel]) => !(rel in allowlist))
    .flatMap(([rel, lines]) => lines.map((line) => `${rel}:${line}`));
}

/** Allowlist entries whose file no longer has an offender (or no longer exists). */
function stale(offenders: Map<string, number[]>, allowlist: Record<string, string>): string[] {
  return Object.entries(allowlist)
    .filter(([rel]) => !offenders.has(rel))
    .map(([rel, slice]) => `${rel} (${slice})`);
}

describe("EmptyState variant guard (Phase 91 E3A, ADR-393)", () => {
  it("self-test: matches only the shared component and requires an explicit variant", () => {
    const lines = (src: string, rel = "components/x/self-test.tsx") => scanFile(rel, src).missing;
    const shared = 'import { EmptyState } from "@/components/empty-state";\n';

    // Single-line and multi-line JSX; with and without a variant.
    expect(
      lines(`${shared}const A = () => <EmptyState icon={I} title="t" description="d" />;`)
    ).toEqual([2]);
    expect(
      lines(
        `${shared}const A = () => (\n  <EmptyState\n    icon={I}\n    title="t"\n    description="d"\n  />\n);`
      )
    ).toEqual([3]);
    expect(
      lines(
        `${shared}const A = () => (\n  <EmptyState\n    icon={I}\n    variant="compact"\n    title="t"\n    description="d"\n  />\n);`
      )
    ).toEqual([]);
    expect(
      lines(
        `${shared}const A = () => <EmptyState variant={v} icon={I} title="t" description="d" />;`
      )
    ).toEqual([]);
    // Opening/closing form (children are ignored by the component, but the tag is still checked).
    expect(
      lines(
        `${shared}const A = () => <EmptyState icon={I} title="t" description="d"></EmptyState>;`
      )
    ).toEqual([2]);

    // A spread without an explicit variant fails; a spread plus variant passes.
    expect(lines(`${shared}const A = (p: P) => <EmptyState {...p} />;`)).toEqual([2]);
    expect(lines(`${shared}const A = (p: P) => <EmptyState {...p} variant="inline" />;`)).toEqual(
      []
    );

    // An aliased import is caught; a namespace import is caught.
    expect(
      lines(
        'import { EmptyState as Blank } from "@/components/empty-state";\nconst A = () => <Blank icon={I} title="t" description="d" />;'
      )
    ).toEqual([2]);
    expect(
      lines(
        'import * as ES from "@/components/empty-state";\nconst A = () => <ES.EmptyState icon={I} title="t" description="d" />;'
      )
    ).toEqual([2]);

    // A relative import that resolves to the shared module is caught; one that does not is ignored.
    expect(
      lines(
        'import { EmptyState } from "../empty-state";\nconst A = () => <EmptyState icon={I} title="t" description="d" />;',
        "components/x/self-test.tsx"
      )
    ).toEqual([2]);
    expect(
      lines(
        'import { EmptyState } from "./empty-state";\nconst A = () => <EmptyState title="t" />;',
        "components/setup/self-test.tsx"
      )
    ).toEqual([]);

    // A local component also named EmptyState, and the setup EmptyState, are ignored.
    expect(
      lines(
        'function EmptyState({ title }: { title: string }) { return <p>{title}</p>; }\nconst A = () => <EmptyState title="t" />;'
      )
    ).toEqual([]);
    expect(
      lines(
        'import { EmptyState } from "@/components/setup/empty-state";\nconst A = () => <EmptyState title="t" />;'
      )
    ).toEqual([]);

    // Allowlist bookkeeping: an unlisted offender is reported; a listed file that no longer
    // needs its entry is reported as stale, so entries cannot linger.
    const offenders = new Map([["components/a.tsx", [3, 9]]]);
    expect(unlisted(offenders, {})).toEqual(["components/a.tsx:3", "components/a.tsx:9"]);
    expect(unlisted(offenders, { "components/a.tsx": "663B" })).toEqual([]);
    expect(stale(offenders, { "components/a.tsx": "663B", "components/b.tsx": "665C" })).toEqual([
      "components/b.tsx (665C)",
    ]);
  });

  it("the scan finds the app's shared EmptyState calls", () => {
    // Sanity floor so the guard cannot pass vacuously: it counts the shared <EmptyState> elements
    // the guard's own matcher (scan roots + file filter + import resolution) finds, with or without
    // a variant. At 663A's HEAD that is ~65 elements in ~55 files; the floor sits below that so
    // ordinary deletions do not trip it, but a broken scan root, filter or import match (→ 0) does.
    // It does not depend on the allowlist, so it still holds once 669B empties it.
    let calls = 0;
    for (const { total } of scanTree().values()) calls += total;
    expect(calls, "shared EmptyState elements found by the guard's matcher").toBeGreaterThan(50);
  });

  it("every shared EmptyState call passes an explicit variant (outside the allowlist)", () => {
    expect(unlisted(offendersByFile(), ALLOWLIST)).toEqual([]);
  });

  it("no allowlist entry is stale (each listed file still has a call without variant)", () => {
    expect(stale(offendersByFile(), ALLOWLIST)).toEqual([]);
  });
});
