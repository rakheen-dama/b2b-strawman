import fs from "node:fs";
import path from "node:path";
import { describe, it, expect } from "vitest";

// G89-F46: `next/font/google` downloads the fonts during `next build`; that fetch failed the CI
// Portal job (#1747) and the login-bare portal build (#1750). The fonts are self-hosted with
// `next/font/local` from committed woff2 files (app/fonts/), so a production build needs no
// network. Twin of frontend/__tests__/self-hosted-fonts.test.ts.

const PORTAL_ROOT = path.resolve(__dirname, "..");
const LAYOUT = path.join(PORTAL_ROOT, "app/layout.tsx");
const SCAN_ROOTS = ["app", "components", "lib", "hooks"];

function collectSourceFiles(dir: string, out: string[] = []): string[] {
  if (!fs.existsSync(dir)) return out;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === "__tests__" || entry.name === "node_modules") continue;
      collectSourceFiles(full, out);
    } else if (/\.tsx?$/.test(entry.name)) {
      out.push(full);
    }
  }
  return out;
}

describe("self-hosted fonts (G89-F46)", () => {
  it("no source file imports next/font/google", () => {
    const hits = SCAN_ROOTS.flatMap((root) =>
      collectSourceFiles(path.join(PORTAL_ROOT, root)),
    )
      .filter((file) =>
        /["']next\/font\/google["']/.test(fs.readFileSync(file, "utf8")),
      )
      .map((file) => path.relative(PORTAL_ROOT, file));
    expect(hits).toEqual([]);
  });

  it("the root layout loads every family from a committed woff2 with its OFL licence", () => {
    const source = fs.readFileSync(LAYOUT, "utf8");
    expect(source).toMatch(/from "next\/font\/local"/);
    const sources = [...source.matchAll(/path:\s*"([^"]+\.woff2)"/g)].map(
      (m) => m[1],
    );
    // Sora (variable), IBM Plex Sans 400/500/600/700, JetBrains Mono (variable).
    expect(sources).toHaveLength(6);
    for (const src of sources) {
      const file = path.resolve(path.dirname(LAYOUT), src);
      expect(fs.existsSync(file), `${src} is committed`).toBe(true);
      expect(
        fs.existsSync(path.join(path.dirname(file), "OFL.txt")),
        `OFL.txt beside ${src}`,
      ).toBe(true);
    }
  });

  it("keeps the CSS variables the theme reads", () => {
    const source = fs.readFileSync(LAYOUT, "utf8");
    for (const variable of ["--font-sora", "--font-plex", "--font-jetbrains"]) {
      expect(source).toContain(`variable: "${variable}"`);
    }
  });
});
