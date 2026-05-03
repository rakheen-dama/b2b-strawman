import { describe, it, expect } from "vitest";

import { PRESET_OPTIONS, resolvePreset, type PresetName } from "../presets";
import type { AuditEventTypeMetadata } from "@/lib/api/audit-events";

const NOW = new Date("2026-05-03T12:00:00.000Z");

const METADATA: AuditEventTypeMetadata[] = [
  {
    eventType: "security.login.failure",
    label: "Login failure",
    severity: "WARNING",
    group: "SECURITY",
  },
  {
    eventType: "security.password.reset",
    label: "Password reset",
    severity: "NOTICE",
    group: "SECURITY",
  },
  {
    eventType: "compliance.consent.granted",
    label: "Consent granted",
    severity: "INFO",
    group: "COMPLIANCE",
  },
  { eventType: "customer.created", label: "Customer created", severity: "INFO", group: "STANDARD" },
];

describe("PRESET_OPTIONS", () => {
  it("exposes all four presets", () => {
    const values = PRESET_OPTIONS.map((o) => o.value).sort();
    expect(values).toEqual(["compliance", "financial-approvals", "security", "sensitive"]);
  });
});

describe("resolvePreset", () => {
  it("Sensitive: severities=WARNING,CRITICAL and from = 30 days ago, not a group preset", () => {
    const r = resolvePreset("sensitive", METADATA, NOW);
    expect(r.severities).toEqual(["WARNING", "CRITICAL"]);
    expect(r.from).toBe("2026-04-03T12:00:00.000Z");
    expect(r.eventTypes).toBeUndefined();
    expect(r.isGroupPreset).toBeUndefined();
  });

  it("group presets (Compliance/Security/Financial) are flagged as group presets", () => {
    expect(resolvePreset("compliance", METADATA, NOW).isGroupPreset).toBe(true);
    expect(resolvePreset("security", METADATA, NOW).isGroupPreset).toBe(true);
    expect(resolvePreset("financial-approvals", METADATA, NOW).isGroupPreset).toBe(true);
  });

  it("Compliance: from = 90 days ago, eventTypes from group=COMPLIANCE", () => {
    const r = resolvePreset("compliance", METADATA, NOW);
    expect(r.from).toBe("2026-02-02T12:00:00.000Z");
    expect(r.eventTypes).toEqual(["compliance.consent.granted"]);
    expect(r.severities).toBeUndefined();
  });

  it("Security: from = 7 days ago, eventTypes from group=SECURITY", () => {
    const r = resolvePreset("security", METADATA, NOW);
    expect(r.from).toBe("2026-04-26T12:00:00.000Z");
    expect(r.eventTypes).toEqual(["security.login.failure", "security.password.reset"]);
  });

  it("Financial approvals: fixed event-type list, from = 30 days ago", () => {
    const r = resolvePreset("financial-approvals", METADATA, NOW);
    expect(r.from).toBe("2026-04-03T12:00:00.000Z");
    expect(r.eventTypes).toEqual([
      "trust.transaction.approved",
      "trust.transaction.rejected",
      "invoice.sent",
      "invoice.voided",
    ]);
  });

  it("Compliance with empty metadata: eventTypes is empty array", () => {
    const r = resolvePreset("compliance", [], NOW);
    expect(r.eventTypes).toEqual([]);
  });

  it("is exhaustive over PresetName", () => {
    for (const opt of PRESET_OPTIONS) {
      const r = resolvePreset(opt.value as PresetName, METADATA, NOW);
      expect(r.from).toBeTruthy();
    }
  });
});
