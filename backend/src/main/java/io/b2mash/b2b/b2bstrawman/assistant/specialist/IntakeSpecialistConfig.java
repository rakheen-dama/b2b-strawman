package io.b2mash.b2b.b2bstrawman.assistant.specialist;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the Intake specialist with full tool subset for SA legal intake. */
@Configuration
public class IntakeSpecialistConfig {

  @Bean
  public Specialist intakeSpecialist() {
    return new Specialist(
        "intake-za",
        "Intake Specialist",
        "Help with client onboarding, KYC, and matter intake.",
        "assistant/specialists/intake-za.md",
        List.of(
            "ListDocumentsForContext", "ExtractTextFromDocument", "ProposeCustomerFieldExtraction"),
        List.of(new LauncherContext("/intake", "intake", "Ask the Intake specialist")),
        true);
  }
}
