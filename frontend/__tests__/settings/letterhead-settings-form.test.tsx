import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { LetterheadSettings } from "@/lib/types";

const mockUpdateLetterhead = vi.fn();
vi.mock("@/app/(app)/org/[slug]/settings/firm/_tabs/general-actions", () => ({
  updateLetterheadSettingsAction: (...args: unknown[]) => mockUpdateLetterhead(...args),
}));

import { LetterheadSettingsForm } from "@/components/settings/letterhead-settings-form";
import { LETTERHEAD_FIELDS, toLetterheadRequest } from "@/lib/schemas/letterhead";

const EMPTY: LetterheadSettings = {
  addressLine1: null,
  addressLine2: null,
  addressLine3: null,
  city: null,
  postalCode: null,
  country: null,
  phone: null,
  email: null,
  website: null,
  bankName: null,
  bankBranchCode: null,
  bankAccountNumber: null,
  bankAccountType: null,
  paymentReferenceNote: null,
  signatoryName: null,
  signatoryDesignation: null,
};

const FILLED: LetterheadSettings = {
  addressLine1: "12 Rissik Street",
  addressLine2: "Marshalltown",
  addressLine3: null,
  city: "Johannesburg",
  postalCode: "2001",
  country: "South Africa",
  phone: "+27 11 555 0100",
  email: "info@mathebula.law",
  website: "https://mathebula.law",
  bankName: "FNB",
  bankBranchCode: "250655",
  bankAccountNumber: "62012345678",
  bankAccountType: "Cheque",
  paymentReferenceNote: "Use the fee-note number as your reference",
  signatoryName: "Thandi Mathebula",
  signatoryDesignation: "Director",
};

const GROUP_LABELS: Record<string, string[]> = {
  Address: ["Address line 1", "Address line 2", "Address line 3", "City", "Postal code", "Country"],
  Contact: ["Phone", "Email", "Website"],
  Banking: ["Bank name", "Branch code", "Account number", "Account type", "Payment reference note"],
  Signatory: ["Signatory name", "Designation"],
};

const SAVE = { name: "Save letterhead" };

afterEach(() => {
  cleanup();
  mockUpdateLetterhead.mockReset();
});

describe("LetterheadSettingsForm — field groups", () => {
  it("renders the Address, Contact, Banking and Signatory groups with all 16 fields", () => {
    render(<LetterheadSettingsForm slug="acme" letterhead={EMPTY} />);

    for (const [group, labels] of Object.entries(GROUP_LABELS)) {
      const fieldset = screen.getByRole("group", { name: group });
      for (const label of labels) {
        expect(within(fieldset).getByLabelText(label)).toBeInTheDocument();
      }
    }
    expect(screen.getAllByRole("textbox")).toHaveLength(16);
    expect(screen.getByRole("heading", { level: 2, name: "Letterhead" })).toBeInTheDocument();
  });

  it("renders the payment reference note as a multi-line field with a 500-character counter", async () => {
    const user = userEvent.setup();
    render(<LetterheadSettingsForm slug="acme" letterhead={EMPTY} />);

    const note = screen.getByLabelText("Payment reference note");
    expect(note.tagName).toBe("TEXTAREA");
    expect(screen.getByText("0/500 characters")).toBeInTheDocument();
    await user.type(note, "Ref");
    expect(screen.getByText("3/500 characters")).toBeInTheDocument();
  });

  it("puts no HTML maxLength on any field (zod owns the limits; no silent truncation)", () => {
    render(<LetterheadSettingsForm slug="acme" letterhead={EMPTY} />);
    for (const box of screen.getAllByRole("textbox")) {
      expect(box).not.toHaveAttribute("maxlength");
    }
  });

  it("pre-fills the saved letterhead and keeps Save disabled until something changes", async () => {
    const user = userEvent.setup();
    render(<LetterheadSettingsForm slug="acme" letterhead={FILLED} />);

    expect(screen.getByLabelText("City")).toHaveValue("Johannesburg");
    expect(screen.getByLabelText("Account number")).toHaveValue("62012345678");
    expect(screen.getByLabelText("Designation")).toHaveValue("Director");
    expect(screen.getByLabelText("Address line 3")).toHaveValue("");

    const save = screen.getByRole("button", SAVE);
    expect(save).toBeDisabled();
    await user.type(screen.getByLabelText("Address line 3"), "Gauteng");
    expect(save).toBeEnabled();
  });
});

