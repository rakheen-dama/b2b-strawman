import { describe, it, expect } from "vitest";
import {
  HEADER_FIELD_PREFIXES,
  HEADER_FIELD_SLUGS,
  buildHeaderFields,
} from "@/lib/constants/header-field-slugs";
import type { FieldDefinitionResponse } from "@/lib/types";

function def(slug: string, name: string): FieldDefinitionResponse {
  return { slug, name } as FieldDefinitionResponse;
}

const PROJECT_DEFS = [
  def("opposing_party", "Opposing Party"),
  def("court_name", "Court"),
  def("case_number", "Case Number"),
];

describe("HEADER_FIELD_SLUGS (Phase 88 D9)", () => {
  it("lists the legal-za matter and client header slugs", () => {
    expect(HEADER_FIELD_SLUGS.PROJECT).toEqual(["case_number", "court_name", "opposing_party"]);
    expect(HEADER_FIELD_SLUGS.CUSTOMER).toEqual(["id_passport_number"]);
  });

  it("prefixes the case number and the ID number only", () => {
    expect(HEADER_FIELD_PREFIXES).toEqual({ case_number: "Case no.", id_passport_number: "ID" });
  });
});

describe("buildHeaderFields", () => {
  it("follows the slug order, not the custom-field or definition order", () => {
    const fields = buildHeaderFields(
      HEADER_FIELD_SLUGS.PROJECT,
      {
        opposing_party: "Road Accident Fund",
        court_name: "Gauteng Local Division",
        case_number: "1234/2026",
      },
      PROJECT_DEFS
    );
    expect(fields).toEqual([
      { slug: "case_number", label: "Case Number", value: "1234/2026" },
      { slug: "court_name", label: "Court", value: "Gauteng Local Division" },
      { slug: "opposing_party", label: "Opposing Party", value: "Road Accident Fund" },
    ]);
  });

  it("skips blank, whitespace-only, null and non-scalar values and trims the rest", () => {
    const fields = buildHeaderFields(
      HEADER_FIELD_SLUGS.PROJECT,
      { case_number: "  1234/2026  ", court_name: "   ", opposing_party: { name: "x" } },
      PROJECT_DEFS
    );
    expect(fields).toEqual([{ slug: "case_number", label: "Case Number", value: "1234/2026" }]);

    expect(
      buildHeaderFields(HEADER_FIELD_SLUGS.PROJECT, { case_number: null }, PROJECT_DEFS)
    ).toEqual([]);
  });

  it("stringifies numbers", () => {
    expect(buildHeaderFields(["case_number"], { case_number: 1234 }, PROJECT_DEFS)).toEqual([
      { slug: "case_number", label: "Case Number", value: "1234" },
    ]);
  });

  it("falls back to the slug as label when the definition is not loaded", () => {
    expect(
      buildHeaderFields(["court_name"], { court_name: "Western Cape High Court" }, [])
    ).toEqual([{ slug: "court_name", label: "court_name", value: "Western Cape High Court" }]);
  });

  it("returns nothing when the record has no custom fields", () => {
    expect(buildHeaderFields(HEADER_FIELD_SLUGS.PROJECT, undefined, PROJECT_DEFS)).toEqual([]);
    expect(buildHeaderFields(HEADER_FIELD_SLUGS.CUSTOMER, null, [])).toEqual([]);
  });
});
