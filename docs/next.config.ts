import nextra from "nextra";

const withNextra = nextra({
  contentDirBasePath: "/",
});

export default withNextra({
  reactStrictMode: true,
  // Static export — the docs are pure MDX content (no API routes / server
  // actions), so they ship as static HTML served by Caddy on the VPS. See
  // docs/Dockerfile (Nextra build -> pagefind index -> caddy:alpine static).
  output: "export",
  images: { unoptimized: true },
});
