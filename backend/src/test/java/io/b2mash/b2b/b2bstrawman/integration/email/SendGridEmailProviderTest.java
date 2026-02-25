package io.b2mash.b2b.b2bstrawman.integration.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import io.b2mash.b2b.b2bstrawman.integration.secret.SecretStore;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SendGridEmailProviderTest {

  private static final String TEST_API_KEY = "SG.test-api-key";
  private static final String TENANT_SCHEMA = "tenant_abc123def456";

  private SecretStore secretStore;
  private SendGrid mockSendGrid;
  private Response mockResponse;
  private SendGridEmailProvider provider;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() throws IOException {
    secretStore = mock(SecretStore.class);
    mockSendGrid = mock(SendGrid.class);
    mockResponse = mock(Response.class);

    when(secretStore.retrieve(SendGridEmailProvider.SECRET_KEY)).thenReturn(TEST_API_KEY);
    when(mockResponse.getStatusCode()).thenReturn(202);
    when(mockResponse.getHeaders()).thenReturn(Map.of("X-Message-Id", "sg-msg-abc123"));
    when(mockSendGrid.api(any(Request.class))).thenReturn(mockResponse);

    // Use package-private factory constructor
    provider =
        new SendGridEmailProvider(secretStore, "noreply@docteams.app", apiKey -> mockSendGrid);
  }

  @Test
  void constructs_correct_mail_object() throws IOException {
    var message =
        EmailMessage.withTracking(
            "recipient@example.com",
            "Test Subject",
            "<h1>Hello</h1>",
            "Hello text",
            null,
            "NOTIFICATION",
            "notif-123",
            TENANT_SCHEMA);

    provider.sendEmail(message);

    ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
    verify(mockSendGrid).api(captor.capture());

    String body = captor.getValue().getBody();
    JsonNode json = objectMapper.readTree(body);
    assertThat(json.get("subject").asText()).isEqualTo("Test Subject");
    assertThat(json.get("personalizations").get(0).get("to").get(0).get("email").asText())
        .isEqualTo("recipient@example.com");
  }

  @Test
  void includes_tenant_schema_in_unique_args() throws IOException {
    var message =
        EmailMessage.withTracking(
            "user@example.com",
            "Subject",
            "<p>body</p>",
            "body",
            null,
            "INVOICE",
            "inv-456",
            TENANT_SCHEMA);

    provider.sendEmail(message);

    ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
    verify(mockSendGrid).api(captor.capture());

    String body = captor.getValue().getBody();
    JsonNode json = objectMapper.readTree(body);
    JsonNode customArgs = json.get("personalizations").get(0).get("custom_args");

    assertThat(customArgs).isNotNull();
    assertThat(customArgs.get("tenantSchema").asText()).isEqualTo(TENANT_SCHEMA);
    assertThat(customArgs.get("referenceType").asText()).isEqualTo("INVOICE");
    assertThat(customArgs.get("referenceId").asText()).isEqualTo("inv-456");
  }

  @Test
  void returns_sg_message_id() {
    var message =
        EmailMessage.withTracking(
            "recipient@example.com",
            "Subject",
            "<p>body</p>",
            "body",
            null,
            "TEST",
            "test-id",
            TENANT_SCHEMA);

    var result = provider.sendEmail(message);

    assertThat(result.success()).isTrue();
    assertThat(result.providerMessageId()).isEqualTo("sg-msg-abc123");
    assertThat(result.errorMessage()).isNull();
  }

  @Test
  void handles_api_error() throws IOException {
    when(mockResponse.getStatusCode()).thenReturn(401);
    when(mockResponse.getBody()).thenReturn("{\"errors\":[{\"message\":\"Invalid API key\"}]}");

    var message =
        new EmailMessage(
            "recipient@example.com",
            "Subject",
            "<p>body</p>",
            "body",
            null,
            Map.of("tenantSchema", TENANT_SCHEMA, "referenceType", "TEST", "referenceId", "t1"));

    var result = provider.sendEmail(message);

    assertThat(result.success()).isFalse();
    assertThat(result.providerMessageId()).isNull();
    assertThat(result.errorMessage()).contains("401");
  }

  @Test
  void includes_base64_attachment() throws IOException {
    byte[] pdfContent = "PDF binary content".getBytes();
    var message =
        EmailMessage.withTracking(
            "recipient@example.com",
            "Invoice",
            "<p>See attached</p>",
            "See attached",
            null,
            "INVOICE",
            "inv-789",
            TENANT_SCHEMA);
    var attachment = new EmailAttachment("invoice.pdf", "application/pdf", pdfContent);

    provider.sendEmailWithAttachment(message, attachment);

    ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
    verify(mockSendGrid).api(captor.capture());

    String body = captor.getValue().getBody();
    JsonNode json = objectMapper.readTree(body);
    JsonNode attachments = json.get("attachments");

    assertThat(attachments).isNotNull();
    assertThat(attachments.get(0).get("filename").asText()).isEqualTo("invoice.pdf");
    assertThat(attachments.get(0).get("type").asText()).isEqualTo("application/pdf");
    assertThat(attachments.get(0).get("content").asText())
        .isEqualTo(Base64.getEncoder().encodeToString(pdfContent));
  }

  @Test
  void testConnection_succeeds_with_valid_key() throws IOException {
    when(mockResponse.getStatusCode()).thenReturn(200);
    when(mockResponse.getHeaders()).thenReturn(Map.of());

    var result = provider.testConnection();

    assertThat(result.success()).isTrue();
    assertThat(result.providerName()).isEqualTo("sendgrid");
    assertThat(result.errorMessage()).isNull();
  }

  @Test
  void testConnection_succeeds_with_restricted_scope() throws IOException {
    when(mockResponse.getStatusCode()).thenReturn(403);
    when(mockResponse.getHeaders()).thenReturn(Map.of());

    var result = provider.testConnection();

    assertThat(result.success()).isTrue();
    assertThat(result.providerName()).isEqualTo("sendgrid");
    assertThat(result.errorMessage()).isNull();
  }

  @Test
  void testConnection_fails_with_server_error() throws IOException {
    when(mockResponse.getStatusCode()).thenReturn(500);
    when(mockResponse.getHeaders()).thenReturn(Map.of());

    var result = provider.testConnection();

    assertThat(result.success()).isFalse();
    assertThat(result.providerName()).isEqualTo("sendgrid");
    assertThat(result.errorMessage()).contains("500");
  }

  @Test
  void testConnection_fails_with_io_exception() throws IOException {
    when(mockSendGrid.api(any(Request.class))).thenThrow(new IOException("Connection refused"));

    var result = provider.testConnection();

    assertThat(result.success()).isFalse();
    assertThat(result.providerName()).isEqualTo("sendgrid");
    assertThat(result.errorMessage()).isEqualTo("Connection refused");
  }
}
