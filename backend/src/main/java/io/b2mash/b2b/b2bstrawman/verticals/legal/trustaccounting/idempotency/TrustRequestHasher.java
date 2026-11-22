package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.idempotency;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.WebUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The {@code request_hash} of a trust write (648B F-11): SHA-256 (hex) over the method, the route
 * template, the path variables (sorted), then either
 *
 * <ul>
 *   <li>a JSON request: the query string and the body normalised (object members sorted, no
 *       insignificant whitespace; a body that is not JSON is hashed as its bytes), or
 *   <li>a multipart request: every parameter / non-file part (sorted by name) and every file
 *       (sorted by part name) by its original name, content type, size and the SHA-256 of its
 *       bytes, digested through a stream.
 * </ul>
 *
 * So the same request hashes the same however its JSON is laid out, and a changed amount, account,
 * client or file hashes differently.
 */
@Component
class TrustRequestHasher {

  private final ObjectMapper objectMapper;

  TrustRequestHasher(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  String hash(HttpServletRequest request, String routeTemplate) {
    var digest = sha256();
    update(digest, request.getMethod());
    update(digest, routeTemplate);
    if (request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE)
        instanceof Map<?, ?> vars) {
      new TreeMap<>(vars).forEach((k, v) -> update(digest, k + "=" + v));
    }
    var multipart = WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class);
    if (multipart != null) {
      updateMultipart(digest, multipart);
    } else {
      update(digest, "query:" + (request.getQueryString() == null ? "" : request.getQueryString()));
      var buffered = WebUtils.getNativeRequest(request, BufferedBodyRequest.class);
      if (buffered == null) {
        // Never hash a body we did not read: two different requests would share a hash.
        throw new IllegalStateException("Trust write body was not buffered for idempotency");
      }
      update(digest, "body:" + canonicalBody(buffered.body()));
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  private void updateMultipart(MessageDigest digest, MultipartHttpServletRequest multipart) {
    new TreeMap<>(multipart.getParameterMap())
        .forEach(
            (name, values) -> update(digest, "param:" + name + "=" + String.join(",", values)));
    new TreeMap<>(multipart.getMultiFileMap())
        .forEach(
            (name, files) -> {
              for (MultipartFile file : files) {
                update(
                    digest,
                    "file:%s|%s|%s|%d|%s"
                        .formatted(
                            name,
                            file.getOriginalFilename(),
                            file.getContentType(),
                            file.getSize(),
                            streamedSha256Hex(file)));
              }
            });
  }

  private String canonicalBody(byte[] body) {
    if (body.length == 0) {
      return "";
    }
    try {
      var sb = new StringBuilder();
      writeCanonical(objectMapper.readTree(body), sb);
      return sb.toString();
    } catch (JacksonException e) {
      return "bytes:" + sha256Hex(body);
    }
  }

  private void writeCanonical(JsonNode node, StringBuilder sb) {
    if (node.isObject()) {
      sb.append('{');
      var sorted = new TreeMap<String, JsonNode>();
      node.properties().forEach(e -> sorted.put(e.getKey(), e.getValue()));
      boolean first = true;
      for (var e : sorted.entrySet()) {
        if (!first) {
          sb.append(',');
        }
        first = false;
        sb.append(objectMapper.writeValueAsString(e.getKey())).append(':');
        writeCanonical(e.getValue(), sb);
      }
      sb.append('}');
    } else if (node.isArray()) {
      sb.append('[');
      boolean first = true;
      for (JsonNode child : node.values()) {
        if (!first) {
          sb.append(',');
        }
        first = false;
        writeCanonical(child, sb);
      }
      sb.append(']');
    } else {
      sb.append(node.toString());
    }
  }

  /** The file's SHA-256, read through a stream: the part is never copied into memory. */
  private static String streamedSha256Hex(MultipartFile file) {
    var digest = sha256();
    try (var in = new DigestInputStream(file.getInputStream(), digest)) {
      in.transferTo(OutputStream.nullOutputStream());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  private static void update(MessageDigest digest, String part) {
    digest.update(part.getBytes(StandardCharsets.UTF_8));
    digest.update((byte) '\n');
  }

  private static String sha256Hex(byte[] bytes) {
    return HexFormat.of().formatHex(sha256().digest(bytes));
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }
}
