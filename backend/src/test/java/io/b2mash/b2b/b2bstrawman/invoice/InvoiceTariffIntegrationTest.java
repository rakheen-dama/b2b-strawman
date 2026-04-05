package io.b2mash.b2b.b2bstrawman.invoice;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ModuleNotEnabledException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.invoice.dto.AddLineItemRequest;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.tariff.TariffItem;
import io.b2mash.b2b.b2bstrawman.verticals.legal.tariff.TariffItemRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.tariff.TariffSchedule;
import io.b2mash.b2b.b2bstrawman.verticals.legal.tariff.TariffScheduleRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvoiceTariffIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_inv_tariff_test";
  private static final String DISABLED_ORG_ID = "org_inv_tariff_disabled";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private InvoiceService invoiceService;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private InvoiceLineRepository invoiceLineRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private TariffScheduleRepository scheduleRepository;
  @Autowired private TariffItemRepository tariffItemRepository;

  private String tenantSchema;
  private String disabledTenantSchema;
  private UUID memberId;
  private UUID disabledMemberId;
  private UUID customerId;
  private UUID disabledCustomerId;
  private UUID tariffItemId;
  private BigDecimal tariffItemAmount;

  @BeforeAll
  void setup() throws Exception {
    // Provision enabled tenant
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Inv Tariff Test Org", null).schemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_inv_tariff",
                "inv_tariff@test.com",
                "Inv Tariff Owner",
                "owner"));

    // Enable lssa_tariff module
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("lssa_tariff"));
                      orgSettingsRepository.save(settings);
                    }));

    // Create customer and tariff data in enabled tenant
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      createActiveCustomer("Tariff Invoice Corp", "tariff_inv@test.com", memberId);
                  customer = customerRepository.save(customer);
                  customerId = customer.getId();

                  var schedule =
                      new TariffSchedule(
                          "Test Schedule",
                          "PARTY_AND_PARTY",
                          "HIGH_COURT",
                          LocalDate.of(2024, 4, 1),
                          null,
                          "Test Source");
                  schedule = scheduleRepository.save(schedule);

                  var item =
                      new TariffItem(
                          schedule,
                          "1(a)",
                          "Instructions",
                          "Taking instructions to sue or defend",
                          new BigDecimal("500.00"),
                          "PER_ITEM",
                          null,
                          1);
                  item = tariffItemRepository.save(item);
                  tariffItemId = item.getId();
                  tariffItemAmount = item.getAmount();
                }));

    // Provision disabled tenant (no modules enabled)
    disabledTenantSchema =
        provisioningService
            .provisionTenant(DISABLED_ORG_ID, "Tariff Disabled Org", null)
            .schemaName();
    disabledMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                DISABLED_ORG_ID,
                "user_inv_tariff_dis",
                "inv_tariff_dis@test.com",
                "Tariff Disabled Owner",
                "owner"));

    // Create customer in disabled tenant
    ScopedValue.where(RequestScopes.TENANT_ID, disabledTenantSchema)
        .where(RequestScopes.ORG_ID, DISABLED_ORG_ID)
        .where(RequestScopes.MEMBER_ID, disabledMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var customer =
                          createActiveCustomer(
                              "Disabled Tariff Corp", "disabled_tariff@test.com", disabledMemberId);
                      customer = customerRepository.save(customer);
                      disabledCustomerId = customer.getId();
                    }));
  }

  // --- Task 403.5: Tariff line creation tests ---

  @Test
  void addTariffLine_createsTariffLineWithCorrectType() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var invoice = createDraftInvoice();

                  var request =
                      new AddLineItemRequest(
                          null,
                          null, // description from tariff item
                          new BigDecimal("2"),
                          null, // unitPrice from tariff item
                          0,
                          null,
                          tariffItemId);

                  var response = invoiceService.addLineItem(invoice.getId(), request);

                  assertThat(response.lines()).hasSize(1);
                  var line = response.lines().get(0);
                  assertThat(line.lineType()).isEqualTo(InvoiceLineType.TARIFF);
                  assertThat(line.tariffItemId()).isEqualTo(tariffItemId);
                  assertThat(line.lineSource()).isEqualTo("TARIFF");
                  assertThat(line.tariffItemNumber()).isEqualTo("1(a)");
                  assertThat(line.description()).isEqualTo("Taking instructions to sue or defend");
                }));
  }

  @Test
  void addTariffLine_calculatesAmountFromTariffItemTimesQuantity() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var invoice = createDraftInvoice();

                  var request =
                      new AddLineItemRequest(
                          null,
                          null,
                          new BigDecimal("3"),
                          null, // use tariff item amount
                          0,
                          null,
                          tariffItemId);

                  var response = invoiceService.addLineItem(invoice.getId(), request);

                  var line = response.lines().get(0);
                  // 500.00 * 3 = 1500.00
                  assertThat(line.unitPrice()).isEqualByComparingTo(tariffItemAmount);
                  assertThat(line.amount()).isEqualByComparingTo(new BigDecimal("1500.00"));
                }));
  }

  @Test
  void addTariffLine_withAmountOverride_usesOverrideButPreservesTariffItemId() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var invoice = createDraftInvoice();

                  var request =
                      new AddLineItemRequest(
                          null,
                          "Custom description override",
                          new BigDecimal("1"),
                          new BigDecimal("750.00"), // override amount
                          0,
                          null,
                          tariffItemId);

                  var response = invoiceService.addLineItem(invoice.getId(), request);

                  var line = response.lines().get(0);
                  assertThat(line.tariffItemId()).isEqualTo(tariffItemId);
                  assertThat(line.unitPrice()).isEqualByComparingTo(new BigDecimal("750.00"));
                  assertThat(line.amount()).isEqualByComparingTo(new BigDecimal("750.00"));
                  assertThat(line.description()).isEqualTo("Custom description override");
                  assertThat(line.lineSource()).isEqualTo("TARIFF");
                }));
  }

  // --- Task 403.6: Module guard and regression tests ---

  @Test
  void addTariffLine_throwsWhenModuleNotEnabled() {
    // Create invoice in a separate transaction first
    final UUID[] invoiceIdHolder = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, disabledTenantSchema)
        .where(RequestScopes.ORG_ID, DISABLED_ORG_ID)
        .where(RequestScopes.MEMBER_ID, disabledMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var invoice =
                          new Invoice(
                              disabledCustomerId,
                              "ZAR",
                              "Disabled Tariff Corp",
                              "disabled_tariff@test.com",
                              null,
                              "Tariff Disabled Org",
                              disabledMemberId);
                      invoice = invoiceRepository.save(invoice);
                      invoiceIdHolder[0] = invoice.getId();
                    }));

    // Now test the module guard outside the transaction
    ScopedValue.where(RequestScopes.TENANT_ID, disabledTenantSchema)
        .where(RequestScopes.ORG_ID, DISABLED_ORG_ID)
        .where(RequestScopes.MEMBER_ID, disabledMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var request =
                  new AddLineItemRequest(
                      null, null, new BigDecimal("1"), null, 0, null, UUID.randomUUID());

              assertThatThrownBy(() -> invoiceService.addLineItem(invoiceIdHolder[0], request))
                  .isInstanceOf(ModuleNotEnabledException.class);
            });
  }

  @Test
  void addTariffLine_throwsWhenTariffItemNotFound() {
    // Create invoice in a separate transaction first
    final UUID[] invoiceIdHolder = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var invoice = createDraftInvoice();
                  invoiceIdHolder[0] = invoice.getId();
                }));

    // Now test the not-found case outside the transaction
    runInTenant(
        () -> {
          var nonExistentId = UUID.randomUUID();
          var request =
              new AddLineItemRequest(null, null, new BigDecimal("1"), null, 0, null, nonExistentId);

          assertThatThrownBy(() -> invoiceService.addLineItem(invoiceIdHolder[0], request))
              .isInstanceOf(ResourceNotFoundException.class);
        });
  }

  @Test
  void addManualLine_stillWorksCorrectly() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var invoice = createDraftInvoice();

                  var request =
                      new AddLineItemRequest(
                          null,
                          "Manual time entry",
                          new BigDecimal("4"),
                          new BigDecimal("100.00"),
                          0,
                          null,
                          null); // no tariffItemId

                  var response = invoiceService.addLineItem(invoice.getId(), request);

                  assertThat(response.lines()).hasSize(1);
                  var line = response.lines().get(0);
                  assertThat(line.lineType()).isEqualTo(InvoiceLineType.MANUAL);
                  assertThat(line.tariffItemId()).isNull();
                  assertThat(line.lineSource()).isNull();
                  assertThat(line.tariffItemNumber()).isNull();
                  assertThat(line.description()).isEqualTo("Manual time entry");
                  assertThat(line.amount()).isEqualByComparingTo(new BigDecimal("400.00"));
                }));
  }

  // --- Helpers ---

  private Invoice createDraftInvoice() {
    var invoice =
        new Invoice(
            customerId,
            "ZAR",
            "Tariff Invoice Corp",
            "tariff_inv@test.com",
            null,
            "Inv Tariff Test Org",
            memberId);
    return invoiceRepository.save(invoice);
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
