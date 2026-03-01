package io.b2mash.b2b.b2bstrawman.proposal;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProposalVariableResolverTest {

  private static final UUID CUSTOMER_ID = UUID.randomUUID();
  private static final UUID CREATED_BY_ID = UUID.randomUUID();
  private static final String ORG_ID = "org_test123";
  private static final String ORG_NAME = "Test Organisation";

  private final ProposalVariableResolver resolver = new ProposalVariableResolver();

  private Proposal buildFixedProposal() {
    var proposal =
        new Proposal("PROP-0001", "Test Proposal", CUSTOMER_ID, FeeModel.FIXED, CREATED_BY_ID);
    proposal.setFixedFeeAmount(new BigDecimal("15000.00"));
    proposal.setFixedFeeCurrency("ZAR");
    proposal.setExpiresAt(Instant.parse("2026-04-15T00:00:00Z"));
    // Trigger @PrePersist manually (package-private, same package)
    proposal.onPrePersist();
    return proposal;
  }

  private Customer buildCustomer() {
    return new Customer("Acme Corp", "info@acme.com", "+27123456789", null, null, CREATED_BY_ID);
  }

  private PortalContact buildContact() {
    return new PortalContact(ORG_ID, CUSTOMER_ID, "alice@acme.com", "Alice Smith", null);
  }

  private OrgSettings buildOrgSettings() {
    return new OrgSettings("ZAR");
  }

  @Test
  void buildContext_fixedFee_includesAmount() {
    var proposal = buildFixedProposal();
    var context =
        resolver.buildContext(
            proposal, buildCustomer(), buildContact(), buildOrgSettings(), ORG_NAME);

    assertThat(context).containsEntry("fee_total", "15000.00");
    assertThat(context).containsEntry("fee_model", "Fixed Fee");
    assertThat(context).containsEntry("client_name", "Acme Corp");
    assertThat(context.get("proposal_date")).isNotBlank();
    assertThat(context).containsEntry("expiry_date", "2026-04-15");
    assertThat(context).containsEntry("org_name", ORG_NAME);
  }

  @Test
  void buildContext_hourly_emptyFeeTotal() {
    var proposal =
        new Proposal("PROP-0002", "Hourly Proposal", CUSTOMER_ID, FeeModel.HOURLY, CREATED_BY_ID);
    proposal.onPrePersist();

    var context =
        resolver.buildContext(
            proposal, buildCustomer(), buildContact(), buildOrgSettings(), ORG_NAME);

    assertThat(context).containsEntry("fee_total", "");
    assertThat(context).containsEntry("fee_model", "Hourly");
  }

  @Test
  void buildContext_missingExpiry_emptyString() {
    var proposal = buildFixedProposal();
    proposal.setExpiresAt(null);

    var context =
        resolver.buildContext(
            proposal, buildCustomer(), buildContact(), buildOrgSettings(), ORG_NAME);

    assertThat(context).containsEntry("expiry_date", "");
  }
}
