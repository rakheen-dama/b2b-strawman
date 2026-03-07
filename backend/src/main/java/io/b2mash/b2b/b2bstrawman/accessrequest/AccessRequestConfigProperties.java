package io.b2mash.b2b.b2bstrawman.accessrequest;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for access request processing.
 *
 * @param blockedEmailDomains list of email domains that are not allowed to submit access requests
 *     (e.g., free email providers)
 * @param otpExpiryMinutes how long an OTP code remains valid, in minutes
 * @param otpMaxAttempts maximum number of OTP verification attempts before the request is locked
 */
@ConfigurationProperties(prefix = "app.access-request")
public record AccessRequestConfigProperties(
    List<String> blockedEmailDomains, int otpExpiryMinutes, int otpMaxAttempts) {}
