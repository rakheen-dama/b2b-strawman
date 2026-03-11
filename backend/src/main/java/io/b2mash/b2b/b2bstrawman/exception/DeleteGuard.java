package io.b2mash.b2b.b2bstrawman.exception;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Fluent guard for checking linked resources before deleting an entity. Collects all violations and
 * throws a single {@link ResourceConflictException} with a descriptive message.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * DeleteGuard.forEntity("customer", customerId)
 *     .checkNotExists("linked projects", () -> projectRepository.existsByCustomerId(id))
 *     .checkCountZero("invoices", invoiceRepository.countByCustomerId(id),
 *         "Void or delete all invoices first.")
 *     .execute();
 * }</pre>
 */
public class DeleteGuard {

  private final String entityName;
  private final UUID entityId;
  private final String verb;
  private final List<String> violations = new ArrayList<>();

  private DeleteGuard(String entityName, UUID entityId, String verb) {
    this.entityName = entityName;
    this.entityId = entityId;
    this.verb = verb;
  }

  public static DeleteGuard forEntity(String entityName, UUID entityId) {
    return new DeleteGuard(entityName, entityId, "delete");
  }

  /** Creates a guard with a custom verb (e.g., "archive" instead of "delete"). */
  public static DeleteGuard forEntity(String entityName, UUID entityId, String verb) {
    return new DeleteGuard(entityName, entityId, verb);
  }

  /**
   * Adds a violation if the supplier returns {@code true}, indicating that linked resources exist.
   */
  public DeleteGuard checkNotExists(String resource, Supplier<Boolean> exists, String remedy) {
    if (exists.get()) {
      violations.add("Cannot %s %s with %s. %s".formatted(verb, entityName, resource, remedy));
    }
    return this;
  }

  /** Adds a violation if the count is greater than zero, indicating that linked resources exist. */
  public DeleteGuard checkCountZero(String resource, long count, String remedy) {
    if (count > 0) {
      violations.add(
          "Cannot %s %s with %d %s. %s".formatted(verb, entityName, count, resource, remedy));
    }
    return this;
  }

  /**
   * Executes the guard. If any violations were recorded, throws a {@link ResourceConflictException}
   * with all violation messages joined.
   */
  public void execute() {
    if (!violations.isEmpty()) {
      throw new ResourceConflictException(
          "Cannot %s %s".formatted(verb, entityName), String.join(" ", violations));
    }
  }
}
