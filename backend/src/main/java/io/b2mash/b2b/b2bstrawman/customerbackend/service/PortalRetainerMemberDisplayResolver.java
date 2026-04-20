package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.settings.PortalRetainerMemberDisplay;
import org.springframework.stereotype.Component;

/**
 * Resolves the portal-facing display name for a firm member who has logged time against a retainer
 * (ADR-255, Epic 496A). The firm-wide {@link PortalRetainerMemberDisplay} privacy toggle determines
 * the output shape. Defensive fallback is {@code "Team member"} whenever the member, role, or
 * member's name is missing.
 */
@Component
public class PortalRetainerMemberDisplayResolver {

  private static final String FALLBACK = "Team member";

  /**
   * Produces the display string according to the given {@code mode}. Null-safe: a null {@code mode}
   * is treated as {@link PortalRetainerMemberDisplay#FIRST_NAME_ROLE} (the system default).
   *
   * @param member the firm member whose name/role to project (may be null)
   * @param roleName human-readable role label (e.g. "Attorney"); may be null
   * @param mode the firm's current privacy toggle (may be null)
   */
  public String resolve(Member member, String roleName, PortalRetainerMemberDisplay mode) {
    PortalRetainerMemberDisplay effective =
        mode != null ? mode : PortalRetainerMemberDisplay.FIRST_NAME_ROLE;

    return switch (effective) {
      case ANONYMISED -> FALLBACK;
      case ROLE_ONLY -> hasText(roleName) ? roleName : FALLBACK;
      case FULL_NAME -> {
        String name = member != null ? member.getName() : null;
        yield hasText(name) ? name.trim() : FALLBACK;
      }
      case FIRST_NAME_ROLE -> {
        String first = firstNameOf(member);
        if (!hasText(first)) {
          // No usable name — degrade gracefully. Prefer role-only over the generic fallback.
          yield hasText(roleName) ? roleName : FALLBACK;
        }
        if (!hasText(roleName)) {
          yield first;
        }
        yield first + " (" + roleName + ")";
      }
    };
  }

  private static String firstNameOf(Member member) {
    if (member == null) return null;
    String raw = member.getName();
    if (!hasText(raw)) return null;
    String trimmed = raw.trim();
    int space = trimmed.indexOf(' ');
    return space > 0 ? trimmed.substring(0, space) : trimmed;
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
