package io.b2mash.b2b.b2bstrawman.proposal;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Builds the variable substitution map for Tiptap template rendering. Variables in the map are used
 * by the template variable engine (e.g., {@code {{client_name}}} resolves to the customer name)
 * when {@code TiptapRenderer.renderToHtml()} is called.
 */
@Service
public class ProposalVariableResolver {

  private static final DateTimeFormatter DATE_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

  /**
   * Builds a variable context map from proposal, customer, contact, and org data.
   *
   * @param proposal the proposal entity
   * @param customer the customer entity
   * @param contact the portal contact entity
   * @param orgSettings the org settings (branding data)
   * @param orgName the organization display name
   * @return a map of variable keys to string values (never null values)
   */
  public Map<String, String> buildContext(
      Proposal proposal,
      Customer customer,
      PortalContact contact,
      OrgSettings orgSettings,
      String orgName) {
    var context = new LinkedHashMap<String, String>();

    context.put("client_name", customer.getName());
    context.put(
        "client_contact_name", contact.getDisplayName() != null ? contact.getDisplayName() : "");
    context.put("proposal_number", proposal.getProposalNumber());
    context.put("proposal_date", DATE_FORMAT.format(proposal.getCreatedAt()));
    context.put("fee_total", resolveFeeTotal(proposal));
    context.put("fee_model", proposal.getFeeModel().getDisplayLabel());
    context.put("org_name", orgName);
    context.put(
        "expiry_date",
        proposal.getExpiresAt() != null ? DATE_FORMAT.format(proposal.getExpiresAt()) : "");

    return context;
  }

  private String resolveFeeTotal(Proposal proposal) {
    return switch (proposal.getFeeModel()) {
      case FIXED ->
          proposal.getFixedFeeAmount() != null ? proposal.getFixedFeeAmount().toPlainString() : "";
      case RETAINER ->
          proposal.getRetainerAmount() != null ? proposal.getRetainerAmount().toPlainString() : "";
      case HOURLY -> "";
    };
  }
}
