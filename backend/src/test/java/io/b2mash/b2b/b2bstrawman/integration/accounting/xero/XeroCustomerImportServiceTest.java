package io.b2mash.b2b.b2bstrawman.integration.accounting.xero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegration;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegrationRepository;
import io.b2mash.b2b.b2bstrawman.integration.secret.SecretStore;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class XeroCustomerImportServiceTest {

  private static final String ORG_ID = "org_xero_import_test";

  @Autowired private XeroCustomerImportService importService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private AccountingXeroConnectionRepository xeroConnectionRepository;
  @Autowired private OrgIntegrationRepository orgIntegrationRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private MockMvc mockMvc;

  @MockitoBean private XeroApiClient xeroApiClient;
  @MockitoBean private SecretStore secretStore;

  private String tenantSchema;
  private UUID memberId;
  private UUID connectionId;
  private UUID orgIntegrationId;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Xero Import Test Org", null);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    String memberIdStr =
        TestMemberHelper.syncMember(
            mockMvc,
            ORG_ID,
            "user_xero_import_test",
            "xero-import@test.com",
            "Xero Import Tester",
            "owner");
    memberId = UUID.fromString(memberIdStr);

    // Create the OrgIntegration once (unique constraint on domain)
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var orgIntegration = new OrgIntegration(IntegrationDomain.ACCOUNTING, "xero");
              orgIntegration.enable();
              var saved = orgIntegrationRepository.save(orgIntegration);
              orgIntegrationId = saved.getId();
            });
  }

  /**
   * Creates a fresh AccountingXeroConnection before each test and resets the one-time import guard
   * so every test starts with a clean state.
   */
  @BeforeEach
  void resetAndCreateFreshConnection() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              // Clean up customers from previous test runs to avoid dedup interference
              customerRepository.deleteAll();
              customerRepository.flush();

              // Reset the import guard on the shared OrgIntegration
              var orgIntegration =
                  orgIntegrationRepository.findById(orgIntegrationId).orElseThrow();
              orgIntegration.updateProvider("xero", null);
              orgIntegrationRepository.save(orgIntegration);

              // Delete any existing connection for this org integration (unique constraint)
              xeroConnectionRepository
                  .findByOrgIntegrationId(orgIntegrationId)
                  .ifPresent(xeroConnectionRepository::delete);
              xeroConnectionRepository.flush();

              // Create a fresh connection for this test
              var connection =
                  new AccountingXeroConnection(
                      orgIntegrationId,
                      "xero-tenant-" + UUID.randomUUID().toString().substring(0, 8),
                      "Test Xero Org",
                      memberId,
                      Instant.now().plus(30, ChronoUnit.MINUTES),
                      "accounting.transactions openid profile email");
              var savedConn = xeroConnectionRepository.save(connection);
              connectionId = savedConn.getId();
            });

    // Stub the secret store to return a valid access token
    when(secretStore.retrieve(orgIntegrationId.toString() + ":xero:access"))
        .thenReturn("test-access-token");
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(action);
  }

  @Test
  void importCreatesNewCustomersForContactsWithNoKaziMatch() {
    when(xeroApiClient.getContacts(anyString(), anyInt(), anyString()))
        .thenReturn(
            Map.of(
                "Contacts",
                List.of(
                    xeroContact("c1-id", "Alpha Corp", "alpha@example.com", null),
                    xeroContact("c2-id", "Beta Inc", "beta@example.com", null))))
        .thenReturn(Map.of("Contacts", List.of()));

    runInTenant(
        () -> {
          var summary = importService.importCustomersFromXero(connectionId, memberId);

          assertThat(summary.created()).isEqualTo(2);
          assertThat(summary.skippedDuplicate()).isZero();
          assertThat(summary.skippedNoEmail()).isZero();
          assertThat(summary.total()).isEqualTo(2);

          // Verify customers are created with correct attributes
          var alpha = customerRepository.findByEmail("alpha@example.com").orElseThrow();
          assertThat(alpha.getName()).isEqualTo("Alpha Corp");
          assertThat(alpha.getLifecycleStatus())
              .isEqualTo(io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus.PROSPECT);
          assertThat(alpha.getCustomFields()).containsEntry("xero_contact_id", "c1-id");
          assertThat(alpha.getNotes()).isEqualTo("Imported from Xero");

          var beta = customerRepository.findByEmail("beta@example.com").orElseThrow();
          assertThat(beta.getCustomFields()).containsEntry("xero_contact_id", "c2-id");
        });
  }

  @Test
  void importSkipsContactsWithoutEmail() {
    when(xeroApiClient.getContacts(anyString(), anyInt(), anyString()))
        .thenReturn(
            Map.of(
                "Contacts",
                List.of(
                    xeroContact("c3-id", "Has Email Corp", "has@example.com", null),
                    xeroContactNoEmail("c4-id", "No Email Corp"))))
        .thenReturn(Map.of("Contacts", List.of()));

    runInTenant(
        () -> {
          var summary = importService.importCustomersFromXero(connectionId, memberId);

          assertThat(summary.created()).isEqualTo(1);
          assertThat(summary.skippedNoEmail()).isEqualTo(1);
          assertThat(summary.total()).isEqualTo(2);
        });
  }

  @Test
  void importDeduplicatesByEmailCaseInsensitive() {
    // Pre-create a customer with lowercase email
    runInTenant(
        () -> {
          var existing =
              TestCustomerFactory.createActiveCustomer("Alice Test", "alice@test.com", memberId);
          customerRepository.save(existing);
        });

    // Mock a Xero contact with uppercase email
    when(xeroApiClient.getContacts(anyString(), anyInt(), anyString()))
        .thenReturn(
            Map.of("Contacts", List.of(xeroContact("c5-id", "Alice Test", "ALICE@TEST.COM", null))))
        .thenReturn(Map.of("Contacts", List.of()));

    runInTenant(
        () -> {
          var summary = importService.importCustomersFromXero(connectionId, memberId);

          assertThat(summary.skippedDuplicate()).isEqualTo(1);
          assertThat(summary.created()).isZero();

          // Verify existing customer now has xero_contact_id
          var alice = customerRepository.findByEmail("alice@test.com").orElseThrow();
          assertThat(alice.getCustomFields()).containsEntry("xero_contact_id", "c5-id");
        });
  }

  @Test
  void importDeduplicatesByNameAndTaxNumber() {
    // Pre-create a customer with name+taxNumber
    runInTenant(
        () -> {
          var existing =
              TestCustomerFactory.createActiveCustomer(
                  "Acme Corp", "acme-existing@test.com", memberId);
          existing.setTaxNumber("VAT123");
          customerRepository.save(existing);
        });

    // Mock a Xero contact with same name+taxNumber but different email
    when(xeroApiClient.getContacts(anyString(), anyInt(), anyString()))
        .thenReturn(
            Map.of(
                "Contacts",
                List.of(xeroContact("c6-id", "Acme Corp", "acme-xero@different.com", "VAT123"))))
        .thenReturn(Map.of("Contacts", List.of()));

    runInTenant(
        () -> {
          var summary = importService.importCustomersFromXero(connectionId, memberId);

          assertThat(summary.skippedDuplicate()).isEqualTo(1);
          assertThat(summary.created()).isZero();

          // Verify existing customer now has xero_contact_id
          var acme = customerRepository.findByEmail("acme-existing@test.com").orElseThrow();
          assertThat(acme.getCustomFields()).containsEntry("xero_contact_id", "c6-id");
        });
  }

  @Test
  void secondImportAttemptThrows409() {
    // First import succeeds
    when(xeroApiClient.getContacts(anyString(), anyInt(), anyString()))
        .thenReturn(
            Map.of(
                "Contacts",
                List.of(xeroContact("c7-id", "First Import Corp", "first@example.com", null))))
        .thenReturn(Map.of("Contacts", List.of()));

    runInTenant(() -> importService.importCustomersFromXero(connectionId, memberId));

    // Second attempt should throw
    runInTenant(
        () ->
            assertThatThrownBy(() -> importService.importCustomersFromXero(connectionId, memberId))
                .isInstanceOf(ResourceConflictException.class));
  }

  // -- Helper methods --

  private Map<String, Object> xeroContact(
      String contactId, String name, String email, String taxNumber) {
    var contact = new java.util.HashMap<String, Object>();
    contact.put("ContactID", contactId);
    contact.put("Name", name);
    contact.put("EmailAddress", email);
    if (taxNumber != null) {
      contact.put("TaxNumber", taxNumber);
    }
    return contact;
  }

  private Map<String, Object> xeroContactNoEmail(String contactId, String name) {
    return Map.of("ContactID", contactId, "Name", name);
  }
}
