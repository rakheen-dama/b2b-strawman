import type { NextConfig } from "next";
import { LEGACY_REDIRECTS } from "./lib/legacy-redirects";
import { SERVER_ACTION_BODY_LIMIT } from "./lib/server-action-upload";

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
    // FR-3: 3 MB, shared by every server action that takes a file (take-on, bank statements);
    // the file limit sits under it with headroom (lib/server-action-upload.ts). Default is 1 MB.
    serverActions: { bodySizeLimit: SERVER_ACTION_BODY_LIMIT },
  },
  async redirects() {
    // One committed table (Phase 87 §12.7.4 / §12.10.5): lib/legacy-redirects.ts.
    // `permanent: true` rows are served as HTTP 308.
    return [...LEGACY_REDIRECTS];
  },
};

export default nextConfig;
