import { z } from "zod";

const stageTypeEnum = z.enum(["OPEN", "WON", "LOST"]);

/**
 * Intake form (new enquiry / new deal). The customer is supplied EITHER by
 * picking an existing one (`customerId`) OR by creating a PROSPECT inline
 * (`customerName` + optional email/phone). `customerMode` drives which path.
 */
export const intakeDealSchema = z
  .object({
    customerMode: z.enum(["existing", "new"]),
    customerId: z.string().optional().or(z.literal("")),
    customerName: z.string().max(255).optional().or(z.literal("")),
    customerEmail: z.string().email("Invalid email address").max(255).optional().or(z.literal("")),
    customerPhone: z.string().max(50).optional().or(z.literal("")),
    title: z.string().min(1, "Title is required").max(200, "Title must be 200 characters or fewer"),
    valueAmount: z.string().optional().or(z.literal("")),
    stageId: z.string().optional().or(z.literal("")),
    source: z.string().max(40).optional().or(z.literal("")),
    expectedCloseDate: z.iso.date().or(z.literal("")).optional(),
  })
  .superRefine((data, ctx) => {
    if (data.customerMode === "existing") {
      if (!data.customerId) {
        ctx.addIssue({
          code: "custom",
          message: "Select a customer",
          path: ["customerId"],
        });
      }
    } else if (!data.customerName || data.customerName.trim().length === 0) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: "Customer name is required",
        path: ["customerName"],
      });
    }
  });

export type IntakeDealFormData = z.infer<typeof intakeDealSchema>;

/**
 * Lose-deal form. The backend requires a non-blank reason when transitioning
 * to a LOST stage (400 otherwise).
 */
export const loseDealSchema = z.object({
  lostReason: z.string().min(1, "A reason is required to mark a deal as lost").max(2000),
});

export type LoseDealFormData = z.infer<typeof loseDealSchema>;

/**
 * Stage configuration edit form (settings/pipeline).
 */
export const stageEditSchema = z.object({
  name: z.string().min(1, "Name is required").max(80, "Name must be 80 characters or fewer"),
  defaultProbabilityPct: z
    .string()
    .min(1, "Probability is required")
    .refine((v) => {
      const n = Number(v);
      return Number.isInteger(n) && n >= 0 && n <= 100;
    }, "Must be a whole number between 0 and 100"),
  stageType: stageTypeEnum,
});

export type StageEditFormData = z.infer<typeof stageEditSchema>;

/**
 * New stage form (settings/pipeline). Position is assigned by the page (append).
 */
export const stageCreateSchema = stageEditSchema;

export type StageCreateFormData = z.infer<typeof stageCreateSchema>;
