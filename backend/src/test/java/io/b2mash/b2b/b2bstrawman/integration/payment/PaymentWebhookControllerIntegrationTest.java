package io.b2mash.b2b.b2bstrawman.integration.payment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
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
class PaymentWebhookControllerIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void unknown_provider_returns_200() throws Exception {
    mockMvc
        .perform(
            post("/api/webhooks/payment/unknown")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk());
  }

  @Test
  void stripe_webhook_with_missing_tenant_returns_200() throws Exception {
    String payload =
        """
        {
          "type": "checkout.session.completed",
          "data": {
            "object": {
              "id": "cs_test_123",
              "metadata": {}
            }
          }
        }
        """;
    mockMvc
        .perform(
            post("/api/webhooks/payment/stripe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isOk());
  }

  @Test
  void stripe_webhook_with_invalid_tenant_format_returns_200() throws Exception {
    String payload =
        """
        {
          "type": "checkout.session.completed",
          "data": {
            "object": {
              "id": "cs_test_123",
              "metadata": {
                "tenantSchema": "DROP TABLE users;"
              }
            }
          }
        }
        """;
    mockMvc
        .perform(
            post("/api/webhooks/payment/stripe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isOk());
  }

  @Test
  void payfast_webhook_with_missing_tenant_returns_200() throws Exception {
    String payload = "payment_id=12345&pf_payment_id=67890&status=COMPLETE";
    mockMvc
        .perform(
            post("/api/webhooks/payment/payfast")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content(payload))
        .andExpect(status().isOk());
  }

  @Test
  void payfast_webhook_with_invalid_tenant_format_returns_200() throws Exception {
    String payload = "payment_id=12345&pf_payment_id=67890&status=COMPLETE&custom_str1=evil_schema";
    mockMvc
        .perform(
            post("/api/webhooks/payment/payfast")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content(payload))
        .andExpect(status().isOk());
  }

  @Test
  void webhook_endpoint_accessible_without_auth() throws Exception {
    // Verify the endpoint doesn't require authentication (permitAll)
    mockMvc
        .perform(
            post("/api/webhooks/payment/stripe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk());
  }
}
