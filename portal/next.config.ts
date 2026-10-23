import type { NextConfig } from "next";

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
    // Phase 87 S10 (617A, arch §12.8): the portal's Matters route segment is /matters.
    // `:path*` matches zero or more segments, so /projects and /projects/<id> are both covered;
    // `permanent: true` → HTTP 308. Keep for one release after Phase 87 (never delete in 87).
    return [
      {
        source: "/projects/:path*",
        destination: "/matters/:path*",
        permanent: true,
      },
    ];
  },
};

export default nextConfig;
