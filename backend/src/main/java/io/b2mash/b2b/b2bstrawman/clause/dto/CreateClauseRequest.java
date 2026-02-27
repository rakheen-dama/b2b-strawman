package io.b2mash.b2b.b2bstrawman.clause.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateClauseRequest(
    @NotBlank(message = "title is required")
        @Size(max = 200, message = "title must not exceed 200 characters")
        String title,
    @Size(max = 500, message = "description must not exceed 500 characters") String description,
    @NotBlank(message = "body is required") String body,
    @NotBlank(message = "category is required")
        @Size(max = 100, message = "category must not exceed 100 characters")
        String category) {}
