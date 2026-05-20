import { z } from "zod";

export const xeroSettingsSchema = z.object({
  paymentPollIntervalMinutes: z.number().int().min(5).max(60),
  pushTrigger: z.enum(["APPROVED", "SENT"]),
  autoSyncEnabled: z.boolean(),
});

export type XeroSettingsFormData = z.infer<typeof xeroSettingsSchema>;
