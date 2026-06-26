package io.b2mash.b2b.b2bstrawman.mcp.dto;

import java.util.List;

/**
 * Response for the {@code resolve_matter_by_email} read tool (Epic 584A, §N.3.c). Lets Claude
 * disambiguate the right matter before calling the write tools. {@code customer} is {@code null}
 * when no client matches the email; {@code matters} lists ALL matters linked to the matched
 * customer (zero, one, or many) — Kazi never auto-files on a guess.
 */
public record ResolveMatterResponse(McpClientDto customer, List<McpMatterDto> matters) {}
