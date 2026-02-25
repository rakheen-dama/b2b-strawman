package io.b2mash.b2b.b2bstrawman.invoice.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record UpdateCustomFieldsRequest(@NotNull Map<String, Object> customFields) {}
