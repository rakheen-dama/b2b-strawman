import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  transpilePackages: ["@b2mash/ui", "@b2mash/shared"],
};

export default nextConfig;
