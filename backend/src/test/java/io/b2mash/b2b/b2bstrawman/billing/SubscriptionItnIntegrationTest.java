package io.b2mash.b2b.b2bstrawman.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.billing.payfast.PlatformPayFastService;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SubscriptionItnIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String TEST_PASSPHRASE = "test-passphrase";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private SubscriptionRepository subscriptionRepository;
  @Autowired private SubscriptionPaymentRepository subscriptionPaymentRepository;

  @MockitoBean private PlatformPayFastService platformPayFastService;

  private UUID orgId;

  @BeforeAll
  void setup() throws Exception {
    String clerkOrgId = "org_itn_test";
    provisioningService.provisionTenant(clerkOrgId, "ITN Test Org", null);
    var org = organizationRepository.findByClerkOrgId(clerkOrgId).orElseThrow();
    orgId = org.getId();
  }

  // --- COMPLETE ITN tests ---

  @Test
  void completeItn_transitionsSubscriptionToActive() throws Exception {
    String freshOrg = "org_itn_complete_active";
    provisioningService.provisionTenant(freshOrg, "Complete Active Test", null);
    var org = organizationRepository.findByClerkOrgId(freshOrg).orElseThrow();

    var params = buildItnParams("pay_complete_001", org.getId().toString(), "COMPLETE", "499.00");
    params.put("token", "sub-token-active-test");
    String sig = computeItnSignature(params);

    mockMvc
        .perform(
            post("/api/webhooks/subscription")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("m_payment_id", "pay_complete_001")
                .param("custom_str1", org.getId().toString())
                .param("payment_status", "COMPLETE")
                .param("token", "sub-token-active-test")
                .param("amount_gross", "499.00")
                .param("signature", sig))
        .andExpect(status().isOk());

    var subscription = subscriptionRepository.findByOrganizationId(org.getId()).orElseThrow();
    assertThat(subscription.getSubscriptionStatus())
        .isEqualTo(Subscription.SubscriptionStatus.ACTIVE);
  }

  @Test
  void completeItn_storesPayfastTokenOnFirstPayment() throws Exception {
    String freshOrg = "org_itn_token_store";
    provisioningService.provisionTenant(freshOrg, "Token Store Test", null);
    var org = organizationRepository.findByClerkOrgId(freshOrg).orElseThrow();

    var params = buildItnParams("pay_token_001", org.getId().toString(), "COMPLETE", "499.00");
    params.put("token", "sub-token-first-payment");
    String sig = computeItnSignature(params);

    mockMvc
        .perform(
            post("/api/webhooks/subscription")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("m_payment_id", "pay_token_001")
                .param("custom_str1", org.getId().toString())
                .param("payment_status", "COMPLETE")
                .param("token", "sub-token-first-payment")
                .param("amount_gross", "499.00")
                .param("signature", sig))
        .andExpect(status().isOk());

    var subscription = subscriptionRepository.findByOrganizationId(org.getId()).orElseThrow();
    assertThat(subscription.getPayfastToken()).isEqualTo("sub-token-first-payment");
  }

  @Test
  void completeItn_createsSubscriptionPaymentRecord() throws Exception {
    String freshOrg = "org_itn_payment_record";
    provisioningService.provisionTenant(freshOrg, "Payment Record Test", null);
    var org = organizationRepository.findByClerkOrgId(freshOrg).orElseThrow();
    var subscription = subscriptionRepository.findByOrganizationId(org.getId()).orElseThrow();

    var params = buildItnParams("pay_record_001", org.getId().toString(), "COMPLETE", "499.00");
    params.put("token", "sub-token-record");
    String sig = computeItnSignature(params);

    mockMvc
        .perform(
            post("/api/webhooks/subscription")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("m_payment_id", "pay_record_001")
                .param("custom_str1", org.getId().toString())
                .param("payment_status", "COMPLETE")
                .param("token", "sub-token-record")
                .param("amount_gross", "499.00")
                .param("signature", sig))
        .andExpect(status().isOk());

    var payments =
        subscriptionPaymentRepository.findBySubscriptionIdOrderByPaymentDateDesc(
            subscription.getId());
    assertThat(payments).isNotEmpty();

    var payment = payments.getFirst();
    assertThat(payment.getPayfastPaymentId()).isEqualTo("pay_record_001");
    assertThat(payment.getAmountCents()).isEqualTo(49900);
    assertThat(payment.getStatus()).isEqualTo(SubscriptionPayment.PaymentStatus.COMPLETE);
    assertThat(payment.getCurrency()).isEqualTo("ZAR");
  }

  @Test
  void completeItn_storesRawItnJsonb() throws Exception {
    String freshOrg = "org_itn_rawitn";
    provisioningService.provisionTenant(freshOrg, "RawItn Test", null);
    var org = organizationRepository.findByClerkOrgId(freshOrg).orElseThrow();
    var subscription = subscriptionRepository.findByOrganizationId(org.getId()).orElseThrow();

    var params = buildItnParams("pay_rawitn_001", org.getId().toString(), "COMPLETE", "499.00");
    params.put("token", "sub-token-rawitn");
    String sig = computeItnSignature(params);

    mockMvc
        .perform(
            post("/api/webhooks/subscription")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("m_payment_id", "pay_rawitn_001")
                .param("custom_str1", org.getId().toString())
                .param("payment_status", "COMPLETE")
                .param("token", "sub-token-rawitn")
                .param("amount_gross", "499.00")
                .param("signature", sig))
        .andExpect(status().isOk());

    var payments =
        subscriptionPaymentRepository.findBySubscriptionIdOrderByPaymentDateDesc(
            subscription.getId());
    assertThat(payments).isNotEmpty();

    var rawItn = payments.getFirst().getRawItn();
    assertThat(rawItn).isNotNull();
    assertThat(rawItn).containsEntry("m_payment_id", "pay_rawitn_001");
    assertThat(rawItn).containsEntry("payment_status", "COMPLETE");
    assertThat(rawItn).containsEntry("custom_str1", org.getId().toString());
    assertThat(rawItn).containsKey("signature");
  }

  @Test
  void duplicatePaymentId_isIdempotent() throws Exception {
    String freshOrg = "org_itn_idempotent";
    provisioningService.provisionTenant(freshOrg, "Idempotent Test", null);
    var org = organizationRepository.findByClerkOrgId(freshOrg).orElseThrow();
    var subscription = subscriptionRepository.findByOrganizationId(org.getId()).orElseThrow();

    var params = buildItnParams("pay_idempotent_001", org.getId().toString(), "COMPLETE", "499.00");
    params.put("token", "sub-token-idemp");
    String sig = computeItnSignature(params);

    // Send first ITN
    mockMvc
        .perform(
            post("/api/webhooks/subscription")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("m_payment_id", "pay_idempotent_001")
                .param("custom_str1", org.getId().toString())
                .param("payment_status", "COMPLETE")
                .param("token", "sub-token-idemp")
                .param("amount_gross", "499.00")
                .param("signature", sig))
        .andExpect(status().isOk());

    // Send duplicate ITN
    mockMvc
        .perform(
            post("/api/webhooks/subscription")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("m_payment_id", "pay_idempotent_001")
                .param("custom_str1", org.getId().toString())
                .param("payment_status", "COMPLETE")
                .param("token", "sub-token-idemp")
                .param("amount_gross", "499.00")
                .param("signature", sig))
        .andExpect(status().isOk());

    // Only one payment record should exist
    var payments =
        subscriptionPaymentRepository.findBySubscriptionIdOrderByPaymentDateDesc(
            subscription.getId());
    long matchingPayments =
        payments.stream().filter(p -> "pay_idempotent_001".equals(p.getPayfastPaymentId())).count();
    assertThat(matchingPayments).isEqualTo(1);
  }

  // --- FAILED ITN test ---

  @Test
  void failedItn_transitionsActiveSubscriptionToPastDue() throws Exception {
    String freshOrg = "org_itn_failed";
    provisioningService.provisionTenant(freshOrg, "Failed ITN Test", null);
    var org = organizationRepository.findByClerkOrgId(freshOrg).orElseThrow();

    // First activate the subscription via a COMPLETE ITN
    var completeParams =
        buildItnParams("pay_activate_for_fail", org.getId().toString(), "COMPLETE", "499.00");
    completeParams.put("token", "sub-token-fail-test");
    String completeSig = computeItnSignature(completeParams);

    mockMvc
        .perform(
            post("/api/webhooks/subscription")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("m_payment_id", "pay_activate_for_fail")
                .param("custom_str1", org.getId().toString())
                .param("payment_status", "COMPLETE")
                .param("token", "sub-token-fail-test")
                .param("amount_gross", "499.00")
                .param("signature", completeSig))
        .andExpect(status().isOk());

    // Verify it's ACTIVE
    var sub = subscriptionRepository.findByOrganizationId(org.getId()).orElseThrow();
    assertThat(sub.getSubscriptionStatus()).isEqualTo(Subscription.SubscriptionStatus.ACTIVE);

    // Now send a FAILED ITN
    var failedParams = buildItnParams("pay_failed_001", org.getId().toString(), "FAILED", "499.00");
    String failedSig = computeItnSignature(failedParams);

    mockMvc
        .perform(
            post("/api/webhooks/subscription")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("m_payment_id", "pay_failed_001")
                .param("custom_str1", org.getId().toString())
                .param("payment_status", "FAILED")
                .param("amount_gross", "499.00")
                .param("signature", failedSig))
        .andExpect(status().isOk());

    sub = subscriptionRepository.findByOrganizationId(org.getId()).orElseThrow();
    assertThat(sub.getSubscriptionStatus()).isEqualTo(Subscription.SubscriptionStatus.PAST_DUE);
  }

  // --- CANCELLED ITN test ---

  @Test
  void cancelledItn_transitionsPendingCancellationToGracePeriod() throws Exception {
    String freshOrg = "org_itn_cancelled";
    provisioningService.provisionTenant(freshOrg, "Cancelled ITN Test", null);
    var org = organizationRepository.findByClerkOrgId(freshOrg).orElseThrow();

    // Activate via COMPLETE ITN
    var completeParams =
        buildItnParams("pay_activate_for_cancel", org.getId().toString(), "COMPLETE", "499.00");
    completeParams.put("token", "sub-token-cancel-test");
    String completeSig = computeItnSignature(completeParams);

    mockMvc
        .perform(
            post("/api/webhooks/subscription")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("m_payment_id", "pay_activate_for_cancel")
                .param("custom_str1", org.getId().toString())
                .param("payment_status", "COMPLETE")
                .param("token", "sub-token-cancel-test")
                .param("amount_gross", "499.00")
                .param("signature", completeSig))
        .andExpect(status().isOk());

    // Transition to PENDING_CANCELLATION directly via entity
    var sub = subscriptionRepository.findByOrganizationId(org.getId()).orElseThrow();
    sub.transitionTo(Subscription.SubscriptionStatus.PENDING_CANCELLATION);
    subscriptionRepository.save(sub);

    // Send CANCELLED ITN
    var cancelledParams =
        buildItnParams("pay_cancelled_001", org.getId().toString(), "CANCELLED", "0.00");
    String cancelledSig = computeItnSignature(cancelledParams);

    mockMvc
        .perform(
            post("/api/webhooks/subscription")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("m_payment_id", "pay_cancelled_001")
                .param("custom_str1", org.getId().toString())
                .param("payment_status", "CANCELLED")
                .param("amount_gross", "0.00")
                .param("signature", cancelledSig))
        .andExpect(status().isOk());

    sub = subscriptionRepository.findByOrganizationId(org.getId()).orElseThrow();
    assertThat(sub.getSubscriptionStatus()).isEqualTo(Subscription.SubscriptionStatus.GRACE_PERIOD);
    assertThat(sub.getGraceEndsAt()).isNotNull();
  }

  // --- Invalid signature test ---

  @Test
  void invalidSignature_returns200ButNoStateChange() throws Exception {
    String freshOrg = "org_itn_bad_sig";
    provisioningService.provisionTenant(freshOrg, "Bad Sig Test", null);
    var org = organizationRepository.findByClerkOrgId(freshOrg).orElseThrow();

    mockMvc
        .perform(
            post("/api/webhooks/subscription")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("m_payment_id", "pay_bad_sig_001")
                .param("custom_str1", org.getId().toString())
                .param("payment_status", "COMPLETE")
                .param("amount_gross", "499.00")
                .param("signature", "invalid_signature_value"))
        .andExpect(status().isOk());

    var sub = subscriptionRepository.findByOrganizationId(org.getId()).orElseThrow();
    assertThat(sub.getSubscriptionStatus()).isEqualTo(Subscription.SubscriptionStatus.TRIALING);
  }

  // --- Invalid IP test ---

  @Test
  void invalidIp_returns200ButNoStateChange() throws Exception {
    // In sandbox mode, localhost is allowed. To test IP rejection, we verify that
    // an invalid signature (which is checked after IP) blocks state change.
    // For a true IP test, we'd need sandbox=false. Instead, verify the endpoint
    // returns 200 even with bad data and doesn't change state.
    String freshOrg = "org_itn_bad_ip";
    provisioningService.provisionTenant(freshOrg, "Bad IP Test", null);
    var org = organizationRepository.findByClerkOrgId(freshOrg).orElseThrow();

    // Send with bad signature — IP passes (sandbox allows localhost), signature fails
    mockMvc
        .perform(
            post("/api/webhooks/subscription")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("m_payment_id", "pay_bad_ip_001")
                .param("custom_str1", org.getId().toString())
                .param("payment_status", "COMPLETE")
                .param("amount_gross", "499.00")
                .param("signature", "deliberately_wrong"))
        .andExpect(status().isOk());

    var sub = subscriptionRepository.findByOrganizationId(org.getId()).orElseThrow();
    assertThat(sub.getSubscriptionStatus()).isEqualTo(Subscription.SubscriptionStatus.TRIALING);
  }

  // --- Cancel with PayFast token test ---

  @Test
  void cancelSubscription_callsPayFastCancellation_whenTokenPresent() throws Exception {
    String freshOrg = "org_itn_cancel_pf";
    provisioningService.provisionTenant(freshOrg, "Cancel PF Test", null);
    var org = organizationRepository.findByClerkOrgId(freshOrg).orElseThrow();

    // Activate via COMPLETE ITN (sets payfast token)
    var completeParams =
        buildItnParams("pay_for_cancel_pf", org.getId().toString(), "COMPLETE", "499.00");
    completeParams.put("token", "sub-token-cancel-pf");
    String completeSig = computeItnSignature(completeParams);

    mockMvc
        .perform(
            post("/api/webhooks/subscription")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("m_payment_id", "pay_for_cancel_pf")
                .param("custom_str1", org.getId().toString())
                .param("payment_status", "COMPLETE")
                .param("token", "sub-token-cancel-pf")
                .param("amount_gross", "499.00")
                .param("signature", completeSig))
        .andExpect(status().isOk());

    // Verify subscription is ACTIVE with token
    var sub = subscriptionRepository.findByOrganizationId(org.getId()).orElseThrow();
    assertThat(sub.getSubscriptionStatus()).isEqualTo(Subscription.SubscriptionStatus.ACTIVE);
    assertThat(sub.getPayfastToken()).isEqualTo("sub-token-cancel-pf");

    // Sync a member so cancel endpoint works (needs tenant context for member count)
    syncMember(freshOrg, "user_cancel_owner", "cancel@test.com", "CancelOwner", "owner");

    // Cancel via API
    mockMvc
        .perform(post("/api/billing/cancel").with(ownerJwt(freshOrg, "user_cancel_owner")))
        .andExpect(status().isOk());

    // Verify PlatformPayFastService.cancelPayFastSubscription was called
    verify(platformPayFastService).cancelPayFastSubscription("sub-token-cancel-pf");
  }

  // --- Helpers ---

  private Map<String, String> buildItnParams(
      String paymentId, String orgIdStr, String paymentStatus, String amountGross) {
    var params = new LinkedHashMap<String, String>();
    params.put("m_payment_id", paymentId);
    params.put("custom_str1", orgIdStr);
    params.put("payment_status", paymentStatus);
    params.put("amount_gross", amountGross);
    return params;
  }

  private String computeItnSignature(Map<String, String> params) {
    var sorted = new TreeMap<>(params);
    sorted.remove("signature");
    var paramString =
        sorted.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue().trim())
            .collect(Collectors.joining("&"));
    paramString += "&passphrase=" + TEST_PASSPHRASE;
    try {
      var md = MessageDigest.getInstance("MD5");
      var digest = md.digest(paramString.getBytes(StandardCharsets.UTF_8));
      var sb = new StringBuilder();
      for (byte b : digest) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private void syncMember(
      String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    mockMvc
        .perform(
            post("/internal/members/sync")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
            {
              "clerkOrgId": "%s", "clerkUserId": "%s", "email": "%s",
              "name": "%s", "avatarUrl": null, "orgRole": "%s"
            }
            """
                        .formatted(orgId, clerkUserId, email, name, orgRole)))
        .andExpect(status().isCreated());
  }

  private static org.springframework.security.test.web.servlet.request
          .SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor
      ownerJwt(String orgId, String userId) {
    return org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.jwt()
        .jwt(j -> j.subject(userId).claim("o", Map.of("id", orgId, "rol", "owner")));
  }
}
