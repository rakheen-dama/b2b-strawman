package io.b2mash.b2b.b2bstrawman.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalContactIntegrationTest {

  private static final String ORG_ID_A = "org_contact_test_a";
  private static final String ORG_ID_B = "org_contact_test_b";
  private static final String API_KEY = "test-api-key";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private CustomerService customerService;
  @Autowired private PortalContactService portalContactService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  private String tenantSchemaA;
  private String tenantSchemaB;
  private UUID memberIdA;
  private UUID memberIdB;
  private UUID customerIdA;
  private UUID customerIdA2;
  private UUID customerIdB;

  @BeforeAll
  void setup() throws Exception {
    // Provision tenant A
    provisioningService.provisionTenant(ORG_ID_A, "Contact Test Org A");
    planSyncService.syncPlan(ORG_ID_A, "pro-plan");

    // Provision tenant B
    provisioningService.provisionTenant(ORG_ID_B, "Contact Test Org B");
    planSyncService.syncPlan(ORG_ID_B, "pro-plan");

    // Sync member for org A
    var syncResultA =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clerkOrgId": "%s",
                          "clerkUserId": "user_contact_owner_a",
                          "email": "contact_owner_a@test.com",
                          "name": "Contact Owner A",
                          "avatarUrl": null,
                          "orgRole": "owner"
                        }
                        """
                            .formatted(ORG_ID_A)))
            .andExpect(status().isCreated())
            .andReturn();
    memberIdA =
        UUID.fromString(
            JsonPath.read(syncResultA.getResponse().getContentAsString(), "$.memberId"));

    // Sync member for org B
    var syncResultB =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clerkOrgId": "%s",
                          "clerkUserId": "user_contact_owner_b",
                          "email": "contact_owner_b@test.com",
                          "name": "Contact Owner B",
                          "avatarUrl": null,
                          "orgRole": "owner"
                        }
                        """
                            .formatted(ORG_ID_B)))
            .andExpect(status().isCreated())
            .andReturn();
    memberIdB =
        UUID.fromString(
            JsonPath.read(syncResultB.getResponse().getContentAsString(), "$.memberId"));

    // Resolve tenant schemas
    tenantSchemaA = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID_A).get().getSchemaName();
    tenantSchemaB = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID_B).get().getSchemaName();

    // Create customers in tenant A
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchemaA)
        .where(RequestScopes.ORG_ID, ORG_ID_A)
        .run(
            () -> {
              var customer =
                  customerService.createCustomer(
                      "Customer A1", "customer-a1@test.com", null, null, null, null, memberIdA);
              customerIdA = customer.getId();

              var customer2 =
                  customerService.createCustomer(
                      "Customer A2", "customer-a2@test.com", null, null, null, null, memberIdA);
              customerIdA2 = customer2.getId();
            });

    // Create customer in tenant B
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchemaB)
        .where(RequestScopes.ORG_ID, ORG_ID_B)
        .run(
            () -> {
              var customer =
                  customerService.createCustomer(
                      "Customer B1", "customer-b1@test.com", null, null, null, null, memberIdB);
              customerIdB = customer.getId();
            });
  }

  @Test
  void shouldCreateContactSuccessfully() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchemaA)
        .where(RequestScopes.ORG_ID, ORG_ID_A)
        .run(
            () -> {
              var contact =
                  portalContactService.createContact(
                      ORG_ID_A,
                      customerIdA,
                      "contact-create@test.com",
                      "Create Test",
                      PortalContact.ContactRole.GENERAL);

              assertThat(contact.getId()).isNotNull();
              assertThat(contact.getOrgId()).isEqualTo(ORG_ID_A);
              assertThat(contact.getCustomerId()).isEqualTo(customerIdA);
              assertThat(contact.getEmail()).isEqualTo("contact-create@test.com");
              assertThat(contact.getDisplayName()).isEqualTo("Create Test");
              assertThat(contact.getRole()).isEqualTo(PortalContact.ContactRole.GENERAL);
              assertThat(contact.getStatus()).isEqualTo(PortalContact.ContactStatus.ACTIVE);
              assertThat(contact.getCreatedAt()).isNotNull();
              assertThat(contact.getUpdatedAt()).isNotNull();
            });
  }

  @Test
  void shouldRejectDuplicateEmailForSameCustomer() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchemaA)
        .where(RequestScopes.ORG_ID, ORG_ID_A)
        .run(
            () -> {
              portalContactService.createContact(
                  ORG_ID_A,
                  customerIdA,
                  "duplicate@test.com",
                  "First Contact",
                  PortalContact.ContactRole.GENERAL);

              assertThatThrownBy(
                      () ->
                          portalContactService.createContact(
                              ORG_ID_A,
                              customerIdA,
                              "duplicate@test.com",
                              "Second Contact",
                              PortalContact.ContactRole.BILLING))
                  .isInstanceOf(ResourceConflictException.class);
            });
  }

  @Test
  void shouldAllowSameEmailOnDifferentCustomers() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchemaA)
        .where(RequestScopes.ORG_ID, ORG_ID_A)
        .run(
            () -> {
              var contact1 =
                  portalContactService.createContact(
                      ORG_ID_A,
                      customerIdA,
                      "shared-email@test.com",
                      "Contact on A1",
                      PortalContact.ContactRole.PRIMARY);

              var contact2 =
                  portalContactService.createContact(
                      ORG_ID_A,
                      customerIdA2,
                      "shared-email@test.com",
                      "Contact on A2",
                      PortalContact.ContactRole.PRIMARY);

              assertThat(contact1.getId()).isNotEqualTo(contact2.getId());
              assertThat(contact1.getCustomerId()).isNotEqualTo(contact2.getCustomerId());
            });
  }

  @Test
  void shouldFindByEmailAndOrg() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchemaA)
        .where(RequestScopes.ORG_ID, ORG_ID_A)
        .run(
            () -> {
              portalContactService.createContact(
                  ORG_ID_A,
                  customerIdA,
                  "findable@test.com",
                  "Findable Contact",
                  PortalContact.ContactRole.GENERAL);

              var found = portalContactService.findByEmailAndOrg("findable@test.com", ORG_ID_A);
              assertThat(found).isPresent();
              assertThat(found.get().getEmail()).isEqualTo("findable@test.com");
              assertThat(found.get().getOrgId()).isEqualTo(ORG_ID_A);
            });
  }

  @Test
  void shouldListContactsForCustomer() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchemaA)
        .where(RequestScopes.ORG_ID, ORG_ID_A)
        .run(
            () -> {
              portalContactService.createContact(
                  ORG_ID_A,
                  customerIdA,
                  "list-test-1@test.com",
                  "List Test 1",
                  PortalContact.ContactRole.GENERAL);

              portalContactService.createContact(
                  ORG_ID_A,
                  customerIdA,
                  "list-test-2@test.com",
                  "List Test 2",
                  PortalContact.ContactRole.BILLING);

              var contacts = portalContactService.listContactsForCustomer(customerIdA);
              // At least the 2 we just created (may be more from other tests)
              assertThat(contacts).hasSizeGreaterThanOrEqualTo(2);
              assertThat(contacts)
                  .extracting(PortalContact::getCustomerId)
                  .containsOnly(customerIdA);
            });
  }

  @Test
  void shouldSuspendContact() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchemaA)
        .where(RequestScopes.ORG_ID, ORG_ID_A)
        .run(
            () -> {
              var contact =
                  portalContactService.createContact(
                      ORG_ID_A,
                      customerIdA,
                      "suspend-me@test.com",
                      "Suspend Test",
                      PortalContact.ContactRole.GENERAL);

              assertThat(contact.getStatus()).isEqualTo(PortalContact.ContactStatus.ACTIVE);

              var suspended = portalContactService.suspendContact(contact.getId());
              assertThat(suspended.getStatus()).isEqualTo(PortalContact.ContactStatus.SUSPENDED);
            });
  }

  @Test
  void shouldArchiveContact() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchemaA)
        .where(RequestScopes.ORG_ID, ORG_ID_A)
        .run(
            () -> {
              var contact =
                  portalContactService.createContact(
                      ORG_ID_A,
                      customerIdA,
                      "archive-me@test.com",
                      "Archive Test",
                      PortalContact.ContactRole.GENERAL);

              var archived = portalContactService.archiveContact(contact.getId());
              assertThat(archived.getStatus()).isEqualTo(PortalContact.ContactStatus.ARCHIVED);
            });
  }

  @Test
  void shouldIsolateTenants() {
    // Create a contact in tenant A
    final UUID[] contactIdHolder = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchemaA)
        .where(RequestScopes.ORG_ID, ORG_ID_A)
        .run(
            () -> {
              var contact =
                  portalContactService.createContact(
                      ORG_ID_A,
                      customerIdA,
                      "isolation@test.com",
                      "Isolation Test",
                      PortalContact.ContactRole.GENERAL);
              contactIdHolder[0] = contact.getId();
            });

    // Try to find that contact in tenant B -- should not be visible
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchemaB)
        .where(RequestScopes.ORG_ID, ORG_ID_B)
        .run(
            () -> {
              var notFound = portalContactService.findByEmailAndOrg("isolation@test.com", ORG_ID_A);
              assertThat(notFound).isEmpty();
            });
  }

  @Test
  void shouldRejectContactForNonExistentCustomer() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchemaA)
        .where(RequestScopes.ORG_ID, ORG_ID_A)
        .run(
            () -> {
              UUID fakeCustomerId = UUID.randomUUID();
              assertThatThrownBy(
                      () ->
                          portalContactService.createContact(
                              ORG_ID_A,
                              fakeCustomerId,
                              "no-customer@test.com",
                              "No Customer",
                              PortalContact.ContactRole.GENERAL))
                  .isInstanceOf(ResourceNotFoundException.class);
            });
  }

  @Test
  void shouldPopulateOrgIdCorrectly() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchemaA)
        .where(RequestScopes.ORG_ID, ORG_ID_A)
        .run(
            () -> {
              var contact =
                  portalContactService.createContact(
                      ORG_ID_A,
                      customerIdA,
                      "org-id-check@test.com",
                      "Org Check",
                      PortalContact.ContactRole.PRIMARY);

              assertThat(contact.getOrgId()).isEqualTo(ORG_ID_A);
            });
  }
}
