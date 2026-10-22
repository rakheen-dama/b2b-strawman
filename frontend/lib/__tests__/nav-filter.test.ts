import { describe, it, expect } from "vitest";
import {
  canAccessNavItem,
  isNavEntryVisible,
  type NavAccess,
  type NavFilterAccess,
} from "../nav-filter";

/** A plain (non-admin, non-owner) user holding exactly `caps`. */
function member(caps: string[]): NavAccess {
  const set = new Set(caps);
  return { hasCapability: (c) => set.has(c), isAdmin: false, isOwner: false };
}

/** `access` plus an org with exactly `modules` enabled. */
function withModules(access: NavAccess, modules: string[]): NavFilterAccess {
  const set = new Set(modules);
  return { ...access, isModuleEnabled: (m) => set.has(m) };
}

// Moved verbatim from lib/__tests__/nav-items.test.ts with canAccessNavItem (616E).
describe("canAccessNavItem", () => {
  it("allows ungated items to everyone", () => {
    expect(canAccessNavItem({}, member([]))).toBe(true);
  });

  it("checks a single capability", () => {
    expect(canAccessNavItem({ requiredCapability: "INVOICING" }, member(["INVOICING"]))).toBe(true);
    expect(canAccessNavItem({ requiredCapability: "INVOICING" }, member([]))).toBe(false);
  });

  it("treats an array as any-of", () => {
    const gate = { requiredCapability: ["PROJECT_MANAGEMENT", "AI_EXECUTE"] as const };
    expect(canAccessNavItem(gate, member(["AI_EXECUTE"]))).toBe(true);
    expect(canAccessNavItem(gate, member(["PROJECT_MANAGEMENT"]))).toBe(true);
    expect(canAccessNavItem(gate, member(["INVOICING"]))).toBe(false);
  });

  it("shows adminOnly items to admins and owners only", () => {
    const gate = { adminOnly: true };
    expect(canAccessNavItem(gate, member(["INVOICING"]))).toBe(false);
    expect(canAccessNavItem(gate, { ...member([]), isAdmin: true })).toBe(true);
    expect(canAccessNavItem(gate, { ...member([]), isOwner: true })).toBe(true);
  });
});

describe("isNavEntryVisible — capability + admin-only + module (616E, one rule for every surface)", () => {
  it("hides a module-gated entry when the module is off", () => {
    expect(
      isNavEntryVisible({ requiredModule: "trust_accounting" }, withModules(member([]), []))
    ).toBe(false);
  });

  it("shows a module-gated entry when the module is on", () => {
    expect(
      isNavEntryVisible(
        { requiredModule: "trust_accounting" },
        withModules(member([]), ["trust_accounting"])
      )
    ).toBe(true);
  });

  it("still needs the capability when the module is on", () => {
    const gate = { requiredModule: "trust_accounting", requiredCapability: "VIEW_TRUST" };
    expect(isNavEntryVisible(gate, withModules(member([]), ["trust_accounting"]))).toBe(false);
    expect(isNavEntryVisible(gate, withModules(member(["VIEW_TRUST"]), ["trust_accounting"]))).toBe(
      true
    );
  });

  it("shows an adminOnly + module entry to an owner only when the module is on", () => {
    const gate = { adminOnly: true, requiredModule: "bulk_billing" };
    const owner = { ...member([]), isOwner: true };
    expect(isNavEntryVisible(gate, withModules(owner, []))).toBe(false);
    expect(isNavEntryVisible(gate, withModules(owner, ["bulk_billing"]))).toBe(true);
    expect(isNavEntryVisible(gate, withModules(member([]), ["bulk_billing"]))).toBe(false);
  });

  it("shows an ungated entry to everyone", () => {
    expect(isNavEntryVisible({}, withModules(member([]), []))).toBe(true);
  });
});
