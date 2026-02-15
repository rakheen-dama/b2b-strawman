package io.b2mash.b2b.b2bstrawman.tag.dto;

import java.util.List;
import java.util.UUID;

public record SetEntityTagsRequest(List<UUID> tagIds) {}
