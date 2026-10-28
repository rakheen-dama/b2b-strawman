// === Auth ===

export interface AuthExchangeResponse {
  jwt: string;
  clientId: string;
  clientName: string;
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

// === Matters ===

export interface PortalMatter {
  id: string;
  name: string;
  description: string | null;
  documentCount: number;
  createdAt: string; // ISO 8601 instant
  status: string; // ACTIVE | CLOSED in the read model today (backend enum also COMPLETED | ARCHIVED)
}

export interface PortalMatterDetail {
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

/** A document chip on a portal comment; download via GET /portal/documents/{documentId}/presign-download. */
export interface PortalCommentAttachment {
  documentId: string;
  fileName: string;
  contentType: string;
  size: number;
}

export interface PortalComment {
  id: string;
  authorName: string;
  content: string;
  createdAt: string;
  /** Client-visible documents only (filtered by the backend); absent on older backends. */
  attachments?: PortalCommentAttachment[];
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
  amountPaid: number; // Phase 88 624A / ADR-347 — 0 on pre-V2 PARTIALLY_PAID rows until re-synced
  balanceDue: number; // total − amountPaid (derived server-side)
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
  amountPaid: number; // Phase 88 624A / ADR-347 — 0 on pre-V2 PARTIALLY_PAID rows until re-synced
  balanceDue: number; // total − amountPaid (derived server-side)
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

export interface PortalMatterSummary {
  matterId: string;
  totalHours: number;
  billableHours: number;
  lastActivityAt: string | null;
}

// === Profile ===

export interface PortalProfile {
  contactId: string;
  clientId: string;
  clientName: string;
  email: string;
  displayName: string;
  role: string; // PRIMARY, BILLING, GENERAL
}

// === Acceptance ===

export type AcceptanceStatus =
  | "PENDING"
  | "SENT"
  | "VIEWED"
  | "ACCEPTED"
  | "DECLINED"
  | "EXPIRED"
  | "REVOKED";

export interface AcceptancePageData {
  requestId: string;
  status: AcceptanceStatus;
  documentTitle: string;
  documentFileName: string;
  expiresAt: string;
  orgName: string;
  orgLogo: string | null;
  brandColor: string | null;
  acceptedAt: string | null;
  acceptorName: string | null;
  declinedAt: string | null; // Phase 88 U23 — PortalPageData carries no decline reason
}

export interface AcceptanceResponse {
  status: AcceptanceStatus;
  acceptedAt: string;
  acceptorName: string;
}

export interface DeclineResponse {
  status: AcceptanceStatus;
  declinedAt: string;
}

// === Pending Acceptances ===

export interface PortalPendingAcceptance {
  id: string;
  documentTitle: string;
  requestToken: string;
  sentAt: string; // ISO 8601
  expiresAt: string; // ISO 8601
  status: string;
}
