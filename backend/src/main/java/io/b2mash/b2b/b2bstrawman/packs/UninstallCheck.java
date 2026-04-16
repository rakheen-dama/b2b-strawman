package io.b2mash.b2b.b2bstrawman.packs;

/**
 * Result of a pre-check before uninstalling a pack. If {@code canUninstall} is false, {@code
 * blockingReason} explains why.
 *
 * @param canUninstall whether the pack can be safely uninstalled
 * @param blockingReason human-readable reason uninstall is blocked, or null if allowed
 */
public record UninstallCheck(boolean canUninstall, String blockingReason) {}
