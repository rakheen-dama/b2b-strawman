package io.b2mash.b2b.b2bstrawman.fielddefinition.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record ReorderFieldsRequest(@NotEmpty List<UUID> fieldIds) {}
