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
          // Proposals / engagement letters (client-facing)
          "proposal.sent",
          "proposal.accepted",
          "proposal.declined",
          "proposal.expired",
          "proposal.withdrawn",
          "proposal.acceptance.completed",
          // Invoices / fee notes (client-facing)
          "invoice.sent",
          "invoice.payment_recorded",
          "invoice.payment_reversed",
          "invoice.payment_partially_reversed",
          // Trust accounting (client-facing approved transactions only)
          "trust_transaction.approved",
          "trust_transaction.reversed",
          // Documents the firm shared with the client
          "document.generated",
          "docx_document.generated",
          // Statements of Account
          "statement.generated",
          // Matter closure (client-facing milestone)
          "matter_closure.closed",
          "matter_closure.reopened");
}
