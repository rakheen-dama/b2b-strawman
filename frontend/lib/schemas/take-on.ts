import { z } from "zod";

// Client and trust take-on (Phase 93 653A, architecture §17.6.2). The backend repeats every check;
// these refuse early so a wrong file never leaves the browser.

export const TAKE_ON_MAX_FILE_BYTES = 10 * 1024 * 1024; // 10 MB (§17.6.2)

/** MIME types a browser reports for .csv / .xlsx; "" when the OS does not know the type. */
export const TAKE_ON_ALLOWED_MIME_TYPES: ReadonlySet<string> = new Set([
  "",
  "text/csv",
  "application/vnd.ms-excel", // Windows reports .csv files as Excel
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
]);

const WRONG_FORMAT = "Save the file as .xlsx or .csv and upload it again.";

/** The client-side refusal for a chosen file (extension, MIME, size), or null when acceptable. */
export function takeOnFileError(file: { name: string; size: number; type: string }): string | null {
  const name = file.name.toLowerCase();
  if (!name.endsWith(".csv") && !name.endsWith(".xlsx")) {
    return WRONG_FORMAT;
  }
  if (!TAKE_ON_ALLOWED_MIME_TYPES.has(file.type)) {
    return WRONG_FORMAT;
  }
  if (file.size > TAKE_ON_MAX_FILE_BYTES) {
    return `File exceeds the 10 MB limit (${(file.size / 1024 / 1024).toFixed(1)} MB)`;
  }
  return null;
}

const isoDate = z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Enter the cutover date.");

/**
 * The upload step's form. `requiresTrustAccount` / `requiresCutoverDate` come from the dataset
 * catalogue; the trust position also needs the section the first file holds.
 */
export const takeOnUploadSchema = z
  .object({
    requiresTrustAccount: z.boolean(),
    requiresCutoverDate: z.boolean(),
    name: z.string().max(200, "The take-on batch name is longer than 200 characters."),
    trustAccountId: z.string(),
    section: z.string(),
    cutoverDate: z.string(),
  })
  .superRefine((data, ctx) => {
    if (data.requiresTrustAccount && !data.trustAccountId) {
      ctx.addIssue({
        code: "custom",
        path: ["trustAccountId"],
        message: "Choose the trust banking account to take on.",
      });
    }
    if (data.requiresTrustAccount && !data.section) {
      ctx.addIssue({
        code: "custom",
        path: ["section"],
        message: "Choose which part of the trust position this file holds.",
      });
    }
    if (data.requiresCutoverDate && !isoDate.safeParse(data.cutoverDate).success) {
      ctx.addIssue({ code: "custom", path: ["cutoverDate"], message: "Enter the cutover date." });
    }
  });

export type TakeOnUploadFormData = z.infer<typeof takeOnUploadSchema>;

export const takeOnSheetSchema = z.object({
  sheetName: z.string().min(1, "Choose the sheet to take on."),
  headerRow: z
    .number({ message: "The header row must be between 1 and 50." })
    .int("The header row must be between 1 and 50.")
    .min(1, "The header row must be between 1 and 50.")
    .max(50, "The header row must be between 1 and 50."),
});

export type TakeOnSheetFormData = z.infer<typeof takeOnSheetSchema>;

/**
 * The mapping step's rule (§17.3.2): every required field is mapped to a column or given a
 * default, and no column is used twice. Built per section from its catalogue fields.
 */
export function takeOnMappingSchema(required: readonly { key: string; label: string }[]) {
  return z
    .object({
      targets: z.record(z.string(), z.string()),
      defaults: z.record(z.string(), z.string()),
      customFields: z.record(z.string(), z.string()),
      parked: z.array(z.string()),
    })
    .superRefine((data, ctx) => {
      for (const field of required) {
        const mapped = Boolean(data.targets[field.key]);
        const defaulted = Boolean(data.defaults[field.key]?.trim());
        if (!mapped && !defaulted) {
          ctx.addIssue({
            code: "custom",
            path: ["targets", field.key],
            message: `Map ${field.label} to a column, or give it a default value.`,
          });
        }
      }
      const used = [
        ...Object.values(data.targets),
        ...Object.values(data.customFields),
        ...data.parked,
      ].filter(Boolean);
      const seen = new Set<string>();
      for (const header of used) {
        if (seen.has(header)) {
          ctx.addIssue({
            code: "custom",
            path: ["targets"],
            message: `The column "${header}" is mapped more than once.`,
          });
        }
        seen.add(header);
      }
    });
}
