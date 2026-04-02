package io.b2mash.b2b.b2bstrawman.billing;

import io.b2mash.b2b.b2bstrawman.security.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook endpoint for PayFast subscription ITN (Instant Transaction Notification). Always returns
 * HTTP 200 as required by PayFast — validation and error handling happen in the service layer via
 * early returns, not exceptions.
 */
@RestController
public class SubscriptionItnController {

  private final SubscriptionItnService subscriptionItnService;

  public SubscriptionItnController(SubscriptionItnService subscriptionItnService) {
    this.subscriptionItnService = subscriptionItnService;
  }

  @PostMapping(
      value = "/api/webhooks/subscription",
      consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  public ResponseEntity<Void> handleItn(
      @RequestParam Map<String, String> params, HttpServletRequest request) {
    subscriptionItnService.processItn(params, ClientIpResolver.resolve(request));
    return ResponseEntity.ok().build();
  }
}
