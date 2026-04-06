package io.b2mash.b2b.b2bstrawman.integration.kyc;

import io.b2mash.b2b.b2bstrawman.integration.ConnectionTestResult;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationAdapter;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * No-op KYC adapter used as the default fallback when no KYC provider is configured. Returns ERROR
 * to signal that verification cannot be performed. This adapter should never be reached from the UI
 * (the verification button is hidden when KYC is unconfigured).
 */
@Component
@IntegrationAdapter(domain = IntegrationDomain.KYC_VERIFICATION, slug = "noop")
public class NoOpKycAdapter implements KycVerificationPort {

  private static final Logger log = LoggerFactory.getLogger(NoOpKycAdapter.class);

  @Override
  public String providerId() {
    return "noop";
  }

  @Override
  public KycVerificationResult verify(KycVerificationRequest request) {
    log.warn("NoOp KYC: verification requested but no provider is configured");
    return new KycVerificationResult(
        KycVerificationStatus.ERROR,
        "noop",
        null,
        "NO_PROVIDER",
        "No KYC provider configured",
        null,
        Map.of());
  }

  @Override
  public ConnectionTestResult testConnection() {
    return new ConnectionTestResult(false, "noop", "No KYC provider configured");
  }
}
