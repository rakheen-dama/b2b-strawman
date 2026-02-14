package io.b2mash.b2b.b2bstrawman.billingrate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
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
class BillingRateResolutionTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_billing_rate_res_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private BillingRateService billingRateService;
  @Autowired private BillingRateRepository billingRateRepository;
  @Autowired private CustomerService customerService;
  @Autowired private CustomerProjectService customerProjectService;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectService projectService;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID memberIdMember;
  private UUID projectId;
  private UUID customerId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "BillingRate Resolution Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember(ORG_ID, "user_rate_res_owner", "rate_res_owner@test.com", "Owner", "owner"));
    memberIdMember =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_rate_res_member", "rate_res_member@test.com", "Member", "member"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Create project, customer, and link within tenant scope
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var customer =
                  customerService.createCustomer(
                      "Rate Test Customer",
                      "rate-customer@test.com",
                      null,
                      null,
                      null,
                      memberIdOwner);
              customerId = customer.getId();

              var project =
                  projectService.createProject(
                      "Rate Resolution Test Project", "Test", memberIdOwner);
              projectId = project.getId();

              customerProjectService.linkCustomerToProject(
                  customerId, projectId, memberIdOwner, memberIdOwner, "owner");
            });
  }

  @Test
  @Order(1)
  void resolveRate_returnsEmpty_whenNoRateConfigured() {
    runInTenant(
        () -> {
          var result = billingRateService.resolveRate(memberIdOwner, projectId, LocalDate.now());
          assertThat(result).isEmpty();
          return null;
        });
  }

  @Test
  @Order(2)
  void resolveRate_returnsMemberDefault_whenOnlyDefaultExists() {
    runInTenant(
        () -> {
          billingRateService.createRate(
              memberIdOwner,
              null,
              null,
              "USD",
              new BigDecimal("100.00"),
              LocalDate.of(2024, 1, 1),
              null,
              memberIdOwner,
              "owner");

          var result =
              billingRateService.resolveRate(memberIdOwner, projectId, LocalDate.of(2024, 6, 15));

          assertThat(result).isPresent();
          assertThat(result.get().source()).isEqualTo("MEMBER_DEFAULT");
          assertThat(result.get().hourlyRate()).isEqualByComparingTo("100.00");
          assertThat(result.get().currency()).isEqualTo("USD");
          return null;
        });
  }

  @Test
  @Order(3)
  void resolveRate_returnsCustomerOverride_whenCustomerRateExists() {
    runInTenant(
        () -> {
          // Customer override should take precedence over member default
          billingRateService.createRate(
              memberIdOwner,
              null,
              customerId,
              "EUR",
              new BigDecimal("150.00"),
              LocalDate.of(2024, 1, 1),
              null,
              memberIdOwner,
              "owner");

          var result =
              billingRateService.resolveRate(memberIdOwner, projectId, LocalDate.of(2024, 6, 15));

          assertThat(result).isPresent();
          assertThat(result.get().source()).isEqualTo("CUSTOMER_OVERRIDE");
          assertThat(result.get().hourlyRate()).isEqualByComparingTo("150.00");
          assertThat(result.get().currency()).isEqualTo("EUR");
          return null;
        });
  }

  @Test
  @Order(4)
  void resolveRate_returnsProjectOverride_whenProjectRateExists() {
    runInTenant(
        () -> {
          // Project override should take highest precedence
          billingRateService.createRate(
              memberIdOwner,
              projectId,
              null,
              "GBP",
              new BigDecimal("200.00"),
              LocalDate.of(2024, 1, 1),
              null,
              memberIdOwner,
              "owner");

          var result =
              billingRateService.resolveRate(memberIdOwner, projectId, LocalDate.of(2024, 6, 15));

          assertThat(result).isPresent();
          assertThat(result.get().source()).isEqualTo("PROJECT_OVERRIDE");
          assertThat(result.get().hourlyRate()).isEqualByComparingTo("200.00");
          assertThat(result.get().currency()).isEqualTo("GBP");
          return null;
        });
  }

  @Test
  @Order(5)
  void resolveRate_projectOverrideTrumpsCustomerOverride() {
    // At this point we have member default, customer override, and project override.
    // Project override must win.
    runInTenant(
        () -> {
          var result =
              billingRateService.resolveRate(memberIdOwner, projectId, LocalDate.of(2024, 6, 15));

          assertThat(result).isPresent();
          assertThat(result.get().source()).isEqualTo("PROJECT_OVERRIDE");
          assertThat(result.get().hourlyRate()).isEqualByComparingTo("200.00");
          return null;
        });
  }

  @Test
  @Order(6)
  void resolveRate_futureRateNotResolved() {
    runInTenant(
        () -> {
          // Create a rate that starts in the future for a different member
          billingRateService.createRate(
              memberIdMember,
              null,
              null,
              "USD",
              new BigDecimal("300.00"),
              LocalDate.of(2030, 1, 1),
              null,
              memberIdOwner,
              "owner");

          var result =
              billingRateService.resolveRate(memberIdMember, projectId, LocalDate.of(2024, 6, 15));

          assertThat(result).isEmpty();
          return null;
        });
  }

  @Test
  @Order(7)
  void resolveRate_picksCorrectDateRange() {
    runInTenant(
        () -> {
          // Create two non-overlapping date ranges for a member default
          billingRateService.createRate(
              memberIdMember,
              null,
              null,
              "USD",
              new BigDecimal("50.00"),
              LocalDate.of(2024, 1, 1),
              LocalDate.of(2024, 6, 30),
              memberIdOwner,
              "owner");

          billingRateService.createRate(
              memberIdMember,
              null,
              null,
              "USD",
              new BigDecimal("75.00"),
              LocalDate.of(2024, 7, 1),
              LocalDate.of(2024, 12, 31),
              memberIdOwner,
              "owner");

          // Query date in first range
          var result1 =
              billingRateService.resolveRate(memberIdMember, projectId, LocalDate.of(2024, 3, 15));
          assertThat(result1).isPresent();
          assertThat(result1.get().hourlyRate()).isEqualByComparingTo("50.00");

          // Query date in second range
          var result2 =
              billingRateService.resolveRate(memberIdMember, projectId, LocalDate.of(2024, 9, 15));
          assertThat(result2).isPresent();
          assertThat(result2.get().hourlyRate()).isEqualByComparingTo("75.00");

          return null;
        });
  }

  @Test
  @Order(8)
  void resolveRate_customerResolutionViaCustomerProjectJoin() {
    // Customer override is resolved via CustomerProject lookup -- verify the join works
    runInTenant(
        () -> {
          var firstCustomerId = customerProjectRepository.findFirstCustomerByProjectId(projectId);
          assertThat(firstCustomerId).isPresent();
          assertThat(firstCustomerId.get()).isEqualTo(customerId);
          return null;
        });
  }

  @Test
  @Order(9)
  void resolveRate_expiredRateNotReturned() {
    runInTenant(
        () -> {
          // The future rate for memberIdMember (created in test 6, starts 2030) should not resolve
          // in 2025. The date range rates (test 7) end 2024-12-31. Check 2025 -- nothing should
          // match.
          var result =
              billingRateService.resolveRate(memberIdMember, projectId, LocalDate.of(2025, 6, 15));

          assertThat(result).isEmpty();
          return null;
        });
  }

  @Test
  @Order(10)
  void resolveRate_returnsRateAfterDateRangeStarts() {
    runInTenant(
        () -> {
          // The future rate for memberIdMember (2030+) should resolve in 2030
          var result =
              billingRateService.resolveRate(memberIdMember, projectId, LocalDate.of(2030, 6, 15));

          assertThat(result).isPresent();
          assertThat(result.get().source()).isEqualTo("MEMBER_DEFAULT");
          assertThat(result.get().hourlyRate()).isEqualByComparingTo("300.00");
          return null;
        });
  }

  // --- Helpers ---

  private <T> T runInTenant(java.util.concurrent.Callable<T> callable) {
    try {
      return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
          .where(RequestScopes.MEMBER_ID, memberIdOwner)
          .where(RequestScopes.ORG_ROLE, "owner")
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
