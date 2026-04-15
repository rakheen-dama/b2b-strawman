package io.b2mash.b2b.b2bstrawman.demo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DemoWelcomeEmailServiceTest {

  @Autowired private DemoWelcomeEmailService welcomeEmailService;

  @Test
  void sendWelcomeEmail_doesNotThrow() {
    // In test profile, JavaMailSender may not be configured (Optional.empty()),
    // so this verifies the non-fatal behavior
    assertDoesNotThrow(
        () ->
            welcomeEmailService.sendWelcomeEmail(
                "test@example.com",
                "Test Org",
                "test-org",
                "legal-za",
                "http://localhost:3000/org/test-org",
                "TempPass123!"));
  }
}
