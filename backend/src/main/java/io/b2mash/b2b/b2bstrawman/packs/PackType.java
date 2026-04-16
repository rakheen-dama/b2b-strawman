package io.b2mash.b2b.b2bstrawman.packs;

/**
 * Types of content packs that can be installed per tenant. Future extensibility: additional pack
 * types (e.g., CHECKLIST_TEMPLATE, REPORT_TEMPLATE) can be added as new verticals are supported.
 */
public enum PackType {
  DOCUMENT_TEMPLATE,
  AUTOMATION_TEMPLATE
}
