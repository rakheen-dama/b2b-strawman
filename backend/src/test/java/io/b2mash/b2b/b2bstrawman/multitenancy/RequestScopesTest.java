package io.b2mash.b2b.b2bstrawman.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RequestScopesTest {

  @Test
  void tenantIdBoundWithinScope() {
    ScopedValue.where(RequestScopes.TENANT_ID, "tenant_a1b2c3d4e5f6")
        .run(
            () -> {
              assertThat(RequestScopes.TENANT_ID.get()).isEqualTo("tenant_a1b2c3d4e5f6");
              assertThat(RequestScopes.TENANT_ID.isBound()).isTrue();
            });
  }

  @Test
  void tenantIdUnboundOutsideScope() {
    assertThat(RequestScopes.TENANT_ID.isBound()).isFalse();
    assertThatThrownBy(() -> RequestScopes.TENANT_ID.get())
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void tenantIdAutoUnboundsAfterScope() {
    ScopedValue.where(RequestScopes.TENANT_ID, "tenant_a1b2c3d4e5f6")
        .run(
            () -> {
              assertThat(RequestScopes.TENANT_ID.isBound()).isTrue();
            });
    assertThat(RequestScopes.TENANT_ID.isBound()).isFalse();
  }

  @Test
  void nestedScopeShadowsOuter() {
    ScopedValue.where(RequestScopes.TENANT_ID, "outer")
        .run(
            () -> {
              assertThat(RequestScopes.TENANT_ID.get()).isEqualTo("outer");

              ScopedValue.where(RequestScopes.TENANT_ID, "inner")
                  .run(
                      () -> {
                        assertThat(RequestScopes.TENANT_ID.get()).isEqualTo("inner");
                      });

              assertThat(RequestScopes.TENANT_ID.get()).isEqualTo("outer");
            });
  }

  @Test
  void memberIdBoundWithinScope() {
    UUID id = UUID.randomUUID();
    ScopedValue.where(RequestScopes.MEMBER_ID, id)
        .run(
            () -> {
              assertThat(RequestScopes.MEMBER_ID.get()).isEqualTo(id);
              assertThat(RequestScopes.MEMBER_ID.isBound()).isTrue();
            });
  }

  @Test
  void scopedValueAutoUnbindsOnException() {
    assertThatThrownBy(
            () ->
                ScopedValue.where(RequestScopes.TENANT_ID, "test")
                    .run(
                        () -> {
                          throw new RuntimeException("test");
                        }))
        .isInstanceOf(RuntimeException.class);
    assertThat(RequestScopes.TENANT_ID.isBound()).isFalse();
  }

  @Test
  void multipleBindingsWorkTogether() {
    UUID id = UUID.randomUUID();
    ScopedValue.where(RequestScopes.TENANT_ID, "tenant_abc")
        .where(RequestScopes.MEMBER_ID, id)
        .where(RequestScopes.ORG_ROLE, "admin")
        .run(
            () -> {
              assertThat(RequestScopes.TENANT_ID.get()).isEqualTo("tenant_abc");
              assertThat(RequestScopes.MEMBER_ID.get()).isEqualTo(id);
              assertThat(RequestScopes.ORG_ROLE.get()).isEqualTo("admin");
            });
  }
}
