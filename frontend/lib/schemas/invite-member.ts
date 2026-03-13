import { z } from "zod";

export const inviteMemberSchema = z.object({
  emailAddress: z.string().min(1, "Email address is required").email("Invalid email address"),
});

export type InviteMemberFormData = z.infer<typeof inviteMemberSchema>;
