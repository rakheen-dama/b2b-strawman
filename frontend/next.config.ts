import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  async redirects() {
    return [
      // GAP-Doc-Drift-26: Legacy bookmarks and QA scenario docs reference
      // /org/:slug/settings/team but the canonical route is /org/:slug/team.
      // Permanent (HTTP 301) so browsers cache the new URL.
      {
        source: "/org/:slug/settings/team",
        destination: "/org/:slug/team",
        permanent: true,
      },
    ];
  },
};

export default nextConfig;
