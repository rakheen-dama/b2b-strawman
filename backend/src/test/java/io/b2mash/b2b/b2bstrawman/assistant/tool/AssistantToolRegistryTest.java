package io.b2mash.b2b.b2bstrawman.assistant.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssistantToolRegistryTest {

  // --- Test-only tool stubs ---

  static class AllAccessTool implements AssistantTool {
    @Override
    public String name() {
      return "all_access_tool";
    }

    @Override
    public String description() {
      return "A tool accessible to everyone.";
    }

    @Override
    public Map<String, Object> inputSchema() {
      return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public boolean requiresConfirmation() {
      return false;
    }

    @Override
    public Set<String> requiredCapabilities() {
      return Set.of();
    }

    @Override
    public Object execute(Map<String, Object> input, TenantToolContext context) {
      return Map.of();
    }
  }

  static class FinancialTool implements AssistantTool {
    @Override
    public String name() {
      return "financial_tool";
    }

    @Override
    public String description() {
      return "A tool requiring FINANCIAL_VISIBILITY capability.";
    }

    @Override
    public Map<String, Object> inputSchema() {
      return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public boolean requiresConfirmation() {
      return false;
    }

    @Override
    public Set<String> requiredCapabilities() {
      return Set.of("FINANCIAL_VISIBILITY");
    }

    @Override
    public Object execute(Map<String, Object> input, TenantToolContext context) {
      return Map.of();
    }
  }

  // --- Tests ---

  @Test
  void registryDiscoversAllToolsFromBeanList() {
    var allAccess = new AllAccessTool();
    var financial = new FinancialTool();
    var registry = new AssistantToolRegistry(List.of(allAccess, financial));

    assertThat(registry.getTool("all_access_tool")).isSameAs(allAccess);
    assertThat(registry.getTool("financial_tool")).isSameAs(financial);
  }

  @Test
  void getToolsForUserWithFullCapabilitiesReturnsAll() {
    var allAccess = new AllAccessTool();
    var financial = new FinancialTool();
    var registry = new AssistantToolRegistry(List.of(allAccess, financial));

    var result = registry.getToolsForUser(Set.of("FINANCIAL_VISIBILITY", "INVOICING"));

    assertThat(result).containsExactlyInAnyOrder(allAccess, financial);
  }

  @Test
  void getToolsForUserWithoutFinancialVisibilityExcludesFinancialTools() {
    var allAccess = new AllAccessTool();
    var financial = new FinancialTool();
    var registry = new AssistantToolRegistry(List.of(allAccess, financial));

    var result = registry.getToolsForUser(Set.of());

    assertThat(result).containsExactly(allAccess);
    assertThat(result).doesNotContain(financial);
  }

  @Test
  void getToolThrowsIllegalArgumentExceptionForUnknownName() {
    var registry = new AssistantToolRegistry(List.of(new AllAccessTool()));

    assertThatThrownBy(() -> registry.getTool("unknown_tool"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown_tool");
  }

  @Test
  void fromRequestScopesPopulatesAllFieldsFromBoundScopes() {
    var tenantId = "tenant_abc123";
    var memberId = UUID.randomUUID();
    var orgRole = "admin";
    var capabilities = Set.of("FINANCIAL_VISIBILITY", "INVOICING");

    ScopedValue.where(RequestScopes.TENANT_ID, tenantId)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, orgRole)
        .where(RequestScopes.CAPABILITIES, capabilities)
        .run(
            () -> {
              var ctx = TenantToolContext.fromRequestScopes();
              assertThat(ctx.tenantId()).isEqualTo(tenantId);
              assertThat(ctx.memberId()).isEqualTo(memberId);
              assertThat(ctx.orgRole()).isEqualTo(orgRole);
              assertThat(ctx.capabilities()).containsExactlyInAnyOrderElementsOf(capabilities);
            });
  }
}
