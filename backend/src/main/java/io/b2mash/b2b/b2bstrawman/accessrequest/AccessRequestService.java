package io.b2mash.b2b.b2bstrawman.accessrequest;

import io.b2mash.b2b.b2bstrawman.accessrequest.dto.AccessRequestDtos.AccessRequestSubmission;
import io.b2mash.b2b.b2bstrawman.accessrequest.dto.AccessRequestDtos.SubmitResponse;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import jakarta.mail.internet.MimeMessage;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccessRequestService {

  private static final Logger log = LoggerFactory.getLogger(AccessRequestService.class);

  private final AccessRequestRepository accessRequestRepository;
  private final AccessRequestConfigProperties configProperties;
  private final EmailDomainValidator emailDomainValidator;
  private final PasswordEncoder passwordEncoder;
  private final SecureRandom secureRandom;
  private final Optional<JavaMailSender> mailSender;
  private final String senderAddress;

  public AccessRequestService(
      AccessRequestRepository accessRequestRepository,
      AccessRequestConfigProperties configProperties,
      EmailDomainValidator emailDomainValidator,
      PasswordEncoder passwordEncoder,
      Optional<JavaMailSender> mailSender,
      @Value("${docteams.email.sender-address:noreply@docteams.app}") String senderAddress) {
    this.accessRequestRepository = accessRequestRepository;
    this.configProperties = configProperties;
    this.emailDomainValidator = emailDomainValidator;
    this.passwordEncoder = passwordEncoder;
    this.mailSender = mailSender;
    this.senderAddress = senderAddress;
    try {
      this.secureRandom = SecureRandom.getInstanceStrong();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("No strong SecureRandom algorithm available", e);
    }
  }

  @Transactional
  public SubmitResponse submitRequest(AccessRequestSubmission submission) {
    String email = submission.email().toLowerCase();

    if (emailDomainValidator.isBlockedDomain(email)) {
      throw new InvalidStateException("Blocked email domain", "Please use a company email address");
    }

    boolean duplicateExists =
        accessRequestRepository.existsByEmailAndStatusIn(
            email, List.of(AccessRequestStatus.PENDING_VERIFICATION, AccessRequestStatus.PENDING));
    if (duplicateExists) {
      // Return generic response to prevent email enumeration on this public endpoint
      return new SubmitResponse(
          "If the email is valid, a verification code will be sent.",
          configProperties.otpExpiryMinutes());
    }

    String otp = String.format("%06d", secureRandom.nextInt(1_000_000));
    String otpHash = passwordEncoder.encode(otp);

    var accessRequest =
        new AccessRequest(
            email,
            submission.fullName(),
            submission.organizationName(),
            submission.country(),
            submission.industry());
    accessRequest.setOtpHash(otpHash);
    accessRequest.setOtpExpiresAt(
        Instant.now().plus(configProperties.otpExpiryMinutes(), ChronoUnit.MINUTES));

    accessRequestRepository.save(accessRequest);

    sendOtpEmail(email, otp, submission.fullName(), configProperties.otpExpiryMinutes());

    return new SubmitResponse(
        "If the email is valid, a verification code will be sent.",
        configProperties.otpExpiryMinutes());
  }

  private void sendOtpEmail(String recipientEmail, String otp, String fullName, int expiryMinutes) {
    if (mailSender.isEmpty()) {
      log.info("No mail sender configured — OTP email not sent for {}", recipientEmail);
      return;
    }
    try {
      MimeMessage message = mailSender.get().createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, false);
      helper.setFrom(senderAddress);
      helper.setTo(recipientEmail);
      helper.setSubject("Your DocTeams verification code");
      helper.setText(
          "Hi %s,\n\nYour DocTeams verification code is: %s\n\nThis code expires in %d minutes.\n\nIf you did not request this, please ignore this email."
              .formatted(fullName, otp, expiryMinutes),
          false);
      mailSender.get().send(message);
      log.debug("OTP email sent to {}", recipientEmail);
    } catch (Exception e) {
      throw new RuntimeException("Failed to send OTP email to " + recipientEmail, e);
    }
  }
}
