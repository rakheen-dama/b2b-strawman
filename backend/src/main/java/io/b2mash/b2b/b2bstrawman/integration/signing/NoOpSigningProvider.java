package io.b2mash.b2b.b2bstrawman.integration.signing;

import io.b2mash.b2b.b2bstrawman.integration.ConnectionTestResult;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NoOpSigningProvider implements DocumentSigningProvider {

  private static final Logger log = LoggerFactory.getLogger(NoOpSigningProvider.class);

  @Override
  public String providerId() {
    return "noop";
  }

  @Override
  public SigningResult sendForSignature(SigningRequest request) {
    log.info(
        "NoOp signing: would send document for signature to {} <{}>",
        request.signerName(),
        request.signerEmail());
    return new SigningResult(
        true, "NOOP-SIGN-" + UUID.randomUUID().toString().substring(0, 8), null);
  }

  @Override
  public SigningStatus checkStatus(String signingReference) {
    return new SigningStatus(SigningState.SIGNED, signingReference, Instant.now());
  }

  @Override
  public byte[] downloadSigned(String signingReference) {
    log.info("NoOp signing: would download signed document for ref {}", signingReference);
    return new byte[0];
  }

  @Override
  public ConnectionTestResult testConnection() {
    return new ConnectionTestResult(true, "noop", null);
  }
}
