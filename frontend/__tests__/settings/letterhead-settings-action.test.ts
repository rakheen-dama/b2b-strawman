import { describe, it, expect, vi, beforeEach } from "vitest";
import type { LetterheadSettings, UpdateLetterheadSettingsRequest } from "@/lib/types";

// Mock server-only so the "use server" module (and the real lib/api/client) load in the test env.
vi.mock("server-only", () => ({}));

const { mockUpdateLetterhead, mockRevalidatePath } = vi.hoisted(() => ({
  mockUpdateLetterhead: vi.fn(),
  mockRevalidatePath: vi.fn(),
}));

// Only the PATCH wrapper is replaced; ApiError stays the real class from lib/api/client.
vi.mock("@/lib/api/settings", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/api/settings")>()),
  updateLetterheadSettings: (...args: unknown[]) => mockUpdateLetterhead(...args),
}));

vi.mock("next/cache", () => ({ revalidatePath: mockRevalidatePath }));

import { ApiError } from "@/lib/api/client";
import { updateLetterheadSettingsAction } from "@/app/(app)/org/[slug]/settings/firm/_tabs/general-actions";

const REQUEST: UpdateLetterheadSettingsRequest = {
  addressLine1: "12 Rissik Street",
  addressLine2: null,
  addressLine3: null,
  city: "Johannesburg",
  postalCode: "2001",
  country: "South Africa",
  phone: "+27 11 555 0100",
  email: "info@mathebula.law",
  website: null,
  bankName: "FNB",
  bankBranchCode: "250655",
  bankAccountNumber: "62012345678",
  bankAccountType: "Cheque",
  paymentReferenceNote: null,
  signatoryName: "Thandi Mathebula",
  signatoryDesignation: "Director",
};

const SAVED: LetterheadSettings = { ...REQUEST };

beforeEach(() => {
  mockUpdateLetterhead.mockReset();
  mockRevalidatePath.mockReset();
});

describe("updateLetterheadSettingsAction", () => {
  it("returns the saved letterhead and revalidates the firm settings page on success", async () => {
    mockUpdateLetterhead.mockResolvedValue({ currency: "ZAR", letterhead: SAVED });

    const result = await updateLetterheadSettingsAction("acme", REQUEST);

    expect(mockUpdateLetterhead).toHaveBeenCalledWith(REQUEST);
    expect(result).toEqual({ success: true, letterhead: SAVED });
    expect(mockRevalidatePath).toHaveBeenCalledTimes(1);
    expect(mockRevalidatePath).toHaveBeenCalledWith("/org/acme/settings/firm");
  });

  it("threads the 400 ProblemDetail fieldErrors through with the backend message", async () => {
    const fieldErrors = [{ field: "email", message: "email must be a valid email" }];
    // GlobalExceptionHandler: ProblemDetail property `fieldErrors` = [{ field, message }].
    mockUpdateLetterhead.mockRejectedValue(
      new ApiError(400, "1 field(s) have validation errors", {
        type: "about:blank",
        title: "Validation failed",
        status: 400,
        detail: "1 field(s) have validation errors",
        fieldErrors,
      })
    );

    const result = await updateLetterheadSettingsAction("acme", REQUEST);

    expect(result).toEqual({
      success: false,
      error: "1 field(s) have validation errors",
      fieldErrors,
    });
    expect(mockRevalidatePath).not.toHaveBeenCalled();
  });

  it("maps a 403 to the owners-and-admins message", async () => {
    mockUpdateLetterhead.mockRejectedValue(new ApiError(403, "Forbidden"));

    const result = await updateLetterheadSettingsAction("acme", REQUEST);

    expect(result).toEqual({
      success: false,
      error: "Only owners and admins can update the letterhead.",
    });
    expect(mockRevalidatePath).not.toHaveBeenCalled();
  });

  it("maps a non-ApiError failure to the generic message", async () => {
    mockUpdateLetterhead.mockRejectedValue(new Error("network down"));

    const result = await updateLetterheadSettingsAction("acme", REQUEST);

    expect(result).toEqual({ success: false, error: "An unexpected error occurred." });
    expect(mockRevalidatePath).not.toHaveBeenCalled();
  });
});
