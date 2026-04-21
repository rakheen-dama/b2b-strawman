package io.b2mash.b2b.b2bstrawman.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
class PortalContactAutoCreationTest {
  private static final String ORG_ID = "org_portal_contact_auto_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;

  private int emailCounter = 0;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Portal Contact Auto Test Org", null);
    TestMemberHelper.syncMemberQuietly(
        mockMvc, ORG_ID, "user_pc_owner", "pc_owner@test.com", "PC Owner", "owner");
  }

  @Test
  void shouldReturnPortalContactsForCustomer() throws Exception {
    // GAP-L-34: portal contact is auto-created at customer-create time; the subsequent
    // PROSPECT -> ONBOARDING transition keeps the same contact (idempotent no-op).
    String customerId =
        TestEntityHelper.createCustomer(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, "user_pc_owner"),
            "Portal Contacts List Corp",
            nextEmail());

    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/transition")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pc_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"targetStatus": "ONBOARDING"}
                    """))
        .andExpect(status().isOk());

    // GET portal contacts should return the auto-created contact
    mockMvc
        .perform(
            get("/api/customers/" + customerId + "/portal-contacts")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pc_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").exists())
        .andExpect(jsonPath("$[0].displayName").value("Portal Contacts List Corp"))
        .andExpect(jsonPath("$[0].email").exists());
  }

  @Test
  void shouldAutoCreatePortalContactOnCustomerCreate() throws Exception {
    // GAP-L-34: contact is created synchronously by PortalContactAutoProvisioner listening
    // on CustomerCreatedEvent — no ONBOARDING transition required.
    String email = nextEmail();
    String customerId =
        TestEntityHelper.createCustomer(
            mockMvc, TestJwtFactory.ownerJwt(ORG_ID, "user_pc_owner"), "Auto Contact Corp", email);

    mockMvc
        .perform(
            get("/api/customers/" + customerId + "/portal-contacts")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pc_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].email").value(email))
        .andExpect(jsonPath("$[0].displayName").value("Auto Contact Corp"));

    // Transitioning PROSPECT -> ONBOARDING must be idempotent — still exactly one contact.
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/transition")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pc_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"targetStatus": "ONBOARDING"}
                    """))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/customers/" + customerId + "/portal-contacts")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pc_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].email").value(email));
  }

  @Test
  void shouldNotDuplicatePortalContactIfAlreadyExists() throws Exception {
    String email = nextEmail();
    String customerId =
        TestEntityHelper.createCustomer(
            mockMvc, TestJwtFactory.ownerJwt(ORG_ID, "user_pc_owner"), "No Dup Corp", email);

    // Confirm one portal contact auto-created on customer create (GAP-L-34).
    var result =
        mockMvc
            .perform(
                get("/api/customers/" + customerId + "/portal-contacts")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pc_owner")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andReturn();

    String firstContactId = JsonPath.read(result.getResponse().getContentAsString(), "$[0].id");
    assertThat(firstContactId).isNotNull();

    // Transition to ONBOARDING must remain idempotent — no duplicate contact.
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/transition")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pc_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"targetStatus": "ONBOARDING"}
                    """))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/customers/" + customerId + "/portal-contacts")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pc_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(firstContactId));
  }

  @Test
  void shouldReturn404ForNonExistentCustomerPortalContacts() throws Exception {
    mockMvc
        .perform(
            get("/api/customers/00000000-0000-0000-0000-000000000099/portal-contacts")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pc_owner")))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldReturnContactListForNewCustomer() throws Exception {
    // GAP-L-34: customers created via POST /api/customers (with email) now have a portal
    // contact auto-provisioned immediately. Previously this test asserted empty list.
    String customerId =
        TestEntityHelper.createCustomer(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, "user_pc_owner"),
            "No Contacts Corp",
            nextEmail());

    mockMvc
        .perform(
            get("/api/customers/" + customerId + "/portal-contacts")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pc_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(1));
  }

  // --- Helpers ---

  private String nextEmail() {
    return "pc_auto_" + (++emailCounter) + "@test.com";
  }
}
