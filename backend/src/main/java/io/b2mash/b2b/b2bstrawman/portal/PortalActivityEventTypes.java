package io.b2mash.b2b.b2bstrawman.portal;

import java.util.Set;

/**
 * The allow-list of audit event_types that may surface on the portal /activity Firm-actions tab.
 * Anything not on this list is firm-internal and must not reach a portal contact's view.
 *
 * <p>Updates to this list are a product / privacy decision -- coordinate with Product before adding
 * a new event type. The corresponding humanised label MUST also be added to {@link
 * PortalActivityEventResponse#summaryFor(String)} in the same change.
 */
public final class PortalActivityEventTypes {

  private PortalActivityEventTypes() {}

  /** Client-facing event types -- the firm's actions on the matter that the client should see. */
  public static final Set<String> PORTAL_VISIBLE_FIRM_EVENT_TYPES =
      Set.of(
          // Information requests (client-facing lifecycle)
          "information_request.created",
          "information_request.sent",
          "information_request.cancelled",
          "information_request.completed",
          "information_request.item_accepted",
          "information_request.item_rejected",
          "information_request.reminder_sent",
          // Invoices / fee notes (client-facing)
          "invoice.sent",
          "invoice.payment_recorded",
          "invoice.payment_reversed",
          "invoice.payment_partially_reversed",
          // Trust accounting (client-facing approved transactions only)
          "trust_transaction.approved",
          // N-6 (Phase 88 625A): the audit rows are trust_<type>.approved (TrustTransactionService
          // performApprovalCompletion); trust_transaction.approved above is only the Spring
          // domain-event type and is never written to audit_events. Only these four types can be
          // approved (deposits and transfers are recorded without an approval step).
          "trust_payment.approved",
          "trust_fee_transfer.approved",
          "trust_refund.approved",
          "trust_reversal.approved",
          "trust_transaction.reversed",
          // G89-F44 (D14): money received for the client, and each interest credit posted to the
          // client's matter. Both rows are matter- and client-scoped (AuditDetails.matterScoped),
          // so findActivityFirmForCustomer shows them to that client only; a deposit or credit
          // with no matter has no project_id and never surfaces (ADR-366 D7).
          "trust_deposit.recorded",
          "trust_interest_credit.recorded",
          // Documents the firm shared with the client
          "document.generated",
          "docx_document.generated",
          // Statements of Account
          "statement.generated",
          // Matter closure (client-facing milestone)
          "matter_closure.closed",
          "matter_closure.reopened");

  /**
   * G89-F23: allow-listed types whose audit {@code entity_id} is a {@code generated_documents.id}.
   * The event type alone does not make them client-facing -- a closure letter or Statement of
   * Account the firm keeps as an internal draft must not be announced. They surface only while the
   * generated document's linked {@code documents} row is portal-visible ({@link
   * io.b2mash.b2b.b2bstrawman.document.Document.Visibility#PORTAL_VISIBLE}); a generated document
   * never saved to the matter ({@code document_id} null) never surfaces. Checked at read time, so
   * sharing the document later makes its event appear on the next read.
   */
  public static final Set<String> GENERATED_DOCUMENT_EVENT_TYPES =
      Set.of("document.generated", "docx_document.generated", "statement.generated");
}
