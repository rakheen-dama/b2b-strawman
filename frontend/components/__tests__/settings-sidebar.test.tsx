import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";

// SettingsSidebar reads the pathname (active family / kept sub-route) and ?tab= (active tab).
vi.mock("next/navigation", () => ({
  usePathname: vi.fn(() => "/org/acme/settings/firm"),
  useSearchParams: vi.fn(() => new URLSearchParams()),
}));

import { usePathname, useSearchParams } from "next/navigation";
import { SettingsSidebar } from "@/components/settings/settings-sidebar";
import { CapabilityProvider } from "@/lib/capabilities";
import { OrgProfileProvider } from "@/lib/org-profile";
import {
  getSettingsFamily,
  settingsAccessFrom,
  visibleSettingsTabs,
  type SettingsFamilyId,
} from "@/lib/settings-nav";

const mockUsePathname = vi.mocked(usePathname);
const mockUseSearchParams = vi.mocked(useSearchParams);

interface Who {
  capabilities?: string[];
  isAdmin?: boolean;
  isOwner?: boolean;
  enabledModules?: string[];
}

const OWNER: Who = { isOwner: true };
const ADMIN: Who = { isAdmin: true };
const MEMBER: Who = {};
const ALL_MODULES = ["bulk_billing", "trust_accounting"];

/** Render the rail at `pathname` (+ optional `?tab=`) as `who`. */
function renderRail(
  pathname: string,
  { capabilities = [], isAdmin = false, isOwner = false, enabledModules = ALL_MODULES }: Who = {},
  tab?: string
) {
  mockUsePathname.mockReturnValue(pathname);
  mockUseSearchParams.mockReturnValue(
    new URLSearchParams(tab ? { tab } : {}) as unknown as ReturnType<typeof useSearchParams>
  );
  return render(
    <OrgProfileProvider
      verticalProfile={null}
      enabledModules={enabledModules}
      terminologyNamespace={null}
    >
      <CapabilityProvider
        capabilities={capabilities}
        role={isOwner ? "owner" : isAdmin ? "admin" : "member"}
        isAdmin={isAdmin}
        isOwner={isOwner}
      >
        <SettingsSidebar slug="acme" />
      </CapabilityProvider>
    </OrgProfileProvider>
  );
}

const rail = () => screen.getByRole("navigation", { name: "Settings navigation" });
const FAMILY_LABELS = ["Firm", "Matters", "Documents", "Fees & Trust", "Connections"];

/** The rail's tab links (every link that is not a family link), in DOM order. */
function tabLinks() {
  return within(rail())
    .getAllByRole("link")
    .filter((a) => !FAMILY_LABELS.includes(a.textContent ?? ""));
}

beforeEach(() => {
  mockUseSearchParams.mockReturnValue(
    new URLSearchParams() as unknown as ReturnType<typeof useSearchParams>
  );
});

afterEach(() => {
  cleanup();
});

