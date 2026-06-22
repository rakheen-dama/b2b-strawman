package io.b2mash.b2b.b2bstrawman.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventFilter;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.crm.dto.CreateDealProposalRequest;
import io.b2mash.b2b.b2bstrawman.crm.dto.DealResponse;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.member.MemberSyncService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.proposal.FeeModel;
import io.b2mash.b2b.b2bstrawman.proposal.Proposal;
import io.b2mash.b2b.b2bstrawman.proposal.ProposalOrchestrationService;
import io.b2mash.b2b.b2bstrawman.proposal.ProposalRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Win-loop integration tests (Phase 80, slice 576A). Drives the <strong>real</strong> proposal
 * acceptance path through {@link ProposalOrchestrationService#acceptProposal} so the AFTER_COMMIT
 * {@link DealProposalAcceptedListener} fires, then asserts the linked deal is flipped to WON
 * idempotently. Each {@code runInTenant} call wraps its body in a {@link TransactionTemplate} so
 * the acceptance transaction actually commits (AFTER_COMMIT listeners only run on commit).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DealProposalWinLoopTest {

  private static final String ORG_ID = "org_deal_proposal_winloop_test";

  @Autowired private DealService dealService;
  @Autowired private DealProposalService dealProposalService;
  @Autowired private PipelineStageService pipelineStageService;
  @Autowired private DealRepository dealRepository;
  @Autowired private ProposalRepository proposalRepository;
  @Autowired private ProposalOrchestrationService orchestrationService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private PortalContactRepository portalContactRepository;
  @Autowired private AuditService auditService;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private MemberSyncService memberSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId;
  private int counter = 0;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Deal Proposal WinLoop Test Org", null);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    memberId =
        memberSyncService
            .syncMember(ORG_ID, "user_dp_owner", "dp_owner@test.com", "DP Owner", null, "owner")
            .memberId();
  }

  // --- Test 1: accept a deal-linked proposal -> deal marked WON ---

  @Test
  void acceptLinkedProposal_marksDealWon() {
    var setup =
        runInTenant(
            () -> {
              var customer = createProspectCustomer();
              var deal = createDeal(customer.getId(), "Win-loop deal", "10000.00");
              var proposal = createSentLinkedProposal(customer.getId(), deal.id(), FeeModel.HOURLY);
              return new Setup(
                  customer.getId(), deal.id(), proposal.getId(), proposal.getPortalContactId());
            });

    runInTenant(() -> orchestrationService.acceptProposal(setup.proposalId, setup.portalContactId));

    runInTenant(
        () -> {
          var deal = dealRepository.findOneById(setup.dealId);
          assertThat(deal.getStatus()).isEqualTo(DealStatus.WON);
          assertThat(deal.getWonAt()).isNotNull();
          assertThat(dealWonAuditCount(setup.dealId)).isEqualTo(1);
          return null;
        });
  }

  // --- Test 2: idempotent — accepting when the deal is already WON is a no-op ---

  @Test
  void acceptLinkedProposal_alreadyWonDeal_isNoOp() {
    var setup =
        runInTenant(
            () -> {
              var customer = createProspectCustomer();
              var deal = createDeal(customer.getId(), "Already-won deal", "5000.00");
              // Pre-win the deal directly via the transition path.
              var won = dealRepository.findOneById(deal.id());
              won.markWon(pipelineStageService.firstWonStage().getId(), java.time.Instant.now());
              dealRepository.save(won);
              var proposal = createSentLinkedProposal(customer.getId(), deal.id(), FeeModel.HOURLY);
              return new Setup(
                  customer.getId(), deal.id(), proposal.getId(), proposal.getPortalContactId());
            });

    long auditBefore = runInTenant(() -> dealWonAuditCount(setup.dealId));

    runInTenant(() -> orchestrationService.acceptProposal(setup.proposalId, setup.portalContactId));

    runInTenant(
        () -> {
          var deal = dealRepository.findOneById(setup.dealId);
          // Still WON, no double-win and no second deal.won audit row for this deal.
          assertThat(deal.getStatus()).isEqualTo(DealStatus.WON);
          assertThat(dealWonAuditCount(setup.dealId)).isEqualTo(auditBefore);
          return null;
        });
  }

  // --- Test 3: proposal with no linked deal -> no effect on any deal ---

  @Test
  void acceptUnlinkedProposal_doesNotTouchDeals() {
    var setup =
        runInTenant(
            () -> {
              var customer = createProspectCustomer();
              var deal = createDeal(customer.getId(), "Untouched deal", "8000.00");
              // Proposal is NOT linked to the deal (dealId stays null).
              var proposal = createSentLinkedProposal(customer.getId(), null, FeeModel.HOURLY);
              return new Setup(
                  customer.getId(), deal.id(), proposal.getId(), proposal.getPortalContactId());
            });

    runInTenant(() -> orchestrationService.acceptProposal(setup.proposalId, setup.portalContactId));

    runInTenant(
        () -> {
          var deal = dealRepository.findOneById(setup.dealId);
          assertThat(deal.getStatus()).isEqualTo(DealStatus.OPEN);
          assertThat(dealWonAuditCount(setup.dealId)).isZero();
          return null;
        });
  }

  // --- Test 4: create-from-deal pre-fills customer + value + dealId ---

  @Test
  void createFromDeal_prefillsCustomerValueAndDealId() {
    var ids =
        runInTenant(
            () -> {
              var customer = createProspectCustomer();
              var deal = createDeal(customer.getId(), "Create-from deal", "12345.00");
              var dto =
                  dealProposalService.createFromDeal(
                      deal.id(),
                      new CreateDealProposalRequest(
                          "Proposal from deal", FeeModel.FIXED, null, "ZAR", null, null, null),
                      memberId);
              return new UUID[] {customer.getId(), deal.id(), dto.id()};
            });

    runInTenant(
        () -> {
          var proposal = proposalRepository.findById(ids[2]).orElseThrow();
          assertThat(proposal.getCustomerId()).isEqualTo(ids[0]);
          assertThat(proposal.getDealId()).isEqualTo(ids[1]);
          // FIXED with no explicit amount -> pre-filled from the deal value.
          assertThat(proposal.getFixedFeeAmount()).isEqualByComparingTo(new BigDecimal("12345.00"));
          return null;
        });
  }

  // --- Test 5: link-existing sets dealId ---

  @Test
  void linkExisting_setsDealId() {
    var ids =
        runInTenant(
            () -> {
              var customer = createProspectCustomer();
              var deal = createDeal(customer.getId(), "Link-existing deal", "3000.00");
              var proposal = createSentLinkedProposal(customer.getId(), null, FeeModel.HOURLY);
              dealProposalService.linkExisting(deal.id(), proposal.getId());
              return new UUID[] {deal.id(), proposal.getId()};
            });

    runInTenant(
        () -> {
          var proposal = proposalRepository.findById(ids[1]).orElseThrow();
          assertThat(proposal.getDealId()).isEqualTo(ids[0]);
          assertThat(dealProposalService.listForDeal(ids[0]))
              .extracting(p -> p.id())
              .contains(ids[1]);
          return null;
        });
  }

  // --- Test 6: delete-deal-with-linked-proposal is blocked (409 via DeleteGuard) ---

  @Test
  void deleteDeal_withLinkedProposal_isBlocked() {
    var dealId =
        runInTenant(
            () -> {
              var customer = createProspectCustomer();
              var deal = createDeal(customer.getId(), "Guarded deal", "1000.00");
              var proposal = createSentLinkedProposal(customer.getId(), deal.id(), FeeModel.HOURLY);
              assertThat(proposal.getDealId()).isEqualTo(deal.id());
              return deal.id();
            });

    assertThatThrownBy(
            () ->
                runInTenant(
                    () -> {
                      dealService.deleteDeal(dealId);
                      return null;
                    }))
        .isInstanceOf(ResourceConflictException.class)
        .hasMessageContaining("linked proposals");

    runInTenant(
        () -> {
          // Deal still present after the blocked delete.
          assertThat(dealRepository.findById(dealId)).isPresent();
          return null;
        });
  }

  // --- Helpers ---

  private record Setup(UUID customerId, UUID dealId, UUID proposalId, UUID portalContactId) {}

  private Customer createProspectCustomer() {
    var customer =
        TestCustomerFactory.createCustomerWithStatus(
            "DP Customer " + (++counter),
            "dp_customer_" + counter + "@test.com",
            memberId,
            LifecycleStatus.PROSPECT);
    return customerRepository.save(customer);
  }

  private DealResponse createDeal(UUID customerId, String title, String valueAmount) {
    return dealService.createDeal(
        customerId, title, null, new BigDecimal(valueAmount), memberId, null, null, memberId);
  }

  /**
   * Creates a SENT proposal (with a real portal contact) optionally linked to a deal. The HOURLY
   * fee model keeps acceptance billing-free so the test focuses on the win-loop glue.
   */
  private Proposal createSentLinkedProposal(UUID customerId, UUID dealId, FeeModel feeModel) {
    var proposal =
        new Proposal(
            "P-DP-" + (++counter), "DP Proposal " + counter, customerId, feeModel, memberId);
    if (dealId != null) {
      proposal.setDealId(dealId);
    }
    proposal = proposalRepository.save(proposal);

    UUID portalContactId = createPortalContact(customerId);
    proposal.markSent(portalContactId);
    return proposalRepository.save(proposal);
  }

  private UUID createPortalContact(UUID customerId) {
    var contact =
        new PortalContact(
            ORG_ID,
            customerId,
            "dp_contact_" + counter + "@test.com",
            "DP Contact " + counter,
            PortalContact.ContactRole.PRIMARY);
    return portalContactRepository.save(contact).getId();
  }

  private long dealWonAuditCount(UUID dealId) {
    return auditService
        .findEvents(
            new AuditEventFilter("DEAL", dealId, null, "deal.won", null, null, null),
            Pageable.ofSize(50))
        .getContent()
        .stream()
        .filter(e -> "deal.won".equals(e.getEventType()))
        .count();
  }

  private <T> T runInTenant(java.util.concurrent.Callable<T> callable) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .call(
            () ->
                transactionTemplate.execute(
                    tx -> {
                      try {
                        return callable.call();
                      } catch (RuntimeException e) {
                        throw e;
                      } catch (Exception e) {
                        throw new RuntimeException(e);
                      }
                    }));
  }
}
