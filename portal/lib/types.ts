// === Auth ===

export interface AuthExchangeResponse {
  jwt: string;
  customerId: string;
  customerName: string;
  email: string;
}

export interface MagicLinkResponse {
  message: string;
  magicLink: string | null;
}

// === Branding ===

export interface BrandingInfo {
  orgName: string;
  logoUrl: string | null;
  brandColor: string | null;
  footerText: string | null;
}

// === Projects ===

export interface PortalProject {
  id: string;
  name: string;
  description: string | null;
  documentCount: number;
  createdAt: string; // ISO 8601 instant
}

export interface PortalProjectDetail {
  id: string;
  name: string;
  status: string;
  description: string | null;
  documentCount: number;
  commentCount: number;
  createdAt: string;
}

// === Documents ===

export interface PortalDocument {
  id: string;
  fileName: string;
  contentType: string;
  size: number;
  scope: string;
  status: string;
  createdAt: string;
}

export interface PortalPresignDownload {
  presignedUrl: string;
  expiresInSeconds: number;
}

// === Comments ===

export interface PortalComment {
  id: string;
  authorName: string;
  content: string;
  createdAt: string;
}

// === Tasks ===

export interface PortalTask {
  id: string;
  name: string;
  status: string;
  assigneeName: string | null;
  sortOrder: number;
}

// === Invoices ===

export interface PortalInvoice {
  id: string;
  invoiceNumber: string;
  status: string;
  issueDate: string; // ISO date (YYYY-MM-DD)
  dueDate: string;
  total: number;
  currency: string;
}

export interface TaxBreakdownEntry {
  rateName: string;
  ratePercent: number;
  taxableAmount: number;
  taxAmount: number;
}

export interface PortalInvoiceDetail {
  id: string;
  invoiceNumber: string;
  status: string;
  issueDate: string;
  dueDate: string;
  subtotal: number;
  taxAmount: number;
  total: number;
  currency: string;
  notes: string | null;
  paymentUrl: string | null;
  lines: PortalInvoiceLine[];
  taxBreakdown: TaxBreakdownEntry[] | null;
  taxRegistrationNumber: string | null;
  taxRegistrationLabel: string | null;
  taxLabel: string | null;
  taxInclusive: boolean;
  hasPerLineTax: boolean;
}

export interface PortalInvoiceLine {
  id: string;
  description: string;
  quantity: number;
  unitPrice: number;
  amount: number;
  sortOrder: number;
  taxRateName: string | null;
  taxRatePercent: number | null;
  taxAmount: number | null;
  taxExempt: boolean;
}

export interface PortalDownload {
  downloadUrl: string;
}

// === Payment ===

export interface PaymentStatusResponse {
  status: string;
  paidAt: string | null;
}

// === Summary ===

export interface PortalProjectSummary {
  projectId: string;
  totalHours: number;
  billableHours: number;
  lastActivityAt: string | null;
}

// === Profile ===

export interface PortalProfile {
  contactId: string;
  customerId: string;
  customerName: string;
  email: string;
  displayName: string;
  role: string; // PRIMARY, BILLING, GENERAL
}
