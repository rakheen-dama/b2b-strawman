package io.b2mash.b2b.b2bstrawman.assistant.specialist;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the Inbox specialist. Tool subset will land in Epic 514. */
@Configuration
public class InboxSpecialistConfig {

  @Bean
  public Specialist inboxSpecialist() {
    return new Specialist(
        "inbox-za",
        "Inbox Specialist",
        "Help with email triage, replies, and document handling.",
        "assistant/specialists/inbox-za.md",
        List.of(),
        List.of(new LauncherContext("/inbox", "inbox", "Ask the Inbox specialist")),
        false);
  }
}
