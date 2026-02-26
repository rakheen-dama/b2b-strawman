// Email delivery types (from EmailAdminController.java)

export type EmailDeliveryStatus =
  | "SENT"
  | "DELIVERED"
  | "BOUNCED"
  | "FAILED"
  | "RATE_LIMITED";

export interface EmailDeliveryLogEntry {
  id: string;
  recipientEmail: string;
  templateName: string;
  referenceType: string;
  referenceId: string;
  status: EmailDeliveryStatus;
  providerMessageId: string | null;
  providerSlug: string | null;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface EmailDeliveryStats {
  sent24h: number;
  bounced7d: number;
  failed7d: number;
  rateLimited7d: number;
  currentHourUsage: number;
  hourlyLimit: number;
  providerSlug: string | null;
}

export interface DeliveryLogParams {
  status?: EmailDeliveryStatus;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}
