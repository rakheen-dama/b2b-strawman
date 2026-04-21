package io.b2mash.b2b.b2bstrawman.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.CustomerCreatedEvent;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GAP-L-34 — verifies that creating a customer (PROSPECT, no lifecycle transition) auto-creates a
 * PortalContact so downstream firm-side portal flows (information requests, proposals, fee notes)
 * are unblocked. Complements the pre-existing PROSPECT → ONBOARDING auto-creation covered by {@link
 * PortalContactAutoCreationTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalContactAutoProvisionerTest {

  private static final String ORG_ID = "org_portal_autoprov_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private PortalContactRepository portalContactRepository;

  private int emailCounter = 0;
  private String tenantId;

  @BeforeAll
  void setup() throws Exception {
    tenantId =
        provisioningService
            .provisionTenant(ORG_ID, "Portal Auto-Provision Test Org", null)
            .schemaName();
    TestMemberHelper.syncMemberQuietly(
        mockMvc, ORG_ID, "user_pap_owner", "pap_owner@test.com", "PAP Owner", "owner");
  }

  @Test
  void shouldAutoCreatePortalContactOnCustomerCreate() throws Exception {
    String email = nextEmail();
    String customerId =
        TestEntityHelper.createCustomer(
            mockMvc, TestJwtFactory.ownerJwt(ORG_ID, "user_pap_owner"), "Sipho Dlamini", email);

    // GET portal contacts — listener should have produced exactly one row matching email + name
    mockMvc
        .perform(
            get("/api/customers/" + customerId + "/portal-contacts")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pap_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].email").value(email))
        .andExpect(jsonPath("$[0].displayName").value("Sipho Dlamini"));
  }

  @Test
  void shouldBeIdempotentWhenCustomerCreatedEventReplayed() throws Exception {
    String email = nextEmail();
    String customerId =
        TestEntityHelper.createCustomer(
            mockMvc, TestJwtFactory.ownerJwt(ORG_ID, "user_pap_owner"), "Replay Client", email);

    // Re-publish the same event — listener must swallow the ResourceConflictException.
    ScopedValue.where(RequestScopes.TENANT_ID, tenantId)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                eventPublisher.publishEvent(
                    new CustomerCreatedEvent(
                        UUID.fromString(customerId), "Replay Client", email, ORG_ID, tenantId)));

    // Still exactly one portal contact for this customer.
    mockMvc
        .perform(
            get("/api/customers/" + customerId + "/portal-contacts")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pap_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void shouldSkipWhenEventHasNoEmail() {
    UUID fakeCustomerId = UUID.randomUUID();
    int[] counts = new int[3];

    // Baseline: nothing for a random customer id.
    ScopedValue.where(RequestScopes.TENANT_ID, tenantId)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(() -> counts[0] = portalContactRepository.findByCustomerId(fakeCustomerId).size());

    // Publishing with null email is a no-op — listener short-circuits before touching the service.
    ScopedValue.where(RequestScopes.TENANT_ID, tenantId)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              eventPublisher.publishEvent(
                  new CustomerCreatedEvent(
                      fakeCustomerId, "No Email Corp", null, ORG_ID, tenantId));
              counts[1] = portalContactRepository.findByCustomerId(fakeCustomerId).size();
            });

    // Same for blank email.
    ScopedValue.where(RequestScopes.TENANT_ID, tenantId)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              eventPublisher.publishEvent(
                  new CustomerCreatedEvent(
                      fakeCustomerId, "Blank Email Corp", "   ", ORG_ID, tenantId));
              counts[2] = portalContactRepository.findByCustomerId(fakeCustomerId).size();
            });

    assertThat(counts[0]).isZero();
    assertThat(counts[1]).isZero();
    assertThat(counts[2]).isZero();
  }

  // --- Helpers ---

  private String nextEmail() {
    return "pap_auto_" + (++emailCounter) + "@test.com";
  }
}
