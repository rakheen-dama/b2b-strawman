import fs from "node:fs";
import path from "node:path";
import { describe, it, expect } from "vitest";
import { SUGGESTED_WORK_TYPES } from "@/lib/constants/work-types";

// Drift guard: the suggestions mirror backend seed data. If a template pack gains a new
// matterType, or a field pack scopes a group to a new work type, the suggestion list must follow.
const BACKEND_RESOURCES = path.resolve(__dirname, "../../../../backend/src/main/resources");
const TEMPLATE_PACK = path.join(BACKEND_RESOURCES, "project-template-packs/legal-za.json");
const FIELD_PACKS_DIR = path.join(BACKEND_RESOURCES, "field-packs");

function readJson(file: string): unknown {
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

function matterTypes(pack: unknown): string[] {
  const templates = (pack as { templates?: { matterType?: string }[] }).templates ?? [];
  return templates.map((t) => t.matterType).filter((t): t is string => typeof t === "string");
}

function applicableWorkTypes(pack: unknown): string[] {
  const group = (pack as { group?: { applicableWorkTypes?: string[] } }).group;
  return group?.applicableWorkTypes ?? [];
}

describe("SUGGESTED_WORK_TYPES", () => {
  it("includes every legal-za project template matterType", () => {
    const types = matterTypes(readJson(TEMPLATE_PACK));

    expect(types.length).toBeGreaterThan(0);
    for (const type of types) {
      expect(SUGGESTED_WORK_TYPES).toContain(type);
    }
  });

  it("includes every work type a field pack group is scoped to", () => {
    const files = fs.readdirSync(FIELD_PACKS_DIR).filter((f) => f.endsWith(".json"));
    const types = files.flatMap((f) =>
      applicableWorkTypes(readJson(path.join(FIELD_PACKS_DIR, f)))
    );

    expect(files.length).toBeGreaterThan(0);
    expect(types.length).toBeGreaterThan(0);
    for (const type of types) {
      expect(SUGGESTED_WORK_TYPES).toContain(type);
    }
  });
});
