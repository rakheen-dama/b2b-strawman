package io.b2mash.b2b.b2bstrawman.assistant.specialist;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the Inbox specialist with full tool subset for SA matter activity summaries. */
@Configuration
public class InboxSpecialistConfig {

  @Bean
  public Specialist inboxSpecialist() {
    return new Specialist(
        "inbox-za",
        "Inbox Specialist",
        "Summarise matter activity across comments, events, requests, deadlines, and trust.",
        "assistant/specialists/inbox-za.md",
        List.of("GetMatterActivityWindow", "PostInboxSummary"),
        List.of(new LauncherContext("/inbox", "inbox", "Ask the Inbox specialist")),
        true,
        8,
        List.of("PostInboxSummary"));
  }
}
