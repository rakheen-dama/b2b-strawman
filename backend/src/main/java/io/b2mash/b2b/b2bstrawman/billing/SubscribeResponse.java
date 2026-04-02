package io.b2mash.b2b.b2bstrawman.billing;

import java.util.Map;

public record SubscribeResponse(String paymentUrl, Map<String, String> formFields) {}
