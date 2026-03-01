#!/bin/sh
# compose/seed/lib/documents.sh — Upload documents via presigned URL flow
# Requires: lib/common.sh, project IDs, customer IDs

seed_documents() {
  echo ""
  echo "==> Seeding documents"
  jwt=$(get_jwt user_e2e_alice owner)

  # Helper: upload a document
  # _upload_doc <upload_init_path> <list_path> <file_name> <content_type> <content> <label>
  _upload_doc() {
    _ud_init_path="$1"
    _ud_list_path="$2"
    _ud_file_name="$3"
    _ud_content_type="$4"
    _ud_content="$5"
    _ud_label="$6"

    # Check if document exists (bare array response)
    _ud_existing=$(api_get "$_ud_list_path" "$jwt" \
      | jq -r ".[] | select(.fileName == \"${_ud_file_name}\") | .id" 2>/dev/null)
    if [ -n "$_ud_existing" ] && [ "$_ud_existing" != "null" ]; then
      echo "    [skip] ${_ud_label} (${_ud_existing})"
      return 0
    fi

    _ud_size=$(echo -n "$_ud_content" | wc -c | tr -d ' ')

    # Step 1: Init upload
    _ud_init_body=$(api_post "$_ud_init_path" "{
      \"fileName\": \"${_ud_file_name}\",
      \"contentType\": \"${_ud_content_type}\",
      \"size\": ${_ud_size}
    }" "$jwt")
    check_status "Init ${_ud_label}" || return 1

    _ud_doc_id=$(echo "$_ud_init_body" | jq -r '.documentId')
    _ud_presigned_url=$(echo "$_ud_init_body" | jq -r '.presignedUrl')

    # Rewrite Docker-internal hostname for host-side execution
    # Backend generates URLs with localstack:4566 but host needs localhost:4566
    if echo "$_ud_presigned_url" | grep -q "localstack:4566"; then
      _ud_presigned_url=$(echo "$_ud_presigned_url" | sed 's|http://localstack:4566|http://localhost:4566|')
    fi

    # Step 2: Upload content to S3
    curl -sf -X PUT "$_ud_presigned_url" \
      -H "Content-Type: ${_ud_content_type}" \
      -d "$_ud_content" > /dev/null
    echo "    [ok] Uploaded to S3" >&2

    # Step 3: Confirm
    api_post "/api/documents/${_ud_doc_id}/confirm" '{}' "$jwt" > /dev/null
    check_status "Confirm ${_ud_label}"

    echo "$_ud_doc_id"
  }

  # ── Project-scoped: design mockup ──────────────────────────────
  _upload_doc \
    "/api/projects/${WEBSITE_REDESIGN_ID}/documents/upload-init" \
    "/api/projects/${WEBSITE_REDESIGN_ID}/documents" \
    "design-mockup.pdf" "application/pdf" \
    "Placeholder content for design mockup document" \
    "design-mockup.pdf (Website Redesign)"

  # ── Customer-scoped: service agreement ─────────────────────────
  _upload_doc \
    "/api/customers/${ACME_ID}/documents/upload-init" \
    "/api/documents?scope=CUSTOMER&customerId=${ACME_ID}" \
    "service-agreement.pdf" "application/pdf" \
    "Placeholder content for service agreement document" \
    "service-agreement.pdf (Acme Corp)"

  # ── Org-scoped: company policies ───────────────────────────────
  _upload_doc \
    "/api/documents/upload-init" \
    "/api/documents?scope=ORG" \
    "company-policies.pdf" "application/pdf" \
    "Placeholder content for company policies document" \
    "company-policies.pdf (Org)"

  echo ""
  echo "    Documents seeded: 3 documents"
}
