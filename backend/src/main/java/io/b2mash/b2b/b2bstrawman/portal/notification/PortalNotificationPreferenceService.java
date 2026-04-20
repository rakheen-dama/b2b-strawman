package io.b2mash.b2b.b2bstrawman.portal.notification;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service scaffolding for portal notification preferences (Epic 498A, ADR-258). Scope is
 * intentionally minimal — just {@link #getOrCreate(UUID)} and {@link #update(UUID,
 * PortalNotificationPreferenceUpdate)}. No scheduling, email-channel wiring, or REST controller yet
 * — those ship in 498B / 498C.
 */
@Service
public class PortalNotificationPreferenceService {

  private final PortalNotificationPreferenceRepository repository;

  public PortalNotificationPreferenceService(PortalNotificationPreferenceRepository repository) {
    this.repository = repository;
  }

  /**
   * Returns the preference row for the given portal contact, creating a new one with all five
   * toggles defaulted to {@code true} when absent. Idempotent after the first call — subsequent
   * calls read the persisted row without resetting it. Safe against the race where two concurrent
   * callers both miss {@link PortalNotificationPreferenceRepository#findById(Object)}: the losing
   * insert raises {@link DataIntegrityViolationException} and falls back to a re-read.
   */
  @Transactional
  public PortalNotificationPreference getOrCreate(UUID portalContactId) {
    if (portalContactId == null) {
      throw new InvalidStateException("Missing field", "portalContactId must not be null");
    }
    var existing = repository.findById(portalContactId);
    if (existing.isPresent()) {
      return existing.get();
    }
    try {
      return repository.saveAndFlush(PortalNotificationPreference.allEnabled(portalContactId));
    } catch (DataIntegrityViolationException ex) {
      return repository.findById(portalContactId).orElseThrow(() -> ex);
    }
  }

  /**
   * Persists an update to all five toggles for the given portal contact. Get-or-creates the row
   * first, then applies the five values and stamps {@code lastUpdatedAt}.
   */
  @Transactional
  public PortalNotificationPreference update(
      UUID portalContactId, PortalNotificationPreferenceUpdate dto) {
    if (portalContactId == null) {
      throw new InvalidStateException("Missing field", "portalContactId must not be null");
    }
    if (dto == null) {
      throw new InvalidStateException("Missing field", "dto must not be null");
    }
    var pref = getOrCreate(portalContactId);
    pref.update(
        dto.digestEnabled(),
        dto.trustActivityEnabled(),
        dto.retainerUpdatesEnabled(),
        dto.deadlineRemindersEnabled(),
        dto.actionRequiredEnabled());
    return repository.save(pref);
  }

  /** DTO carrying the five boolean toggles for a preference update. */
  public record PortalNotificationPreferenceUpdate(
      boolean digestEnabled,
      boolean trustActivityEnabled,
      boolean retainerUpdatesEnabled,
      boolean deadlineRemindersEnabled,
      boolean actionRequiredEnabled) {}
}