describe("SettingsSidebar — the five families from SETTINGS_FAMILIES (616E)", () => {
  it("lists the five family links in order, with no tabs and no headings", () => {
    renderRail("/org/acme/settings", OWNER);
    const links = within(rail()).getAllByRole("link");
    expect(links.map((a) => a.textContent)).toEqual(FAMILY_LABELS);
    expect(links.map((a) => a.getAttribute("href"))).toEqual([
      "/org/acme/settings/firm",
      "/org/acme/settings/matters",
      "/org/acme/settings/documents",
      "/org/acme/settings/fees",
      "/org/acme/settings/connections",
    ]);
    // No family is active on the settings index, so no tab links render.
    for (const a of links) expect(a).not.toHaveAttribute("aria-current");
    expect(within(rail()).queryAllByRole("tab")).toEqual([]);
    expect(within(rail()).queryAllByRole("heading")).toEqual([]);
  });

  it("marks the active family aria-current=page and lists only its permitted tabs", () => {
    renderRail("/org/acme/settings/fees", OWNER);
    expect(screen.getByRole("link", { name: "Fees & Trust" })).toHaveAttribute(
      "aria-current",
      "page"
    );
    for (const label of ["Firm", "Matters", "Documents", "Connections"]) {
      expect(screen.getByRole("link", { name: label })).not.toHaveAttribute("aria-current");
    }
    expect(tabLinks().map((a) => a.getAttribute("href"))).toEqual([
      "/org/acme/settings/fees?tab=tariffs",
      "/org/acme/settings/fees?tab=tax",
      "/org/acme/settings/fees?tab=time-recording",
      "/org/acme/settings/fees?tab=fee-runs",
      "/org/acme/settings/fees?tab=collections",
      "/org/acme/settings/fees?tab=trust-accounting",
    ]);
    expect(screen.queryByRole("link", { name: "Roles" })).not.toBeInTheDocument();
    // Exactly one aria-current="page" in the rail.
    expect(
      within(rail())
        .getAllByRole("link")
        .filter((a) => a.getAttribute("aria-current") === "page")
    ).toHaveLength(1);
  });

  it("marks the ?tab= tab current, resolved exactly as the family page resolves it", () => {
    renderRail("/org/acme/settings/firm", OWNER, "roles");
    expect(screen.getByRole("link", { name: "Roles" })).toHaveAttribute("aria-current", "true");
    expect(screen.getByRole("link", { name: "General" })).not.toHaveAttribute("aria-current");
    cleanup();

    renderRail("/org/acme/settings/firm", OWNER);
    expect(screen.getByRole("link", { name: "General" })).toHaveAttribute("aria-current", "true");
    cleanup();

    // A forbidden ?tab= falls back to the first permitted tab, same as the page (D4).
    renderRail("/org/acme/settings/firm", MEMBER, "roles");
    expect(screen.getByRole("link", { name: "General" })).toHaveAttribute("aria-current", "true");
  });

  it("leaves admin-only tabs absent (not disabled) for a member", () => {
    renderRail("/org/acme/settings/firm", MEMBER);
    expect(tabLinks().map((a) => a.textContent)).toEqual(["General", "Subscription"]);
    expect(within(rail()).queryByText("Roles")).not.toBeInTheDocument();
    expect(rail().querySelector("[aria-disabled]")).toBeNull();
    cleanup();

    // D5: a member holding TEAM_OVERSIGHT also sees Audit trail.
    renderRail("/org/acme/settings/firm", { capabilities: ["TEAM_OVERSIGHT"] });
    expect(tabLinks().map((a) => a.textContent)).toEqual([
      "General",
      "Audit trail",
      "Subscription",
    ]);
  });

  it("hides module-gated tabs when the org has the module off", () => {
    renderRail("/org/acme/settings/fees", { ...OWNER, enabledModules: [] });
    expect(screen.queryByRole("link", { name: "Fee runs" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Trust accounting" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Collections" })).toBeInTheDocument();
  });

  it("marks the family and its tab on a kept sub-route (matters/checklists/[id]/edit)", () => {
    renderRail("/org/acme/settings/matters/checklists/abc/edit", OWNER);
    expect(screen.getByRole("link", { name: "Matters" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "Checklists" })).toHaveAttribute(
      "aria-current",
      "true"
    );
    expect(screen.getByRole("link", { name: "Templates" })).not.toHaveAttribute("aria-current");
  });

  it("marks Connections › Xero on /settings/connections/xero/sync-log", () => {
    renderRail("/org/acme/settings/connections/xero/sync-log", OWNER);
    expect(screen.getByRole("link", { name: "Connections" })).toHaveAttribute(
      "aria-current",
      "page"
    );
    expect(screen.getByRole("link", { name: "Xero" })).toHaveAttribute("aria-current", "true");
  });

  it("has no Security or Coming soon entry", () => {
    for (const family of ["firm", "connections"]) {
      renderRail(`/org/acme/settings/${family}`, OWNER);
      expect(screen.queryByText(/security/i)).not.toBeInTheDocument();
      expect(screen.queryByText(/coming soon/i)).not.toBeInTheDocument();
      cleanup();
    }
  });

  // One source: the rail lists exactly the tabs the family page's strip renders
  // (components/settings/settings-family-page.tsx → visibleSettingsTabs(settingsAccessFrom(…))).
  describe("parity with the family page's tab strip", () => {
    const WHO: Record<string, Who & { capabilities: string[] }> = {
      owner: { ...OWNER, capabilities: [] },
      admin: { ...ADMIN, capabilities: [] },
      member: { ...MEMBER, capabilities: [] },
      "member + INTEGRATION_MANAGE": { capabilities: ["INTEGRATION_MANAGE"] },
    };
    const FAMILIES: SettingsFamilyId[] = ["firm", "matters", "documents", "fees", "connections"];

    for (const [name, who] of Object.entries(WHO)) {
      it(`matches the page's tabs on every family for ${name}`, () => {
        for (const id of FAMILIES) {
          renderRail(`/org/acme/settings/${id}`, who);
          const expected = visibleSettingsTabs(
            getSettingsFamily(id),
            settingsAccessFrom(
              {
                capabilities: who.capabilities,
                isAdmin: who.isAdmin ?? false,
                isOwner: who.isOwner ?? false,
              },
              ALL_MODULES
            )
          ).map((t) => t.label);
          expect(
            tabLinks().map((a) => a.textContent),
            `${name} / ${id}`
          ).toEqual(expected);
          cleanup();
        }
      });
    }
  });
});
