import fs from "node:fs";
import path from "node:path";
import { describe, it, expect } from "vitest";
import nextConfig from "@/next.config";

// Phase 87 S10 (617A, arch §12.8): the portal's first redirects() block.
describe("portal next.config redirects", () => {
  it("permanently redirects the old /projects segment to /matters", async () => {
    expect(await nextConfig.redirects!()).toEqual([
      {
        source: "/projects/:path*",
        destination: "/matters/:path*",
        permanent: true,
      },
    ]);
  });

  it("serves Matters from app/(authenticated)/matters — the old segment has no page", () => {
    const group = path.resolve(__dirname, "../app/(authenticated)");
    expect(fs.existsSync(path.join(group, "projects"))).toBe(false);
    expect(fs.existsSync(path.join(group, "matters", "page.tsx"))).toBe(true);
    expect(fs.existsSync(path.join(group, "matters", "[id]", "page.tsx"))).toBe(
      true,
    );
  });
});
