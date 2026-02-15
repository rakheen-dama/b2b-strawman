package io.b2mash.b2b.b2bstrawman.tag.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record SetEntityTagsRequest(@NotNull(message = "tagIds is required") List<UUID> tagIds) {}
