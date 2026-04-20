package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Sanitises firm-side trust transaction descriptions before they are persisted to the portal
 * read-model. Per ADR-254, the pipeline is:
 *
 * <ol>
 *   <li>Strip a leading {@code [internal]} tag (case-insensitive, optional whitespace) — when the
 *       firm user prefixes a description with {@code [internal]} they are signalling that the rest
 *       of the text is for internal consumption only. In that case the description is dropped.
 *   <li>Truncate the remaining text to 140 characters with an ellipsis marker.
 *   <li>If the sanitised result is blank, synthesise a fallback from the transaction type label and
 *       matter reference, e.g. {@code "DEPOSIT — TX-2026-0012"}.
 * </ol>
 *
 * The sanitiser runs at sync time (inside {@code TrustLedgerPortalSyncService}); the portal schema
 * never stores the raw firm-side description.
 */
@Component
public class PortalTrustDescriptionSanitiser {

  /**
   * 140-char cap matches the portal list-view single-line constraint. Leaves room for an ellipsis
   * marker when truncating.
   */
  private static final int MAX_LENGTH = 140;

  /**
   * Matches an optional leading whitespace run, then the literal {@code [internal]} tag (case
   * insensitive), then any trailing whitespace — consumes everything up to the meaningful start of
   * the description.
   */
  private static final Pattern INTERNAL_TAG =
      Pattern.compile("^\\s*\\[internal]\\s*", Pattern.CASE_INSENSITIVE);

  /**
   * Sanitises {@code raw} per ADR-254. Never returns {@code null}; returns a non-empty fallback
   * when the sanitised input is empty.
   */
  public String sanitise(String raw, String fallbackTypeLabel, String matterRef) {
    String stripped = stripInternalTag(raw);
    String truncated = truncate(stripped);
    if (truncated == null || truncated.isBlank()) {
      // Fallback also passes through truncate so a pathological fallbackTypeLabel/matterRef
      // combination can never exceed the column's 140-char cap.
      return truncate(synthesiseFallback(fallbackTypeLabel, matterRef));
    }
    return truncated;
  }

  private static String stripInternalTag(String raw) {
    if (raw == null) {
      return "";
    }
    var matcher = INTERNAL_TAG.matcher(raw);
    if (matcher.find()) {
      // When the raw description starts with the [internal] marker, drop the entire description —
      // ADR-254: the tag indicates firm-only content.
      return "";
    }
    return raw.strip();
  }

  private static String truncate(String value) {
    if (value == null) {
      return "";
    }
    if (value.length() <= MAX_LENGTH) {
      return value;
    }
    // Preserve 139 chars plus an ellipsis so the total stays within the 140 constraint.
    return value.substring(0, MAX_LENGTH - 1) + "\u2026";
  }

  private static String synthesiseFallback(String fallbackTypeLabel, String matterRef) {
    String type = fallbackTypeLabel == null ? "" : fallbackTypeLabel.strip();
    String ref = matterRef == null ? "" : matterRef.strip();
    if (type.isEmpty() && ref.isEmpty()) {
      return "Trust transaction";
    }
    if (ref.isEmpty()) {
      return type;
    }
    if (type.isEmpty()) {
      return ref;
    }
    return type + " \u2014 " + ref;
  }
}
