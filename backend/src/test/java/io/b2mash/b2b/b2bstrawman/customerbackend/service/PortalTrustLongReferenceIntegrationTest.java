package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordDepositRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * G89-F36 (MEDIUM, D35): the firm trust {@code reference} is 200 characters but the portal
 * read-model column was 100, with no validation or truncation, so a reference over 100 characters
 * failed the portal upsert and that matter's portal sync aborted (the deposit never reached the
 * portal). The portal column now matches the firm (global V6).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalTrustLongReferenceIntegrationTest {

  private static final String ORG_ID = "org_portal_trust_long_reference";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TrustAccountRepository trustAccountRepository;
  @Autowired private TrustTransactionService trustTransactionService;
  @Autowired private TransactionTemplate transactionTemplate;

  @Autowired
  @Qualifier("portalJdbcClient")
  private JdbcClient portalJdbc;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;
  private UUID matterId;
  private UUID trustAccountId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Long Reference Firm", "legal-za").schemaName();
    memberId = UUID.fromString(TestMemberHelper.syncOwner(mockMvc, ORG_ID, "portal_long_ref"));

    inTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsService.getOrCreateForCurrentTenant();
                  settings.setEnabledModules(List.of("trust_accounting"));
                  orgSettingsRepository.save(settings);

                  customerId =
                      customerRepository
                          .saveAndFlush(
                              TestCustomerFactory.createActiveCustomer(
                                  "Long Reference Client", "long-ref@test.com", memberId))
                          .getId();
                  var matter = new Project("Long Reference Matter", "G89-F36", memberId);
                  matter.setCustomerId(customerId);
                  matterId = projectRepository.saveAndFlush(matter).getId();

                  trustAccountId =
                      trustAccountRepository
                          .saveAndFlush(
                              new TrustAccount(
                                  "Long Reference Trust",
                                  "Test Bank",
                                  "250655",
                                  "6200-000-LREF",
                                  TrustAccountType.GENERAL,
                                  true,
                                  false,
                                  null,
                                  LocalDate.of(2026, 1, 1),
                                  "G89-F36 tests"))
                          .getId();
                }));
  }

  @Test
  void deposit_with150CharReference_reachesPortal_withFullReference() {
    String reference = "EFT-" + "R".repeat(146);
    assertThat(reference).hasSize(150);

    UUID depositId =
        inTenantReturning(
            () ->
                transactionTemplate.execute(
                    tx ->
                        trustTransactionService
                            .recordDeposit(
                                trustAccountId,
                                new RecordDepositRequest(
                                    customerId,
                                    matterId,
                                    new BigDecimal("1500.00"),
                                    reference,
                                    "Long reference deposit",
                                    LocalDate.of(2026, 11, 2)))
                            .id()));

    List<String> portalReferences =
        portalJdbc
            .sql("SELECT reference FROM portal.portal_trust_transaction WHERE id = ?")
            .params(depositId)
            .query(String.class)
            .list();
    assertThat(portalReferences)
        .as("the deposit is projected to the portal with its whole reference")
        .containsExactly(reference);

    BigDecimal portalBalance =
        portalJdbc
            .sql(
                "SELECT current_balance FROM portal.portal_trust_balance"
                    + " WHERE customer_id = ? AND matter_id = ?")
            .params(customerId, matterId)
            .query(BigDecimal.class)
            .single();
    assertThat(portalBalance).isEqualByComparingTo("1500.00");
  }

  private void inTenant(Runnable runnable) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(runnable);
  }

  private <T> T inTenantReturning(ScopedValue.CallableOp<T, RuntimeException> op) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .call(op);
  }
}
