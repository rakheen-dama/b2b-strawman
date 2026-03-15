package io.b2mash.b2b.b2bstrawman.seeder;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AccountingProfileManifestTest {

  @Test
  void profileManifestIsValidJson() throws Exception {
    var mapper = new ObjectMapper();
    try (var stream = getClass().getResourceAsStream("/vertical-profiles/accounting-za.json")) {
      assertThat(stream).isNotNull();
      var node = mapper.readTree(stream);
      assertThat(node.get("profileId").asText()).isEqualTo("accounting-za");
      assertThat(node.get("packs").has("field")).isTrue();
      assertThat(node.get("packs").has("compliance")).isTrue();
      assertThat(node.get("packs").has("template")).isTrue();
      assertThat(node.get("packs").has("clause")).isTrue();
      assertThat(node.get("packs").has("automation")).isTrue();
      assertThat(node.get("packs").has("request")).isTrue();
    }
  }
}
