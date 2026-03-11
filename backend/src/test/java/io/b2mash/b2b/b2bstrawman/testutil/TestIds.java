package io.b2mash.b2b.b2bstrawman.testutil;

import java.lang.reflect.Field;
import java.util.UUID;

/**
 * Shared reflection helper for injecting IDs into JPA entities in tests. Centralises the common
 * getDeclaredField("id") / setAccessible pattern so that field-name changes break in exactly one
 * place.
 */
public final class TestIds {

  private TestIds() {}

  /**
   * Sets the {@code id} field on a JPA entity via reflection. Returns the entity for fluent
   * chaining.
   */
  public static <T> T withId(T entity, UUID id) {
    setField(entity, "id", id);
    return entity;
  }

  /**
   * Sets an arbitrary field on an entity via reflection. Useful for fields other than {@code id}
   * (e.g. taxRateId, taxRateName).
   */
  public static <T> T withField(T entity, String fieldName, Object value) {
    setField(entity, fieldName, value);
    return entity;
  }

  private static void setField(Object entity, String fieldName, Object value) {
    try {
      Field field = findField(entity.getClass(), fieldName);
      field.setAccessible(true);
      field.set(entity, value);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(
          "Failed to set field '%s' on %s".formatted(fieldName, entity.getClass().getSimpleName()),
          e);
    }
  }

  /** Walks the class hierarchy to find the field — handles inherited fields. */
  private static Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
    Class<?> current = clazz;
    while (current != null) {
      try {
        return current.getDeclaredField(fieldName);
      } catch (NoSuchFieldException e) {
        current = current.getSuperclass();
      }
    }
    throw new NoSuchFieldException(
        "Field '%s' not found in %s or its superclasses"
            .formatted(fieldName, clazz.getSimpleName()));
  }
}
