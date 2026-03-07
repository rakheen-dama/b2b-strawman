package io.b2mash.b2b.b2bstrawman.accessrequest;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/** Validates email domains against the configured blocked-domain list. */
@Component
@EnableConfigurationProperties(AccessRequestConfigProperties.class)
public class EmailDomainValidator {

  private final Set<String> blockedDomains;

  public EmailDomainValidator(AccessRequestConfigProperties config) {
    this.blockedDomains =
        config.blockedEmailDomains().stream().map(String::toLowerCase).collect(Collectors.toSet());
  }

  /**
   * Returns {@code true} if the email's domain is in the blocked list.
   *
   * @param email the email address to check
   * @return true if the domain is blocked, false otherwise (including for null/invalid input)
   */
  public boolean isBlockedDomain(String email) {
    if (email == null || email.isEmpty()) {
      return false;
    }
    int atIndex = email.indexOf('@');
    if (atIndex < 0 || atIndex == email.length() - 1) {
      return false;
    }
    String domain = email.substring(atIndex + 1).toLowerCase();
    return blockedDomains.contains(domain);
  }
}
