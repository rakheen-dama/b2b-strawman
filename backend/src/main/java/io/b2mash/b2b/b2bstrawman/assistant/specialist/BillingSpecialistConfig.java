package io.b2mash.b2b.b2bstrawman.assistant.specialist;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the Billing specialist with full tool subset (Phase 70 Epic 512A).
 *
 * <p>Read tools come from the existing Phase 52 registry (snake_case ids); the new write tools are
 * the propose-only contract from architecture §1.6. {@code automationCapable=true} per arch §10
 * Slice B1 — automated invocation handled by 515C's INVOKE_AI_SPECIALIST hook (out of scope here).
 */
@Configuration
public class BillingSpecialistConfig {

  @Bean
  public Specialist billingSpecialist() {
    return new Specialist(
        "billing-za",
        "Billing Specialist",
        "Help with invoices, statements, and billing questions.",
        "assistant/specialists/billing-za.md",
        List.of(
            // Read tools — Phase 52 registry (snake_case)
            "get_invoice",
            "get_unbilled_time",
            "get_time_summary",
            "list_projects",
            "get_project",
            "list_customers",
            "get_customer",
            // Write tools — Epic 512A (PascalCase, propose-only)
            "ProposeTimeEntryPolish",
            "ProposeInvoiceLineGrouping"),
        List.of(new LauncherContext("/billing", "billing", "Ask the Billing specialist")),
        true);
  }
}
