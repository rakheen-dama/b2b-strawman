package io.b2mash.b2b.b2bstrawman.assistant.specialist;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the Billing specialist. Tool subset will land in Epic 512. */
@Configuration
public class BillingSpecialistConfig {

  @Bean
  public Specialist billingSpecialist() {
    return new Specialist(
        "billing-za",
        "Billing Specialist",
        "Help with invoices, statements, and billing questions.",
        "assistant/specialists/billing-za.md",
        List.of(),
        List.of(new LauncherContext("/billing", "billing", "Ask the Billing specialist")),
        false);
  }
}
