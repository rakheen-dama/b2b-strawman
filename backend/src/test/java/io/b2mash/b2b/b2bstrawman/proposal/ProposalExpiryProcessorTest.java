package io.b2mash.b2b.b2bstrawman.proposal;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.member.MemberSyncService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@RecordApplicationEvents
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProposalExpiryProcessorTest {

  private static final String ORG_ID = "org_expiry_proc_test";
  private static final String CLERK_USER_ID = "user_expiry_proc_test";

  @Autowired private ProposalExpiryProcessor expiryProcessor;
  @Autowired private ProposalRepository proposalRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private PortalContactRepository portalContactRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private MemberSyncService memberSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private ProposalPortalSyncService proposalPortalSyncService;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ApplicationEvents events;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;
  private UUID portalContactId;
  private int counter = 0;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Expiry Processor Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    var syncResult =
        memberSyncService.syncMember(
            ORG_ID, CLERK_USER_ID, "expiry_test@test.com", "Expiry Tester", null, "owner");
    memberId = syncResult.memberId();

    // Create customer and portal contact
    runInTenant(
        () -> {
          var customer =
              TestCustomerFactory.createCustomerWithStatus(
                  "Expiry Test Corp",
                  "expiry_customer@test.com",
                  memberId,
                  LifecycleStatus.PROSPECT);
          customer = customerRepository.save(customer);
          customerId = customer.getId();

          var contact =
              new PortalContact(
                  ORG_ID,
                  customerId,
                  "portal_expiry@test.com",
                  "Portal Contact",
                  PortalContact.ContactRole.PRIMARY);
          contact = portalContactRepository.save(contact);
          portalContactId = contact.getId();
          return null;
        });
  }

  @Test
  void processExpired_sentPastExpiry_transitionsToExpired() {
    UUID proposalId =
        runInTenant(
            () -> {
              var proposal = createSentProposalWithExpiry(Instant.now().minus(1, ChronoUnit.DAYS));
              return proposal.getId();
            });

    expiryProcessor.processExpiredProposals();

    runInTenant(
        () -> {
          var proposal = proposalRepository.findById(proposalId).orElseThrow();
          assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.EXPIRED);
          return null;
        });

    assertThat(events.stream(ProposalExpiredEvent.class).count()).isEqualTo(1);
  }

  @Test
  void processExpired_sentNotYetExpired_noChange() {
    UUID proposalId =
        runInTenant(
            () -> {
              var proposal = createSentProposalWithExpiry(Instant.now().plus(7, ChronoUnit.DAYS));
              return proposal.getId();
            });

    expiryProcessor.processExpiredProposals();

    runInTenant(
        () -> {
          var proposal = proposalRepository.findById(proposalId).orElseThrow();
          assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.SENT);
          return null;
        });
  }

  @Test
  void processExpired_draftWithExpiry_ignored() {
    UUID proposalId =
        runInTenant(
            () -> {
              var proposal =
                  new Proposal(
                      "P-EXP-" + (++counter),
                      "Draft Expiry Test " + counter,
                      customerId,
                      FeeModel.FIXED,
                      memberId);
              proposal.setFixedFeeAmount(new BigDecimal("1000.00"));
              proposal.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
              proposal.setContentJson(Map.of("type", "doc"));
              proposal = proposalRepository.save(proposal);
              return proposal.getId();
            });

    expiryProcessor.processExpiredProposals();

    runInTenant(
        () -> {
          var proposal = proposalRepository.findById(proposalId).orElseThrow();
          assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.DRAFT);
          return null;
        });
  }

  @Test
  void processExpired_sentNoExpiry_ignored() {
    UUID proposalId =
        runInTenant(
            () -> {
              var proposal =
                  new Proposal(
                      "P-EXP-" + (++counter),
                      "No Expiry Test " + counter,
                      customerId,
                      FeeModel.FIXED,
                      memberId);
              proposal.setFixedFeeAmount(new BigDecimal("1000.00"));
              proposal.setContentJson(Map.of("type", "doc"));
              // No expiresAt set — null
              proposal = proposalRepository.save(proposal);
              proposal.markSent(portalContactId);
              proposal = proposalRepository.save(proposal);
              return proposal.getId();
            });

    expiryProcessor.processExpiredProposals();

    runInTenant(
        () -> {
          var proposal = proposalRepository.findById(proposalId).orElseThrow();
          assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.SENT);
          return null;
        });
  }

  @Test
  void processExpired_updatesPortalStatus() {
    UUID proposalId =
        runInTenant(
            () -> {
              var proposal = createSentProposalWithExpiry(Instant.now().minus(1, ChronoUnit.DAYS));

              // Sync proposal to portal so the portal_proposals row exists
              proposalPortalSyncService.syncProposalToPortal(
                  proposal, "<p>test</p>", ORG_ID, "Expiry Processor Test Org", null);

              return proposal.getId();
            });

    expiryProcessor.processExpiredProposals();

    // Verify portal status is updated to EXPIRED
    String portalStatus =
        jdbcTemplate.queryForObject(
            "SELECT status FROM portal.portal_proposals WHERE id = ?", String.class, proposalId);
    assertThat(portalStatus).isEqualTo("EXPIRED");
  }

  // --- Helpers ---

  private Proposal createSentProposalWithExpiry(Instant expiresAt) {
    var proposal =
        new Proposal(
            "P-EXP-" + (++counter), "Expiry Test " + counter, customerId, FeeModel.FIXED, memberId);
    proposal.setFixedFeeAmount(new BigDecimal("1000.00"));
    proposal.setExpiresAt(expiresAt);
    proposal.setContentJson(Map.of("type", "doc"));
    proposal = proposalRepository.save(proposal);
    proposal.markSent(portalContactId);
    return proposalRepository.save(proposal);
  }

  private <T> T runInTenant(java.util.concurrent.Callable<T> callable) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .call(
            () -> {
              try {
                return callable.call();
              } catch (RuntimeException e) {
                throw e;
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
  }
}
