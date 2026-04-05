package io.b2mash.b2b.b2bstrawman.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestChecklistHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests verifying audit events produced by {@code CustomerService} and {@code
 * CustomerProjectService} operations.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomerServiceAuditTest {
  private static final String ORG_ID = "org_cust_audit_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private AuditService auditService;
  @Autowired private TenantProvisioningService provisioningService;

  private String schemaName;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Customer Audit Test Org", null);
    schemaName =
        provisioningService.provisionTenant(ORG_ID, "Customer Audit Test Org", null).schemaName();

    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_ca_owner", "ca_owner@test.com", "CA Owner", "owner");
  }

  @Test
  void createCustomerProducesAuditEvent() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ca_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Audit Cust", "email": "audit-cust@test.com", "phone": "555-0100", "idNumber": "ID001", "notes": "test"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var customerId = UUID.fromString(TestEntityHelper.extractIdFromLocation(result));

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "customer", customerId, null, "customer.created", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("customer.created");
              assertThat(event.getEntityType()).isEqualTo("customer");
              assertThat(event.getEntityId()).isEqualTo(customerId);
              assertThat(event.getDetails()).containsEntry("name", "Audit Cust");
              assertThat(event.getDetails()).containsEntry("email", "audit-cust@test.com");
            });
  }

  @Test
  void updateCustomerCapturesFieldDeltas() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ca_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Old Name", "email": "old-email@test.com", "phone": "111", "idNumber": "OLD-ID", "notes": "old notes"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var customerId = UUID.fromString(TestEntityHelper.extractIdFromLocation(createResult));

    mockMvc
        .perform(
            put("/api/customers/" + customerId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ca_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "New Name", "email": "new-email@test.com", "phone": "222", "idNumber": "NEW-ID", "notes": "new notes"}
                    """))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "customer", customerId, null, "customer.updated", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("customer.updated");

              @SuppressWarnings("unchecked")
              var nameChange = (Map<String, Object>) event.getDetails().get("name");
              assertThat(nameChange).containsEntry("from", "Old Name");
              assertThat(nameChange).containsEntry("to", "New Name");

              @SuppressWarnings("unchecked")
              var emailChange = (Map<String, Object>) event.getDetails().get("email");
              assertThat(emailChange).containsEntry("from", "old-email@test.com");
              assertThat(emailChange).containsEntry("to", "new-email@test.com");
            });
  }

  @Test
  void archiveCustomerProducesAuditEvent() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ca_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Archive Me", "email": "archive@test.com", "phone": null, "idNumber": null, "notes": null}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var customerId = UUID.fromString(TestEntityHelper.extractIdFromLocation(createResult));

    // Archive the customer (DELETE endpoint archives, doesn't truly delete)
    mockMvc
        .perform(
            delete("/api/customers/" + customerId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ca_owner")))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "customer", customerId, null, "customer.archived", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("customer.archived");
              assertThat(event.getEntityId()).isEqualTo(customerId);
            });
  }

  @Test
  void linkCustomerToProjectProducesAuditEvent() throws Exception {
    // Create a project first
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ca_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Link Test Project", "description": null}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var projectId =
        UUID.fromString(
            projectResult
                .getResponse()
                .getHeader("Location")
                .substring(projectResult.getResponse().getHeader("Location").lastIndexOf('/') + 1));

    // Create a customer
    var customerResult =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ca_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Link Cust", "email": "link-cust@test.com", "phone": null, "idNumber": null, "notes": null}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var customerId = UUID.fromString(TestEntityHelper.extractIdFromLocation(customerResult));

    // Transition customer to ACTIVE so linking is permitted by lifecycle guard
    transitionToActive(customerId);

    // Link customer to project
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/projects/" + projectId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ca_owner")))
        .andExpect(status().isCreated());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "customer", customerId, null, "customer.linked", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("customer.linked");
              assertThat(event.getEntityId()).isEqualTo(customerId);
              assertThat(event.getDetails()).containsEntry("project_id", projectId.toString());
            });
  }

  @Test
  void unlinkCustomerFromProjectProducesAuditEvent() throws Exception {
    // Create a project
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ca_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Unlink Test Project", "description": null}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var projectId =
        UUID.fromString(
            projectResult
                .getResponse()
                .getHeader("Location")
                .substring(projectResult.getResponse().getHeader("Location").lastIndexOf('/') + 1));

    // Create a customer
    var customerResult =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ca_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Unlink Cust", "email": "unlink-cust@test.com", "phone": null, "idNumber": null, "notes": null}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var customerId = UUID.fromString(TestEntityHelper.extractIdFromLocation(customerResult));

    // Transition customer to ACTIVE so linking is permitted by lifecycle guard
    transitionToActive(customerId);

    // Link then unlink
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/projects/" + projectId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ca_owner")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            delete("/api/customers/" + customerId + "/projects/" + projectId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ca_owner")))
        .andExpect(status().isNoContent());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "customer", customerId, null, "customer.unlinked", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("customer.unlinked");
              assertThat(event.getEntityId()).isEqualTo(customerId);
              assertThat(event.getDetails()).containsEntry("project_id", projectId.toString());
            });
  }

  // --- Helpers ---

  private void transitionToActive(UUID customerId) throws Exception {
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/transition")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ca_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStatus\": \"ONBOARDING\"}"))
        .andExpect(status().isOk());
    // Completing all checklist items auto-transitions ONBOARDING -> ACTIVE
    TestChecklistHelper.completeChecklistItems(
        mockMvc, customerId.toString(), TestJwtFactory.ownerJwt(ORG_ID, "user_ca_owner"));
  }
}
