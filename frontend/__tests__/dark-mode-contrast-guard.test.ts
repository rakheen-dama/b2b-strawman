import fs from "node:fs";
import path from "node:path";
import { describe, it, expect } from "vitest";
import ts from "typescript";
import { FRONTEND_ROOT, readSourceTree } from "./helpers/source-scan";
import { nativeSelectClassName } from "@/lib/styles/native-select";

// Phase 90 dark-mode debt (644B closure report, C.5):
// - D-3: form fields (native <select>, inputs, textareas) styled "border-slate-200 bg-transparent …
//   dark:border-slate-800" have a 1.15:1 border and no fill on the dark card, so they nearly vanish
//   next to the shared Input. They must carry the Input recipe's dark fill (`dark:bg-input/30`).
// - D-4: muted text "text-slate-400 dark:text-slate-600" is 2.35–2.62:1 on the dark card (and about
//   2.6:1 on white). Use the `text-muted-foreground` token instead.
// - D-5: "Connect to Xero" was white on Xero blue (2.38:1 in both themes); it keeps the brand fill
//   with near-black text.

const SCAN_ROOTS = ["app", "components", "lib"] as const;

const token = (t: string) => new RegExp(`(?<![\\w:/-])${t.replace(/[/[\]]/g, "\\$&")}(?![\\w/-])`);
const BG_TRANSPARENT = token("bg-transparent");
const BORDER_SLATE_200 = token("border-slate-200");
const DARK_FIELD_BORDER = /(?<![\w:/-])dark:border-slate-(?:700|800)(?![\w/-])/;
const MUTED_LIGHT = token("text-slate-400");
const MUTED_DARK = token("dark:text-slate-600");

/** A field-chrome class string with a light border, no fill, a dark border and no dark fill. */
function isUnfilledDarkField(line: string): boolean {
  return (
    BG_TRANSPARENT.test(line) &&
    BORDER_SLATE_200.test(line) &&
    DARK_FIELD_BORDER.test(line) &&
    !line.includes("dark:bg-input/30")
  );
}

/** The low-contrast muted-text pair. */
function isLowContrastMuted(line: string): boolean {
  return MUTED_LIGHT.test(line) && MUTED_DARK.test(line);
}

function offendingLines(match: (line: string) => boolean): string[] {
  return readSourceTree(SCAN_ROOTS).flatMap(({ rel, text }) =>
    text
      .split("\n")
      .map((line, i) => (match(line) ? `${rel}:${i + 1}` : null))
      .filter((hit): hit is string => hit !== null)
  );
}

// Forced-light routes (outside /org/, ADR-371 §15.3): never painted dark, so exempt.
const FORCED_LIGHT_FILES = new Set(["components/auth/mock-login-form.tsx"]);

const MAX_VARIANTS = 64;

/** Cartesian join of class-string alternatives, capped so pathological expressions stay cheap. */
function product(parts: string[][]): string[] {
  return parts
    .reduce<string[]>((acc, alts) => acc.flatMap((a) => alts.map((b) => `${a} ${b}`.trim())), [""])
    .slice(0, MAX_VARIANTS);
}

/**
 * Every native <select>/<textarea> JSX element in one source file, with each class string it can
 * end up with. A conditional keeps its branches apart (`c ? "a" : "b"` -> ["a", "b"]; `c && "a"`
 * -> ["", "a"]), so a branch with the dark fill cannot mask a branch without it. Classes are
 * resolved through string and template literals, `cn(...)` / arrays / object keys, the shared
 * `nativeSelectClassName`, and same-file constants (`const x = "..."` or `useMemo(() => "...")`).
 */
