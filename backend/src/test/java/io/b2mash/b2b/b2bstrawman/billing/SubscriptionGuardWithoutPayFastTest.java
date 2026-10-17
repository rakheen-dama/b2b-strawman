package io.b2mash.b2b.b2bstrawman.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.integration.payment.PayFastPaymentGateway;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Replacement-path test for Epic 602 (Phase 86 slice 8, ADR-335): the PayFast platform subscription
 * path is gone and manual firm-level billing is the only tenant billing model. (a) The ITN webhook
 * is no longer public and has no handler; subscribe/cancel/payments are unmapped. (b) The platform
 * PayFast beans/classes are absent while the tenant pay-now gateway is still wired and its webhook
 * still public. (c) {@code BillingMethod} is {DEBIT_ORDER, PILOT, COMPLIMENTARY, MANUAL}; the
 * billing response carries no self-serve flags; an admin cannot set PAYFAST. (d) Global V29 dropped
 * {@code subscription_payments} and the six PayFast columns. (e) {@code SubscriptionGuardFilter}
 * still gates a MANUAL tenant purely by {@code SubscriptionStatus}. {@code
 * SubscriptionGuardFilterTest} runs unchanged in the same suite.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SubscriptionGuardWithoutPayFastTest {

  private static final String ORG_ID = "org_guard_without_payfast";
  private static final String OWNER = "user_gwp_owner";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private SubscriptionRepository subscriptionRepository;
  @Autowired private SubscriptionStatusCache statusCache;
  @Autowired private ApplicationContext applicationContext;
  @Autowired private DataSource dataSource;

  private UUID orgUuid;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Guard Without PayFast Org", null);
    TestMemberHelper.syncMember(mockMvc, ORG_ID, OWNER, "gwp_owner@test.com", "GWP Owner", "owner");
    orgUuid = organizationRepository.findByExternalOrgId(ORG_ID).orElseThrow().getId();
  }

  // (a) ITN webhook + self-serve routes gone
  @Test
  void itnWebhookIsNotPublicAndHasNoHandler() throws Exception {
    // Was permitAll (SecurityConfig) → now falls under /api/** authenticated.
    mockMvc
        .perform(
            post("/api/webhooks/subscription")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("m_payment_id=x&payment_status=COMPLETE"))
        .andExpect(status().isUnauthorized());
    setStatus(Subscription.SubscriptionStatus.TRIALING);
    // Authenticated: no handler (was SubscriptionItnController) → 404, not 403.
    mockMvc
        .perform(
            post("/api/webhooks/subscription")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("m_payment_id=x&payment_status=COMPLETE"))
        .andExpect(status().isNotFound());
  }

  @Test
  void selfServeBillingRoutesAreUnmapped() throws Exception {
    var owner = TestJwtFactory.ownerJwt(ORG_ID, OWNER);
    mockMvc.perform(post("/api/billing/subscribe").with(owner)).andExpect(status().isNotFound());
    mockMvc.perform(post("/api/billing/cancel").with(owner)).andExpect(status().isNotFound());
    mockMvc.perform(get("/api/billing/payments").with(owner)).andExpect(status().isNotFound());
  }

  // (b) platform PayFast gone, tenant pay-now kept
  @Test
  void platformPayFastBeansAndClassesGone_tenantPayNowKept() throws Exception {
    for (String bean :
        List.of(
            "subscriptionItnController",
            "subscriptionItnService",
            "platformPayFastService",
            "payFastBillingConfig")) {
      assertThat(applicationContext.containsBean(bean)).as(bean).isFalse();
    }
    for (String fqn :
        List.of(
            "io.b2mash.b2b.b2bstrawman.billing.SubscriptionItnController",
            "io.b2mash.b2b.b2bstrawman.billing.SubscriptionPayment",
            "io.b2mash.b2b.b2bstrawman.billing.payfast.PlatformPayFastService")) {
      assertThatThrownBy(() -> Class.forName(fqn)).isInstanceOf(ClassNotFoundException.class);
    }
    // KEEP: tenant invoice pay-now gateway + its public webhook.
    assertThat(applicationContext.getBean(PayFastPaymentGateway.class)).isNotNull();
    mockMvc
        .perform(
            post("/api/webhooks/payment/payfast")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("payment_id=12345&pf_payment_id=67890&status=COMPLETE"))
        .andExpect(status().isOk());
  }

  // (c) billing-method model + response shape
  @Test
  void billingMethodModelIsManualOnly() throws Exception {
    assertThat(Arrays.stream(BillingMethod.values()).map(Enum::name))
        .containsExactlyInAnyOrder("DEBIT_ORDER", "PILOT", "COMPLIMENTARY", "MANUAL");
    setStatus(Subscription.SubscriptionStatus.TRIALING);
    mockMvc
        .perform(get("/api/billing/subscription").with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.billingMethod").value("MANUAL"))
        .andExpect(jsonPath("$.status").value("TRIALING"))
        .andExpect(jsonPath("$.canSubscribe").doesNotExist())
        .andExpect(jsonPath("$.canCancel").doesNotExist())
        .andExpect(jsonPath("$.adminManaged").doesNotExist())
        .andExpect(jsonPath("$.nextBillingAt").doesNotExist());
  }

  @Test
  void platformAdminCannotSetPayFastMethod() throws Exception {
    mockMvc
        .perform(
            put("/api/platform-admin/billing/tenants/" + orgUuid + "/status")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject("user_gwp_platform_admin")
                                    .claim("groups", List.of("platform-admins"))))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"billingMethod": "PAYFAST", "adminNote": "should be rejected"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Invalid billing method"))
        .andExpect(jsonPath("$.detail").value("Unknown billing method: PAYFAST"));
  }

  // (d) V29 schema effect
  @Test
  void payFastSchemaIsDropped() {
    var jdbc = new JdbcTemplate(dataSource);
    assertThat(
            jdbc.queryForObject(
                "SELECT to_regclass('public.subscription_payments')::text", String.class))
        .isNull();
    for (String column :
        List.of(
            "payfast_token",
            "payfast_payment_id",
            "monthly_amount_cents",
            "currency",
            "last_payment_at",
            "next_billing_at")) {
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public'"
                      + " AND table_name = 'subscriptions' AND column_name = ?",
                  Integer.class,
                  column))
          .as(column)
          .isZero();
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM public.subscriptions WHERE billing_method = 'PAYFAST'",
                Integer.class))
        .isZero();
  }

  // (e) guard still keys on SubscriptionStatus for a MANUAL tenant
  @Test
  void guardStillGatesManualTenantByStatus() throws Exception {
    var owner = TestJwtFactory.ownerJwt(ORG_ID, OWNER);

    setStatus(Subscription.SubscriptionStatus.TRIALING);
    TestEntityHelper.createProject(mockMvc, owner, "GWP Trial Write", "trial writes pass"); // 201

    setStatus(Subscription.SubscriptionStatus.GRACE_PERIOD);
    mockMvc
        .perform(
            post("/api/projects")
                .with(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"GWP Grace Write\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("subscription_required"));
    mockMvc.perform(get("/api/billing/subscription").with(owner)).andExpect(status().isOk());

    setStatus(Subscription.SubscriptionStatus.LOCKED);
    mockMvc
        .perform(get("/api/projects").with(owner))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("subscription_locked"));
    mockMvc.perform(get("/api/billing/subscription").with(owner)).andExpect(status().isOk());

    setStatus(Subscription.SubscriptionStatus.TRIALING);
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  /**
   * Admin-allowed targets only (TRIALING/ACTIVE/GRACE_PERIOD/LOCKED); billing method stays MANUAL.
   */
  private void setStatus(Subscription.SubscriptionStatus target) {
    var sub = subscriptionRepository.findByOrganizationId(orgUuid).orElseThrow();
    if (sub.getSubscriptionStatus() != target) {
      sub.adminTransitionTo(target);
      subscriptionRepository.saveAndFlush(sub);
    }
    statusCache.evict(orgUuid);
  }
}
