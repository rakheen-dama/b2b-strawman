package io.b2mash.b2b.b2bstrawman.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantContextTest {

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void shouldSetAndGetTenantId() {
    TenantContext.setTenantId("tenant_a1b2c3d4e5f6");
    assertThat(TenantContext.getTenantId()).isEqualTo("tenant_a1b2c3d4e5f6");
  }

  @Test
  void shouldReturnNullWhenNotSet() {
    assertThat(TenantContext.getTenantId()).isNull();
  }

  @Test
  void shouldClearTenantId() {
    TenantContext.setTenantId("tenant_a1b2c3d4e5f6");
    TenantContext.clear();
    assertThat(TenantContext.getTenantId()).isNull();
  }
}
