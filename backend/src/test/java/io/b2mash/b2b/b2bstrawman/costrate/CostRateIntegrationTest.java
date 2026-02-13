package io.b2mash.b2b.b2bstrawman.costrate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
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
class CostRateIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_cost_rate_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private CostRateService costRateService;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  private String tenantSchema;
  private UUID memberIdAdmin;
  private UUID memberIdMember;
  private UUID createdRateId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "CostRate Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdAdmin =
        UUID.fromString(
            syncMember(ORG_ID, "user_cost_admin", "cost_admin@test.com", "Admin", "admin"));
    memberIdMember =
        UUID.fromString(
            syncMember(ORG_ID, "user_cost_member", "cost_member@test.com", "Member", "member"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  @Order(1)
  void createCostRate_success() {
    var rate =
        runInTenantAs(
            memberIdAdmin,
            "admin",
            () ->
                costRateService.createCostRate(
                    memberIdMember,
                    "USD",
                    new BigDecimal("85.00"),
                    LocalDate.of(2024, 1, 1),
                    null,
                    memberIdAdmin,
                    "admin"));

    assertThat(rate).isNotNull();
    assertThat(rate.getId()).isNotNull();
    assertThat(rate.getMemberId()).isEqualTo(memberIdMember);
    assertThat(rate.getCurrency()).isEqualTo("USD");
    assertThat(rate.getHourlyCost()).isEqualByComparingTo("85.00");
    assertThat(rate.getEffectiveFrom()).isEqualTo(LocalDate.of(2024, 1, 1));
    assertThat(rate.getEffectiveTo()).isNull();
    createdRateId = rate.getId();
  }

  @Test
  @Order(2)
  void resolveCostRate_returnsRate_whenEffectiveDateMatches() {
    var result =
        runInTenantAs(
            memberIdAdmin,
            "admin",
            () -> costRateService.resolveCostRate(memberIdMember, LocalDate.of(2024, 6, 15)));

    assertThat(result).isPresent();
    assertThat(result.get().hourlyCost()).isEqualByComparingTo("85.00");
    assertThat(result.get().currency()).isEqualTo("USD");
    assertThat(result.get().costRateId()).isEqualTo(createdRateId);
  }

  @Test
  @Order(3)
  void resolveCostRate_returnsEmpty_whenNoRateConfigured() {
    var result =
        runInTenantAs(
            memberIdAdmin,
            "admin",
            () -> costRateService.resolveCostRate(memberIdAdmin, LocalDate.of(2024, 6, 15)));

    assertThat(result).isEmpty();
  }

  @Test
  @Order(4)
  void resolveCostRate_respectsDateRange() {
    // Create a bounded cost rate for admin member
    runInTenantAs(
        memberIdAdmin,
        "admin",
        () ->
            costRateService.createCostRate(
                memberIdAdmin,
                "EUR",
                new BigDecimal("50.00"),
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 6, 30),
                memberIdAdmin,
                "admin"));

    runInTenantAs(
        memberIdAdmin,
        "admin",
        () ->
            costRateService.createCostRate(
                memberIdAdmin,
                "EUR",
                new BigDecimal("60.00"),
                LocalDate.of(2024, 7, 1),
                LocalDate.of(2024, 12, 31),
                memberIdAdmin,
                "admin"));

    // Date in first range
    var result1 =
        runInTenantAs(
            memberIdAdmin,
            "admin",
            () -> costRateService.resolveCostRate(memberIdAdmin, LocalDate.of(2024, 3, 15)));
    assertThat(result1).isPresent();
    assertThat(result1.get().hourlyCost()).isEqualByComparingTo("50.00");

    // Date in second range
    var result2 =
        runInTenantAs(
            memberIdAdmin,
            "admin",
            () -> costRateService.resolveCostRate(memberIdAdmin, LocalDate.of(2024, 9, 15)));
    assertThat(result2).isPresent();
    assertThat(result2.get().hourlyCost()).isEqualByComparingTo("60.00");

    // Date after all ranges
    var result3 =
        runInTenantAs(
            memberIdAdmin,
            "admin",
            () -> costRateService.resolveCostRate(memberIdAdmin, LocalDate.of(2025, 6, 15)));
    assertThat(result3).isEmpty();
  }

  @Test
  @Order(5)
  void createCostRate_rejectsOverlap() {
    // The member default for memberIdMember created in test 1 starts 2024-01-01 with no end date.
    // Creating another overlapping rate should fail.
    assertThatThrownBy(
            () ->
                runInTenantAs(
                    memberIdAdmin,
                    "admin",
                    () ->
                        costRateService.createCostRate(
                            memberIdMember,
                            "EUR",
                            new BigDecimal("90.00"),
                            LocalDate.of(2024, 6, 1),
                            LocalDate.of(2024, 12, 31),
                            memberIdAdmin,
                            "admin")))
        .isInstanceOf(ResourceConflictException.class);
  }

  @Test
  @Order(6)
  void updateCostRate_success() {
    var updated =
        runInTenantAs(
            memberIdAdmin,
            "admin",
            () ->
                costRateService.updateCostRate(
                    createdRateId,
                    new BigDecimal("95.00"),
                    "CAD",
                    LocalDate.of(2024, 1, 1),
                    LocalDate.of(2025, 12, 31),
                    memberIdAdmin,
                    "admin"));

    assertThat(updated.getHourlyCost()).isEqualByComparingTo("95.00");
    assertThat(updated.getCurrency()).isEqualTo("CAD");
    assertThat(updated.getEffectiveTo()).isEqualTo(LocalDate.of(2025, 12, 31));
  }

  @Test
  @Order(7)
  void deleteCostRate_success() {
    // Create a rate to delete
    var rate =
        runInTenantAs(
            memberIdAdmin,
            "admin",
            () ->
                costRateService.createCostRate(
                    memberIdMember,
                    "GBP",
                    new BigDecimal("70.00"),
                    LocalDate.of(2030, 1, 1),
                    LocalDate.of(2030, 6, 30),
                    memberIdAdmin,
                    "admin"));

    UUID rateId = rate.getId();

    // Delete it
    runInTenantAs(
        memberIdAdmin,
        "admin",
        () -> {
          costRateService.deleteCostRate(rateId, memberIdAdmin, "admin");
          return null;
        });

    // Verify it's gone
    assertThatThrownBy(
            () ->
                runInTenantAs(
                    memberIdAdmin,
                    "admin",
                    () ->
                        costRateService.updateCostRate(
                            rateId,
                            new BigDecimal("80.00"),
                            "GBP",
                            LocalDate.of(2030, 1, 1),
                            null,
                            memberIdAdmin,
                            "admin")))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  @Order(8)
  void createCostRate_nonAdminRejected() {
    assertThatThrownBy(
            () ->
                runInTenantAs(
                    memberIdMember,
                    "member",
                    () ->
                        costRateService.createCostRate(
                            memberIdMember,
                            "USD",
                            new BigDecimal("100.00"),
                            LocalDate.of(2026, 1, 1),
                            null,
                            memberIdMember,
                            "member")))
        .isInstanceOf(ForbiddenException.class);
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
