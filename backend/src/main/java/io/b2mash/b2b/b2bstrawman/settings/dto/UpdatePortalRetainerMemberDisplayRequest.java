package io.b2mash.b2b.b2bstrawman.settings.dto;

import io.b2mash.b2b.b2bstrawman.settings.PortalRetainerMemberDisplay;
import jakarta.validation.constraints.NotNull;

/** Request to update how firm member names are displayed on the portal retainer list. */
public record UpdatePortalRetainerMemberDisplayRequest(
    @NotNull(message = "portalRetainerMemberDisplay is required")
        PortalRetainerMemberDisplay portalRetainerMemberDisplay) {}
