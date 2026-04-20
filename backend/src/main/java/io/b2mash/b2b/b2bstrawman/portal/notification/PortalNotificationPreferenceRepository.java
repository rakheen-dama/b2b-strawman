package io.b2mash.b2b.b2bstrawman.portal.notification;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link PortalNotificationPreference}. Rows live in the global {@code portal}
 * schema, keyed by the portal contact's UUID (no FK — {@code portal_contacts} is per-tenant, see
 * V22 migration rationale).
 */
public interface PortalNotificationPreferenceRepository
    extends JpaRepository<PortalNotificationPreference, UUID> {}
