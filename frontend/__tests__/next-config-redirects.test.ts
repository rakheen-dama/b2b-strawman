import { describe, it, expect } from "vitest";
import nextConfig from "../next.config";

/**
 * GAP-Doc-Drift-26: legacy /org/:slug/settings/team must redirect to the
 * canonical /org/:slug/team with a permanent (301) redirect so that
 * old bookmarks and QA scenario docs don't 404.
 */
describe("next.config redirects", () => {
  it("declares an async redirects() function", () => {
    expect(typeof nextConfig.redirects).toBe("function");
  });

  it("includes a permanent redirect from /org/:slug/settings/team to /org/:slug/team", async () => {
    const redirects = await nextConfig.redirects!();

    const teamRedirect = redirects.find((r) => r.source === "/org/:slug/settings/team");

    expect(teamRedirect).toBeDefined();
    expect(teamRedirect).toMatchObject({
      source: "/org/:slug/settings/team",
      destination: "/org/:slug/team",
      permanent: true,
    });
  });

  it("does not redirect the canonical /org/:slug/team route", async () => {
    const redirects = await nextConfig.redirects!();

    const canonicalRedirect = redirects.find((r) => r.source === "/org/:slug/team");

    expect(canonicalRedirect).toBeUndefined();
  });

  it("does not redirect unrelated /org/:slug/settings/* paths", async () => {
    const redirects = await nextConfig.redirects!();

    // Only the /settings/team subpath should be redirected. Other settings
    // children (e.g. /settings/billing) must remain untouched.
    const otherSettings = redirects.find(
      (r) =>
        r.source === "/org/:slug/settings" ||
        r.source === "/org/:slug/settings/:path*" ||
        r.source === "/org/:slug/settings/billing"
    );

    expect(otherSettings).toBeUndefined();
  });
});
