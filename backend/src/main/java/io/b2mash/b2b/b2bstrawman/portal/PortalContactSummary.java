package io.b2mash.b2b.b2bstrawman.portal;

import java.util.UUID;

/** Lightweight projection of a portal contact for listing endpoints. */
public record PortalContactSummary(UUID id, String displayName, String email) {

  public static PortalContactSummary from(PortalContact contact) {
    return new PortalContactSummary(contact.getId(), contact.getDisplayName(), contact.getEmail());
  }
}
