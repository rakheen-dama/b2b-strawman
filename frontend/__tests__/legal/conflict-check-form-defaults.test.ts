import { describe, it, expect } from "vitest";
import { buildConflictCheckFormDefaults } from "@/app/(app)/org/[slug]/conflict-check/form-defaults";

describe("buildConflictCheckFormDefaults (conflict-check page prefill, G-A6)", () => {
  it("returns no defaults without search params", () => {
    expect(buildConflictCheckFormDefaults({})).toEqual({});
  });

  it("prefills the New Matter link-out: client, name, ID number and NEW_MATTER check type", () => {
    expect(
      buildConflictCheckFormDefaults({
        checkType: "NEW_MATTER",
        customerId: "11111111-1111-4111-8111-111111111111",
        checkedName: "Sipho Ndlovu",
        checkedIdNumber: "8501015800087",
      })
    ).toEqual({
      checkType: "NEW_MATTER",
      customerId: "11111111-1111-4111-8111-111111111111",
      checkedName: "Sipho Ndlovu",
      checkedIdNumber: "8501015800087",
    });
  });

  it.each(["NEW_CLIENT", "NEW_MATTER", "PERIODIC_REVIEW"] as const)(
    "accepts the %s check type",
    (checkType) => {
      expect(buildConflictCheckFormDefaults({ checkType })).toEqual({ checkType });
    }
  );

  it.each(["BOGUS", "new_matter", ""])("ignores an unknown check type %j", (checkType) => {
    expect(buildConflictCheckFormDefaults({ checkType, checkedName: "Sipho Ndlovu" })).toEqual({
      checkedName: "Sipho Ndlovu",
    });
  });

  it("keeps the existing client-overflow-menu link working (customerId + checkedName only)", () => {
    expect(
      buildConflictCheckFormDefaults({ customerId: "c-1", checkedName: "Acme Holdings" })
    ).toEqual({ customerId: "c-1", checkedName: "Acme Holdings" });
  });
});