describe("LetterheadSettingsForm — submit payload (full replace)", () => {
  it("submits every key, trimmed, with blanks as null", async () => {
    mockUpdateLetterhead.mockResolvedValue({
      success: true,
      letterhead: { ...EMPTY, city: "Sandton" },
    });
    const user = userEvent.setup();
    render(<LetterheadSettingsForm slug="acme" letterhead={EMPTY} />);

    await user.type(screen.getByLabelText("City"), "  Sandton  ");
    await user.type(screen.getByLabelText("Phone"), "   ");
    await user.click(screen.getByRole("button", SAVE));

    await waitFor(() => expect(mockUpdateLetterhead).toHaveBeenCalledTimes(1));
    const [slug, body] = mockUpdateLetterhead.mock.calls[0];
    expect(slug).toBe("acme");
    expect(body).toEqual({ ...EMPTY, city: "Sandton" });
    expect(Object.keys(body)).toHaveLength(16);
  });

  it("sends the full filled letterhead with only the edited field changed", async () => {
    mockUpdateLetterhead.mockResolvedValue({
      success: true,
      letterhead: { ...FILLED, signatoryDesignation: "Partner" },
    });
    const user = userEvent.setup();
    render(<LetterheadSettingsForm slug="acme" letterhead={FILLED} />);

    await user.clear(screen.getByLabelText("Designation"));
    await user.type(screen.getByLabelText("Designation"), "Partner");
    await user.click(screen.getByRole("button", SAVE));

    await waitFor(() =>
      expect(mockUpdateLetterhead).toHaveBeenCalledWith("acme", {
        ...FILLED,
        signatoryDesignation: "Partner",
      })
    );
  });

  it("sends null for a field the user cleared", async () => {
    mockUpdateLetterhead.mockResolvedValue({
      success: true,
      letterhead: { ...FILLED, bankAccountNumber: null },
    });
    const user = userEvent.setup();
    render(<LetterheadSettingsForm slug="acme" letterhead={FILLED} />);

    await user.clear(screen.getByLabelText("Account number"));
    await user.click(screen.getByRole("button", SAVE));

    await waitFor(() =>
      expect(mockUpdateLetterhead).toHaveBeenCalledWith("acme", {
        ...FILLED,
        bankAccountNumber: null,
      })
    );
  });

  it("shows 'Letterhead saved.' and re-bases the form on the saved values", async () => {
    mockUpdateLetterhead.mockResolvedValue({
      success: true,
      letterhead: { ...EMPTY, city: "Sandton" },
    });
    const user = userEvent.setup();
    render(<LetterheadSettingsForm slug="acme" letterhead={EMPTY} />);

    await user.type(screen.getByLabelText("City"), "  Sandton  ");
    await user.click(screen.getByRole("button", SAVE));

    expect(await screen.findByRole("status")).toHaveTextContent("Letterhead saved.");
    // reset(saved): the field shows the server-stored (trimmed) value and the form is pristine.
    expect(screen.getByLabelText("City")).toHaveValue("Sandton");
    expect(screen.getByRole("button", SAVE)).toBeDisabled();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});

describe("LetterheadSettingsForm — validation", () => {
  it("blocks an invalid email inline without calling the action", async () => {
    const user = userEvent.setup();
    render(<LetterheadSettingsForm slug="acme" letterhead={EMPTY} />);

    await user.type(screen.getByLabelText("Email"), "not-an-email");
    await user.click(screen.getByRole("button", SAVE));

    expect(await screen.findByText("Invalid email address")).toBeInTheDocument();
    expect(screen.getByLabelText("Email")).toHaveAttribute("aria-invalid", "true");
    expect(mockUpdateLetterhead).not.toHaveBeenCalled();
  });

  it("accepts a blank email (it is optional)", async () => {
    mockUpdateLetterhead.mockResolvedValue({ success: true, letterhead: { ...EMPTY } });
    const user = userEvent.setup();
    render(<LetterheadSettingsForm slug="acme" letterhead={{ ...EMPTY, email: "a@b.law" }} />);

    await user.clear(screen.getByLabelText("Email"));
    await user.click(screen.getByRole("button", SAVE));

    await waitFor(() =>
      expect(mockUpdateLetterhead).toHaveBeenCalledWith("acme", { ...EMPTY, email: null })
    );
    expect(screen.queryByText("Invalid email address")).not.toBeInTheDocument();
  });

  it("enforces the backend max length on the trimmed value", async () => {
    const user = userEvent.setup();
    render(<LetterheadSettingsForm slug="acme" letterhead={EMPTY} />);

    await user.type(screen.getByLabelText("Postal code"), "1".repeat(21));
    await user.click(screen.getByRole("button", SAVE));

    expect(
      await screen.findByText("Postal code must be 20 characters or fewer")
    ).toBeInTheDocument();
    expect(mockUpdateLetterhead).not.toHaveBeenCalled();
  });

  it("enforces the 500-character limit on the payment reference note", async () => {
    const user = userEvent.setup();
    render(<LetterheadSettingsForm slug="acme" letterhead={EMPTY} />);

    const note = screen.getByLabelText("Payment reference note");
    await user.click(note);
    await user.paste("x".repeat(501));
    await user.click(screen.getByRole("button", SAVE));

    expect(
      await screen.findByText("Payment reference note must be 500 characters or fewer")
    ).toBeInTheDocument();
    expect(mockUpdateLetterhead).not.toHaveBeenCalled();
  });

  it("measures the limit after trimming (padded value at the limit is accepted)", async () => {
    mockUpdateLetterhead.mockResolvedValue({ success: true, letterhead: { ...EMPTY } });
    const user = userEvent.setup();
    render(<LetterheadSettingsForm slug="acme" letterhead={EMPTY} />);

    await user.type(screen.getByLabelText("Postal code"), `  ${"2".repeat(20)}  `);
    await user.click(screen.getByRole("button", SAVE));

    await waitFor(() =>
      expect(mockUpdateLetterhead).toHaveBeenCalledWith("acme", {
        ...EMPTY,
        postalCode: "2".repeat(20),
      })
    );
  });
});

describe("LetterheadSettingsForm — server errors", () => {
  it("puts backend 400 fieldErrors on the matching field with a generic alert", async () => {
    mockUpdateLetterhead.mockResolvedValue({
      success: false,
      error: "1 field(s) have validation errors",
      fieldErrors: [{ field: "email", message: "email must be a valid email" }],
    });
    const user = userEvent.setup();
    render(<LetterheadSettingsForm slug="acme" letterhead={EMPTY} />);

    await user.type(screen.getByLabelText("Email"), "info@firm.law");
    await user.click(screen.getByRole("button", SAVE));

    expect(await screen.findByText("email must be a valid email")).toBeInTheDocument();
    expect(screen.getByLabelText("Email")).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByText("Fix the highlighted fields and save again.")).toBeInTheDocument();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("shows the action's error when the save is refused (403)", async () => {
    mockUpdateLetterhead.mockResolvedValue({
      success: false,
      error: "Only owners and admins can update the letterhead.",
    });
    const user = userEvent.setup();
    render(<LetterheadSettingsForm slug="acme" letterhead={EMPTY} />);

    await user.type(screen.getByLabelText("Phone"), "011 555 0100");
    await user.click(screen.getByRole("button", SAVE));

    const alerts = await screen.findAllByRole("alert");
    expect(alerts.map((a) => a.textContent)).toContain(
      "Only owners and admins can update the letterhead."
    );
    expect(screen.queryByText("Letterhead saved.")).not.toBeInTheDocument();
    // Unsaved edits stay in the form so the user does not lose them.
    expect(screen.getByLabelText("Phone")).toHaveValue("011 555 0100");
  });

  it("shows a generic error when the action throws", async () => {
    mockUpdateLetterhead.mockRejectedValue(new Error("boom"));
    const user = userEvent.setup();
    render(<LetterheadSettingsForm slug="acme" letterhead={EMPTY} />);

    await user.type(screen.getByLabelText("Phone"), "011");
    await user.click(screen.getByRole("button", SAVE));

    expect(await screen.findByText("An unexpected error occurred.")).toBeInTheDocument();
  });
});

describe("letterhead schema helpers", () => {
  it("lists the 16 backend fields in UpdateLetterheadSettingsRequest record order", () => {
    expect(LETTERHEAD_FIELDS).toEqual([
      "addressLine1",
      "addressLine2",
      "addressLine3",
      "city",
      "postalCode",
      "country",
      "phone",
      "email",
      "website",
      "bankName",
      "bankBranchCode",
      "bankAccountNumber",
      "bankAccountType",
      "paymentReferenceNote",
      "signatoryName",
      "signatoryDesignation",
    ]);
  });

  it("toLetterheadRequest trims, maps blank to null and always sends all 16 keys", () => {
    const values = Object.fromEntries(LETTERHEAD_FIELDS.map((f) => [f, ""])) as Record<
      (typeof LETTERHEAD_FIELDS)[number],
      string
    >;
    const body = toLetterheadRequest({ ...values, city: " Sandton ", phone: "  " });
    expect(body).toEqual({ ...EMPTY, city: "Sandton" });
  });
});
