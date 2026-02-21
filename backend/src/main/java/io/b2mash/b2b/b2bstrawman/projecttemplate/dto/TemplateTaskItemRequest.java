package io.b2mash.b2b.b2bstrawman.projecttemplate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TemplateTaskItemRequest(@NotBlank @Size(max = 500) String title, int sortOrder) {}
