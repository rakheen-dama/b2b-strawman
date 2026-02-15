package io.b2mash.b2b.b2bstrawman.fielddefinition.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record SetFieldGroupsRequest(@NotNull List<UUID> appliedFieldGroups) {}
