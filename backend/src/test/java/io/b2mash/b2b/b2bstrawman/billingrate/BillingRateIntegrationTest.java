package io.b2mash.b2b.b2bstrawman.billingrate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectService;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BillingRateIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_billing_rate_crud_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private BillingRateService billingRateService;
  @Autowired private CustomerService customerService;
  @Autowired private ProjectService projectService;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID memberIdAdmin;
  private UUID memberIdMember;
  private UUID projectId;
  private UUID customerId;
  private UUID createdRateId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "BillingRate CRUD Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_rate_crud_owner", "rate_crud_owner@test.com", "Owner", "owner"));
    memberIdAdmin =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_rate_crud_admin", "rate_crud_admin@test.com", "Admin", "admin"));
    memberIdMember =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_rate_crud_member", "rate_crud_member@test.com", "Member", "member"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Create customer and project within tenant scope
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var customer =
                  customerService.createCustomer(
                      "CRUD Test Customer",
                      "crud-customer@test.com",
                      null,
                      null,
                      null,
                      memberIdOwner);
              customerId = customer.getId();

              var project =
                  projectService.createProject("CRUD Rate Test Project", "Test", memberIdOwner);
              projectId = project.getId();
            });
  }

  @Test
  @Order(1)
  void createRate_memberDefault() {
    var rate =
        runInTenantAs(
            memberIdAdmin,
            "admin",
            () ->
                billingRateService.createRate(
                    memberIdOwner,
                    null,
                    null,
                    "USD",
                    new BigDecimal("125.00"),
                    LocalDate.of(2024, 1, 1),
                    null,
                    memberIdAdmin,
                    "admin"));

    assertThat(rate).isNotNull();
    assertThat(rate.getId()).isNotNull();
    assertThat(rate.getMemberId()).isEqualTo(memberIdOwner);
    assertThat(rate.getProjectId()).isNull();
    assertThat(rate.getCustomerId()).isNull();
    assertThat(rate.getCurrency()).isEqualTo("USD");
    assertThat(rate.getHourlyRate()).isEqualByComparingTo("125.00");
    assertThat(rate.getScope()).isEqualTo("MEMBER_DEFAULT");
    createdRateId = rate.getId();
  }

  @Test
  @Order(2)
  void createRate_projectOverride() {
    var rate =
        runInTenantAs(
            memberIdOwner,
            "owner",
            () ->
                billingRateService.createRate(
                    memberIdOwner,
                    projectId,
                    null,
                    "EUR",
                    new BigDecimal("175.00"),
                    LocalDate.of(2024, 1, 1),
                    LocalDate.of(2024, 12, 31),
                    memberIdOwner,
                    "owner"));

    assertThat(rate.getProjectId()).isEqualTo(projectId);
    assertThat(rate.getCustomerId()).isNull();
    assertThat(rate.getScope()).isEqualTo("PROJECT_OVERRIDE");
    assertThat(rate.getCurrency()).isEqualTo("EUR");
  }

  @Test
  @Order(3)
  void createRate_customerOverride() {
    var rate =
        runInTenantAs(
            memberIdAdmin,
            "admin",
            () ->
                billingRateService.createRate(
                    memberIdOwner,
                    null,
                    customerId,
                    "GBP",
                    new BigDecimal("160.00"),
                    LocalDate.of(2024, 1, 1),
                    null,
                    memberIdAdmin,
                    "admin"));

    assertThat(rate.getProjectId()).isNull();
    assertThat(rate.getCustomerId()).isEqualTo(customerId);
    assertThat(rate.getScope()).isEqualTo("CUSTOMER_OVERRIDE");
    assertThat(rate.getCurrency()).isEqualTo("GBP");
  }

  @Test
  @Order(4)
  void createRate_rejectsCompoundScope() {
    assertThatThrownBy(
            () ->
                runInTenantAs(
                    memberIdOwner,
                    "owner",
                    () ->
                        billingRateService.createRate(
                            memberIdOwner,
                            projectId,
                            customerId,
                            "USD",
                            new BigDecimal("100.00"),
                            LocalDate.of(2025, 1, 1),
                            null,
                            memberIdOwner,
                            "owner")))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  @Order(5)
  void updateRate_success() {
    var updated =
        runInTenantAs(
            memberIdAdmin,
            "admin",
            () ->
                billingRateService.updateRate(
                    createdRateId,
                    new BigDecimal("140.00"),
                    "CAD",
                    LocalDate.of(2024, 1, 1),
                    LocalDate.of(2025, 12, 31),
                    memberIdAdmin,
                    "admin"));

    assertThat(updated.getHourlyRate()).isEqualByComparingTo("140.00");
    assertThat(updated.getCurrency()).isEqualTo("CAD");
    assertThat(updated.getEffectiveTo()).isEqualTo(LocalDate.of(2025, 12, 31));
  }

  @Test
  @Order(6)
  void deleteRate_success() {
    // Create a rate to delete
    var rate =
        runInTenantAs(
            memberIdOwner,
            "owner",
            () ->
                billingRateService.createRate(
                    memberIdMember,
                    null,
                    null,
                    "USD",
                    new BigDecimal("80.00"),
                    LocalDate.of(2024, 1, 1),
                    LocalDate.of(2024, 6, 30),
                    memberIdOwner,
                    "owner"));

    UUID rateId = rate.getId();

    // Delete it
    runInTenantAs(
        memberIdOwner,
        "owner",
        () -> {
          billingRateService.deleteRate(rateId, memberIdOwner, "owner");
          return null;
        });

    // Verify it's gone
    assertThatThrownBy(
            () ->
                runInTenantAs(
                    memberIdOwner,
                    "owner",
                    () ->
                        billingRateService.updateRate(
                            rateId,
                            new BigDecimal("90.00"),
                            "USD",
                            LocalDate.of(2024, 1, 1),
                            null,
                            memberIdOwner,
                            "owner")))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  @Order(7)
  void createRate_adminCanCreate() {
    var rate =
        runInTenantAs(
            memberIdAdmin,
            "admin",
            () ->
                billingRateService.createRate(
                    memberIdMember,
                    null,
                    null,
                    "USD",
                    new BigDecimal("90.00"),
                    LocalDate.of(2025, 1, 1),
                    null,
                    memberIdAdmin,
                    "admin"));

    assertThat(rate).isNotNull();
    assertThat(rate.getMemberId()).isEqualTo(memberIdMember);
  }

  @Test
  @Order(8)
  void createRate_memberGetsForbidden() {
    assertThatThrownBy(
            () ->
                runInTenantAs(
                    memberIdMember,
                    "member",
                    () ->
                        billingRateService.createRate(
                            memberIdMember,
                            null,
                            null,
                            "USD",
                            new BigDecimal("100.00"),
                            LocalDate.of(2026, 1, 1),
                            null,
                            memberIdMember,
                            "member")))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  @Order(9)
  void createRate_overlapRejected() {
    // The member default for memberIdMember created in test 7 starts 2025-01-01 with no end date.
    // Creating another member default overlapping that range should fail.
    assertThatThrownBy(
            () ->
                runInTenantAs(
                    memberIdAdmin,
                    "admin",
                    () ->
                        billingRateService.createRate(
                            memberIdMember,
                            null,
                            null,
                            "EUR",
                            new BigDecimal("110.00"),
                            LocalDate.of(2025, 6, 1),
                            LocalDate.of(2025, 12, 31),
                            memberIdAdmin,
                            "admin")))
        .isInstanceOf(ResourceConflictException.class);
  }

  // --- Helpers ---

  private <T> T runInTenantAs(
      UUID actorId, String role, java.util.concurrent.Callable<T> callable) {
    try {
      return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
          .where(RequestScopes.MEMBER_ID, actorId)
          .where(RequestScopes.ORG_ROLE, role)
          .call(() -> callable.call());
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private String syncMember(
      String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clerkOrgId": "%s",
                          "clerkUserId": "%s",
                          "email": "%s",
                          "name": "%s",
                          "avatarUrl": null,
                          "orgRole": "%s"
                        }
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }
}
