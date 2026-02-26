package io.b2mash.b2b.b2bstrawman.invoice;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
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
class PaymentEventRepositoryIntegrationTest {

  private static final String ORG_ID = "org_payment_event_test";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private PaymentEventRepository paymentEventRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private MemberRepository memberRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID invoiceId;

  @BeforeAll
  void provisionTenant() {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Payment Event Test Org").schemaName();
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    // Create a member, customer, and invoice to reference from payment events
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var member =
                          new Member(
                              "user_pe_test", "pe_test@example.com", "PE Tester", null, "owner");
                      member = memberRepository.save(member);

                      var customer =
                          createActiveCustomer("Test Customer", "test@example.com", member.getId());
                      customer = customerRepository.save(customer);

                      var invoice =
                          new Invoice(
                              customer.getId(),
                              "ZAR",
                              "Test Customer",
                              "test@example.com",
                              "123 Test St",
                              "Test Org",
                              member.getId());
                      invoice = invoiceRepository.save(invoice);
                      invoiceId = invoice.getId();
                    }));
  }

  @Test
  void save_and_findById() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var event =
                          new PaymentEvent(
                              invoiceId,
                              "stripe",
                              "cs_test_001",
                              PaymentEventStatus.CREATED,
                              new BigDecimal("1500.00"),
                              "ZAR",
                              "OPERATING");

                      var saved = paymentEventRepository.save(event);
                      assertThat(saved.getId()).isNotNull();

                      var found = paymentEventRepository.findById(saved.getId());
                      assertThat(found).isPresent();
                      assertThat(found.get().getInvoiceId()).isEqualTo(invoiceId);
                      assertThat(found.get().getProviderSlug()).isEqualTo("stripe");
                      assertThat(found.get().getSessionId()).isEqualTo("cs_test_001");
                      assertThat(found.get().getStatus()).isEqualTo(PaymentEventStatus.CREATED);
                      assertThat(found.get().getAmount())
                          .isEqualByComparingTo(new BigDecimal("1500.00"));
                      assertThat(found.get().getCurrency()).isEqualTo("ZAR");
                      assertThat(found.get().getPaymentDestination()).isEqualTo("OPERATING");
                      assertThat(found.get().getCreatedAt()).isNotNull();
                      assertThat(found.get().getUpdatedAt()).isNotNull();
                    }));
  }

  @Test
  void findByInvoiceIdOrderByCreatedAtDesc_returns_ordered() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var event1 =
                          new PaymentEvent(
                              invoiceId,
                              "stripe",
                              "cs_order_001",
                              PaymentEventStatus.CREATED,
                              new BigDecimal("100.00"),
                              "ZAR",
                              "OPERATING");
                      paymentEventRepository.saveAndFlush(event1);

                      try {
                        Thread.sleep(10);
                      } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                      }

                      var event2 =
                          new PaymentEvent(
                              invoiceId,
                              "stripe",
                              "cs_order_002",
                              PaymentEventStatus.PENDING,
                              new BigDecimal("200.00"),
                              "ZAR",
                              "OPERATING");
                      paymentEventRepository.saveAndFlush(event2);

                      var results =
                          paymentEventRepository.findByInvoiceIdOrderByCreatedAtDesc(invoiceId);

                      assertThat(results).hasSizeGreaterThanOrEqualTo(2);
                      // Most recent first — event2 was saved after event1
                      var createdAts = results.stream().map(PaymentEvent::getCreatedAt).toList();
                      for (int i = 0; i < createdAts.size() - 1; i++) {
                        assertThat(createdAts.get(i)).isAfterOrEqualTo(createdAts.get(i + 1));
                      }
                    }));
  }

  @Test
  void findBySessionIdAndStatus_finds_match() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var event =
                          new PaymentEvent(
                              invoiceId,
                              "payfast",
                              "cs_find_001",
                              PaymentEventStatus.PENDING,
                              new BigDecimal("500.00"),
                              "ZAR",
                              "OPERATING");
                      paymentEventRepository.save(event);

                      var found =
                          paymentEventRepository.findBySessionIdAndStatus(
                              "cs_find_001", PaymentEventStatus.PENDING);

                      assertThat(found).isPresent();
                      assertThat(found.get().getSessionId()).isEqualTo("cs_find_001");
                      assertThat(found.get().getStatus()).isEqualTo(PaymentEventStatus.PENDING);
                    }));
  }

  @Test
  void findBySessionIdAndStatus_returns_empty_when_no_match() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var event =
                          new PaymentEvent(
                              invoiceId,
                              "stripe",
                              "cs_nomatch_001",
                              PaymentEventStatus.COMPLETED,
                              new BigDecimal("750.00"),
                              "ZAR",
                              "OPERATING");
                      paymentEventRepository.save(event);

                      // Search with wrong status
                      var found =
                          paymentEventRepository.findBySessionIdAndStatus(
                              "cs_nomatch_001", PaymentEventStatus.FAILED);
                      assertThat(found).isEmpty();

                      // Search with wrong session ID
                      var found2 =
                          paymentEventRepository.findBySessionIdAndStatus(
                              "cs_nonexistent", PaymentEventStatus.COMPLETED);
                      assertThat(found2).isEmpty();
                    }));
  }

  @Test
  void existsBySessionIdAndStatus() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var event =
                          new PaymentEvent(
                              invoiceId,
                              "manual",
                              "cs_exists_001",
                              PaymentEventStatus.CREATED,
                              new BigDecimal("300.00"),
                              "ZAR",
                              "TRUST");
                      paymentEventRepository.save(event);

                      assertThat(
                              paymentEventRepository.existsBySessionIdAndStatus(
                                  "cs_exists_001", PaymentEventStatus.CREATED))
                          .isTrue();

                      assertThat(
                              paymentEventRepository.existsBySessionIdAndStatus(
                                  "cs_exists_001", PaymentEventStatus.FAILED))
                          .isFalse();

                      assertThat(
                              paymentEventRepository.existsBySessionIdAndStatus(
                                  "cs_nonexistent", PaymentEventStatus.CREATED))
                          .isFalse();
                    }));
  }
}
