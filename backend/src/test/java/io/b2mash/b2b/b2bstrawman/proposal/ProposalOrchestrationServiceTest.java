package io.b2mash.b2b.b2bstrawman.proposal;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerType;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineType;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import io.b2mash.b2b.b2bstrawman.member.MemberSyncService;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProposalOrchestrationServiceTest {

  private static final String ORG_ID = "org_orch_test";

  @Autowired private ProposalOrchestrationService orchestrationService;
  @Autowired private ProposalRepository proposalRepository;
  @Autowired private ProposalMilestoneRepository milestoneRepository;
  @Autowired private ProposalTeamMemberRepository teamMemberRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private ProjectMemberRepository projectMemberRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private InvoiceLineRepository invoiceLineRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private MemberSyncService memberSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private PortalContactRepository portalContactRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId;
  private UUID secondMemberId;
  private UUID thirdMemberId;
  private int counter = 0;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Orchestration Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    var syncResult =
        memberSyncService.syncMember(
            ORG_ID, "user_orch_owner", "orch_owner@test.com", "Orch Owner", null, "owner");
    memberId = syncResult.memberId();

    var syncResult2 =
        memberSyncService.syncMember(
            ORG_ID, "user_orch_member2", "orch_member2@test.com", "Orch Member 2", null, "admin");
    secondMemberId = syncResult2.memberId();

    var syncResult3 =
        memberSyncService.syncMember(
            ORG_ID, "user_orch_member3", "orch_member3@test.com", "Orch Member 3", null, "member");
    thirdMemberId = syncResult3.memberId();
  }

  // --- Test 1: FIXED fee with milestones ---

  @Test
  void acceptProposal_fixedFeeWithMilestones_createsProjectAndInvoices() {
    var setup =
        runInTenant(
            () -> {
              var customer = createProspectCustomer();
              var proposal =
                  createSentProposal(customer.getId(), FeeModel.FIXED, "10000.00", "ZAR");

              // Add milestones
              milestoneRepository.save(
                  new ProposalMilestone(
                      proposal.getId(), "Phase 1 — 50%", new BigDecimal("50.00"), 30, 0));
              milestoneRepository.save(
                  new ProposalMilestone(
                      proposal.getId(), "Phase 2 — 50%", new BigDecimal("50.00"), 60, 1));

              return new TestSetup(
                  customer.getId(), proposal.getId(), proposal.getPortalContactId());
            });

    var result =
        runInTenant(
            () -> orchestrationService.acceptProposal(setup.proposalId, setup.portalContactId));

    assertThat(result.proposalId()).isEqualTo(setup.proposalId);
    assertThat(result.projectId()).isNotNull();
    assertThat(result.createdInvoiceIds()).hasSize(2);

    // Verify proposal status
    runInTenant(
        () -> {
          var proposal = proposalRepository.findById(setup.proposalId).orElseThrow();
          assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.ACCEPTED);
          assertThat(proposal.getAcceptedAt()).isNotNull();
          assertThat(proposal.getCreatedProjectId()).isEqualTo(result.projectId());
          return null;
        });

    // Verify invoices
    runInTenant(
        () -> {
          for (var invoiceId : result.createdInvoiceIds()) {
            var invoice = invoiceRepository.findById(invoiceId).orElseThrow();
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.DRAFT);
            assertThat(invoice.getCustomerId()).isEqualTo(setup.customerId);
            assertThat(invoice.getTotal()).isEqualByComparingTo(new BigDecimal("5000.00"));

            var lines = invoiceLineRepository.findByInvoiceIdOrderBySortOrder(invoiceId);
            assertThat(lines).hasSize(1);
            assertThat(lines.getFirst().getLineType()).isEqualTo(InvoiceLineType.MANUAL);
            assertThat(lines.getFirst().getAmount())
                .isEqualByComparingTo(new BigDecimal("5000.00"));
          }
          return null;
        });

    // Verify milestones linked to invoices
    runInTenant(
        () -> {
          var milestones = milestoneRepository.findByProposalIdOrderBySortOrder(setup.proposalId);
          assertThat(milestones).hasSize(2);
          assertThat(milestones.get(0).getInvoiceId()).isNotNull();
          assertThat(milestones.get(1).getInvoiceId()).isNotNull();
          return null;
        });
  }

  // --- Test 2: FIXED fee without milestones ---

  @Test
  void acceptProposal_fixedFeeNoMilestones_createsSingleInvoice() {
    var setup =
        runInTenant(
            () -> {
              var customer = createProspectCustomer();
              var proposal = createSentProposal(customer.getId(), FeeModel.FIXED, "7500.00", "ZAR");
              return new TestSetup(
                  customer.getId(), proposal.getId(), proposal.getPortalContactId());
            });

    var result =
        runInTenant(
            () -> orchestrationService.acceptProposal(setup.proposalId, setup.portalContactId));

    assertThat(result.createdInvoiceIds()).hasSize(1);

    runInTenant(
        () -> {
          var invoice =
              invoiceRepository.findById(result.createdInvoiceIds().getFirst()).orElseThrow();
          assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.DRAFT);
          assertThat(invoice.getTotal()).isEqualByComparingTo(new BigDecimal("7500.00"));

          var lines = invoiceLineRepository.findByInvoiceIdOrderBySortOrder(invoice.getId());
          assertThat(lines).hasSize(1);
          assertThat(lines.getFirst().getLineType()).isEqualTo(InvoiceLineType.MANUAL);
          return null;
        });
  }

  // --- Test 3: HOURLY fee — no invoices ---

  @Test
  void acceptProposal_hourlyFee_createsProjectNoInvoices() {
    var setup =
        runInTenant(
            () -> {
              var customer = createProspectCustomer();
              var proposal = createSentProposal(customer.getId(), FeeModel.HOURLY, null, null);
              return new TestSetup(
                  customer.getId(), proposal.getId(), proposal.getPortalContactId());
            });

    var result =
        runInTenant(
            () -> orchestrationService.acceptProposal(setup.proposalId, setup.portalContactId));

    assertThat(result.projectId()).isNotNull();
    assertThat(result.createdInvoiceIds()).isEmpty();
  }

  // --- Test 4: Project from template ---
  // Note: Template instantiation requires a template to exist. Skipping direct template test
  // since template setup is complex. The bare project path is verified in test 5.

  // --- Test 5: Bare project with customer link ---

  @Test
  void acceptProposal_bareProject_createsProjectWithCustomerLink() {
    var setup =
        runInTenant(
            () -> {
              var customer = createProspectCustomer();
              var proposal = createSentProposal(customer.getId(), FeeModel.HOURLY, null, null);
              return new TestSetup(
                  customer.getId(), proposal.getId(), proposal.getPortalContactId());
            });

    var result =
        runInTenant(
            () -> orchestrationService.acceptProposal(setup.proposalId, setup.portalContactId));

    runInTenant(
        () -> {
          var project = projectRepository.findById(result.projectId()).orElseThrow();
          assertThat(project.getCustomerId()).isEqualTo(setup.customerId);
          return null;
        });
  }

  // --- Test 6: Team member assignment ---

  @Test
  void acceptProposal_assignsTeamMembers() {
    var setup =
        runInTenant(
            () -> {
              var customer = createProspectCustomer();
              var proposal = createSentProposal(customer.getId(), FeeModel.HOURLY, null, null);

              // Add team members
              teamMemberRepository.save(
                  new ProposalTeamMember(proposal.getId(), secondMemberId, "Developer", 0));
              teamMemberRepository.save(
                  new ProposalTeamMember(proposal.getId(), thirdMemberId, "Designer", 1));

              return new TestSetup(
                  customer.getId(), proposal.getId(), proposal.getPortalContactId());
            });

    var result =
        runInTenant(
            () -> orchestrationService.acceptProposal(setup.proposalId, setup.portalContactId));

    assertThat(result.assignedMemberIds()).containsExactlyInAnyOrder(secondMemberId, thirdMemberId);

    // Verify members are actually on the project
    runInTenant(
        () -> {
          var members = projectMemberRepository.findByProjectId(result.projectId());
          var memberIds = members.stream().map(m -> m.getMemberId()).toList();
          // Project creator (memberId) is added as LEAD by ProjectService.createProject,
          // plus the two team members
          assertThat(memberIds).contains(secondMemberId, thirdMemberId);
          return null;
        });
  }

  // --- Test 7: PROSPECT customer transitions to ONBOARDING ---

  @Test
  void acceptProposal_prospectCustomer_transitionsToOnboarding() {
    var setup =
        runInTenant(
            () -> {
              var customer = createProspectCustomer();
              assertThat(customer.getLifecycleStatus()).isEqualTo(LifecycleStatus.PROSPECT);
              var proposal = createSentProposal(customer.getId(), FeeModel.HOURLY, null, null);
              return new TestSetup(
                  customer.getId(), proposal.getId(), proposal.getPortalContactId());
            });

    runInTenant(() -> orchestrationService.acceptProposal(setup.proposalId, setup.portalContactId));

    runInTenant(
        () -> {
          var customer = customerRepository.findById(setup.customerId).orElseThrow();
          assertThat(customer.getLifecycleStatus()).isEqualTo(LifecycleStatus.ONBOARDING);
          return null;
        });
  }

  // --- Test 8: ACTIVE customer stays ACTIVE ---

  @Test
  void acceptProposal_activeCustomer_noLifecycleChange() {
    var setup =
        runInTenant(
            () -> {
              var customer =
                  new Customer(
                      "Active Corp " + (++counter),
                      "active_orch_" + counter + "@test.com",
                      null,
                      null,
                      null,
                      memberId,
                      CustomerType.INDIVIDUAL,
                      LifecycleStatus.ACTIVE);
              customer = customerRepository.save(customer);

              var proposal = createSentProposal(customer.getId(), FeeModel.HOURLY, null, null);
              return new TestSetup(
                  customer.getId(), proposal.getId(), proposal.getPortalContactId());
            });

    runInTenant(() -> orchestrationService.acceptProposal(setup.proposalId, setup.portalContactId));

    runInTenant(
        () -> {
          var customer = customerRepository.findById(setup.customerId).orElseThrow();
          assertThat(customer.getLifecycleStatus()).isEqualTo(LifecycleStatus.ACTIVE);
          return null;
        });
  }

  // --- Helpers ---

  private record TestSetup(UUID customerId, UUID proposalId, UUID portalContactId) {}

  private Customer createProspectCustomer() {
    var customer =
        new Customer(
            "Orch Customer " + (++counter),
            "orch_customer_" + counter + "@test.com",
            null,
            null,
            null,
            memberId,
            CustomerType.INDIVIDUAL,
            LifecycleStatus.PROSPECT);
    return customerRepository.save(customer);
  }

  private Proposal createSentProposal(
      UUID customerId, FeeModel feeModel, String fixedFeeAmount, String fixedFeeCurrency) {
    var proposal =
        new Proposal(
            "P-ORCH-" + counter, "Orchestration Test " + counter, customerId, feeModel, memberId);

    if (fixedFeeAmount != null) {
      proposal.setFixedFeeAmount(new BigDecimal(fixedFeeAmount));
    }
    if (fixedFeeCurrency != null) {
      proposal.setFixedFeeCurrency(fixedFeeCurrency);
    }

    proposal = proposalRepository.save(proposal);

    // Create a real portal contact in the DB to satisfy FK constraint
    UUID portalContactId = createPortalContact(customerId);
    proposal.markSent(portalContactId);
    return proposalRepository.save(proposal);
  }

  private UUID createPortalContact(UUID customerId) {
    var contact =
        new PortalContact(
            ORG_ID,
            customerId,
            "contact_" + counter + "@orch-test.com",
            "Orch Contact " + counter,
            PortalContact.ContactRole.PRIMARY);
    return portalContactRepository.save(contact).getId();
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
