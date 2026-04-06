import { z } from "zod";

export const kycVerifySchema = z.object({
  customerId: z.string().uuid(),
  checklistInstanceItemId: z.string().uuid(),
  idNumber: z.string().min(1, "ID number is required"),
  fullName: z.string().min(1, "Full name is required"),
  idDocumentType: z.enum(["SA_ID", "SMART_ID", "PASSPORT"]).optional(),
  consentAcknowledged: z
    .boolean({ message: "You must acknowledge consent before proceeding" })
    .refine((val) => val === true, {
      message: "You must acknowledge consent before proceeding",
    }),
});

export type KycVerifyFormData = z.infer<typeof kycVerifySchema>;
