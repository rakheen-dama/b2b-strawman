package io.b2mash.b2b.b2bstrawman.integration.payment;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/webhooks/payment")
public class PaymentWebhookController {

  private static final Logger log = LoggerFactory.getLogger(PaymentWebhookController.class);
  private static final Set<String> ALLOWED_PROVIDERS = Set.of("stripe", "payfast");
  private static final Pattern TENANT_SCHEMA_PATTERN = Pattern.compile("tenant_[a-f0-9]+");

  private final PaymentWebhookService paymentWebhookService;
  private final ObjectMapper objectMapper;

  public PaymentWebhookController(
      PaymentWebhookService paymentWebhookService, ObjectMapper objectMapper) {
    this.paymentWebhookService = paymentWebhookService;
    this.objectMapper = objectMapper;
  }

  @PostMapping("/{provider}")
  public ResponseEntity<Void> handleWebhook(
      @PathVariable String provider,
      @RequestBody String payload,
      @RequestHeader Map<String, String> headers) {

    String sanitizedProvider = provider.replaceAll("[^a-zA-Z0-9_-]", "");

    if (!ALLOWED_PROVIDERS.contains(sanitizedProvider)) {
      log.warn("Received payment webhook for unknown provider: {}", sanitizedProvider);
      return ResponseEntity.ok().build();
    }

    String tenantSchema = extractTenantSchema(sanitizedProvider, payload);
    if (tenantSchema == null) {
      log.warn("Could not extract tenant schema from {} webhook payload", sanitizedProvider);
      return ResponseEntity.ok().build();
    }

    try {
      ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
          .run(() -> paymentWebhookService.processWebhook(sanitizedProvider, payload, headers));
    } catch (Exception e) {
      log.error("Error processing {} webhook", sanitizedProvider, e);
    }

    return ResponseEntity.ok().build();
  }

  private String extractTenantSchema(String provider, String payload) {
    try {
      String schema =
          switch (provider) {
            case "stripe" -> extractStripeSchema(payload);
            case "payfast" -> extractPayFastSchema(payload);
            default -> null;
          };

      if (schema != null && TENANT_SCHEMA_PATTERN.matcher(schema).matches()) {
        return schema;
      }

      if (schema != null) {
        log.warn("Invalid tenant schema format received in webhook");
      }
      return null;
    } catch (Exception e) {
      log.warn("Failed to extract tenant schema from {} payload: {}", provider, e.getMessage());
      return null;
    }
  }

  private String extractStripeSchema(String payload) {
    try {
      JsonNode root = objectMapper.readTree(payload);
      JsonNode metadata = root.path("data").path("object").path("metadata");
      JsonNode tenantNode = metadata.path("tenantSchema");
      return tenantNode.isMissingNode() ? null : tenantNode.asText();
    } catch (Exception e) {
      return null;
    }
  }

  private String extractPayFastSchema(String payload) {
    try {
      for (String param : payload.split("&")) {
        String[] parts = param.split("=", 2);
        if (parts.length == 2 && "custom_str1".equals(parts[0])) {
          return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
        }
      }
      return null;
    } catch (Exception e) {
      return null;
    }
  }
}
