package io.b2mash.b2b.b2bstrawman.template;

import java.util.List;

/**
 * Describes whether a field pack required by a template is applied in the current org, and which
 * fields (if any) are missing.
 */
public record FieldPackStatus(
    String packId, String packName, boolean applied, List<String> missingFields) {}
