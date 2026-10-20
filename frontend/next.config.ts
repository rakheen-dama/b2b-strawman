import type { NextConfig } from "next";
import { LEGACY_REDIRECTS } from "./lib/legacy-redirects";

const nextConfig: NextConfig = {
  output: "standalone",
  transpilePackages: ["@b2mash/ui", "@b2mash/shared"],
  async redirects() {
    // One committed table (Phase 87 §12.7.4 / §12.10.5): lib/legacy-redirects.ts.
    // `permanent: true` rows are served as HTTP 308.
    return [...LEGACY_REDIRECTS];
  },
};

export default nextConfig;
