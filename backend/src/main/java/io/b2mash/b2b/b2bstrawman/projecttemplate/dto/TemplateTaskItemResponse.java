package io.b2mash.b2b.b2bstrawman.projecttemplate.dto;

import java.util.UUID;

public record TemplateTaskItemResponse(UUID id, String title, int sortOrder) {}
