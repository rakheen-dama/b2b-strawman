import type { NextConfig } from "next";
import { LEGACY_REDIRECTS } from "./lib/legacy-redirects";

const nextConfig: NextConfig = {
  output: "standalone",
  // Next 16.3 `next dev` writes/upserts AGENTS.md + CLAUDE.md in the app dir when it
  // detects an AI agent. Our agent docs are curated (frontend/CLAUDE.md); keep dev
  // runs from touching tracked files.
  agentRules: false,
  transpilePackages: ["@b2mash/ui", "@b2mash/shared"],
  experimental: {
    // Next 16.3 defaults this to true, which runs `tsc` over the whole tsconfig project
    // during `next build`. The API checker (16.2 behaviour) skips diagnostics in
    // `__tests__/` and `*.test.*` / `*.spec.*` files; keep that build scope.
    useTypeScriptCli: false,
  },
  async redirects() {
    // One committed table (Phase 87 §12.7.4 / §12.10.5): lib/legacy-redirects.ts.
    // `permanent: true` rows are served as HTTP 308.
    return [...LEGACY_REDIRECTS];
  },
};

export default nextConfig;
