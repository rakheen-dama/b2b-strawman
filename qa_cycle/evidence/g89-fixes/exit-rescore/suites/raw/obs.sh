#!/usr/bin/env bash
# G89 exit re-score: read-only pack probe only (pack_install incl. legal-za + trust-reports).
OBS(){ echo $RAW/obs-$1.txt; }

# (1) legal-za pack version + the SoA template row the tenant actually holds
pack_probe(){ # label
  local L=$1 T; T=$(tenant); local F; F=$(OBS pack-$L)
  {
  echo "===== pack probe $L $(ts) tenant=$T"
  echo "-- pack_install (all rows)"
  psql_e2e "select pack_id||'|'||pack_type||'|'||pack_version||'|'||installed_at from $T.pack_install order by pack_id"
  echo "-- pack_install legal-za document template pack_version: $(psql_e2e "select pack_version from $T.pack_install where pack_id='legal-za'")"
  echo "-- org_settings template pack status (legal-za):"
  psql_e2e "select template_pack_status::text from $T.org_settings" 2>&1 | grep -o '{[^{}]*legal-za[^{}]*}'
  echo "-- document_templates statement-of-account"
  psql_e2e "select pack_id||'|'||pack_template_key||'|content_hash='||coalesce(content_hash,'null')||'|updated='||updated_at||'|hasTypeLabelKey='||(content::text like '%\"transactionTypeLabel\"%')||'|hasRawTypeKey='||(content::text like '%\"key\": \"transactionType\"%') from $T.document_templates where pack_template_key='statement-of-account'"
  echo "-- shipped v15 'to' hash: sha256:2d513fa18c853ba7a7c93a9786610f79e9dbf1b999bd6c652ead8e88fc67f384 ; v14 'to' hash: sha256:b49ceb09dd24aad3137163e648fcedd4c04495674d7ca57de00088ce784a3795"
  echo "-- backend log: legal-za / reconcile / pack lines"
  docker logs e2e-backend 2>&1 | grep -iE 'legal-za|reconcil' | grep -iE 'template|reconcil' | sed -E 's/.*"message":"([^"]*)".*"level":"([A-Z]+)".*/\2 \1/; s/.*"level":"([A-Z]+)".*"message":"([^"]*)".*/\1 \2/' | cut -c1-260 | sort | uniq -c
  echo "-- backend log WARN/ERROR mentioning reconcile/pack: $(docker logs e2e-backend 2>&1 | grep -E '"level":"(WARN|ERROR)"' | grep -ciE 'reconcil|pack')"
  } >> $F 2>&1
}

