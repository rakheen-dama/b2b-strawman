package io.b2mash.b2b.b2bstrawman.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.mcp.McpEnablementService;
import io.b2mash.b2b.b2bstrawman.mcp.McpMetrics;
import io.b2mash.b2b.b2bstrawman.mcp.McpPagination;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Plain unit test (no Spring context) for the response-size ceiling guard on {@code list_clients}.
 * When the full materialised result set exceeds {@link McpPagination#RESPONSE_ITEM_CEILING}, the
 * tool must fail with a non-leaking {@code response_too_large} {@link CallToolResult} ({@code
 * isError:true}) rather than ever emitting a truncated page. {@code list_clients} is the cheapest
 * tool to drive here because it builds the list straight from {@code CustomerService} (which we
 * mock) with no actor/audit dependency on the guard path. The other two guarded tools ({@code
 * list_matters}, {@code search_documents}) share the identical guard idiom.
 */
class ClientToolsCeilingTest {

  @Test
  void listClientsReturnsResponseTooLargeWhenResultSetExceedsCeiling() {
    CustomerService customerService = mock(CustomerService.class);
    CustomerRepository customerRepository = mock(CustomerRepository.class);
    CustomerProjectService customerProjectService = mock(CustomerProjectService.class);
    AuditService auditService = mock(AuditService.class);
    ObjectMapper objectMapper = new ObjectMapper();
    McpEnablementService enablement = mock(McpEnablementService.class);
    when(enablement.effectiveState()).thenReturn(true);

    // One more than the ceiling so the guard trips (exceedsResponseCeiling is strictly >).
    int overCeiling = McpPagination.RESPONSE_ITEM_CEILING + 1;
    List<Customer> customers =
        IntStream.range(0, overCeiling)
            .mapToObj(
                i -> {
                  Customer c = mock(Customer.class);
                  when(c.getId()).thenReturn(UUID.randomUUID());
                  return c;
                })
            .toList();
    when(customerService.listCustomers()).thenReturn(customers);

    McpMetrics metrics = new McpMetrics(new SimpleMeterRegistry());
    ClientTools tools =
        new ClientTools(
            customerService,
            customerRepository,
            customerProjectService,
            auditService,
            objectMapper,
            enablement,
            metrics);

    Object result = tools.listClients(0, 50, null);

    assertThat(result).isInstanceOf(CallToolResult.class);
    CallToolResult toolResult = (CallToolResult) result;
    assertThat(toolResult.isError()).isTrue();
    String text = ((TextContent) toolResult.content().get(0)).text();
    assertThat(text).contains("response_too_large");
  }
}
