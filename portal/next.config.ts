import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  transpilePackages: ["@b2mash/ui", "@b2mash/shared"],
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
