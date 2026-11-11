import fs from "node:fs";
import path from "node:path";
import { describe, it, expect } from "vitest";
import { FRONTEND_ROOT, readSourceTree } from "./helpers/source-scan";

// G89-F46: `next/font/google` downloads the fonts during `next build`; that fetch failed the CI
// Portal job (#1747), the Checkpoint 1 e2e frontend build and the login-bare portal build (#1750).
// The fonts are self-hosted with `next/font/local` from committed woff2 files (app/fonts/), so a
// production build needs no network. Portal twin: portal/__tests__/self-hosted-fonts.test.ts.

const LAYOUT = path.join(FRONTEND_ROOT, "app/layout.tsx");

function localFontSources(source: string): string[] {
  return [...source.matchAll(/path:\s*"([^"]+\.woff2)"/g)].map((m) => m[1]);
}

describe("self-hosted fonts (G89-F46)", () => {
  it("no source file imports next/font/google", () => {
    const hits = readSourceTree(["app", "components", "lib", "hooks"])
      .filter(({ text }) => /["']next\/font\/google["']/.test(text))
      .map(({ rel }) => rel);
    expect(hits).toEqual([]);
  });

  it("the root layout loads every family from a committed woff2 with its OFL licence", () => {
    const source = fs.readFileSync(LAYOUT, "utf8");
    expect(source).toMatch(/from "next\/font\/local"/);
    const sources = localFontSources(source);
    // Sora (variable), IBM Plex Sans 400/500/600/700, JetBrains Mono (variable).
    expect(sources).toHaveLength(6);
    for (const src of sources) {
      const file = path.resolve(path.dirname(LAYOUT), src);
      expect(fs.existsSync(file), `${src} is committed`).toBe(true);
      expect(fs.existsSync(path.join(path.dirname(file), "OFL.txt")), `OFL.txt beside ${src}`).toBe(
        true
      );
    }
  });

  it("keeps the CSS variables the theme reads", () => {
    const source = fs.readFileSync(LAYOUT, "utf8");
    for (const variable of ["--font-sora", "--font-plex", "--font-jetbrains"]) {
      expect(source).toContain(`variable: "${variable}"`);
    }
  });
});