function nativeFieldsIn(rel: string, text: string): { where: string; variants: string[] }[] {
  const out: { where: string; variants: string[] }[] = [];
  const sf = ts.createSourceFile(rel, text, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  const consts = new Map<string, string[]>();
  const variants = (n: ts.Node): string[] => {
    if (ts.isStringLiteral(n) || ts.isNoSubstitutionTemplateLiteral(n)) return [n.text];
    if (ts.isTemplateExpression(n))
      return product([
        [n.head.text],
        ...n.templateSpans.flatMap((sp) => [variants(sp.expression), [sp.literal.text]]),
      ]);
    if (ts.isIdentifier(n)) {
      if (n.text === "nativeSelectClassName") return [nativeSelectClassName];
      return consts.get(n.text) ?? [""];
    }
    if (ts.isConditionalExpression(n)) return [...variants(n.whenTrue), ...variants(n.whenFalse)];
    if (ts.isBinaryExpression(n)) {
      const op = n.operatorToken.kind;
      if (op === ts.SyntaxKind.AmpersandAmpersandToken) return ["", ...variants(n.right)];
      if (op === ts.SyntaxKind.BarBarToken || op === ts.SyntaxKind.QuestionQuestionToken)
        return [...variants(n.left), ...variants(n.right)];
      if (op === ts.SyntaxKind.PlusToken) return product([variants(n.left), variants(n.right)]);
    }
    if (ts.isParenthesizedExpression(n) || ts.isJsxExpression(n) || ts.isAsExpression(n))
      return n.expression ? variants(n.expression) : [""];
    if (ts.isArrowFunction(n)) {
      if (!ts.isBlock(n.body)) return variants(n.body);
      const returns: string[] = [];
      const walk = (x: ts.Node) => {
        if (ts.isReturnStatement(x) && x.expression) returns.push(...variants(x.expression));
        else if (!ts.isFunctionLike(x)) ts.forEachChild(x, walk);
      };
      ts.forEachChild(n.body, walk);
      return returns.length ? returns : [""];
    }
    if (ts.isCallExpression(n)) return product(n.arguments.map(variants));
    if (ts.isArrayLiteralExpression(n)) return product(n.elements.map(variants));
    if (ts.isObjectLiteralExpression(n))
      return product(
        n.properties.map((p) =>
          p.name && (ts.isStringLiteral(p.name) || ts.isIdentifier(p.name))
            ? ["", p.name.text]
            : [""]
        )
      );
    // Anything else: combine whatever class text appears inside it.
    const kids: string[][] = [];
    ts.forEachChild(n, (c) => {
      kids.push(variants(c));
    });
    return kids.length ? product(kids) : [""];
  };
  const collect = (n: ts.Node) => {
    if (ts.isVariableDeclaration(n) && ts.isIdentifier(n.name) && n.initializer)
      consts.set(n.name.text, variants(n.initializer));
    ts.forEachChild(n, collect);
  };
  collect(sf);
  const visit = (n: ts.Node) => {
    if (
      (ts.isJsxOpeningElement(n) || ts.isJsxSelfClosingElement(n)) &&
      ["select", "textarea"].includes(n.tagName.getText(sf))
    ) {
      const attr = n.attributes.properties.find(
        (p) => ts.isJsxAttribute(p) && p.name.getText(sf) === "className"
      ) as ts.JsxAttribute | undefined;
      const line = sf.getLineAndCharacterOfPosition(n.getStart(sf)).line + 1;
      out.push({
        where: `${rel}:${line}`,
        variants: attr?.initializer ? [...new Set(variants(attr.initializer))] : [""],
      });
    }
    ts.forEachChild(n, visit);
  };
  visit(sf);
  return out;
}

const HAS_BORDER = /(?<![\w:/-])border(?:-|\b)/;

/** A field fails if its classes are unresolvable, or any bordered alternative lacks the dark fill. */
function unfilledField({ variants }: { variants: string[] }): boolean {
  if (variants.every((v) => v.trim() === "")) return true;
  return variants.some((v) => HAS_BORDER.test(v) && !v.includes("dark:bg-input/30"));
}

function nativeFields(): { where: string; variants: string[] }[] {
  return readSourceTree(SCAN_ROOTS)
    .filter(({ rel, text }) => rel.endsWith(".tsx") && /<(select|textarea)\b/.test(text))
    .flatMap(({ rel, text }) => nativeFieldsIn(rel, text));
}

describe("dark-mode contrast guard (Phase 90 debt D-3, D-4, D-5)", () => {
  it("self-test: recognises the old patterns and accepts the fixed ones", () => {
    const oldField =
      'className="flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 dark:border-slate-800"';
    expect(isUnfilledDarkField(oldField)).toBe(true);
    expect(
      isUnfilledDarkField(oldField.replace("dark:border-slate-800", "dark:border-slate-700"))
    ).toBe(true);
    expect(
      isUnfilledDarkField(
        oldField.replace("dark:border-slate-800", "dark:border-slate-700 dark:bg-input/30")
      )
    ).toBe(false);
    // Table rows use hover:bg-transparent; they are not fields.
    expect(
      isUnfilledDarkField(
        '<TableRow className="border-slate-200 hover:bg-transparent dark:border-slate-800">'
      )
    ).toBe(false);

    // Element scan: each conditional branch is checked on its own.
    const scan = (jsx: string) =>
      nativeFieldsIn("self-test.tsx", `const C = () => (${jsx});`).map(unfilledField);
    expect(scan('<select className="h-9 border dark:bg-input/30" />')).toEqual([false]);
    expect(scan('<select className="h-9 border" />')).toEqual([true]);
    expect(scan('<select className={on ? "border dark:bg-input/30" : "border"} />')).toEqual([
      true,
    ]);
    expect(
      scan('<select className={on ? "border dark:bg-input/30" : "border dark:bg-input/30"} />')
    ).toEqual([false]);
    expect(
      scan('<select className={cn("h-9 border dark:bg-input/30", err && "border-red-500")} />')
    ).toEqual([false]);
    expect(scan('<select className={cn("h-9 border", ok && "dark:bg-input/30")} />')).toEqual([
      true,
    ]);
    expect(scan("<select className={props.className} />")).toEqual([true]);

    expect(isLowContrastMuted('className="text-sm text-slate-400 dark:text-slate-600"')).toBe(true);
    expect(isLowContrastMuted('className="text-slate-400 italic dark:text-slate-600"')).toBe(true);
    expect(isLowContrastMuted('className="text-sm text-muted-foreground"')).toBe(false);
    // Decorative empty-state icons (text-slate-300) are out of scope.
    expect(isLowContrastMuted('className="size-10 text-slate-300 dark:text-slate-600"')).toBe(
      false
    );
  });

  it("D-3: no form field keeps an unfilled, near-invisible border in dark mode", () => {
    expect(offendingLines(isUnfilledDarkField)).toEqual([]);
  });

  it("D-3: every native select/textarea with a border carries the Input dark fill", () => {
    const fields = nativeFields();
    expect(fields.length, "the scan finds the app's native fields").toBeGreaterThan(70);
    const missing = fields
      .filter(({ where }) => !FORCED_LIGHT_FILES.has(where.replace(/:\d+$/, "")))
      .filter(unfilledField)
      .map(
        ({ where, variants }) => `${where} ${JSON.stringify(variants.map((v) => v.slice(0, 50)))}`
      );
    expect(missing).toEqual([]);
  });

  it("D-3: the shared native-select recipe matches the Input primitive in dark mode", () => {
    expect(nativeSelectClassName).toContain("dark:bg-input/30");
    expect(nativeSelectClassName).toContain("dark:border-slate-700");
    expect(nativeSelectClassName).not.toContain("dark:border-slate-800");
  });

  it("D-4: no element uses the text-slate-400 / dark:text-slate-600 muted pair", () => {
    expect(offendingLines(isLowContrastMuted)).toEqual([]);
  });

  it("D-5: the Connect to Xero button keeps the brand fill with near-black text", () => {
    const source = fs.readFileSync(
      path.join(FRONTEND_ROOT, "components/integrations/XeroConnectionCard.tsx"),
      "utf8"
    );
    // The solid brand fill only (not the bg-[#13B5EA]/10 icon tile).
    const connect = source.match(/className="([^"]*(?<![\w:/-])bg-\[#13B5EA\](?![\w/-])[^"]*)"/);
    expect(connect, "the brand-filled Connect button").not.toBeNull();
    expect(connect![1]).toContain("text-slate-950");
    expect(connect![1]).not.toMatch(/(?<![\w:/-])text-white(?![\w/-])/);
  });
});
