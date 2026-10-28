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

  // The New Matter link carries only customerId (no name / ID number in the URL — POPIA), so the
  // page resolves the client server-side.
  describe("with the resolved client details", () => {
    const CLIENT_ID = "11111111-1111-4111-8111-111111111111";

    it("fills name and ID number from the client", () => {
      expect(
        buildConflictCheckFormDefaults(
          { checkType: "NEW_MATTER", customerId: CLIENT_ID },
          { name: "Sipho Ndlovu", idNumber: " 8501015800087 " }
        )
      ).toEqual({
        checkType: "NEW_MATTER",
        customerId: CLIENT_ID,
        checkedName: "Sipho Ndlovu",
        checkedIdNumber: "8501015800087",
      });
    });

    it("lets explicit query values win over the client's", () => {
      expect(
        buildConflictCheckFormDefaults(
          { customerId: CLIENT_ID, checkedName: "S. Ndlovu", checkedIdNumber: "9001015800083" },
          { name: "Sipho Ndlovu", idNumber: "8501015800087" }
        )
      ).toEqual({
        customerId: CLIENT_ID,
        checkedName: "S. Ndlovu",
        checkedIdNumber: "9001015800083",
      });
    });

    it("adds the client's ID number to the overflow-menu link (customerId + checkedName)", () => {
      expect(
        buildConflictCheckFormDefaults(
          { customerId: CLIENT_ID, checkedName: "Sipho Ndlovu" },
          { name: "Sipho Ndlovu", idNumber: "8501015800087" }
        )
      ).toEqual({
        customerId: CLIENT_ID,
        checkedName: "Sipho Ndlovu",
        checkedIdNumber: "8501015800087",
      });
    });

    it.each([null, "", "   "])("ignores a blank client ID number %j", (idNumber) => {
      expect(
        buildConflictCheckFormDefaults(
          { customerId: CLIENT_ID },
          { name: "Acme Holdings", idNumber }
        )
      ).toEqual({ customerId: CLIENT_ID, checkedName: "Acme Holdings" });
    });

    it("still drops an invalid check type", () => {
      expect(
        buildConflictCheckFormDefaults(
          { checkType: "BOGUS", customerId: CLIENT_ID },
          { name: "Sipho Ndlovu", idNumber: null }
        )
      ).toEqual({ customerId: CLIENT_ID, checkedName: "Sipho Ndlovu" });
    });

    it("treats a null client (lookup failed or skipped) as no details", () => {
      expect(
        buildConflictCheckFormDefaults({ checkType: "NEW_MATTER", customerId: CLIENT_ID }, null)
      ).toEqual({ checkType: "NEW_MATTER", customerId: CLIENT_ID });
    });
  });
});
